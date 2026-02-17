package cluster.kotlin

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.kafka.ConsumerSettings
import org.apache.pekko.kafka.ProducerSettings
import org.apache.pekko.kafka.Subscriptions
import org.apache.pekko.kafka.javadsl.Consumer
import org.apache.pekko.kafka.javadsl.Producer
import org.apache.pekko.stream.SystemMaterializer
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

sealed class KafkaSingletonCommand
object StartKafkaStream : KafkaSingletonCommand()
object StopKafkaStream : KafkaSingletonCommand()
data class FireKafkaEvent(val replyTo: ActorRef<KafkaFireEventResult>) : KafkaSingletonCommand()
data class KafkaRunCompleted(
    val produced: String,
    val observed: String,
    val replyTo: ActorRef<KafkaFireEventResult>?
) : KafkaSingletonCommand()
data class KafkaRunFailed(
    val produced: String,
    val reason: String,
    val replyTo: ActorRef<KafkaFireEventResult>?
) : KafkaSingletonCommand()
data class KafkaFireEventResult(val success: Boolean, val produced: String, val observed: String, val error: String)

interface KafkaStreamRunner {
    fun runOnce(
        bootstrapServers: String,
        topic: String,
        groupIdPrefix: String,
        payload: String,
        timeout: Duration
    ): CompletionStage<String>
}

class PekkoKafkaStreamRunner(private val system: ActorSystem<*>) : KafkaStreamRunner {
    override fun runOnce(
        bootstrapServers: String,
        topic: String,
        groupIdPrefix: String,
        payload: String,
        timeout: Duration
    ): CompletionStage<String> {
        val materializer = SystemMaterializer.get(system).materializer()

        val producerSettings = ProducerSettings.create(system, StringSerializer(), StringSerializer())
            .withBootstrapServers(bootstrapServers)
            .withProperty("enable.idempotence", "true")
            .withProperty("acks", "all")

        val produced: CompletionStage<Done> = Source
            .single(ProducerRecord(topic, "cluster", payload))
            .runWith(Producer.plainSink(producerSettings), materializer)

        val groupId = "$groupIdPrefix-${UUID.randomUUID()}"
        val consumerSettings = ConsumerSettings.create(system, StringDeserializer(), StringDeserializer())
            .withBootstrapServers(bootstrapServers)
            .withGroupId(groupId)
            .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")

        val consumed = Consumer
            .plainSource(consumerSettings, Subscriptions.topics(topic))
            .map { record: ConsumerRecord<String, String> -> record.value() }
            .filter { value -> value == payload }
            .take(1)
            .runWith(Sink.head(), materializer)
            .toCompletableFuture()
            .orTimeout(timeout.seconds, TimeUnit.SECONDS)

        return produced.thenCompose { consumed }
    }
}

class KafkaStreamSingletonActor private constructor(
    context: ActorContext<KafkaSingletonCommand>,
    private val bootstrapServers: String,
    private val topic: String,
    private val groupIdPrefix: String,
    private val timeout: Duration,
    private val runner: KafkaStreamRunner
) : AbstractBehavior<KafkaSingletonCommand>(context) {

    companion object {
        fun create(
            bootstrapServers: String,
            topic: String,
            groupIdPrefix: String,
            timeout: Duration,
            runner: KafkaStreamRunner? = null
        ): Behavior<KafkaSingletonCommand> {
            return Behaviors.setup { ctx ->
                KafkaStreamSingletonActor(
                    ctx,
                    bootstrapServers,
                    topic,
                    groupIdPrefix,
                    timeout,
                    runner ?: PekkoKafkaStreamRunner(ctx.system)
                )
            }
        }
    }

    private var started = false

    override fun createReceive(): Receive<KafkaSingletonCommand> {
        return newReceiveBuilder()
            .onMessage(StartKafkaStream::class.java) {
                if (started) {
                    context.log.info("Kafka stream singleton already executed. Ignoring duplicate trigger.")
                    return@onMessage Behaviors.same()
                }

                started = true
                executeOnce(null)
                Behaviors.same()
            }
            .onMessage(FireKafkaEvent::class.java) { msg ->
                executeOnce(msg.replyTo)
                Behaviors.same()
            }
            .onMessage(StopKafkaStream::class.java) {
                Behaviors.stopped()
            }
            .onMessage(KafkaRunCompleted::class.java) { msg ->
                context.log.info(
                    "Kafka stream round-trip succeeded. topic={}, consumed={}",
                    topic,
                    msg.observed
                )
                msg.replyTo?.tell(KafkaFireEventResult(true, msg.produced, msg.observed, ""))
                Behaviors.same()
            }
            .onMessage(KafkaRunFailed::class.java) { msg ->
                context.log.error(
                    "Kafka stream round-trip failed. topic={}, reason={}",
                    topic,
                    msg.reason
                )
                msg.replyTo?.tell(KafkaFireEventResult(false, msg.produced, "", msg.reason))
                Behaviors.same()
            }
            .build()
    }

    private fun executeOnce(replyTo: ActorRef<KafkaFireEventResult>?) {
        val payload = "kotlin-cluster-event-${Instant.now()}"

        context.log.info(
            "Starting Kafka stream round-trip once. bootstrap={}, topic={}",
            bootstrapServers,
            topic
        )

        context.pipeToSelf(runner.runOnce(bootstrapServers, topic, groupIdPrefix, payload, timeout)) { observed, ex ->
            if (ex != null) {
                KafkaRunFailed(payload, ex.message ?: "unknown", replyTo)
            } else {
                KafkaRunCompleted(payload, observed, replyTo)
            }
        }
    }
}
