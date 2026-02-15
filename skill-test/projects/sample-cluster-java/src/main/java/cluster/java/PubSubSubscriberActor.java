package cluster.java;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * A PubSub subscriber actor that subscribes to a given topic and forwards
 * received messages to a reportTo reference.
 */
public class PubSubSubscriberActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final ActorRef mediator;
    private final String topic;
    private final ActorRef reportTo;

    public PubSubSubscriberActor(String topic, ActorRef reportTo) {
        this.topic = topic;
        this.reportTo = reportTo;
        this.mediator = DistributedPubSub.get(getContext().getSystem()).mediator();

        // Subscribe to the topic
        mediator.tell(new DistributedPubSubMediator.Subscribe(topic, getSelf()), getSelf());
    }

    public static Props props(String topic, ActorRef reportTo) {
        return Props.create(PubSubSubscriberActor.class, () -> new PubSubSubscriberActor(topic, reportTo));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(DistributedPubSubMediator.SubscribeAck.class, ack -> {
                log.info("Subscribed to topic: {}", topic);
                reportTo.tell("subscribed", getSelf());
            })
            .match(String.class, message -> {
                log.info("Received message on topic [{}]: {}", topic, message);
                reportTo.tell(message, getSelf());
            })
            .build();
    }
}
