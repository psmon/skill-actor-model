package cluster.kotlin

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import java.time.Duration

fun main() {
    val overrides = buildString {
        System.getenv("CLUSTER_HOSTNAME")?.let { appendLine("pekko.remote.artery.canonical.hostname = \"$it\"") }
        System.getenv("CLUSTER_PORT")?.let { appendLine("pekko.remote.artery.canonical.port = $it") }
        System.getenv("CLUSTER_SEED_NODES")?.let { appendLine("pekko.cluster.seed-nodes = $it") }
        System.getenv("CLUSTER_MIN_NR")?.let { appendLine("pekko.cluster.min-nr-of-members = $it") }
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

    println("Cluster system started: ${system.name()}")

    system.whenTerminated.toCompletableFuture().join()
}
