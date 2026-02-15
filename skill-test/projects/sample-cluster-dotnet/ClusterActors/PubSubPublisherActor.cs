using Akka.Actor;
using Akka.Cluster.Tools.PublishSubscribe;

namespace ClusterActors;

public class PubSubPublisherActor : ReceiveActor
{
    private readonly IActorRef _mediator;

    public PubSubPublisherActor()
    {
        _mediator = DistributedPubSub.Get(Context.System).Mediator;

        Receive<string>(msg =>
        {
            _mediator.Tell(new Publish("test-topic", msg));
        });
    }
}
