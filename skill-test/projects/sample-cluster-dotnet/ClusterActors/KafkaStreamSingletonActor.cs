using Akka.Actor;
using Akka.Event;
using Akka.Streams;
using Akka.Streams.Dsl;
using Akka.Streams.Kafka.Dsl;
using Akka.Streams.Kafka.Messages;
using Akka.Streams.Kafka.Settings;
using Confluent.Kafka;

namespace ClusterActors;

public interface IKafkaStreamRunner
{
    Task<string> RunOnceAsync(
        string bootstrapServers,
        string topic,
        string groupIdPrefix,
        string payload,
        TimeSpan timeout);
}

public sealed class AkkaStreamsKafkaRunner : IKafkaStreamRunner
{
    private readonly ActorSystem _system;
    private readonly IMaterializer _materializer;

    public AkkaStreamsKafkaRunner(ActorSystem system, IMaterializer materializer)
    {
        _system = system;
        _materializer = materializer;
    }

    public async Task<string> RunOnceAsync(
        string bootstrapServers,
        string topic,
        string groupIdPrefix,
        string payload,
        TimeSpan timeout)
    {
        var producerSettings = ProducerSettings<string, string>.Create(
                _system,
                Serializers.Utf8,
                Serializers.Utf8)
            .WithBootstrapServers(bootstrapServers)
            .WithProperty("enable.idempotence", "true")
            .WithProperty("acks", "all");

        await Source.Single(payload)
            .Select(value => new ProducerRecord<string, string>(topic, "cluster", value))
            .RunWith(KafkaProducer.PlainSink(producerSettings), _materializer);

        var uniqueGroup = $"{groupIdPrefix}-{Guid.NewGuid():N}";

        var consumerSettings = ConsumerSettings<string, string>.Create(
                _system,
                Deserializers.Utf8,
                Deserializers.Utf8)
            .WithBootstrapServers(bootstrapServers)
            .WithGroupId(uniqueGroup)
            .WithProperty("auto.offset.reset", "earliest")
            .WithProperty("enable.auto.commit", "true");

        var consumeTask = KafkaConsumer
            .PlainSource(consumerSettings, Subscriptions.Topics(topic))
            .Select(record => record.Message.Value)
            .Where(value => value == payload)
            .Take(1)
            .RunWith(Sink.First<string>(), _materializer);

        return await consumeTask.WaitAsync(timeout);
    }
}

public class KafkaStreamSingletonActor : ReceiveActor
{
    public sealed record Start;
    public sealed record Stop;
    public sealed record FireEvent;
    public sealed record FireEventResult(bool Success, string Produced, string? Observed, string? Error);

    private readonly ILoggingAdapter _log = Context.GetLogger();
    private readonly IKafkaStreamRunner _runner;
    private readonly string _bootstrapServers;
    private readonly string _topic;
    private readonly string _groupIdPrefix;
    private readonly TimeSpan _timeout;
    private bool _started;

    public KafkaStreamSingletonActor(
        string bootstrapServers,
        string topic,
        string groupIdPrefix,
        TimeSpan timeout,
        IKafkaStreamRunner? runner = null)
    {
        _bootstrapServers = bootstrapServers;
        _topic = topic;
        _groupIdPrefix = groupIdPrefix;
        _timeout = timeout;
        _runner = runner ?? new AkkaStreamsKafkaRunner(Context.System, Context.Materializer());

        Receive<Start>(_msg =>
        {
            if (_started)
            {
                _log.Info("Kafka stream singleton already executed. Ignoring duplicate trigger.");
                return;
            }

            _started = true;
            _ = ExecuteOnceAsync();
        });

        ReceiveAsync<FireEvent>(async _ =>
        {
            var replyTo = Sender;
            var result = await ExecuteOnceAsync();
            replyTo.Tell(result);
        });

        Receive<Stop>(_ => Context.Stop(Self));
    }

    private async Task<FireEventResult> ExecuteOnceAsync()
    {
        var payload = $"dotnet-cluster-event-{DateTimeOffset.UtcNow:O}";

        try
        {
            _log.Info(
                "Starting Kafka stream round-trip once. bootstrap={0}, topic={1}",
                _bootstrapServers,
                _topic);

            var observed = await _runner.RunOnceAsync(
                _bootstrapServers,
                _topic,
                _groupIdPrefix,
                payload,
                _timeout);

            _log.Info(
                "Kafka stream round-trip succeeded. topic={0}, produced={1}, consumed={2}",
                _topic,
                payload,
                observed);

            return new FireEventResult(true, payload, observed, null);
        }
        catch (Exception ex)
        {
            _log.Error(ex, "Kafka stream round-trip failed. topic={0}, payload={1}", _topic, payload);
            return new FireEventResult(false, payload, null, ex.Message);
        }
    }
}
