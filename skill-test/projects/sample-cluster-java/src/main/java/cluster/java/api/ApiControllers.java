package cluster.java.api;

import akka.pattern.PatternsCS;
import cluster.java.ClusterInfoActor;
import cluster.java.HelloActor;
import cluster.java.KafkaStreamSingletonActor;
import cluster.java.config.AkkaActorRuntime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
}
