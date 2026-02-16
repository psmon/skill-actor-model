package cluster.java;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaStreamSingletonActorTest {

    private static ActorSystem system;

    @BeforeAll
    static void setup() {
        system = ActorSystem.create("KafkaSingletonTestSystem");
    }

    @AfterAll
    static void teardown() {
        TestKit.shutdownActorSystem(system,
            FiniteDuration.apply(10, TimeUnit.SECONDS), true);
    }

    @Test
    void kafkaSingletonShouldExecuteRunnerOnlyOnce() {
        AtomicInteger callCount = new AtomicInteger();

        KafkaStreamSingletonActor.KafkaStreamRunner fakeRunner =
            (bootstrapServers, topic, groupIdPrefix, payload, timeout) -> {
                callCount.incrementAndGet();
                return CompletableFuture.completedFuture(payload);
            };

        ActorRef singleton = system.actorOf(
            KafkaStreamSingletonActor.props(
                "localhost:9092",
                "cluster-java-events",
                "test-group",
                Duration.ofSeconds(3),
                fakeRunner));

        singleton.tell(KafkaStreamSingletonActor.Start.INSTANCE, ActorRef.noSender());
        singleton.tell(KafkaStreamSingletonActor.Start.INSTANCE, ActorRef.noSender());
        singleton.tell(KafkaStreamSingletonActor.Start.INSTANCE, ActorRef.noSender());

        new TestKit(system) {{
            awaitAssert(Duration.ofSeconds(3), Duration.ofMillis(100), () -> {
                assertEquals(1, callCount.get());
                return null;
            });
        }};
    }
}
