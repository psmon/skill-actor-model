package cluster.java.api;

import akka.pattern.PatternsCS;
import cluster.java.ClusterInfoActor;
import cluster.java.HelloActor;
import cluster.java.KafkaStreamSingletonActor;
import cluster.java.cafe24.Cafe24ApiManagerActor;
import cluster.java.cafe24.Cafe24MetricsSingletonActor;
import cluster.java.config.AkkaActorRuntime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@RestController
@RequestMapping("/api")
public class ApiControllers {

    private final AkkaActorRuntime runtime;

    public ApiControllers(AkkaActorRuntime runtime) {
        this.runtime = runtime;
    }

    @GetMapping("/heath")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "sample-cluster-java",
            "timestamp", Instant.now().toString());
    }

    @GetMapping("/actor/hello")
    public CompletionStage<HelloActor.HelloResponse> hello() {
        return PatternsCS.ask(runtime.helloActor(), new HelloActor.Hello("hello"), Duration.ofSeconds(5))
            .thenApply(HelloActor.HelloResponse.class::cast);
    }

    @GetMapping("/cluster/info")
    public CompletionStage<ClusterInfoActor.ClusterInfoResponse> clusterInfo() {
        return PatternsCS.ask(runtime.clusterInfoActor(), new ClusterInfoActor.GetClusterInfo(), Duration.ofSeconds(5))
            .thenApply(ClusterInfoActor.ClusterInfoResponse.class::cast);
    }

    @PostMapping("/kafka/fire-event")
    public CompletionStage<ResponseEntity<KafkaStreamSingletonActor.FireEventResult>> fireEvent() {
        return PatternsCS.ask(
                runtime.kafkaSingletonProxy(),
                KafkaStreamSingletonActor.FireEvent.INSTANCE,
                Duration.ofSeconds(35))
            .thenApply(KafkaStreamSingletonActor.FireEventResult.class::cast)
            .thenApply(result -> result.success()
                ? ResponseEntity.ok(result)
                : ResponseEntity.internalServerError().body(result));
    }

    @GetMapping("/cafe24/call")
    public CompletionStage<Cafe24ApiManagerActor.ApiResponse> cafe24Call(
        @RequestParam String mallId,
        @RequestParam String word
    ) {
        return PatternsCS.ask(
                runtime.cafe24ApiManager(),
                new Cafe24ApiManagerActor.ApiRequest(mallId, word),
                Duration.ofSeconds(15))
            .thenApply(Cafe24ApiManagerActor.ApiResponse.class::cast);
    }

    @GetMapping("/cafe24/metrics")
    public CompletionStage<Cafe24MetricsSingletonActor.MallMetrics> cafe24Metrics(
        @RequestParam String mallId
    ) {
        return PatternsCS.ask(
                runtime.cafe24MetricsProxy(),
                new Cafe24MetricsSingletonActor.GetMallMetrics(mallId),
                Duration.ofSeconds(5))
            .thenApply(Cafe24MetricsSingletonActor.MallMetrics.class::cast);
    }
}
