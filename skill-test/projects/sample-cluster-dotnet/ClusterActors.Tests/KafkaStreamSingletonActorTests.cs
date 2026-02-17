using Akka.Actor;
using Akka.Cluster;
using Akka.Configuration;
using Akka.TestKit.Xunit2;
using ClusterActors;
using Xunit;

namespace ClusterActors.Tests;

public class KafkaStreamSingletonActorTests : TestKit
{
    private static readonly string HoconConfig = @"
        akka {
            actor.provider = cluster
            remote.dot-netty.tcp {
                hostname = ""127.0.0.1""
                port = 0
            }
            cluster {
                seed-nodes = []
                min-nr-of-members = 1
            }
            loglevel = INFO
        }";

    public KafkaStreamSingletonActorTests() : base(ConfigurationFactory.ParseString(HoconConfig))
    {
        var cluster = Cluster.Get(Sys);
        cluster.Join(cluster.SelfAddress);
        AwaitAssert(() =>
        {
            var upCount = cluster.State.Members.Count(m => m.Status == MemberStatus.Up);
            Assert.True(upCount >= 1, $"Expected at least 1 Up member but got {upCount}");
        }, TimeSpan.FromSeconds(10), TimeSpan.FromMilliseconds(200));
    }

    [Fact]
    public void Kafka_singleton_should_execute_stream_runner_only_once()
    {
        var runner = new FakeKafkaStreamRunner();

        var actor = Sys.ActorOf(
            Props.Create(() => new KafkaStreamSingletonActor(
                "localhost:9092",
                "cluster-dotnet-events",
                "test-group",
                TimeSpan.FromSeconds(3),
                runner)));

        actor.Tell(new KafkaStreamSingletonActor.Start());
        actor.Tell(new KafkaStreamSingletonActor.Start());
        actor.Tell(new KafkaStreamSingletonActor.Start());

        AwaitAssert(() => Assert.Equal(1, runner.Calls), TimeSpan.FromSeconds(3), TimeSpan.FromMilliseconds(100));
    }

    [Fact]
    public void Kafka_fire_event_should_reply_success()
    {
        var runner = new FakeKafkaStreamRunner();
        var actor = Sys.ActorOf(
            Props.Create(() => new KafkaStreamSingletonActor(
                "localhost:9092",
                "cluster-dotnet-events",
                "test-group",
                TimeSpan.FromSeconds(3),
                runner)));

        actor.Tell(new KafkaStreamSingletonActor.FireEvent(), TestActor);

        var result = ExpectMsg<KafkaStreamSingletonActor.FireEventResult>(TimeSpan.FromSeconds(3));
        Assert.True(result.Success);
        Assert.NotNull(result.Observed);
    }

    private sealed class FakeKafkaStreamRunner : IKafkaStreamRunner
    {
        public int Calls { get; private set; }

        public Task<string> RunOnceAsync(
            string bootstrapServers,
            string topic,
            string groupIdPrefix,
            string payload,
            TimeSpan timeout)
        {
            Calls++;
            return Task.FromResult(payload);
        }
    }
}
