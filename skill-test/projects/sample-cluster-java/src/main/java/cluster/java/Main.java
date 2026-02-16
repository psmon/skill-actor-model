package cluster.java;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;

/**
 * Main entry point for the cluster application.
 * Creates the ActorSystem, spawns ClusterListenerActor, and blocks until terminated.
 */
public class Main {

    public static void main(String[] args) {
        StringBuilder overrides = new StringBuilder();
        String hostname = System.getenv("CLUSTER_HOSTNAME");
        if (hostname != null) overrides.append("akka.remote.artery.canonical.hostname = \"").append(hostname).append("\"\n");
        String port = System.getenv("CLUSTER_PORT");
        if (port != null) overrides.append("akka.remote.artery.canonical.port = ").append(port).append("\n");
        String seedNodes = System.getenv("CLUSTER_SEED_NODES");
        if (seedNodes != null) overrides.append("akka.cluster.seed-nodes = ").append(seedNodes).append("\n");
        String minNr = System.getenv("CLUSTER_MIN_NR");
        if (minNr != null) overrides.append("akka.cluster.min-nr-of-members = ").append(minNr).append("\n");

        Config config = ConfigFactory.parseString(overrides.toString())
            .withFallback(ConfigFactory.load());

        ActorSystem system = ActorSystem.create("ClusterSystem", config);

        ActorRef listener = system.actorOf(
            ClusterListenerActor.props(system.deadLetters()),
            "clusterListener"
        );

        System.out.println("Cluster system started: " + system.name());

        system.getWhenTerminated().toCompletableFuture().join();
    }
}
