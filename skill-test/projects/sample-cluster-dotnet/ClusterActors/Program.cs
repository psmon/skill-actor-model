using Akka.Actor;
using Akka.Configuration;
using ClusterActors;

var hostname = Environment.GetEnvironmentVariable("CLUSTER_HOSTNAME") ?? "127.0.0.1";
var port = Environment.GetEnvironmentVariable("CLUSTER_PORT") ?? "0";
var seedNodes = Environment.GetEnvironmentVariable("CLUSTER_SEED_NODES") ?? "";
var minNr = Environment.GetEnvironmentVariable("CLUSTER_MIN_NR") ?? "1";

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
    coordinated-shutdown.exit-clr = on
    loglevel = INFO
}}
";

var config = ConfigurationFactory.ParseString(hocon);
var system = ActorSystem.Create("ClusterSystem", config);

system.ActorOf(Props.Create(() => new ClusterListenerActor(system.DeadLetters)), "clusterListener");

Console.WriteLine($"Cluster system started: {system.Name}");

await system.WhenTerminated;
