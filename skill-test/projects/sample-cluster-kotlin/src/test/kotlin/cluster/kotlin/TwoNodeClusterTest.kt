package cluster.kotlin

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.receptionist.Receptionist
import org.apache.pekko.actor.typed.receptionist.ServiceKey
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.Cluster
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration

/**
 * 2-Node Cluster Test: seed 노드(고정 포트 25521)와 joining 노드(자동 포트)로
 * 실제 2노드 클러스터를 구성하여 크로스노드 통신을 검증한다.
 * 기존 1-Node 테스트(ClusterActorTest)는 그대로 유지된다.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TwoNodeClusterTest {
    companion object {
        private lateinit var seedTestKit: ActorTestKit
        private lateinit var joiningTestKit: ActorTestKit

        @JvmStatic
        @BeforeAll
        fun setup() {
            // Node 1: seed 노드 (고정 포트 25521)
            val seedConfig = ConfigFactory.load("two-node-seed")
            seedTestKit = ActorTestKit.create("TwoNodeClusterSystem", seedConfig)

            // Node 2: joining 노드 (자동 포트, seed-nodes로 Node 1 지정)
            val joiningConfig = ConfigFactory.load("two-node-joining")
            joiningTestKit = ActorTestKit.create("TwoNodeClusterSystem", joiningConfig)

            // 두 노드 모두 Up 상태가 될 때까지 폴링 대기
            waitForClusterUp(seedTestKit, 2, 15)
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            // joining 노드 먼저 shutdown
            joiningTestKit.shutdownTestKit()
            seedTestKit.shutdownTestKit()
        }

        private fun waitForClusterUp(testKit: ActorTestKit, expectedMembers: Int, timeoutSeconds: Int) {
            val observer = testKit.createTestProbe<String>()
            observer.awaitAssert(Duration.ofSeconds(timeoutSeconds.toLong()), Duration.ofMillis(200)) {
                val upCount = Cluster.get(testKit.system()).state().members.count {
                    it.status() == MemberStatus.up()
                }
                require(upCount >= expectedMembers) {
                    "Expected at least $expectedMembers Up members but got $upCount"
                }
                "cluster-ready"
            }
        }
    }

    @Test
    @Order(1)
    fun `both nodes should be Up in cluster`() {
        val seedCluster = Cluster.get(seedTestKit.system())
        val joiningCluster = Cluster.get(joiningTestKit.system())

        // 양쪽 노드 모두 2개의 멤버를 인식
        val seedMemberCount = seedCluster.state().members.count()
        val joiningMemberCount = joiningCluster.state().members.count()
        assertEquals(2, seedMemberCount, "Seed should see 2 members")
        assertEquals(2, joiningMemberCount, "Joining should see 2 members")

        // 모든 멤버가 Up 상태
        seedCluster.state().members.forEach {
            assertEquals(MemberStatus.up(), it.status(),
                "Member ${it.address()} should be Up")
        }
    }

    @Test
    @Order(2)
    fun `cluster listener should receive two MemberUp events`() {
        val probe = seedTestKit.createTestProbe<String>()
        seedTestKit.spawn(ClusterListenerActor.create(probe.ref()), "listener-2node")

        // initialStateAsEvents 구독이므로 이미 Up인 2개 노드에 대해 MemberUp 이벤트 수신
        probe.expectMessage(Duration.ofSeconds(5), "member-up")
        probe.expectMessage(Duration.ofSeconds(5), "member-up")
        probe.expectNoMessage(Duration.ofMillis(500))
    }

    @Test
    @Order(3)
    fun `counter should work across nodes via Receptionist`() {
        // Seed 노드에 카운터 액터 생성 및 Receptionist에 등록
        val counterKey = ServiceKey.create(CounterCommand::class.java, "counter-2node")
        val counter = seedTestKit.spawn(CounterSingletonActor.create(), "counter-2node")
        seedTestKit.system().receptionist().tell(
            Receptionist.register(counterKey, counter)
        )

        // Seed 노드에서 2회 증가
        counter.tell(Increment)
        counter.tell(Increment)

        // Joining 노드에서 Receptionist를 통해 카운터 디스커버리
        val listingProbe = joiningTestKit.createTestProbe<Receptionist.Listing>()
        joiningTestKit.system().receptionist().tell(
            Receptionist.subscribe(counterKey, listingProbe.ref())
        )

        val remoteCounter = listingProbe.awaitAssert(Duration.ofSeconds(10), Duration.ofMillis(200)) {
            val listing = listingProbe.receiveMessage(Duration.ofMillis(500))
            val instances = listing.getServiceInstances(counterKey)
            require(instances.isNotEmpty()) { "Counter service is not yet discoverable on joining node" }
            instances.first()
        }

        // Joining 노드에서 1회 증가
        remoteCounter.tell(Increment)

        // Joining 노드에서 카운트 조회
        val resultProbe = joiningTestKit.createTestProbe<CounterCommand>()
        resultProbe.awaitAssert(Duration.ofSeconds(5), Duration.ofMillis(200)) {
            remoteCounter.tell(GetCount(resultProbe.ref()))
            val result = resultProbe.expectMessageClass(CountValue::class.java, Duration.ofMillis(500))
            assertEquals(3, result.value, "Counter should be 3 after cross-node increments")
            result
        }
    }

    @Test
    @Order(4)
    fun `pubsub should deliver across nodes`() {
        // 양쪽 노드에 PubSubManager 생성 (Topic은 클러스터를 통해 자동 디스커버리)
        val seedPubSub = seedTestKit.spawn(PubSubManagerActor.create(), "pubsub-seed-2node")
        val joiningPubSub = joiningTestKit.spawn(PubSubManagerActor.create(), "pubsub-joining-2node")

        val subscriberProbe = joiningTestKit.createTestProbe<String>()
        val seedDummyProbe = seedTestKit.createTestProbe<String>()

        // 양쪽 노드에서 동일 토픽에 구독 → Topic 액터가 양쪽 모두 생성됨
        joiningPubSub.tell(SubscribeToTopic("cross-topic", subscriberProbe.ref()))
        seedPubSub.tell(SubscribeToTopic("cross-topic", seedDummyProbe.ref()))

        subscriberProbe.awaitAssert(Duration.ofSeconds(10), Duration.ofMillis(300)) {
            seedPubSub.tell(PublishMessage("cross-topic", "cross-node-hello"))
            subscriberProbe.expectMessage(Duration.ofMillis(700), "cross-node-hello")
            "delivered"
        }
    }
}
