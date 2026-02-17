using Akka.Actor;
using Akka.Cluster.Tools.Singleton;
using Akka.Configuration;
using Akka.DependencyInjection;
using Microsoft.Extensions.Hosting;

namespace ClusterActors;

public sealed class ActorRuntime : IHostedService
{
    private readonly IConfiguration _configuration;
    private readonly IServiceProvider _serviceProvider;

    public ActorRuntime(IConfiguration configuration, IServiceProvider serviceProvider)
    {
        _configuration = configuration;
        _serviceProvider = serviceProvider;
    }

    public ActorSystem System { get; private set; } = null!;
    public IActorRef HelloActor { get; private set; } = null!;
    public IActorRef ClusterInfoActor { get; private set; } = null!;
    public IActorRef KafkaStreamSingletonProxy { get; private set; } = null!;

    public Task StartAsync(CancellationToken cancellationToken)
    {
        var podName = Environment.GetEnvironmentVariable("POD_NAME");
        var clusterServiceName = Environment.GetEnvironmentVariable("CLUSTER_SERVICE_NAME") ?? "akkanet-cluster";

        var hostname =
            Environment.GetEnvironmentVariable("CLUSTER_HOSTNAME")
            ?? (!string.IsNullOrWhiteSpace(podName)
                ? $"{podName}.{clusterServiceName}.default.svc.cluster.local"
                : null)
            ?? _configuration["Cluster:Hostname"]
            ?? "127.0.0.1";

        var port = Environment.GetEnvironmentVariable("CLUSTER_PORT")
            ?? _configuration["Cluster:Port"]
            ?? "0";

        var seedNodes = Environment.GetEnvironmentVariable("CLUSTER_SEED_NODES")
            ?? _configuration["Cluster:SeedNodes"]
            ?? "";

        var minNr = Environment.GetEnvironmentVariable("CLUSTER_MIN_NR")
            ?? _configuration["Cluster:MinMembers"]
            ?? "1";

        var seedNodesHocon = string.IsNullOrWhiteSpace(seedNodes)
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
        var bootstrap = BootstrapSetup.Create().WithConfig(config);
        var di = DependencyResolverSetup.Create(_serviceProvider);
        var setup = bootstrap.And(di);
        System = ActorSystem.Create("ClusterSystem", setup);

        var kafkaBootstrapServers = Environment.GetEnvironmentVariable("KAFKA_BOOTSTRAP_SERVERS")
            ?? _configuration["Kafka:BootstrapServers"]
            ?? "kafka.default.svc.cluster.local:9092";

        var kafkaTopic = Environment.GetEnvironmentVariable("KAFKA_TOPIC")
            ?? _configuration["Kafka:Topic"]
            ?? "cluster-dotnet-events";

        var kafkaGroupIdPrefix = Environment.GetEnvironmentVariable("KAFKA_GROUP_ID_PREFIX")
            ?? _configuration["Kafka:GroupIdPrefix"]
            ?? "cluster-dotnet-group";

        System.ActorOf(
            ClusterSingletonManager.Props(
                Props.Create(() => new KafkaStreamSingletonActor(
                    kafkaBootstrapServers,
                    kafkaTopic,
                    kafkaGroupIdPrefix,
                    TimeSpan.FromSeconds(30),
                    null)),
                new KafkaStreamSingletonActor.Stop(),
                ClusterSingletonManagerSettings.Create(System)),
            "kafkaStreamSingletonManager");

        KafkaStreamSingletonProxy = System.ActorOf(
            ClusterSingletonProxy.Props(
                "/user/kafkaStreamSingletonManager",
                ClusterSingletonProxySettings.Create(System)),
            "kafkaStreamSingletonProxy");

        var resolver = DependencyResolver.For(System);
        HelloActor = System.ActorOf(resolver.Props<HelloActor>(), "helloActor");
        ClusterInfoActor = System.ActorOf(Props.Create(() => new ClusterInfoActor()), "clusterInfoActor");
        System.ActorOf(Props.Create(() => new ClusterListenerActor(System.DeadLetters)), "clusterListener");

        return Task.CompletedTask;
    }

    public async Task StopAsync(CancellationToken cancellationToken)
    {
        if (System == null)
            return;

        await CoordinatedShutdown.Get(System).Run(CoordinatedShutdown.ClrExitReason.Instance);
        await System.Terminate();
    }
}
