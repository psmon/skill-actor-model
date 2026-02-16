package cluster.java;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.MemberStatus;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.javadsl.TestKit;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.*;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2-Node Cluster Test: seed 노드(고정 포트 25520)와 joining 노드(자동 포트)로
 * 실제 2노드 클러스터를 구성하여 크로스노드 통신을 검증한다.
 * 기존 1-Node 테스트(ClusterActorTest)는 그대로 유지된다.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TwoNodeClusterTest {

    private static ActorSystem seedSystem;
    private static ActorSystem joiningSystem;

    @BeforeAll
    static void setup() {
        // Node 1: seed 노드 (고정 포트 25520)
        seedSystem = ActorSystem.create("TwoNodeClusterSystem",
                ConfigFactory.load("two-node-seed"));

        // Node 2: joining 노드 (자동 포트, seed-nodes로 Node 1 지정)
        joiningSystem = ActorSystem.create("TwoNodeClusterSystem",
                ConfigFactory.load("two-node-joining"));

        awaitClusterUp(seedSystem, 2, Duration.ofSeconds(15));
    }

    @AfterAll
    static void teardown() {
        // joining 노드 먼저 graceful leave 후 shutdown
        if (joiningSystem != null) {
            Cluster.get(joiningSystem).leave(Cluster.get(joiningSystem).selfAddress());
            TestKit.shutdownActorSystem(joiningSystem,
                    FiniteDuration.apply(10, TimeUnit.SECONDS), true);
        }
        if (seedSystem != null) {
            TestKit.shutdownActorSystem(seedSystem,
                    FiniteDuration.apply(10, TimeUnit.SECONDS), true);
        }
    }

    private static void awaitClusterUp(ActorSystem system, int expectedMembers, Duration timeout) {
        new TestKit(system) {{
            awaitAssert(timeout, Duration.ofMillis(200), () -> {
                long upCount = StreamSupport.stream(
                        Cluster.get(system).state().getMembers().spliterator(), false)
                    .filter(m -> m.status().equals(MemberStatus.up()))
                    .count();

                if (upCount < expectedMembers) {
                    throw new AssertionError("Expected at least " + expectedMembers + " Up members but got " + upCount);
                }
                return null;
            });
        }};
    }

    @Test
    @Order(1)
    void bothNodesShouldBeUpInCluster() {
        Cluster seedCluster = Cluster.get(seedSystem);
        Cluster joiningCluster = Cluster.get(joiningSystem);

        // 양쪽 노드 모두 2개의 멤버를 인식
        long seedMemberCount = StreamSupport.stream(
                seedCluster.state().getMembers().spliterator(), false).count();
        long joiningMemberCount = StreamSupport.stream(
                joiningCluster.state().getMembers().spliterator(), false).count();
        assertEquals(2, seedMemberCount, "Seed should see 2 members");
        assertEquals(2, joiningMemberCount, "Joining should see 2 members");

        // 모든 멤버가 Up 상태
        seedCluster.state().getMembers().forEach(m ->
                assertEquals(MemberStatus.up(), m.status(),
                        "Member " + m.address() + " should be Up"));
    }

    @Test
    @Order(2)
    void clusterListenerShouldReceiveTwoMemberUpEvents() {
        new TestKit(seedSystem) {{
            ActorRef listener = seedSystem.actorOf(
                    ClusterListenerActor.props(getRef()), "listener-2node");

            // initialStateAsEvents 구독이므로 이미 Up인 2개 노드에 대해 MemberUp 이벤트 수신
            expectMsgEquals(Duration.ofSeconds(5), "member-up");
            expectMsgEquals(Duration.ofSeconds(5), "member-up");
            expectNoMessage(Duration.ofMillis(500));
        }};
    }

    @Test
    @Order(3)
    void counterShouldWorkAcrossNodes() {
        // Seed 노드에 카운터 액터 생성
        ActorRef counter = seedSystem.actorOf(
                CounterSingletonActor.props(), "counter-2node");

        // Seed 노드에서 2회 증가
        new TestKit(seedSystem) {{
            counter.tell(CounterSingletonActor.Increment.INSTANCE, getRef());
            counter.tell(CounterSingletonActor.Increment.INSTANCE, getRef());
        }};

        // Joining 노드에서 ActorSelection으로 리모트 카운터에 접근
        new TestKit(joiningSystem) {{
            String counterPath = seedSystem.provider().getDefaultAddress() + "/user/counter-2node";
            var remoteCounter = joiningSystem.actorSelection(counterPath);

            // Joining 노드에서 1회 증가
            remoteCounter.tell(CounterSingletonActor.Increment.INSTANCE, getRef());
            awaitAssert(Duration.ofSeconds(5), Duration.ofMillis(200), () -> {
                remoteCounter.tell(new CounterSingletonActor.GetCount(getRef()), getRef());
                CounterSingletonActor.CountValue result =
                    expectMsgClass(Duration.ofMillis(500), CounterSingletonActor.CountValue.class);
                assertEquals(3, result.getValue(), "Counter should be 3 after cross-node increments");
                return null;
            });
        }};
    }

    @Test
    @Order(4)
    void pubSubShouldDeliverAcrossNodes() {
        // Seed 노드에서도 DistributedPubSub 초기화 (mediator 사전 생성)
        ActorRef seedMediator = DistributedPubSub.get(seedSystem).mediator();

        new TestKit(joiningSystem) {{
            // Joining 노드에서 구독자 생성
            ActorRef subscriber = joiningSystem.actorOf(
                    PubSubSubscriberActor.props("test-topic", getRef()), "sub-2node");

            // 구독 완료 대기
            expectMsgEquals(Duration.ofSeconds(5), "subscribed");

            awaitAssert(Duration.ofSeconds(10), Duration.ofMillis(300), () -> {
                seedMediator.tell(
                    new DistributedPubSubMediator.Publish("test-topic", "cross-node-hello"),
                    ActorRef.noSender());

                expectMsgEquals(Duration.ofMillis(700), "cross-node-hello");
                return null;
            });
            expectNoMessage(Duration.ofMillis(500));
        }};
    }
}
