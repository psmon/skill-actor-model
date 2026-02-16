package cluster.java;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.MemberStatus;
import akka.testkit.javadsl.TestKit;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import scala.concurrent.duration.FiniteDuration;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

class ClusterActorTest {
    private static ActorSystem system;

    @BeforeAll
    static void setup() {
        system = ActorSystem.create("ClusterTestSystem", ConfigFactory.load());
        // Single-node cluster: join self
        Cluster cluster = Cluster.get(system);
        cluster.join(cluster.selfAddress());
        awaitClusterUp(system, 1, Duration.ofSeconds(10));
    }

    @AfterAll
    static void teardown() {
        TestKit.shutdownActorSystem(system,
            FiniteDuration.apply(10, TimeUnit.SECONDS), true);
    }

    private static void awaitClusterUp(ActorSystem targetSystem, int expectedMembers, Duration timeout) {
        new TestKit(targetSystem) {{
            awaitAssert(timeout, Duration.ofMillis(200), () -> {
                long upCount = StreamSupport.stream(
                        Cluster.get(targetSystem).state().getMembers().spliterator(), false)
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
    void clusterListenerReceivesMemberUpEvent() {
        new TestKit(system) {{
            ActorRef listener = system.actorOf(
                ClusterListenerActor.props(getRef()), "listener-test");

            // Should receive MemberUp notification for self node
            expectMsgEquals(Duration.ofSeconds(5), "member-up");
            expectNoMessage(Duration.ofMillis(500));
        }};
    }

    @Test
    void counterSingletonActorCountsCorrectly() {
        new TestKit(system) {{
            ActorRef counter = system.actorOf(CounterSingletonActor.props(), "counter-test");

            counter.tell(CounterSingletonActor.Increment.INSTANCE, getRef());
            counter.tell(CounterSingletonActor.Increment.INSTANCE, getRef());
            counter.tell(CounterSingletonActor.Increment.INSTANCE, getRef());
            counter.tell(new CounterSingletonActor.GetCount(getRef()), getRef());

            CounterSingletonActor.CountValue result =
                expectMsgClass(Duration.ofSeconds(3), CounterSingletonActor.CountValue.class);
            assert result.getValue() == 3 : "Expected count 3, got " + result.getValue();
        }};
    }

    @Test
    void pubSubDeliversMessageToSubscriber() {
        new TestKit(system) {{
            // Create subscriber with reportTo = testkit probe
            ActorRef subscriber = system.actorOf(
                PubSubSubscriberActor.props("test-topic", getRef()), "sub-test");

            // Wait for subscription acknowledgment
            expectMsgEquals(Duration.ofSeconds(5), "subscribed");

            // Create publisher and publish
            ActorRef publisher = system.actorOf(PubSubPublisherActor.props(), "pub-test");
            publisher.tell("hello-cluster", ActorRef.noSender());

            // Subscriber should forward message to reportTo
            expectMsgEquals(Duration.ofSeconds(5), "hello-cluster");
            expectNoMessage(Duration.ofMillis(500));
        }};
    }
}
