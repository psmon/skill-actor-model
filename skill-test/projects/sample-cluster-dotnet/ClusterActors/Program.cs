using Akka.Actor;
using Akka.Cluster.Tools.Singleton;
using Akka.Configuration;
using ClusterActors;

var hostname = Environment.GetEnvironmentVariable("CLUSTER_HOSTNAME") ?? "127.0.0.1";
var port = Environment.GetEnvironmentVariable("CLUSTER_PORT") ?? "0";
var seedNodes = Environment.GetEnvironmentVariable("CLUSTER_SEED_NODES") ?? "";
var minNr = Environment.GetEnvironmentVariable("CLUSTER_MIN_NR") ?? "1";
var kafkaBootstrapServers = Environment.GetEnvironmentVariable("KAFKA_BOOTSTRAP_SERVERS")
    ?? "kafka.default.svc.cluster.local:9092";
var kafkaTopic = Environment.GetEnvironmentVariable("KAFKA_TOPIC") ?? "cluster-dotnet-events";
var kafkaGroupIdPrefix = Environment.GetEnvironmentVariable("KAFKA_GROUP_ID_PREFIX") ?? "cluster-dotnet-group";
var kafkaStartDelaySeconds = int.TryParse(
    Environment.GetEnvironmentVariable("KAFKA_START_DELAY_SECONDS"),
    out var parsedDelay)
    ? parsedDelay
    : 15;

var seedNodesHocon = string.IsNullOrEmpty(seedNodes)
    ? "seed-nodes = []"
    : $"seed-nodes = [{seedNodes}]";

var hocon = $@"
akka {{
    actor.provider = cluster
    remote {{
        dot-netty.tcp {{
            hostname = ""{hostname}""
            port = {port}
        }}
    }}
    cluster {{
        {seedNodesHocon}
        min-nr-of-members = {minNr}
        downing-provider-class = ""Akka.Cluster.SBR.SplitBrainResolverProvider, Akka.Cluster""
    }}
    kafka.producer {{
        kafka-clients {{}}
    }}
    kafka.consumer {{
        kafka-clients {{}}
    }}
    kafka.default-dispatcher {{
        type = Dispatcher
        executor = ""fork-join-executor""
        fork-join-executor {{
            parallelism-min = 2
            parallelism-factor = 1.0
            parallelism-max = 8
        }}
        throughput = 100
    }}
    coordinated-shutdown.exit-clr = on
    loglevel = INFO
}}
";

var config = ConfigurationFactory.ParseString(hocon);
var system = ActorSystem.Create("ClusterSystem", config);

system.ActorOf(
    ClusterSingletonManager.Props(
        Props.Create(() => new KafkaStreamSingletonActor(
            kafkaBootstrapServers,
            kafkaTopic,
            kafkaGroupIdPrefix,
            TimeSpan.FromSeconds(30),
            null)),
        new KafkaStreamSingletonActor.Stop(),
        ClusterSingletonManagerSettings.Create(system)),
    "kafkaStreamSingletonManager");

var kafkaStreamSingletonProxy = system.ActorOf(
    ClusterSingletonProxy.Props(
        "/user/kafkaStreamSingletonManager",
        ClusterSingletonProxySettings.Create(system)),
    "kafkaStreamSingletonProxy");

system.ActorOf(
    Props.Create(() => new ClusterListenerActor(
        system.DeadLetters,
        kafkaStreamSingletonProxy,
        int.Parse(minNr),
        TimeSpan.FromSeconds(kafkaStartDelaySeconds))),
    "clusterListener");

Console.WriteLine($"Cluster system started: {system.Name}");

await system.WhenTerminated;
