package cluster.java.cafe24;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Cafe24MetricsSingletonActor extends AbstractActor {

    public record RecordCall(String mallId, int statusCode, long queueDelayMs) implements Serializable {
    }

    public record GetMallMetrics(String mallId) implements Serializable {
    }

    public record MallMetrics(String mallId, long totalCalls, long throttled429, double avgQueueDelayMs)
        implements Serializable {
    }

    public enum Stop implements Serializable {
        INSTANCE
    }

    private static final class MetricState {
        long totalCalls;
        long throttled429;
        long sumQueueDelayMs;
    }

    private final Map<String, MetricState> stateByMall = new HashMap<>();

    public static Props props() {
        return Props.create(Cafe24MetricsSingletonActor.class, Cafe24MetricsSingletonActor::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(RecordCall.class, this::onRecordCall)
            .match(GetMallMetrics.class, this::onGetMallMetrics)
            .match(Stop.class, stop -> getContext().stop(getSelf()))
            .build();
    }

    private void onRecordCall(RecordCall msg) {
        MetricState state = stateByMall.computeIfAbsent(msg.mallId(), key -> new MetricState());
        state.totalCalls += 1;
        if (msg.statusCode() == 429) {
            state.throttled429 += 1;
        }
        state.sumQueueDelayMs += msg.queueDelayMs();
    }

    private void onGetMallMetrics(GetMallMetrics msg) {
        MetricState state = stateByMall.getOrDefault(msg.mallId(), new MetricState());
        double avgDelay = state.totalCalls == 0 ? 0.0 : (double) state.sumQueueDelayMs / state.totalCalls;
        getSender().tell(
            new MallMetrics(msg.mallId(), state.totalCalls, state.throttled429, avgDelay),
            getSelf());
    }
}
