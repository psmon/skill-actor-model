package cluster.java;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import cluster.java.cafe24.Cafe24ApiManagerActor;
import cluster.java.cafe24.Cafe24MetricsSingletonActor;
import cluster.java.cafe24.DummyCafe24Api;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Cafe24ApiActorTest {
    private static ActorSystem system;
    private static DummyCafe24Api dummyCafe24Api;

    @BeforeAll
    static void setup() {
        system = ActorSystem.create("Cafe24ApiTestSystem", ConfigFactory.load());
        dummyCafe24Api = new DummyCafe24Api(10, 2);
    }

    @AfterAll
    static void teardown() {
        dummyCafe24Api.close();
        TestKit.shutdownActorSystem(system,
            FiniteDuration.apply(10, TimeUnit.SECONDS), true);
    }

    @Test
    void helloShouldBecomeWorldThroughSafeCaller() {
        new TestKit(system) {{
            ActorRef metrics = system.actorOf(Cafe24MetricsSingletonActor.props(), "cafe24-metrics-test");
            ActorRef manager = system.actorOf(
                Cafe24ApiManagerActor.props(2, dummyCafe24Api, metrics), "cafe24-manager-test");

            manager.tell(new Cafe24ApiManagerActor.ApiRequest("mall-a", "hello"), getRef());
            Cafe24ApiManagerActor.ApiResponse response =
                expectMsgClass(Duration.ofSeconds(5), Cafe24ApiManagerActor.ApiResponse.class);

            assertEquals("mall-a", response.mallId());
            assertEquals("world", response.result());
            assertEquals(200, response.statusCode());
            expectNoMessage(Duration.ofMillis(200));
        }};
    }

    @Test
    void perMallBackpressureShouldKeepBurstCallsSuccessfulAndIsolated() {
        new TestKit(system) {{
            ActorRef metrics = system.actorOf(Cafe24MetricsSingletonActor.props(), "cafe24-metrics-test-2");
            ActorRef manager = system.actorOf(
                Cafe24ApiManagerActor.props(2, dummyCafe24Api, metrics), "cafe24-manager-test-2");

            for (int i = 0; i < 6; i++) {
                manager.tell(new Cafe24ApiManagerActor.ApiRequest("mall-a", "a-" + i), getRef());
                manager.tell(new Cafe24ApiManagerActor.ApiRequest("mall-b", "b-" + i), getRef());
            }

            List<Cafe24ApiManagerActor.ApiResponse> responses = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                responses.add(expectMsgClass(Duration.ofSeconds(20), Cafe24ApiManagerActor.ApiResponse.class));
            }
            assertTrue(responses.stream().allMatch(r -> r.statusCode() == 200));

            awaitAssert(Duration.ofSeconds(5), Duration.ofMillis(200), () -> {
                metrics.tell(new Cafe24MetricsSingletonActor.GetMallMetrics("mall-a"), getRef());
                Cafe24MetricsSingletonActor.MallMetrics mallA =
                    expectMsgClass(Duration.ofMillis(500), Cafe24MetricsSingletonActor.MallMetrics.class);
                assertEquals(6, mallA.totalCalls());
                assertEquals(0, mallA.throttled429());
                return null;
            });

            awaitAssert(Duration.ofSeconds(5), Duration.ofMillis(200), () -> {
                metrics.tell(new Cafe24MetricsSingletonActor.GetMallMetrics("mall-b"), getRef());
                Cafe24MetricsSingletonActor.MallMetrics mallB =
                    expectMsgClass(Duration.ofMillis(500), Cafe24MetricsSingletonActor.MallMetrics.class);
                assertEquals(6, mallB.totalCalls());
                assertEquals(0, mallB.throttled429());
                return null;
            });
        }};
    }
}
