package cluster.java;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * Actor that listens to cluster membership events and reports them.
 * Subscribes to MemberUp, UnreachableMember, and MemberRemoved events.
 */
public class ClusterListenerActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final Cluster cluster = Cluster.get(getContext().getSystem());
    private final ActorRef reportTo;

    public ClusterListenerActor(ActorRef reportTo) {
        this.reportTo = reportTo;
    }

    public static Props props(ActorRef reportTo) {
        return Props.create(ClusterListenerActor.class, () -> new ClusterListenerActor(reportTo));
    }

    @Override
    public void preStart() {
        cluster.subscribe(
            getSelf(),
            ClusterEvent.initialStateAsEvents(),
            MemberUp.class,
            UnreachableMember.class,
            MemberRemoved.class
        );
    }

    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(MemberUp.class, event -> {
                log.info("Member is Up: {}", event.member());
                reportTo.tell("member-up", getSelf());
            })
            .match(UnreachableMember.class, event -> {
                log.warning("Member detected as unreachable: {}", event.member());
                reportTo.tell("member-unreachable", getSelf());
            })
            .match(MemberRemoved.class, event -> {
                log.info("Member is Removed: {}", event.member());
                reportTo.tell("member-removed", getSelf());
            })
            .build();
    }
}
