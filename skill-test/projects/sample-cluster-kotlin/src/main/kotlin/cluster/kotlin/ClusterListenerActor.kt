package cluster.kotlin

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.cluster.ClusterEvent
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Subscribe
import java.time.Duration

// ===== Message Protocol =====
sealed class ClusterListenerCommand
data class WrappedMemberUp(val event: ClusterEvent.MemberUp) : ClusterListenerCommand()

// ===== Actor Implementation =====
class ClusterListenerActor private constructor(
    context: ActorContext<ClusterListenerCommand>,
    private val reportTo: ActorRef<String>,
    private val kafkaSingletonProxy: ActorRef<KafkaSingletonCommand>?,
    private val requiredMembersForKafkaStart: Int,
    private val kafkaStartDelay: Duration
) : AbstractBehavior<ClusterListenerCommand>(context) {

    companion object {
        fun create(
            reportTo: ActorRef<String>,
            kafkaSingletonProxy: ActorRef<KafkaSingletonCommand>? = null,
            requiredMembersForKafkaStart: Int = 1,
            kafkaStartDelay: Duration = Duration.ofSeconds(15)
        ): Behavior<ClusterListenerCommand> =
            Behaviors.setup { ctx ->
                // Typed 액터에서 클러스터 이벤트를 수신하려면 messageAdapter로 변환
                val memberUpAdapter = ctx.messageAdapter(
                    ClusterEvent.MemberUp::class.java
                ) { event -> WrappedMemberUp(event) }

                Cluster.get(ctx.system).subscriptions().tell(
                    Subscribe.create(memberUpAdapter, ClusterEvent.MemberUp::class.java)
                )

                ClusterListenerActor(
                    ctx,
                    reportTo,
                    kafkaSingletonProxy,
                    requiredMembersForKafkaStart,
                    kafkaStartDelay
                )
            }
    }

    private var kafkaStartScheduled = false

    override fun createReceive(): Receive<ClusterListenerCommand> {
        return newReceiveBuilder()
            .onMessage(WrappedMemberUp::class.java) { msg ->
                context.log.info("Member is Up: {}", msg.event.member())
                reportTo.tell("member-up")
                tryScheduleKafkaRun()
                Behaviors.same()
            }
            .build()
    }

    private fun tryScheduleKafkaRun() {
        val proxy = kafkaSingletonProxy ?: return
        if (kafkaStartScheduled) return

        val upCount = Cluster.get(context.system).state().members.count {
            it.status() == MemberStatus.up()
        }

        if (upCount < requiredMembersForKafkaStart) return

        kafkaStartScheduled = true
        context.log.info(
            "Cluster is ready ({}/{} members Up). Scheduling Kafka singleton trigger in {}.",
            upCount,
            requiredMembersForKafkaStart,
            kafkaStartDelay
        )

        context.scheduleOnce(kafkaStartDelay, proxy, StartKafkaStream)
    }
}
