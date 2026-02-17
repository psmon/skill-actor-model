package cluster.kotlin

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.cluster.typed.Cluster
import scala.jdk.javaapi.CollectionConverters

sealed interface ClusterInfoCommand

data class GetClusterInfo(val replyTo: ActorRef<ClusterInfoResponse>) : ClusterInfoCommand

data class ClusterMemberDto(
    val address: String,
    val status: String,
    val roles: List<String>,
    val upNumber: Int
)

data class ClusterInfoResponse(
    val selfAddress: String,
    val leader: String,
    val memberCount: Int,
    val members: List<ClusterMemberDto>
)

class ClusterInfoActor private constructor(
    context: ActorContext<ClusterInfoCommand>
) : AbstractBehavior<ClusterInfoCommand>(context) {

    companion object {
        fun create(): Behavior<ClusterInfoCommand> =
            Behaviors.setup { context -> ClusterInfoActor(context) }
    }

    override fun createReceive(): Receive<ClusterInfoCommand> =
        newReceiveBuilder()
            .onMessage(GetClusterInfo::class.java) { msg ->
                val cluster = Cluster.get(context.system)
                val members = cluster.state().members
                    .sortedBy { it.address().toString() }
                    .map {
                        ClusterMemberDto(
                            address = it.address().toString(),
                            status = it.status().toString(),
                            roles = CollectionConverters.asJava(it.roles()).toList().sorted(),
                            upNumber = it.upNumber()
                        )
                    }

                val leader = if (cluster.state().leader().isDefined) {
                    cluster.state().leader().get().toString()
                } else {
                    ""
                }

                msg.replyTo.tell(
                    ClusterInfoResponse(
                        selfAddress = cluster.selfMember().address().toString(),
                        leader = leader,
                        memberCount = members.size,
                        members = members
                    )
                )
                Behaviors.same()
            }
            .build()
}
