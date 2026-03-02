using System.Collections.Concurrent;

namespace ClusterActors;

public sealed class DummyCafe24Api : IDisposable
{
    public sealed record ApiCallResult(
        int StatusCode,
        string Body,
        int BucketUsed,
        int BucketMax,
        int CallRemainSeconds);

    private readonly int _bucketCapacity;
    private readonly int _leakRatePerSecond;
    private readonly ConcurrentDictionary<string, int> _bucketByMall = new();
    private readonly Timer _leakTimer;

    public DummyCafe24Api(int bucketCapacity, int leakRatePerSecond)
    {
        _bucketCapacity = bucketCapacity;
        _leakRatePerSecond = leakRatePerSecond;
        _leakTimer = new Timer(_ =>
        {
            foreach (var entry in _bucketByMall.ToArray())
            {
                var next = Math.Max(0, entry.Value - _leakRatePerSecond);
                _bucketByMall[entry.Key] = next;
            }
        }, null, TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(1));
    }

    public Task<ApiCallResult> CallAsync(string mallId, string word)
    {
        var level = _bucketByMall.AddOrUpdate(mallId, 1, (_, current) => current + 1);
        if (level > _bucketCapacity)
        {
            _bucketByMall.AddOrUpdate(mallId, 0, (_, current) => Math.Max(0, current - 1));
            var over = level - _bucketCapacity;
            var remain = (int)Math.Ceiling((double)over / _leakRatePerSecond) + 1;
            return Task.FromResult(new ApiCallResult(429, "Too Many Requests", level, _bucketCapacity, remain));
        }

        var body = word == "hello" ? "world" : word;
        return Task.FromResult(new ApiCallResult(200, body, level, _bucketCapacity, 0));
    }

    public void Dispose()
    {
        _leakTimer.Dispose();
    }
}
