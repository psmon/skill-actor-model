using Akka.Actor;
using Akka.Event;

namespace ClusterActors;

public class CounterSingletonActor : ReceiveActor
{
    private readonly ILoggingAdapter _log = Context.GetLogger();
    private int _count = 0;

    public sealed record Increment;
    public sealed record GetCount(IActorRef ReplyTo);
    public sealed record CountValue(int Value);

    public CounterSingletonActor()
    {
        Receive<Increment>(_ =>
        {
            _count++;
            _log.Info("Counter incremented to {0}", _count);
        });

        Receive<GetCount>(msg =>
        {
            msg.ReplyTo.Tell(new CountValue(_count));
        });
    }
}
