package cluster.kotlin

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.Behaviors

fun main() {
    val overrides = buildString {
        System.getenv("CLUSTER_HOSTNAME")?.let { appendLine("pekko.remote.artery.canonical.hostname = \"$it\"") }
        System.getenv("CLUSTER_PORT")?.let { appendLine("pekko.remote.artery.canonical.port = $it") }
        System.getenv("CLUSTER_SEED_NODES")?.let { appendLine("pekko.cluster.seed-nodes = $it") }
        System.getenv("CLUSTER_MIN_NR")?.let { appendLine("pekko.cluster.min-nr-of-members = $it") }
    }

    val config = ConfigFactory.parseString(overrides)
        .withFallback(ConfigFactory.load())

    val system = ActorSystem.create(
        Behaviors.setup<Nothing> { ctx ->
            ctx.spawn(
                ClusterListenerActor.create(ctx.system.ignoreRef()),
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
