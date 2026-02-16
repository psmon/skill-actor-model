package cluster.kotlin

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.cluster.ClusterEvent
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Subscribe

// ===== Message Protocol =====
sealed class ClusterListenerCommand
data class WrappedMemberUp(val event: ClusterEvent.MemberUp) : ClusterListenerCommand()

// ===== Actor Implementation =====
class ClusterListenerActor private constructor(
    context: ActorContext<ClusterListenerCommand>,
    private val reportTo: ActorRef<String>
) : AbstractBehavior<ClusterListenerCommand>(context) {

    companion object {
        fun create(reportTo: ActorRef<String>): Behavior<ClusterListenerCommand> =
            Behaviors.setup { ctx ->
                // Typed 액터에서 클러스터 이벤트를 수신하려면 messageAdapter로 변환
                val memberUpAdapter = ctx.messageAdapter(
                    ClusterEvent.MemberUp::class.java
                ) { event -> WrappedMemberUp(event) }

                Cluster.get(ctx.system).subscriptions().tell(
                    Subscribe.create(memberUpAdapter, ClusterEvent.MemberUp::class.java)
                )

                ClusterListenerActor(ctx, reportTo)
            }
    }

    override fun createReceive(): Receive<ClusterListenerCommand> {
        return newReceiveBuilder()
            .onMessage(WrappedMemberUp::class.java) { msg ->
                context.log.info("Member is Up: {}", msg.event.member())
                reportTo.tell("member-up")
                Behaviors.same()
            }
            .build()
    }
}
