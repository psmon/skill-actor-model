package cluster.java.cafe24;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.stream.OverflowStrategy;
import akka.stream.SystemMaterializer;
import akka.stream.ThrottleMode;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class Cafe24ApiManagerActor extends AbstractActor {

    public record ApiRequest(String mallId, String word) implements Serializable {
    }

    public record ApiResponse(
        String mallId,
        String word,
        String result,
        int statusCode,
        int bucketUsed,
        int bucketMax
    ) implements Serializable {
    }

    private final int perMallMaxRequestsPerSecond;
    private final DummyCafe24Api dummyCafe24Api;
    private final ActorRef metricsActor;

    public static Props props(int perMallMaxRequestsPerSecond, DummyCafe24Api dummyCafe24Api, ActorRef metricsActor) {
        return Props.create(Cafe24ApiManagerActor.class,
            () -> new Cafe24ApiManagerActor(perMallMaxRequestsPerSecond, dummyCafe24Api, metricsActor));
    }

    public Cafe24ApiManagerActor(int perMallMaxRequestsPerSecond, DummyCafe24Api dummyCafe24Api, ActorRef metricsActor) {
        this.perMallMaxRequestsPerSecond = perMallMaxRequestsPerSecond;
        this.dummyCafe24Api = dummyCafe24Api;
        this.metricsActor = metricsActor;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ApiRequest.class, this::onApiRequest)
            .build();
    }

    private void onApiRequest(ApiRequest msg) {
        ActorRef replyTo = getSender();
        String childName = "mall-" + msg.mallId().replaceAll("[^A-Za-z0-9_-]", "_");
        var existing = getContext().child(childName);
        ActorRef child;
        if (existing.isDefined()) {
            child = existing.get();
        } else {
            child = getContext().actorOf(
                MallApiCallerActor.props(
                    msg.mallId(),
                    perMallMaxRequestsPerSecond,
                    dummyCafe24Api,
                    metricsActor),
                childName);
        }

        child.tell(new MallApiCallerActor.CallMallApi(msg.word(), replyTo), getSelf());
    }

    static class MallApiCallerActor extends AbstractActor {

        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        record CallMallApi(String word, ActorRef replyTo) implements Serializable {
        }

        private record StreamEnvelope(CallMallApi request, long enqueuedAtNanos) implements Serializable {
        }

        private record StreamResult(CallMallApi request, ApiResponse response, long queueDelayMs) implements Serializable {
        }

        private final String mallId;
        private final int maxRequestsPerSecond;
        private final DummyCafe24Api dummyCafe24Api;
        private final ActorRef metricsActor;
        private ActorRef streamEntry;

        static Props props(
            String mallId,
            int maxRequestsPerSecond,
            DummyCafe24Api dummyCafe24Api,
            ActorRef metricsActor
        ) {
            return Props.create(
                MallApiCallerActor.class,
                () -> new MallApiCallerActor(mallId, maxRequestsPerSecond, dummyCafe24Api, metricsActor));
        }

        MallApiCallerActor(
            String mallId,
            int maxRequestsPerSecond,
            DummyCafe24Api dummyCafe24Api,
            ActorRef metricsActor
        ) {
            this.mallId = mallId;
            this.maxRequestsPerSecond = maxRequestsPerSecond;
            this.dummyCafe24Api = dummyCafe24Api;
            this.metricsActor = metricsActor;
        }

        @Override
        public void preStart() {
            var materializer = SystemMaterializer.get(getContext().getSystem()).materializer();

            streamEntry = Source.<StreamEnvelope>actorRef(256, OverflowStrategy.dropNew())
                .throttle(maxRequestsPerSecond, Duration.ofSeconds(1), maxRequestsPerSecond, ThrottleMode.shaping())
                .mapAsync(1, this::callApiWithAdaptiveBackpressure)
                .to(Sink.foreach(result -> getSelf().tell(result, getSelf())))
                .run(materializer);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                .match(CallMallApi.class, this::onCallMallApi)
                .match(StreamResult.class, this::onStreamResult)
                .build();
        }

        private void onCallMallApi(CallMallApi msg) {
            streamEntry.tell(new StreamEnvelope(msg, System.nanoTime()), getSelf());
        }

        private void onStreamResult(StreamResult msg) {
            msg.request().replyTo().tell(msg.response(), getSelf());
            log.info("Cafe24 safe call mall={} word={} status={} bucket={}",
                mallId, msg.response().word(), msg.response().statusCode(),
                msg.response().bucketUsed() + "/" + msg.response().bucketMax());
            metricsActor.tell(
                new Cafe24MetricsSingletonActor.RecordCall(mallId, msg.response().statusCode(), msg.queueDelayMs()),
                getSelf());
        }

        private CompletionStage<StreamResult> callApiWithAdaptiveBackpressure(StreamEnvelope envelope) {
            return executeWithRetry(envelope.request().word(), 0)
                .thenCompose(response -> {
                    double usageRatio = response.bucketMax() == 0
                        ? 0.0
                        : (double) response.bucketUsed() / response.bucketMax();

                    long adaptiveDelayMs = usageRatio > 0.8 ? 500 : usageRatio > 0.5 ? 200 : 0;
                    long queueDelayMs = (System.nanoTime() - envelope.enqueuedAtNanos()) / 1_000_000;
                    StreamResult result = new StreamResult(envelope.request(), response, queueDelayMs);

                    if (adaptiveDelayMs > 0) {
                        return delay(adaptiveDelayMs).thenApply(v -> result);
                    }
                    return CompletableFuture.completedFuture(result);
                });
        }

        private CompletionStage<ApiResponse> executeWithRetry(String word, int retryCount) {
            return dummyCafe24Api.call(mallId, word)
                .thenCompose(result -> {
                    if (result.statusCode() == 429 && retryCount < 3) {
                        return delay(result.callRemainSeconds() * 1000L)
                            .thenCompose(v -> executeWithRetry(word, retryCount + 1));
                    }

                    return CompletableFuture.completedFuture(
                        new ApiResponse(
                            mallId,
                            word,
                            result.body(),
                            result.statusCode(),
                            result.bucketUsed(),
                            result.bucketMax()
                        )
                    );
                });
        }

        private CompletionStage<Void> delay(long millis) {
            return CompletableFuture.supplyAsync(
                () -> null,
                CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS)
            );
        }
    }
}
