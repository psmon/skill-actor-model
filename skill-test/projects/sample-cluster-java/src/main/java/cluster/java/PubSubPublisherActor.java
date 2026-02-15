package cluster.java;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * A PubSub publisher actor that publishes String messages to "test-topic".
 */
public class PubSubPublisherActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final ActorRef mediator;

    public PubSubPublisherActor() {
        this.mediator = DistributedPubSub.get(getContext().getSystem()).mediator();
    }

    public static Props props() {
        return Props.create(PubSubPublisherActor.class, PubSubPublisherActor::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, message -> {
                log.info("Publishing message to test-topic: {}", message);
                mediator.tell(
                    new DistributedPubSubMediator.Publish("test-topic", message),
                    getSelf()
                );
            })
            .build();
    }
}
