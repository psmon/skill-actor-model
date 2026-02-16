using Akka.Actor;
using Akka.Cluster;
using Akka.Cluster.Tools.PublishSubscribe;
using Akka.Configuration;
using ClusterActors;
using Xunit;

namespace ClusterActors.Tests;

/// <summary>
/// 2-Node 클러스터 공유 픽스처. seed/joining 시스템을 모든 테스트에서 재사용한다.
/// </summary>
public sealed class TwoNodeClusterFixture : IDisposable
{
    private static readonly string SeedConfig = @"
        akka {
            actor.provider = cluster
            remote.dot-netty.tcp {
                hostname = ""127.0.0.1""
                port = 25522
            }
            cluster {
                seed-nodes = [""akka.tcp://TwoNodeClusterSystem@127.0.0.1:25522""]
                min-nr-of-members = 2
            }
            loglevel = INFO
        }";

    private static readonly string JoiningConfig = @"
        akka {
            actor.provider = cluster
            remote.dot-netty.tcp {
                hostname = ""127.0.0.1""
                port = 0
            }
            cluster {
                seed-nodes = [""akka.tcp://TwoNodeClusterSystem@127.0.0.1:25522""]
                min-nr-of-members = 2
            }
            loglevel = INFO
        }";

    public ActorSystem SeedSystem { get; }
    public ActorSystem JoiningSystem { get; }

    public TwoNodeClusterFixture()
    {
        SeedSystem = ActorSystem.Create("TwoNodeClusterSystem",
            ConfigurationFactory.ParseString(SeedConfig));
        JoiningSystem = ActorSystem.Create("TwoNodeClusterSystem",
            ConfigurationFactory.ParseString(JoiningConfig));

        WaitForClusterUp(SeedSystem, 2, 15);
    }

    private static void WaitForClusterUp(ActorSystem system, int expectedMembers, int timeoutSeconds)
    {
        var cluster = Cluster.Get(system);
        var deadline = DateTime.UtcNow.AddSeconds(timeoutSeconds);
        while (DateTime.UtcNow < deadline)
        {
            var upCount = cluster.State.Members.Count(m => m.Status == MemberStatus.Up);
            if (upCount >= expectedMembers) return;
            Thread.Sleep(500);
        }
        throw new Exception($"Cluster did not form with {expectedMembers} Up members within {timeoutSeconds}s");
    }

    public void Dispose()
    {
        var joiningCluster = Cluster.Get(JoiningSystem);
        joiningCluster.Leave(joiningCluster.SelfAddress);
        JoiningSystem.Terminate().Wait(TimeSpan.FromSeconds(10));
        SeedSystem.Terminate().Wait(TimeSpan.FromSeconds(10));
    }
}

/// <summary>
/// 수신한 메시지를 순서대로 수집하는 경량 프로브 액터.
/// WaitForMessages로 특정 개수의 메시지가 도착할 때까지 대기할 수 있다.
/// </summary>
public class MessageCollectorActor : ReceiveActor
{
    private readonly List<object> _messages = new();
    private readonly List<(int expected, TaskCompletionSource<List<object>> tcs)> _waiters = new();

    public sealed record GetCollected(TaskCompletionSource<List<object>> Tcs);

    public MessageCollectorActor()
    {
        Receive<GetCollected>(msg =>
        {
            msg.Tcs.TrySetResult(new List<object>(_messages));
        });

        ReceiveAny(msg =>
        {
            _messages.Add(msg);
            foreach (var (expected, tcs) in _waiters.ToList())
            {
                if (_messages.Count >= expected)
                {
                    tcs.TrySetResult(new List<object>(_messages));
                    _waiters.Remove((expected, tcs));
                }
            }
        });
    }

    /// <summary>
    /// 외부에서 N개 메시지 도착을 대기하는 도우미.
    /// 이미 N개 이상 수집되었으면 즉시 반환한다.
    /// </summary>
    public static async Task<List<object>> WaitForMessages(
        IActorRef collector, int count, TimeSpan timeout)
    {
        var tcs = new TaskCompletionSource<List<object>>();
        collector.Tell(new GetCollected(tcs));
        var current = await tcs.Task;
        if (current.Count >= count) return current;

        // 아직 부족하면 폴링으로 대기
        var deadline = DateTime.UtcNow.Add(timeout);
        while (DateTime.UtcNow < deadline)
        {
            await Task.Delay(200);
            var tcs2 = new TaskCompletionSource<List<object>>();
            collector.Tell(new GetCollected(tcs2));
            var result = await tcs2.Task;
            if (result.Count >= count) return result;
        }
        throw new TimeoutException($"Expected {count} messages but only got {current.Count}");
    }
}

/// <summary>
/// 2-Node Cluster Test: seed 노드(고정 포트 25522)와 joining 노드(자동 포트)로
/// 실제 2노드 클러스터를 구성하여 크로스노드 통신을 검증한다.
/// 기존 1-Node 테스트(ClusterActorTests)는 그대로 유지된다.
/// </summary>
public class TwoNodeClusterTests : IClassFixture<TwoNodeClusterFixture>
{
    private readonly TwoNodeClusterFixture _f;

    public TwoNodeClusterTests(TwoNodeClusterFixture fixture)
    {
        _f = fixture;
    }

    [Fact]
    public void BothNodes_should_be_Up_in_cluster()
    {
        var seedCluster = Cluster.Get(_f.SeedSystem);
        var joiningCluster = Cluster.Get(_f.JoiningSystem);

        Assert.Equal(2, seedCluster.State.Members.Count);
        Assert.Equal(2, joiningCluster.State.Members.Count);
        Assert.All(seedCluster.State.Members, m => Assert.Equal(MemberStatus.Up, m.Status));
    }

    [Fact]
    public async Task ClusterListener_should_receive_two_MemberUp_events()
    {
        var collector = _f.SeedSystem.ActorOf(
            Props.Create<MessageCollectorActor>(), $"listener-collector-{Guid.NewGuid():N}");

        // ClusterListenerActor가 collector에 "member-up" 메시지를 전달
        _f.SeedSystem.ActorOf(
            Props.Create(() => new ClusterListenerActor(collector)),
            $"listener-2node-{Guid.NewGuid():N}");

        var messages = await MessageCollectorActor
            .WaitForMessages(collector, 2, TimeSpan.FromSeconds(5));

        Assert.Equal(2, messages.Count);
        Assert.All(messages, m => Assert.Equal("member-up", m));
    }

    [Fact]
    public async Task Counter_should_work_across_nodes()
    {
        var actorName = $"counter-2node-{Guid.NewGuid():N}";

        // Seed 노드에 카운터 생성
        var counter = _f.SeedSystem.ActorOf(
            Props.Create<CounterSingletonActor>(), actorName);

        // Seed 노드에서 2회 증가
        counter.Tell(new CounterSingletonActor.Increment());
        counter.Tell(new CounterSingletonActor.Increment());

        // Joining 노드에서 ActorSelection으로 리모트 카운터에 접근
        var seedAddress = Cluster.Get(_f.SeedSystem).SelfAddress;
        var counterPath = $"{seedAddress}/user/{actorName}";
        var remoteCounter = _f.JoiningSystem.ActorSelection(counterPath);

        // Joining 노드에서 1회 증가
        remoteCounter.Tell(new CounterSingletonActor.Increment());
        await Task.Delay(1000);

        // Joining 노드의 collector에서 카운트 조회
        var collector = _f.JoiningSystem.ActorOf(
            Props.Create<MessageCollectorActor>(), $"counter-reply-{Guid.NewGuid():N}");

        remoteCounter.Tell(new CounterSingletonActor.GetCount(collector));

        var messages = await MessageCollectorActor
            .WaitForMessages(collector, 1, TimeSpan.FromSeconds(5));

        var countValue = Assert.IsType<CounterSingletonActor.CountValue>(messages[0]);
        Assert.Equal(3, countValue.Value);
    }

    [Fact]
    public async Task PubSub_should_deliver_across_nodes()
    {
        // Seed 노드에서 DistributedPubSub mediator 사전 초기화
        var seedMediator = DistributedPubSub.Get(_f.SeedSystem).Mediator;

        // Joining 노드에서 PubSubSubscriberActor를 통해 구독 (collector에 포워딩)
        var collector = _f.JoiningSystem.ActorOf(
            Props.Create<MessageCollectorActor>(), $"pubsub-collector-{Guid.NewGuid():N}");

        _f.JoiningSystem.ActorOf(
            Props.Create(() => new PubSubSubscriberActor("test-topic", collector)),
            $"sub-2node-{Guid.NewGuid():N}");

        // "subscribed" 메시지 수신 대기
        var ackMessages = await MessageCollectorActor
            .WaitForMessages(collector, 1, TimeSpan.FromSeconds(5));
        Assert.Equal("subscribed", ackMessages[0]);

        // DistributedPubSub 크로스노드 전파 대기
        await Task.Delay(5000);

        // Seed 노드의 mediator로 직접 발행 (크로스노드)
        seedMediator.Tell(new Publish("test-topic", "cross-node-hello"));

        // Joining 노드의 collector: "subscribed" + "cross-node-hello" = 2개
        var allMessages = await MessageCollectorActor
            .WaitForMessages(collector, 2, TimeSpan.FromSeconds(10));

        Assert.Equal("cross-node-hello", allMessages[1]);
    }
}
