package cluster.java.config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import cluster.java.ClusterInfoActor;
import cluster.java.ClusterListenerActor;
import cluster.java.KafkaStreamSingletonActor;
import cluster.java.infra.SpringExtensionProvider;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AkkaActorRuntime {

    private final ActorSystem actorSystem;
    private final ActorRef helloActor;
    private final ActorRef clusterInfoActor;
    private final ActorRef kafkaSingletonProxy;

    public AkkaActorRuntime(ApplicationContext applicationContext) {
        StringBuilder overrides = new StringBuilder();
        String hostname = System.getenv("CLUSTER_HOSTNAME");
        if (hostname != null) overrides.append("akka.remote.artery.canonical.hostname = \"").append(hostname).append("\"\n");
        String port = System.getenv("CLUSTER_PORT");
        if (port != null) overrides.append("akka.remote.artery.canonical.port = ").append(port).append("\n");
        String seedNodes = System.getenv("CLUSTER_SEED_NODES");
        if (seedNodes != null) overrides.append("akka.cluster.seed-nodes = ").append(seedNodes).append("\n");
        String minNr = System.getenv("CLUSTER_MIN_NR");
        if (minNr != null) overrides.append("akka.cluster.min-nr-of-members = ").append(minNr).append("\n");

        Config config = ConfigFactory.parseString(overrides.toString()).withFallback(ConfigFactory.load());
        this.actorSystem = ActorSystem.create("ClusterSystem", config);

        SpringExtensionProvider.getInstance().get(actorSystem).initialize(applicationContext);

        String kafkaBootstrapServers = System.getenv().getOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS", "kafka.default.svc.cluster.local:9092");
        String kafkaTopic = System.getenv().getOrDefault("KAFKA_TOPIC", "cluster-java-events");
        String kafkaGroupIdPrefix = System.getenv().getOrDefault("KAFKA_GROUP_ID_PREFIX", "cluster-java-group");

        actorSystem.actorOf(
            ClusterSingletonManager.props(
                KafkaStreamSingletonActor.props(
                    kafkaBootstrapServers,
                    kafkaTopic,
                    kafkaGroupIdPrefix,
                    Duration.ofSeconds(30)),
                KafkaStreamSingletonActor.Stop.INSTANCE,
                ClusterSingletonManagerSettings.create(actorSystem)),
            "kafkaStreamSingletonManager");

        this.kafkaSingletonProxy = actorSystem.actorOf(
            ClusterSingletonProxy.props(
                "/user/kafkaStreamSingletonManager",
                ClusterSingletonProxySettings.create(actorSystem)),
            "kafkaStreamSingletonProxy");

        this.helloActor = actorSystem.actorOf(
            SpringExtensionProvider.getInstance().get(actorSystem).props("helloActorBean"),
            "helloActor");

        this.clusterInfoActor = actorSystem.actorOf(ClusterInfoActor.props(), "clusterInfoActor");
        actorSystem.actorOf(ClusterListenerActor.props(actorSystem.deadLetters()), "clusterListener");

    }

    public ActorSystem actorSystem() {
        return actorSystem;
    }

    public ActorRef helloActor() {
        return helloActor;
    }

    public ActorRef clusterInfoActor() {
        return clusterInfoActor;
    }

    public ActorRef kafkaSingletonProxy() {
        return kafkaSingletonProxy;
    }

    @PreDestroy
    public void shutdown() {
        actorSystem.terminate();
    }
}
