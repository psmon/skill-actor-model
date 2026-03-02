using Akka.Actor;
using Akka.Configuration;
using Akka.TestKit.Xunit2;
using ClusterActors;
using Xunit;

namespace ClusterActors.Tests;

public class Cafe24ApiActorTests : TestKit
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

    private readonly DummyCafe24Api _dummyCafe24Api = new(10, 2);

    public Cafe24ApiActorTests() : base(ConfigurationFactory.ParseString(HoconConfig))
    {
    }

    protected override void AfterAll()
    {
        _dummyCafe24Api.Dispose();
        base.AfterAll();
    }

    [Fact]
    public void Hello_should_become_world_through_safe_caller()
    {
        var metrics = Sys.ActorOf(Props.Create(() => new Cafe24MetricsSingletonActor()), "cafe24-metrics-test");
        var manager = Sys.ActorOf(Props.Create(() => new Cafe24ApiManagerActor(2, _dummyCafe24Api, metrics)), "cafe24-manager-test");

        manager.Tell(new Cafe24ApiManagerActor.ApiRequest("mall-a", "hello"), TestActor);

        var response = ExpectMsg<Cafe24ApiManagerActor.ApiResponse>(TimeSpan.FromSeconds(5));
        Assert.Equal("mall-a", response.MallId);
        Assert.Equal("world", response.Result);
        Assert.Equal(200, response.StatusCode);
        ExpectNoMsg(TimeSpan.FromMilliseconds(200));
    }

    [Fact]
    public void Per_mall_backpressure_should_keep_burst_calls_successful_and_isolated()
    {
        var metrics = Sys.ActorOf(Props.Create(() => new Cafe24MetricsSingletonActor()), "cafe24-metrics-test-2");
        var manager = Sys.ActorOf(Props.Create(() => new Cafe24ApiManagerActor(2, _dummyCafe24Api, metrics)), "cafe24-manager-test-2");

        for (var i = 0; i < 6; i++)
        {
            manager.Tell(new Cafe24ApiManagerActor.ApiRequest("mall-a", $"a-{i}"), TestActor);
            manager.Tell(new Cafe24ApiManagerActor.ApiRequest("mall-b", $"b-{i}"), TestActor);
        }

        var responses = new List<Cafe24ApiManagerActor.ApiResponse>();
        for (var i = 0; i < 12; i++)
        {
            responses.Add(ExpectMsg<Cafe24ApiManagerActor.ApiResponse>(TimeSpan.FromSeconds(20)));
        }

        Assert.All(responses, response => Assert.Equal(200, response.StatusCode));

        AwaitAssert(() =>
        {
            metrics.Tell(new Cafe24MetricsSingletonActor.GetMallMetrics("mall-a"), TestActor);
            var mallA = ExpectMsg<Cafe24MetricsSingletonActor.MallMetrics>(TimeSpan.FromMilliseconds(500));
            Assert.Equal(6, mallA.TotalCalls);
            Assert.Equal(0, mallA.Throttled429);
        }, TimeSpan.FromSeconds(5), TimeSpan.FromMilliseconds(200));

        AwaitAssert(() =>
        {
            metrics.Tell(new Cafe24MetricsSingletonActor.GetMallMetrics("mall-b"), TestActor);
            var mallB = ExpectMsg<Cafe24MetricsSingletonActor.MallMetrics>(TimeSpan.FromMilliseconds(500));
            Assert.Equal(6, mallB.TotalCalls);
            Assert.Equal(0, mallB.Throttled429);
        }, TimeSpan.FromSeconds(5), TimeSpan.FromMilliseconds(200));
    }
}
