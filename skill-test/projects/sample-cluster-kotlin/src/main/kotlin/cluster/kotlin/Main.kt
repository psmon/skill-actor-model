package cluster.kotlin

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Props
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap
import org.apache.pekko.management.javadsl.PekkoManagement
import com.typesafe.config.ConfigFactory
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@SpringBootApplication
class ClusterKotlinCoroutineApplication

fun main(args: Array<String>) {
    runApplication<ClusterKotlinCoroutineApplication>(*args)
}

@Component
class WelcomeMessageProvider {
    fun message(): String = "wellcome actor world!"
}

@Component
class AkkaActorRuntime(
    welcomeMessageProvider: WelcomeMessageProvider
) {
    lateinit var system: ActorSystem<Nothing>
    lateinit var helloActor: ActorRef<HelloCommand>
    lateinit var clusterInfoActor: ActorRef<ClusterInfoCommand>
    lateinit var kafkaSingletonProxy: ActorRef<KafkaSingletonCommand>
    lateinit var cafe24MetricsProxy: ActorRef<Cafe24MetricsCommand>
    lateinit var cafe24ApiManager: ActorRef<Cafe24ApiManagerCommand>
    private lateinit var dummyCafe24Api: DummyCafe24Api

    init {
        val overrides = buildString {
            System.getenv("CLUSTER_HOSTNAME")?.let { appendLine("pekko.remote.artery.canonical.hostname = \"$it\"") }
            System.getenv("CLUSTER_PORT")?.let { appendLine("pekko.remote.artery.canonical.port = $it") }
            System.getenv("CLUSTER_MIN_NR")?.let { appendLine("pekko.cluster.min-nr-of-members = $it") }
            System.getenv("CLUSTER_SEED_NODES")?.let { appendLine("pekko.cluster.seed-nodes = $it") }
            System.getenv("CLUSTER_BOOTSTRAP_SERVICE_NAME")
                ?.let { appendLine("pekko.management.cluster.bootstrap.contact-point-discovery.service-name = \"$it\"") }
            System.getenv("CLUSTER_BOOTSTRAP_REQUIRED_CONTACT_POINTS")
                ?.let { appendLine("pekko.management.cluster.bootstrap.contact-point-discovery.required-contact-point-nr = $it") }
            System.getenv("CLUSTER_BOOTSTRAP_PORT_NAME")
                ?.let { appendLine("pekko.management.cluster.bootstrap.contact-point-discovery.port-name = \"$it\"") }
            System.getenv("MANAGEMENT_HOSTNAME")?.let { appendLine("pekko.management.http.hostname = \"$it\"") }
            System.getenv("MANAGEMENT_PORT")?.let { appendLine("pekko.management.http.port = $it") }
        }

        val config = ConfigFactory.parseString(overrides)
            .withFallback(ConfigFactory.load())

        val kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS")
            ?: "kafka.default.svc.cluster.local:9092"
        val kafkaTopic = System.getenv("KAFKA_TOPIC") ?: "cluster-kotlin-events"
        val kafkaGroupIdPrefix = System.getenv("KAFKA_GROUP_ID_PREFIX") ?: "cluster-kotlin-group"
        val cafe24BucketCapacity = (System.getenv("CAFE24_BUCKET_CAPACITY") ?: "10").toInt()
        val cafe24LeakRate = (System.getenv("CAFE24_LEAK_RATE_PER_SECOND") ?: "2").toInt()
        val cafe24MallRps = (System.getenv("CAFE24_PER_MALL_MAX_RPS") ?: "2").toInt()

        system = ActorSystem.create(Behaviors.empty(), "ClusterSystem", config)

        helloActor = system.systemActorOf(HelloActor.create(welcomeMessageProvider), "helloActor", Props.empty())
        clusterInfoActor = system.systemActorOf(ClusterInfoActor.create(), "clusterInfoActor", Props.empty())
        kafkaSingletonProxy = ClusterSingleton.get(system).init(
            SingletonActor.of(
                KafkaStreamSingletonActor.create(
                    kafkaBootstrapServers,
                    kafkaTopic,
                    kafkaGroupIdPrefix,
                    Duration.ofSeconds(30)
                ),
                "kafkaStreamSingleton"
            ).withStopMessage(StopKafkaStream)
        )
        cafe24MetricsProxy = ClusterSingleton.get(system).init(
            SingletonActor.of(
                Cafe24MetricsSingletonActor.create(),
                "cafe24MetricsSingleton"
            ).withStopMessage(StopCafe24Metrics)
        )
        dummyCafe24Api = DummyCafe24Api(cafe24BucketCapacity, cafe24LeakRate)
        cafe24ApiManager = system.systemActorOf(
            Cafe24ApiManagerActor.create(cafe24MallRps, dummyCafe24Api, cafe24MetricsProxy),
            "cafe24ApiManager",
            Props.empty()
        )
        system.systemActorOf(ClusterListenerActor.create(system.ignoreRef()), "clusterListener", Props.empty())

        PekkoManagement.get(system).start()
        ClusterBootstrap.get(system).start()
    }

    @PreDestroy
    fun shutdown() {
        dummyCafe24Api.close()
        system.terminate()
    }
}

