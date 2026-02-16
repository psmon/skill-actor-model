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

        WaitForClusterUpAsync(SeedSystem, 2, TimeSpan.FromSeconds(15))
            .GetAwaiter()
            .GetResult();
    }

    private static Task WaitForClusterUpAsync(ActorSystem system, int expectedMembers, TimeSpan timeout)
    {
        var cluster = Cluster.Get(system);
        var deadline = DateTime.UtcNow + timeout;
        var tcs = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);

        Timer? timer = null;
        timer = new Timer(_ =>
        {
            var upCount = cluster.State.Members.Count(m => m.Status == MemberStatus.Up);
            if (upCount >= expectedMembers)
            {
                timer?.Dispose();
                tcs.TrySetResult(true);
                return;
            }

            if (DateTime.UtcNow >= deadline)
            {
                timer?.Dispose();
                tcs.TrySetException(new TimeoutException(
                    $"Expected at least {expectedMembers} Up members but got {upCount}."));
            }
        }, null, TimeSpan.Zero, TimeSpan.FromMilliseconds(200));

        return tcs.Task;
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
/// 수신 메시지를 순서대로 모으고, 지정 개수 도달 시 즉시 완료한다.
/// </summary>
public class MessageCollectorActor : ReceiveActor
{
    private readonly List<object> _messages = new();
    private readonly List<Waiter> _waiters = new();

    public sealed record WaitForCount(int Count, TimeSpan Timeout, TaskCompletionSource<List<object>> Tcs);
    private sealed record WaitTimeout(TaskCompletionSource<List<object>> Tcs);

    private sealed class Waiter
    {
        public required int Count { get; init; }
        public required TaskCompletionSource<List<object>> Tcs { get; init; }
        public required ICancelable Timeout { get; init; }
    }

    public MessageCollectorActor()
    {
        Receive<WaitForCount>(msg =>
        {
            if (_messages.Count >= msg.Count)
            {
                msg.Tcs.TrySetResult(new List<object>(_messages));
                return;
            }

            var timeout = Context.System.Scheduler.ScheduleTellOnceCancelable(
                msg.Timeout,
                Self,
                new WaitTimeout(msg.Tcs),
                Self);

            _waiters.Add(new Waiter
            {
                Count = msg.Count,
                Tcs = msg.Tcs,
                Timeout = timeout
            });
        });

        Receive<WaitTimeout>(msg =>
        {
            var waiter = _waiters.FirstOrDefault(w => ReferenceEquals(w.Tcs, msg.Tcs));
            if (waiter is null) return;

            waiter.Timeout.Cancel();
            _waiters.Remove(waiter);
            msg.Tcs.TrySetException(new TimeoutException(
                $"Expected {waiter.Count} messages but only got {_messages.Count}."));
        });

        ReceiveAny(msg =>
        {
            _messages.Add(msg);
            CompleteSatisfiedWaiters();
        });
    }

    private void CompleteSatisfiedWaiters()
    {
        foreach (var waiter in _waiters.ToList())
        {
            if (_messages.Count < waiter.Count) continue;

            waiter.Timeout.Cancel();
            _waiters.Remove(waiter);
            waiter.Tcs.TrySetResult(new List<object>(_messages));
        }
    }

    public static async Task<List<object>> WaitForMessages(
        IActorRef collector,
        int count,
        TimeSpan timeout)
    {
        var tcs = new TaskCompletionSource<List<object>>(TaskCreationOptions.RunContinuationsAsynchronously);
        collector.Tell(new WaitForCount(count, timeout, tcs));
        return await tcs.Task.ConfigureAwait(false);
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

        var counter = _f.SeedSystem.ActorOf(
            Props.Create<CounterSingletonActor>(), actorName);

        counter.Tell(new CounterSingletonActor.Increment());
        counter.Tell(new CounterSingletonActor.Increment());

        var seedAddress = Cluster.Get(_f.SeedSystem).SelfAddress;
        var counterPath = $"{seedAddress}/user/{actorName}";
        var remoteCounter = _f.JoiningSystem.ActorSelection(counterPath);

        remoteCounter.Tell(new CounterSingletonActor.Increment());

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
        var seedMediator = DistributedPubSub.Get(_f.SeedSystem).Mediator;

        var collector = _f.JoiningSystem.ActorOf(
            Props.Create<MessageCollectorActor>(), $"pubsub-collector-{Guid.NewGuid():N}");

        _f.JoiningSystem.ActorOf(
            Props.Create(() => new PubSubSubscriberActor("test-topic", collector)),
            $"sub-2node-{Guid.NewGuid():N}");

        var ackMessages = await MessageCollectorActor
            .WaitForMessages(collector, 1, TimeSpan.FromSeconds(5));
        Assert.Equal("subscribed", ackMessages[0]);

        var repeatedPublish = _f.SeedSystem.Scheduler.ScheduleTellRepeatedlyCancelable(
            TimeSpan.Zero,
            TimeSpan.FromMilliseconds(300),
            seedMediator,
            new Publish("test-topic", "cross-node-hello"),
            ActorRefs.NoSender);

        try
        {
            var allMessages = await MessageCollectorActor
                .WaitForMessages(collector, 2, TimeSpan.FromSeconds(10));

            Assert.Equal("cross-node-hello", allMessages[1]);
        }
        finally
        {
            repeatedPublish.Cancel();
        }
    }
}
