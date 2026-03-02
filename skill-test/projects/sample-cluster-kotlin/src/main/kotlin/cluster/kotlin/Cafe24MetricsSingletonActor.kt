package cluster.kotlin

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import java.io.Serializable

sealed interface Cafe24MetricsCommand : Serializable

data class RecordCafe24Call(
    val mallId: String,
    val statusCode: Int,
    val queueDelayMs: Long
) : Cafe24MetricsCommand

data class GetMallMetrics(
    val mallId: String,
    val replyTo: ActorRef<Cafe24MetricsResponse>
) : Cafe24MetricsCommand

data class Cafe24MetricsResponse(
    val mallId: String,
    val totalCalls: Long,
    val throttled429: Long,
    val avgQueueDelayMs: Double
)

object StopCafe24Metrics : Cafe24MetricsCommand

class Cafe24MetricsSingletonActor private constructor(
    context: ActorContext<Cafe24MetricsCommand>
) : AbstractBehavior<Cafe24MetricsCommand>(context) {

    private data class MallMetricState(
        var totalCalls: Long = 0,
        var throttled429: Long = 0,
        var sumQueueDelayMs: Long = 0
    )

    private val states = mutableMapOf<String, MallMetricState>()

    companion object {
        fun create(): Behavior<Cafe24MetricsCommand> = Behaviors.setup(::Cafe24MetricsSingletonActor)
    }

    override fun createReceive(): Receive<Cafe24MetricsCommand> =
        newReceiveBuilder()
            .onMessage(RecordCafe24Call::class.java, this::onRecord)
            .onMessage(GetMallMetrics::class.java, this::onGetMallMetrics)
            .onMessage(StopCafe24Metrics::class.java) { Behaviors.stopped() }
            .build()

    private fun onRecord(msg: RecordCafe24Call): Behavior<Cafe24MetricsCommand> {
        val state = states.getOrPut(msg.mallId) { MallMetricState() }
        state.totalCalls += 1
        if (msg.statusCode == 429) {
            state.throttled429 += 1
        }
        state.sumQueueDelayMs += msg.queueDelayMs
        return Behaviors.same()
    }

    private fun onGetMallMetrics(msg: GetMallMetrics): Behavior<Cafe24MetricsCommand> {
        val state = states[msg.mallId] ?: MallMetricState()
        val avgDelay = if (state.totalCalls == 0L) 0.0 else state.sumQueueDelayMs.toDouble() / state.totalCalls
        msg.replyTo.tell(
            Cafe24MetricsResponse(
                mallId = msg.mallId,
                totalCalls = state.totalCalls,
                throttled429 = state.throttled429,
                avgQueueDelayMs = avgDelay
            )
        )
        return Behaviors.same()
    }
}
