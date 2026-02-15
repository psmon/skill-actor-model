package cluster.kotlin

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

sealed class CounterCommand
object Increment : CounterCommand()
data class GetCount(val replyTo: ActorRef<CounterCommand>) : CounterCommand()
data class CountValue(val value: Int) : CounterCommand()
object StopCounter : CounterCommand()

class CounterSingletonActor private constructor(
    context: ActorContext<CounterCommand>
) : AbstractBehavior<CounterCommand>(context) {

    companion object {
        fun create(): Behavior<CounterCommand> = Behaviors.setup(::CounterSingletonActor)
    }

    private var count = 0

    override fun createReceive(): Receive<CounterCommand> {
        return newReceiveBuilder()
            .onMessage(Increment::class.java) { _ ->
                count++
                context.log.info("Counter incremented to {}", count)
                Behaviors.same()
            }
            .onMessage(GetCount::class.java) { msg ->
                msg.replyTo.tell(CountValue(count))
                Behaviors.same()
            }
            .onMessage(StopCounter::class.java) { _ ->
                context.log.info("Counter stopping with final count: {}", count)
                Behaviors.stopped()
            }
            .build()
    }
}
