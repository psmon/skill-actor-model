using Akka.Actor;
using Akka.Cluster.Tools.PublishSubscribe;
using Akka.Event;

namespace ClusterActors;

public class PubSubSubscriberActor : ReceiveActor
{
    private readonly ILoggingAdapter _log = Context.GetLogger();

    public PubSubSubscriberActor(string topic, IActorRef reportTo)
    {
        var mediator = DistributedPubSub.Get(Context.System).Mediator;
        mediator.Tell(new Subscribe(topic, Self));

        Receive<SubscribeAck>(_ =>
        {
            _log.Info("Subscribed to topic: {0}", topic);
            reportTo.Tell("subscribed");
        });

        Receive<string>(msg =>
        {
            _log.Info("Received from topic: {0}", msg);
            reportTo.Tell(msg);
        });
    }
}
