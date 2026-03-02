using Akka;
using Akka.Actor;
using Akka.Event;
using Akka.Streams;
using Akka.Streams.Dsl;

namespace ClusterActors;

public sealed class Cafe24ApiManagerActor : ReceiveActor
{
    public sealed record ApiRequest(string MallId, string Word);
    public sealed record ApiResponse(
        string MallId,
        string Word,
        string Result,
        int StatusCode,
        int BucketUsed,
        int BucketMax);

    private readonly int _perMallMaxRequestsPerSecond;
    private readonly DummyCafe24Api _dummyCafe24Api;
    private readonly IActorRef _metricsActor;

    public Cafe24ApiManagerActor(
        int perMallMaxRequestsPerSecond,
        DummyCafe24Api dummyCafe24Api,
        IActorRef metricsActor)
    {
        _perMallMaxRequestsPerSecond = perMallMaxRequestsPerSecond;
        _dummyCafe24Api = dummyCafe24Api;
        _metricsActor = metricsActor;

        Receive<ApiRequest>(msg =>
        {
            var childName = "mall-" + Sanitize(msg.MallId);
            var child = Context.Child(childName);
            if (Equals(child, ActorRefs.Nobody))
            {
                child = Context.ActorOf(
                    Props.Create(() => new MallApiCallerActor(
                        msg.MallId,
                        _perMallMaxRequestsPerSecond,
                        _dummyCafe24Api,
                        _metricsActor)),
                    childName);
            }

            child.Tell(new MallApiCallerActor.CallMallApi(msg.Word, Sender), Self);
        });
    }

    private static string Sanitize(string mallId)
    {
        var chars = mallId.Select(ch => char.IsLetterOrDigit(ch) || ch is '_' or '-' ? ch : '_').ToArray();
        return new string(chars);
    }

    private sealed class MallApiCallerActor : ReceiveActor
    {
        internal sealed record CallMallApi(string Word, IActorRef ReplyTo);
        private sealed record StreamEnvelope(CallMallApi Request, long EnqueuedAtNanos);
        private sealed record StreamResult(CallMallApi Request, ApiResponse Response, long QueueDelayMs);

        private readonly ILoggingAdapter _log = Context.GetLogger();
        private readonly string _mallId;
        private readonly int _maxRequestsPerSecond;
        private readonly DummyCafe24Api _dummyCafe24Api;
        private readonly IActorRef _metricsActor;
        private readonly IMaterializer _materializer;
        private IActorRef _streamEntry = ActorRefs.Nobody;

        public MallApiCallerActor(
            string mallId,
            int maxRequestsPerSecond,
            DummyCafe24Api dummyCafe24Api,
            IActorRef metricsActor)
        {
            _mallId = mallId;
            _maxRequestsPerSecond = maxRequestsPerSecond;
            _dummyCafe24Api = dummyCafe24Api;
            _metricsActor = metricsActor;
            _materializer = Context.Materializer();

            Receive<CallMallApi>(msg =>
            {
                _streamEntry.Tell(new StreamEnvelope(msg, DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()));
            });

            Receive<StreamResult>(msg =>
            {
                msg.Request.ReplyTo.Tell(msg.Response);
                _log.Info("Cafe24 safe call mall={0} word={1} status={2} bucket={3}/{4}", _mallId, msg.Response.Word, msg.Response.StatusCode, msg.Response.BucketUsed, msg.Response.BucketMax);
                _metricsActor.Tell(new Cafe24MetricsSingletonActor.RecordCall(
                    _mallId, msg.Response.StatusCode, msg.QueueDelayMs));
            });
        }

        protected override void PreStart()
        {
            _streamEntry = Source.ActorRef<StreamEnvelope>(256, OverflowStrategy.DropNew)
                .Throttle(_maxRequestsPerSecond, TimeSpan.FromSeconds(1), _maxRequestsPerSecond, ThrottleMode.Shaping)
                .SelectAsync(1, CallApiWithAdaptiveBackpressureAsync)
                .To(Sink.ActorRef<StreamResult>(Self, NotUsed.Instance, ex => new Status.Failure(ex)))
                .Run(_materializer);

            base.PreStart();
        }

        private async Task<StreamResult> CallApiWithAdaptiveBackpressureAsync(StreamEnvelope envelope)
        {
            var response = await ExecuteWithRetryAsync(envelope.Request.Word, 0);
            var usageRatio = response.BucketMax == 0 ? 0.0 : (double)response.BucketUsed / response.BucketMax;
            var adaptiveDelayMs = usageRatio > 0.8 ? 500 : usageRatio > 0.5 ? 200 : 0;
            if (adaptiveDelayMs > 0)
            {
                await Task.Delay(adaptiveDelayMs);
            }

            var queueDelayMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - envelope.EnqueuedAtNanos;
            return new StreamResult(envelope.Request, response, queueDelayMs);
        }

        private async Task<ApiResponse> ExecuteWithRetryAsync(string word, int retryCount)
        {
            var result = await _dummyCafe24Api.CallAsync(_mallId, word);
            if (result.StatusCode == 429 && retryCount < 3)
            {
                await Task.Delay(TimeSpan.FromSeconds(result.CallRemainSeconds));
                return await ExecuteWithRetryAsync(word, retryCount + 1);
            }

            return new ApiResponse(
                _mallId,
                word,
                result.Body,
                result.StatusCode,
                result.BucketUsed,
                result.BucketMax);
        }
    }
}
