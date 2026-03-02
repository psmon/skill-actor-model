using Akka.Actor;

namespace ClusterActors;

public sealed class Cafe24MetricsSingletonActor : ReceiveActor
{
    public sealed record RecordCall(string MallId, int StatusCode, long QueueDelayMs);
    public sealed record GetMallMetrics(string MallId);
    public sealed record MallMetrics(string MallId, long TotalCalls, long Throttled429, double AvgQueueDelayMs);
    public sealed record Stop;

    private sealed class MetricState
    {
        public long TotalCalls { get; set; }
        public long Throttled429 { get; set; }
        public long SumQueueDelayMs { get; set; }
    }

    private readonly Dictionary<string, MetricState> _stateByMall = new();

    public Cafe24MetricsSingletonActor()
    {
        Receive<RecordCall>(msg =>
        {
            if (!_stateByMall.TryGetValue(msg.MallId, out var state))
            {
                state = new MetricState();
                _stateByMall[msg.MallId] = state;
            }

            state.TotalCalls += 1;
            if (msg.StatusCode == 429) state.Throttled429 += 1;
            state.SumQueueDelayMs += msg.QueueDelayMs;
        });

        Receive<GetMallMetrics>(msg =>
        {
            if (!_stateByMall.TryGetValue(msg.MallId, out var state))
            {
                Sender.Tell(new MallMetrics(msg.MallId, 0, 0, 0));
                return;
            }

            var avg = state.TotalCalls == 0 ? 0 : (double)state.SumQueueDelayMs / state.TotalCalls;
            Sender.Tell(new MallMetrics(msg.MallId, state.TotalCalls, state.Throttled429, avg));
        });

        Receive<Stop>(_ => Context.Stop(Self));
    }
}
