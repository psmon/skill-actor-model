using Akka.Actor;
using Akka.Cluster;
using Akka.Event;

namespace ClusterActors;

public class ClusterListenerActor : ReceiveActor
{
    private readonly ILoggingAdapter _log = Context.GetLogger();
    private readonly Cluster _cluster = Cluster.Get(Context.System);
    private readonly IActorRef _reportTo;
    private readonly IActorRef? _kafkaSingletonProxy;
    private readonly int _requiredMembersForKafkaStart;
    private readonly TimeSpan _kafkaStartDelay;
    private bool _kafkaStartScheduled;

    public ClusterListenerActor(IActorRef reportTo)
        : this(reportTo, null, 1, TimeSpan.FromSeconds(15))
    {
    }

    public ClusterListenerActor(
        IActorRef reportTo,
        IActorRef? kafkaSingletonProxy,
        int requiredMembersForKafkaStart,
        TimeSpan kafkaStartDelay)
    {
        _reportTo = reportTo;
        _kafkaSingletonProxy = kafkaSingletonProxy;
        _requiredMembersForKafkaStart = requiredMembersForKafkaStart;
        _kafkaStartDelay = kafkaStartDelay;

        Receive<ClusterEvent.MemberUp>(msg =>
        {
            _log.Info("Member is Up: {0}", msg.Member);
            _reportTo.Tell("member-up");
            TryScheduleKafkaRun();
        });

        Receive<ClusterEvent.UnreachableMember>(msg =>
        {
            _log.Warning("Member unreachable: {0}", msg.Member);
        });

        Receive<ClusterEvent.MemberRemoved>(msg =>
        {
            _log.Info("Member removed: {0}", msg.Member);
        });
    }

    private void TryScheduleKafkaRun()
    {
        if (_kafkaSingletonProxy is null || _kafkaStartScheduled)
            return;

        var upCount = _cluster.State.Members.Count(m => m.Status == MemberStatus.Up);
        if (upCount < _requiredMembersForKafkaStart)
            return;

        _kafkaStartScheduled = true;
        _log.Info(
            "Cluster is ready ({0}/{1} members Up). Scheduling Kafka singleton trigger in {2}.",
            upCount,
            _requiredMembersForKafkaStart,
            _kafkaStartDelay);

        Context.System.Scheduler.ScheduleTellOnce(
            _kafkaStartDelay,
            _kafkaSingletonProxy,
            new KafkaStreamSingletonActor.Start(),
            Self);
    }

    protected override void PreStart()
    {
        _cluster.Subscribe(Self,
            ClusterEvent.SubscriptionInitialStateMode.InitialStateAsEvents,
            typeof(ClusterEvent.MemberUp),
            typeof(ClusterEvent.UnreachableMember),
            typeof(ClusterEvent.MemberRemoved));
    }

    protected override void PostStop()
    {
        _cluster.Unsubscribe(Self);
    }
}
