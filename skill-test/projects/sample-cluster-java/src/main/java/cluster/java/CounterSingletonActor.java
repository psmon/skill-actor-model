package cluster.java;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * A simple counter actor designed for use as a cluster singleton.
 * Supports Increment and GetCount messages.
 */
public class CounterSingletonActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private int count = 0;

    // --- Messages ---

    /** Increment the counter by one. Singleton instance. */
    public static final class Increment {
        public static final Increment INSTANCE = new Increment();
        private Increment() {}
    }

    /** Request the current count. The reply is sent to the provided replyTo reference. */
    public static final class GetCount {
        private final ActorRef replyTo;

        public GetCount(ActorRef replyTo) {
            this.replyTo = replyTo;
        }

        public ActorRef getReplyTo() {
            return replyTo;
        }
    }

    /** Response message carrying the current count value. */
    public static final class CountValue {
        private final int value;

        public CountValue(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // --- Actor lifecycle ---

    public static Props props() {
        return Props.create(CounterSingletonActor.class, CounterSingletonActor::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Increment.class, msg -> {
                count++;
                log.info("Counter incremented to {}", count);
            })
            .match(GetCount.class, msg -> {
                log.info("Returning count: {}", count);
                msg.getReplyTo().tell(new CountValue(count), getSelf());
            })
            .build();
    }
}
