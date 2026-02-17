package cluster.java;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.kafka.javadsl.Producer;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class KafkaStreamSingletonActor extends AbstractActor {

    public interface KafkaStreamRunner {
        CompletionStage<String> runOnce(
            String bootstrapServers,
            String topic,
            String groupIdPrefix,
            String payload,
            Duration timeout);
    }

    public static final class AkkaStreamsKafkaRunner implements KafkaStreamRunner {
        private final ActorSystem system;
        private final Materializer materializer;

        public AkkaStreamsKafkaRunner(ActorSystem system) {
            this.system = system;
            this.materializer = SystemMaterializer.get(system).materializer();
        }

        @Override
        public CompletionStage<String> runOnce(
            String bootstrapServers,
            String topic,
            String groupIdPrefix,
            String payload,
            Duration timeout) {

            ProducerSettings<String, String> producerSettings =
                ProducerSettings.create(system, new StringSerializer(), new StringSerializer())
                    .withBootstrapServers(bootstrapServers)
                    .withProperty("enable.idempotence", "true")
                    .withProperty("acks", "all");

            CompletionStage<akka.Done> produceDone = Source
                .single(new ProducerRecord<>(topic, "cluster", payload))
                .runWith(Producer.plainSink(producerSettings), materializer);

            String uniqueGroupId = groupIdPrefix + "-" + UUID.randomUUID();
            ConsumerSettings<String, String> consumerSettings =
                ConsumerSettings.create(system, new StringDeserializer(), new StringDeserializer())
                    .withBootstrapServers(bootstrapServers)
                    .withGroupId(uniqueGroupId)
                    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                    .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

            CompletionStage<String> consumeDone = Consumer
                .plainSource(consumerSettings, Subscriptions.topics(topic))
                .map(ConsumerRecord::value)
                .filter(value -> payload.equals(value))
                .take(1)
                .runWith(Sink.head(), materializer)
                .toCompletableFuture()
                .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

            return produceDone.thenCompose(done -> consumeDone);
        }
    }

    public static final class Start implements Serializable {
        public static final Start INSTANCE = new Start();
        private Start() {
        }
    }

    public static final class Stop implements Serializable {
        public static final Stop INSTANCE = new Stop();
        private Stop() {
        }
    }

    public static final class FireEvent implements Serializable {
        public static final FireEvent INSTANCE = new FireEvent();
        private FireEvent() {
        }
    }

    public record FireEventResult(boolean success, String produced, String observed, String error) implements Serializable {
    }

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final KafkaStreamRunner runner;
    private final String bootstrapServers;
    private final String topic;
    private final String groupIdPrefix;
    private final Duration timeout;
    private boolean started;

    public static Props props(
        String bootstrapServers,
        String topic,
        String groupIdPrefix,
        Duration timeout) {
        return Props.create(
            KafkaStreamSingletonActor.class,
            () -> new KafkaStreamSingletonActor(bootstrapServers, topic, groupIdPrefix, timeout, null));
    }

    public static Props props(
        String bootstrapServers,
        String topic,
        String groupIdPrefix,
        Duration timeout,
        KafkaStreamRunner runner) {
        return Props.create(
            KafkaStreamSingletonActor.class,
            () -> new KafkaStreamSingletonActor(bootstrapServers, topic, groupIdPrefix, timeout, runner));
    }

    public KafkaStreamSingletonActor(
        String bootstrapServers,
        String topic,
        String groupIdPrefix,
        Duration timeout,
        KafkaStreamRunner runner) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.groupIdPrefix = groupIdPrefix;
        this.timeout = timeout;
        this.runner = runner != null ? runner : new AkkaStreamsKafkaRunner(getContext().getSystem());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Start.class, ignored -> {
                if (started) {
                    log.info("Kafka stream singleton already executed. Ignoring duplicate trigger.");
                    return;
                }

                started = true;
                runFireEvent(getSelf(), false);
            })
            .match(FireEvent.class, ignored -> runFireEvent(getSender(), true))
            .match(Stop.class, ignored -> getContext().stop(getSelf()))
            .build();
    }

    private void runFireEvent(ActorRef replyTo, boolean withReply) {
        String payload = "java-cluster-event-" + Instant.now();

        log.info(
            "Starting Kafka stream round-trip once. bootstrap={}, topic={}",
            bootstrapServers,
            topic);

        runner.runOnce(bootstrapServers, topic, groupIdPrefix, payload, timeout)
            .whenComplete((value, ex) -> {
                if (ex != null) {
                    log.error(ex, "Kafka stream round-trip failed. topic={}, payload={}", topic, payload);
                    if (withReply) {
                        replyTo.tell(new FireEventResult(false, payload, "", ex.getMessage()), getSelf());
                    }
                    return;
                }

                log.info(
                    "Kafka stream round-trip succeeded. topic={}, produced={}, consumed={}",
                    topic,
                    payload,
                    value);

                if (withReply) {
                    replyTo.tell(new FireEventResult(true, payload, value, ""), getSelf());
                }
            });
    }
}
