using Akka.Actor;
using Akka.Cluster;
using Akka.Event;

namespace ClusterActors;

public class ClusterListenerActor : ReceiveActor
{
    private readonly ILoggingAdapter _log = Context.GetLogger();
    private readonly Cluster _cluster = Cluster.Get(Context.System);
    private readonly IActorRef _reportTo;

    public ClusterListenerActor(IActorRef reportTo)
    {
        _reportTo = reportTo;

        Receive<ClusterEvent.MemberUp>(msg =>
        {
            _log.Info("Member is Up: {0}", msg.Member);
            _reportTo.Tell("member-up");
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
