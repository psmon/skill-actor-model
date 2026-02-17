package cluster.kotlin

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap
import org.apache.pekko.management.javadsl.PekkoManagement
import java.time.Duration

fun main() {
    val overrides = buildString {
        System.getenv("CLUSTER_HOSTNAME")?.let { appendLine("pekko.remote.artery.canonical.hostname = \"$it\"") }
        System.getenv("CLUSTER_PORT")?.let { appendLine("pekko.remote.artery.canonical.port = $it") }
        System.getenv("CLUSTER_MIN_NR")?.let { appendLine("pekko.cluster.min-nr-of-members = $it") }
        System.getenv("CLUSTER_SEED_NODES")?.let { appendLine("pekko.cluster.seed-nodes = $it") } // local fallback
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
    val kafkaStartDelaySeconds = System.getenv("KAFKA_START_DELAY_SECONDS")?.toLongOrNull() ?: 15L
    val requiredMembers = System.getenv("CLUSTER_MIN_NR")?.toIntOrNull() ?: 1

    val system = ActorSystem.create(
        Behaviors.setup<Nothing> { ctx ->
            val kafkaSingletonProxy = ClusterSingleton.get(ctx.system).init(
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

            ctx.spawn(
                ClusterListenerActor.create(
                    ctx.system.ignoreRef(),
                    kafkaSingletonProxy,
                    requiredMembers,
                    Duration.ofSeconds(kafkaStartDelaySeconds)
                ),
                "clusterListener"
            )
            Behaviors.empty()
        },
        "ClusterSystem",
        config
    )

    PekkoManagement.get(system).start()
    ClusterBootstrap.get(system).start()

    println("Cluster system started: ${system.name()}")

    system.whenTerminated.toCompletableFuture().join()
}