@RestController
@RequestMapping("/api")
class ApiController(
    private val runtime: AkkaActorRuntime
) {
    @GetMapping("/heath")
    suspend fun heath(): Map<String, String> = mapOf(
        "status" to "UP",
        "service" to "sample-cluster-kotlin",
        "timestamp" to Instant.now().toString()
    )

    @GetMapping("/actor/hello")
    suspend fun hello(): HelloResponse {
        return AskPattern.ask<HelloCommand, HelloResponse>(
            runtime.helloActor,
            { replyTo -> Hello("hello", replyTo) },
            Duration.ofSeconds(5),
            runtime.system.scheduler()
        ).toCompletableFuture().join()
    }

    @GetMapping("/cluster/info")
    suspend fun clusterInfo(): ClusterInfoResponse {
        return AskPattern.ask<ClusterInfoCommand, ClusterInfoResponse>(
            runtime.clusterInfoActor,
            { replyTo -> GetClusterInfo(replyTo) },
            Duration.ofSeconds(5),
            runtime.system.scheduler()
        ).toCompletableFuture().join()
    }

    @PostMapping("/kafka/fire-event")
    suspend fun fireEvent(): ResponseEntity<KafkaFireEventResult> {
        val result = AskPattern.ask<KafkaSingletonCommand, KafkaFireEventResult>(
            runtime.kafkaSingletonProxy,
            { replyTo -> FireKafkaEvent(replyTo) },
            Duration.ofSeconds(35),
            runtime.system.scheduler()
        ).toCompletableFuture().join()

        return if (result.success) ResponseEntity.ok(result)
        else ResponseEntity.internalServerError().body(result)
    }

    @GetMapping("/cafe24/call")
    suspend fun cafe24Call(
        @RequestParam mallId: String,
        @RequestParam word: String
    ): Cafe24ApiResponse {
        return AskPattern.ask<Cafe24ApiManagerCommand, Cafe24ApiResponse>(
            runtime.cafe24ApiManager,
            { replyTo -> Cafe24ApiRequest(mallId, word, replyTo) },
            Duration.ofSeconds(15),
            runtime.system.scheduler()
        ).toCompletableFuture().join()
    }

    @GetMapping("/cafe24/metrics")
    suspend fun cafe24Metrics(
        @RequestParam mallId: String
    ): Cafe24MetricsResponse {
        return AskPattern.ask<Cafe24MetricsCommand, Cafe24MetricsResponse>(
            runtime.cafe24MetricsProxy,
            { replyTo -> GetMallMetrics(mallId, replyTo) },
            Duration.ofSeconds(5),
            runtime.system.scheduler()
        ).toCompletableFuture().join()
    }
}
