package cluster.java;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;

import java.time.Duration;

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
        String kafkaBootstrapServers = System.getenv().getOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS", "kafka.default.svc.cluster.local:9092");
        String kafkaTopic = System.getenv().getOrDefault("KAFKA_TOPIC", "cluster-java-events");
        String kafkaGroupIdPrefix = System.getenv().getOrDefault("KAFKA_GROUP_ID_PREFIX", "cluster-java-group");
        int kafkaStartDelaySeconds = Integer.parseInt(System.getenv().getOrDefault("KAFKA_START_DELAY_SECONDS", "15"));
        int requiredMembers = Integer.parseInt(System.getenv().getOrDefault("CLUSTER_MIN_NR", "1"));

        Config config = ConfigFactory.parseString(overrides.toString())
            .withFallback(ConfigFactory.load());

        ActorSystem system = ActorSystem.create("ClusterSystem", config);

        system.actorOf(
            ClusterSingletonManager.props(
                KafkaStreamSingletonActor.props(
                    kafkaBootstrapServers,
                    kafkaTopic,
                    kafkaGroupIdPrefix,
                    Duration.ofSeconds(30)),
                KafkaStreamSingletonActor.Stop.INSTANCE,
                ClusterSingletonManagerSettings.create(system)),
            "kafkaStreamSingletonManager");

        ActorRef kafkaSingletonProxy = system.actorOf(
            ClusterSingletonProxy.props(
                "/user/kafkaStreamSingletonManager",
                ClusterSingletonProxySettings.create(system)),
            "kafkaStreamSingletonProxy");

        system.actorOf(
            ClusterListenerActor.props(
                system.deadLetters(),
                kafkaSingletonProxy,
                requiredMembers,
                Duration.ofSeconds(kafkaStartDelaySeconds)),
            "clusterListener"
        );

        System.out.println("Cluster system started: " + system.name());

        system.getWhenTerminated().toCompletableFuture().join();
    }
}
