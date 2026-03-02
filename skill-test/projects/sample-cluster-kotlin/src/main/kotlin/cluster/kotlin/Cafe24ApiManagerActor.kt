package cluster.kotlin

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.ActorRef as ClassicActorRef
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.SystemMaterializer
import org.apache.pekko.stream.ThrottleMode
import org.apache.pekko.stream.javadsl.Sink
import org.apache.pekko.stream.javadsl.Source
import java.io.Serializable
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

sealed interface Cafe24ApiManagerCommand : Serializable

data class Cafe24ApiRequest(
    val mallId: String,
    val word: String,
    val replyTo: ActorRef<Cafe24ApiResponse>
) : Cafe24ApiManagerCommand

data class Cafe24ApiResponse(
    val mallId: String,
    val word: String,
    val result: String,
    val statusCode: Int,
    val bucketUsed: Int,
    val bucketMax: Int
) : Serializable

class DummyCafe24Api(
    private val bucketCapacity: Int,
    private val leakRatePerSecond: Int
) : AutoCloseable {
    data class DummyCafe24Result(
        val statusCode: Int,
        val body: String,
        val bucketUsed: Int,
        val bucketMax: Int,
        val callRemainSeconds: Int
    )

    private val bucketByMall = ConcurrentHashMap<String, AtomicInteger>()
    private val leakScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "kotlin-cafe24-leak").apply { isDaemon = true }
        }

    init {
        leakScheduler.scheduleAtFixedRate(
            {
                bucketByMall.values.forEach { level ->
                    level.updateAndGet { current -> maxOf(0, current - leakRatePerSecond) }
                }
            },
            1,
            1,
            TimeUnit.SECONDS
        )
    }

    fun call(mallId: String, word: String): CompletionStage<DummyCafe24Result> {
        val level = bucketByMall.computeIfAbsent(mallId) { AtomicInteger(0) }.incrementAndGet()
        return if (level > bucketCapacity) {
            bucketByMall[mallId]?.decrementAndGet()
            val over = level - bucketCapacity
            val remain = ceil(over.toDouble() / leakRatePerSecond).toInt() + 1
            CompletableFuture.completedFuture(
                DummyCafe24Result(
                    statusCode = 429,
                    body = "Too Many Requests",
                    bucketUsed = level,
                    bucketMax = bucketCapacity,
                    callRemainSeconds = remain
                )
            )
        } else {
            CompletableFuture.completedFuture(
                DummyCafe24Result(
                    statusCode = 200,
                    body = if (word == "hello") "world" else word,
                    bucketUsed = level,
                    bucketMax = bucketCapacity,
                    callRemainSeconds = 0
                )
            )
        }
    }

    override fun close() {
        leakScheduler.shutdownNow()
    }
}

class Cafe24ApiManagerActor private constructor(
    context: ActorContext<Cafe24ApiManagerCommand>,
    private val perMallMaxRequestsPerSecond: Int,
    private val dummyApi: DummyCafe24Api,
    private val metricsActor: ActorRef<Cafe24MetricsCommand>
) : AbstractBehavior<Cafe24ApiManagerCommand>(context) {

    companion object {
        fun create(
            perMallMaxRequestsPerSecond: Int,
            dummyApi: DummyCafe24Api,
            metricsActor: ActorRef<Cafe24MetricsCommand>
        ): Behavior<Cafe24ApiManagerCommand> =
            Behaviors.setup { context ->
                Cafe24ApiManagerActor(context, perMallMaxRequestsPerSecond, dummyApi, metricsActor)
            }
    }

    override fun createReceive(): Receive<Cafe24ApiManagerCommand> =
        newReceiveBuilder()
            .onMessage(Cafe24ApiRequest::class.java, this::onApiRequest)
            .build()

    private fun onApiRequest(msg: Cafe24ApiRequest): Behavior<Cafe24ApiManagerCommand> {
        val actorName = "mall-" + msg.mallId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val existing = context.getChild(actorName)
        val child = if (existing.isPresent) {
            existing.get() as ActorRef<Cafe24MallApiCallerCommand>
        } else {
            context.spawn(
                Cafe24MallApiCallerActor.create(
                    msg.mallId,
                    perMallMaxRequestsPerSecond,
                    dummyApi,
                    metricsActor
                ),
                actorName
            )
        }

        child.tell(CallMallApi(msg.word, msg.replyTo))
        return Behaviors.same()
    }
}

private sealed interface Cafe24MallApiCallerCommand : Serializable

private data class CallMallApi(
    val word: String,
    val replyTo: ActorRef<Cafe24ApiResponse>
) : Cafe24MallApiCallerCommand

