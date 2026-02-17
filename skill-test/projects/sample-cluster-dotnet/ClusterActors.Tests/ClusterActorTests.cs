using Akka.Actor;
using Akka.Cluster;
using Akka.Configuration;
using Akka.TestKit.Xunit2;
using ClusterActors;
using Xunit;

namespace ClusterActors.Tests;

public class ClusterActorTests : TestKit
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

    public ClusterActorTests() : base(ConfigurationFactory.ParseString(HoconConfig))
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
    public void ClusterListener_should_receive_MemberUp_event()
    {
        var listener = Sys.ActorOf(Props.Create(() => new ClusterListenerActor(TestActor)));
        ExpectMsg("member-up", TimeSpan.FromSeconds(5));
        ExpectNoMsg(TimeSpan.FromMilliseconds(500));
    }

    [Fact]
    public void HelloActor_should_reply_welcome_message()
    {
        var actor = Sys.ActorOf(Props.Create(() => new HelloActor(new WelcomeMessageProvider())));
        actor.Tell(new HelloActor.Hello("hello"), TestActor);

        var response = ExpectMsg<HelloActor.HelloResponse>(TimeSpan.FromSeconds(3));
        Assert.Equal("wellcome actor world!", response.Message);
    }

    [Fact]
    public void ClusterInfoActor_should_return_self_member()
    {
        var actor = Sys.ActorOf(Props.Create(() => new ClusterInfoActor()));
        actor.Tell(new ClusterInfoActor.GetClusterInfo(), TestActor);

        var response = ExpectMsg<ClusterInfoActor.ClusterInfoResponse>(TimeSpan.FromSeconds(3));
        Assert.True(response.MemberCount >= 1);
    }

    [Fact]
    public void CounterSingleton_should_count_correctly()
    {
        var counter = Sys.ActorOf(Props.Create<CounterSingletonActor>());

        counter.Tell(new CounterSingletonActor.Increment());
        counter.Tell(new CounterSingletonActor.Increment());
        counter.Tell(new CounterSingletonActor.Increment());
        counter.Tell(new CounterSingletonActor.GetCount(TestActor));

        var result = ExpectMsg<CounterSingletonActor.CountValue>(TimeSpan.FromSeconds(3));
        Assert.Equal(3, result.Value);
    }

    [Fact]
    public void PubSub_should_deliver_message_to_subscriber()
    {
        var subscriber = Sys.ActorOf(
            Props.Create(() => new PubSubSubscriberActor("test-topic", TestActor)));

        ExpectMsg("subscribed", TimeSpan.FromSeconds(5));

        var publisher = Sys.ActorOf(Props.Create<PubSubPublisherActor>());
        publisher.Tell("hello-cluster");

        ExpectMsg("hello-cluster", TimeSpan.FromSeconds(5));
        ExpectNoMsg(TimeSpan.FromMilliseconds(500));
    }
}
