package cluster.kotlin

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Join
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class ClusterActorTest {
    companion object {
        private lateinit var testKit: ActorTestKit

        @JvmStatic
        @BeforeAll
        fun setup() {
            val config = ConfigFactory.parseString("""
                pekko {
                  actor.provider = "cluster"
                  remote.artery {
                    canonical.hostname = "127.0.0.1"
                    canonical.port = 0
                  }
                  cluster {
                    seed-nodes = []
                    min-nr-of-members = 1
                    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
                  }
                  loglevel = "INFO"
                }
            """.trimIndent()).withFallback(ConfigFactory.load())

            testKit = ActorTestKit.create(config)
            // Single-node cluster: join self
            val cluster = Cluster.get(testKit.system())
            cluster.manager().tell(Join.create(cluster.selfMember().address()))
            awaitClusterUp(testKit, 1, Duration.ofSeconds(10))
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            testKit.shutdownTestKit()
        }

        private fun awaitClusterUp(testKit: ActorTestKit, expectedMembers: Int, timeout: Duration) {
            val observer = testKit.createTestProbe<String>()
            observer.awaitAssert(timeout, Duration.ofMillis(200)) {
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
    fun `counter singleton actor counts correctly`() {
        val counter = testKit.spawn(CounterSingletonActor.create(), "counter-test")
        val probe = testKit.createTestProbe<CounterCommand>()

        counter.tell(Increment)
        counter.tell(Increment)
        counter.tell(Increment)
        counter.tell(GetCount(probe.ref()))

        val result = probe.expectMessageClass(CountValue::class.java)
        assert(result.value == 3) { "Expected 3, got ${result.value}" }
    }

    @Test
    fun `pubsub delivers message to subscriber`() {
        val pubSubManager = testKit.spawn(PubSubManagerActor.create(), "pubsub-test")
        val subscriberProbe = testKit.createTestProbe<String>()

        // Subscribe
        pubSubManager.tell(SubscribeToTopic("test-topic", subscriberProbe.ref()))

        subscriberProbe.awaitAssert(Duration.ofSeconds(5), Duration.ofMillis(200)) {
            pubSubManager.tell(PublishMessage("test-topic", "hello-cluster"))
            subscriberProbe.expectMessage(Duration.ofMillis(700), "hello-cluster")
            "delivered"
        }
    }

    @Test
    fun `kafka singleton should execute runner only once`() {
        val calls = AtomicInteger(0)
        val fakeRunner = object : KafkaStreamRunner {
            override fun runOnce(
                bootstrapServers: String,
                topic: String,
                groupIdPrefix: String,
                payload: String,
                timeout: Duration
            ) = CompletableFuture.completedFuture(payload).also { calls.incrementAndGet() }
        }

        val singleton = testKit.spawn(
            KafkaStreamSingletonActor.create(
                "localhost:9092",
                "cluster-kotlin-events",
                "test-group",
                Duration.ofSeconds(3),
                fakeRunner
            ),
            "kafka-singleton-test"
        )

        singleton.tell(StartKafkaStream)
        singleton.tell(StartKafkaStream)
        singleton.tell(StartKafkaStream)

        val observer = testKit.createTestProbe<String>()
        observer.awaitAssert(Duration.ofSeconds(3), Duration.ofMillis(100)) {
            kotlin.test.assertEquals(1, calls.get())
            "done"
        }
    }

    @Test
    fun `kafka singleton should stop gracefully on stop message`() {
        val singleton = testKit.spawn(
            KafkaStreamSingletonActor.create(
                "localhost:9092",
                "cluster-kotlin-events",
                "test-group",
                Duration.ofSeconds(3)
            ),
            "kafka-singleton-stop-test"
        )

        val watcher = testKit.createTestProbe<Any>()
        singleton.tell(StopKafkaStream)
        watcher.expectTerminated(singleton, Duration.ofSeconds(3))
    }
}
