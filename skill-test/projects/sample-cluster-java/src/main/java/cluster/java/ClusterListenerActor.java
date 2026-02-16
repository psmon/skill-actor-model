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

import java.time.Duration;
import java.util.stream.StreamSupport;

/**
 * Actor that listens to cluster membership events and reports them.
 * Subscribes to MemberUp, UnreachableMember, and MemberRemoved events.
 */
public class ClusterListenerActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final Cluster cluster = Cluster.get(getContext().getSystem());
    private final ActorRef reportTo;
    private final ActorRef kafkaSingletonProxy;
    private final int requiredMembersForKafkaStart;
    private final Duration kafkaStartDelay;
    private boolean kafkaStartScheduled;

    public ClusterListenerActor(ActorRef reportTo) {
        this(reportTo, null, 1, Duration.ofSeconds(15));
    }

    public ClusterListenerActor(
        ActorRef reportTo,
        ActorRef kafkaSingletonProxy,
        int requiredMembersForKafkaStart,
        Duration kafkaStartDelay) {
        this.reportTo = reportTo;
        this.kafkaSingletonProxy = kafkaSingletonProxy;
        this.requiredMembersForKafkaStart = requiredMembersForKafkaStart;
        this.kafkaStartDelay = kafkaStartDelay;
    }

    public static Props props(ActorRef reportTo) {
        return Props.create(ClusterListenerActor.class, () -> new ClusterListenerActor(reportTo));
    }

    public static Props props(
        ActorRef reportTo,
        ActorRef kafkaSingletonProxy,
        int requiredMembersForKafkaStart,
        Duration kafkaStartDelay) {
        return Props.create(
            ClusterListenerActor.class,
            () -> new ClusterListenerActor(
                reportTo,
                kafkaSingletonProxy,
                requiredMembersForKafkaStart,
                kafkaStartDelay));
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
                tryScheduleKafkaRun();
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

    private void tryScheduleKafkaRun() {
        if (kafkaSingletonProxy == null || kafkaStartScheduled) {
            return;
        }

        long upMembers = StreamSupport.stream(cluster.state().getMembers().spliterator(), false)
            .filter(member -> member.status().equals(akka.cluster.MemberStatus.up()))
            .count();

        if (upMembers < requiredMembersForKafkaStart) {
            return;
        }

        kafkaStartScheduled = true;
        log.info(
            "Cluster is ready ({}/{} members Up). Scheduling Kafka singleton trigger in {}.",
            upMembers,
            requiredMembersForKafkaStart,
            kafkaStartDelay);

        getContext().getSystem().scheduler().scheduleOnce(
            scala.concurrent.duration.Duration.fromNanos(kafkaStartDelay.toNanos()),
            kafkaSingletonProxy,
            KafkaStreamSingletonActor.Start.INSTANCE,
            getContext().dispatcher(),
            getSelf());
    }
}
