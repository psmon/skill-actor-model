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
import cluster.java.cafe24.Cafe24ApiManagerActor;
import cluster.java.cafe24.Cafe24MetricsSingletonActor;
import cluster.java.cafe24.DummyCafe24Api;
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
    private final ActorRef cafe24ApiManager;
    private final ActorRef cafe24MetricsProxy;
    private final DummyCafe24Api dummyCafe24Api;

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
        int cafe24BucketCapacity = Integer.parseInt(System.getenv().getOrDefault("CAFE24_BUCKET_CAPACITY", "10"));
        int cafe24LeakRate = Integer.parseInt(System.getenv().getOrDefault("CAFE24_LEAK_RATE_PER_SECOND", "2"));
        int cafe24PerMallRps = Integer.parseInt(System.getenv().getOrDefault("CAFE24_PER_MALL_MAX_RPS", "2"));

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
        actorSystem.actorOf(
            ClusterSingletonManager.props(
                Cafe24MetricsSingletonActor.props(),
                Cafe24MetricsSingletonActor.Stop.INSTANCE,
                ClusterSingletonManagerSettings.create(actorSystem)),
            "cafe24MetricsSingletonManager");

        this.kafkaSingletonProxy = actorSystem.actorOf(
            ClusterSingletonProxy.props(
                "/user/kafkaStreamSingletonManager",
                ClusterSingletonProxySettings.create(actorSystem)),
            "kafkaStreamSingletonProxy");
        this.cafe24MetricsProxy = actorSystem.actorOf(
            ClusterSingletonProxy.props(
                "/user/cafe24MetricsSingletonManager",
                ClusterSingletonProxySettings.create(actorSystem)),
            "cafe24MetricsSingletonProxy");

        this.dummyCafe24Api = new DummyCafe24Api(cafe24BucketCapacity, cafe24LeakRate);
        this.cafe24ApiManager = actorSystem.actorOf(
            Cafe24ApiManagerActor.props(cafe24PerMallRps, dummyCafe24Api, cafe24MetricsProxy),
            "cafe24ApiManager");

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

    public ActorRef cafe24ApiManager() {
        return cafe24ApiManager;
    }

    public ActorRef cafe24MetricsProxy() {
        return cafe24MetricsProxy;
    }

    @PreDestroy
    public void shutdown() {
        dummyCafe24Api.close();
        actorSystem.terminate();
    }
}