private data class StreamEnvelope(
    val request: CallMallApi,
    val enqueuedAtNanos: Long
) : Serializable

private data class StreamResult(
    val request: CallMallApi,
    val response: Cafe24ApiResponse,
    val queueDelayMs: Long
) : Cafe24MallApiCallerCommand

private class Cafe24MallApiCallerActor private constructor(
    context: ActorContext<Cafe24MallApiCallerCommand>,
    private val mallId: String,
    private val maxRequestsPerSecond: Int,
    private val dummyApi: DummyCafe24Api,
    private val metricsActor: ActorRef<Cafe24MetricsCommand>
) : AbstractBehavior<Cafe24MallApiCallerCommand>(context) {

    private val streamEntry: ClassicActorRef

    companion object {
        fun create(
            mallId: String,
            maxRequestsPerSecond: Int,
            dummyApi: DummyCafe24Api,
            metricsActor: ActorRef<Cafe24MetricsCommand>
        ): Behavior<Cafe24MallApiCallerCommand> =
            Behaviors.setup { context ->
                Cafe24MallApiCallerActor(
                    context,
                    mallId,
                    maxRequestsPerSecond,
                    dummyApi,
                    metricsActor
                )
            }
    }

    init {
        val materializer = SystemMaterializer.get(context.system.classicSystem()).materializer()
        streamEntry = Source.actorRef<StreamEnvelope>(256, OverflowStrategy.dropNew())
            .throttle(maxRequestsPerSecond, Duration.ofSeconds(1), maxRequestsPerSecond, ThrottleMode.shaping())
            .mapAsync(1, this::callApiWithAdaptiveBackpressure)
            .to(Sink.foreach { result -> context.self.tell(result) })
            .run(materializer)
    }

    override fun createReceive(): Receive<Cafe24MallApiCallerCommand> =
        newReceiveBuilder()
            .onMessage(CallMallApi::class.java, this::onCall)
            .onMessage(StreamResult::class.java, this::onStreamResult)
            .build()

    private fun onCall(msg: CallMallApi): Behavior<Cafe24MallApiCallerCommand> {
        streamEntry.tell(StreamEnvelope(msg, System.nanoTime()), ClassicActorRef.noSender())
        return Behaviors.same()
    }

    private fun onStreamResult(msg: StreamResult): Behavior<Cafe24MallApiCallerCommand> {
        msg.request.replyTo.tell(msg.response)
        context.log.info(
            "Cafe24 safe call mall={} word={} status={} bucket={}/{}",
            mallId,
            msg.response.word,
            msg.response.statusCode,
            msg.response.bucketUsed,
            msg.response.bucketMax
        )
        metricsActor.tell(
            RecordCafe24Call(
                mallId = mallId,
                statusCode = msg.response.statusCode,
                queueDelayMs = msg.queueDelayMs
            )
        )
        return Behaviors.same()
    }

    private fun callApiWithAdaptiveBackpressure(envelope: StreamEnvelope): CompletionStage<StreamResult> {
        return executeWithRetry(envelope.request.word, 0)
            .thenCompose { response ->
                val usageRatio = if (response.bucketMax == 0) 0.0 else response.bucketUsed.toDouble() / response.bucketMax
                val adaptiveDelayMs = when {
                    usageRatio > 0.8 -> 500L
                    usageRatio > 0.5 -> 200L
                    else -> 0L
                }

                val queueDelayMs = (System.nanoTime() - envelope.enqueuedAtNanos) / 1_000_000
                val result = StreamResult(envelope.request, response, queueDelayMs)

                if (adaptiveDelayMs > 0) {
                    delay(adaptiveDelayMs).thenApply { result }
                } else {
                    CompletableFuture.completedFuture(result)
                }
            }
    }

    private fun executeWithRetry(word: String, retryCount: Int): CompletionStage<Cafe24ApiResponse> {
        return dummyApi.call(mallId, word)
            .thenCompose { result ->
                if (result.statusCode == 429 && retryCount < 3) {
                    delay(result.callRemainSeconds * 1000L)
                        .thenCompose { executeWithRetry(word, retryCount + 1) }
                } else {
                    CompletableFuture.completedFuture(
                        Cafe24ApiResponse(
                            mallId = mallId,
                            word = word,
                            result = result.body,
                            statusCode = result.statusCode,
                            bucketUsed = result.bucketUsed,
                            bucketMax = result.bucketMax
                        )
                    )
                }
            }
    }

    private fun delay(millis: Long): CompletionStage<Void> =
        CompletableFuture.supplyAsync(
            { null },
            CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS)
        )
}
