package cluster.kotlin

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.pubsub.Topic

sealed class PubSubCommand
data class PublishMessage(val topicName: String, val message: String) : PubSubCommand()
data class SubscribeToTopic(val topicName: String, val subscriber: ActorRef<String>) : PubSubCommand()

class PubSubManagerActor private constructor(
    context: ActorContext<PubSubCommand>
) : AbstractBehavior<PubSubCommand>(context) {

    companion object {
        fun create(): Behavior<PubSubCommand> = Behaviors.setup(::PubSubManagerActor)
    }

    private val topics = mutableMapOf<String, ActorRef<Topic.Command<String>>>()

    private fun getOrCreateTopic(topicName: String): ActorRef<Topic.Command<String>> {
        return topics.getOrPut(topicName) {
            context.spawn(
                Topic.create(String::class.java, topicName),
                "topic-$topicName"
            )
        }
    }

    override fun createReceive(): Receive<PubSubCommand> {
        return newReceiveBuilder()
            .onMessage(PublishMessage::class.java) { msg ->
                val topic = getOrCreateTopic(msg.topicName)
                topic.tell(Topic.publish(msg.message))
                context.log.info("Published '{}' to topic '{}'", msg.message, msg.topicName)
                Behaviors.same()
            }
            .onMessage(SubscribeToTopic::class.java) { msg ->
                val topic = getOrCreateTopic(msg.topicName)
                topic.tell(Topic.subscribe(msg.subscriber))
                context.log.info("Subscriber added to topic '{}'", msg.topicName)
                Behaviors.same()
            }
            .build()
    }
}
