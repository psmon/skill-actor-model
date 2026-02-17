package cluster.kotlin

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

sealed interface HelloCommand

data class Hello(val message: String, val replyTo: ActorRef<HelloResponse>) : HelloCommand

data class HelloResponse(val message: String)

class HelloActor private constructor(
    context: ActorContext<HelloCommand>,
    private val welcomeMessageProvider: WelcomeMessageProvider
) : AbstractBehavior<HelloCommand>(context) {

    companion object {
        fun create(welcomeMessageProvider: WelcomeMessageProvider): Behavior<HelloCommand> =
            Behaviors.setup { context -> HelloActor(context, welcomeMessageProvider) }
    }

    override fun createReceive(): Receive<HelloCommand> =
        newReceiveBuilder()
            .onMessage(Hello::class.java) { msg ->
                msg.replyTo.tell(HelloResponse(welcomeMessageProvider.message()))
                Behaviors.same()
            }
            .build()
}
