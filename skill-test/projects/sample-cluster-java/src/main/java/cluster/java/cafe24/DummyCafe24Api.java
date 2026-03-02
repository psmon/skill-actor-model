package cluster.java.cafe24;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DummyCafe24Api implements AutoCloseable {

    public record ApiCallResult(
        int statusCode,
        String body,
        int bucketUsed,
        int bucketMax,
        int callRemainSeconds
    ) {
    }

    private final int bucketCapacity;
    private final int leakRatePerSecond;
    private final Map<String, AtomicInteger> bucketByMall = new ConcurrentHashMap<>();
    private final ScheduledExecutorService leakScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "java-cafe24-leak");
            t.setDaemon(true);
            return t;
        });

    public DummyCafe24Api(int bucketCapacity, int leakRatePerSecond) {
        this.bucketCapacity = bucketCapacity;
        this.leakRatePerSecond = leakRatePerSecond;

        leakScheduler.scheduleAtFixedRate(() -> bucketByMall.values().forEach(level ->
                level.updateAndGet(current -> Math.max(0, current - leakRatePerSecond))),
            1, 1, TimeUnit.SECONDS);
    }

    public CompletionStage<ApiCallResult> call(String mallId, String word) {
        int level = bucketByMall.computeIfAbsent(mallId, k -> new AtomicInteger()).incrementAndGet();
        if (level > bucketCapacity) {
            bucketByMall.get(mallId).decrementAndGet();
            int over = level - bucketCapacity;
            int remain = (int) Math.ceil((double) over / leakRatePerSecond) + 1;
            return CompletableFuture.completedFuture(
                new ApiCallResult(429, "Too Many Requests", level, bucketCapacity, remain));
        }

        String body = "hello".equals(word) ? "world" : word;
        return CompletableFuture.completedFuture(
            new ApiCallResult(200, body, level, bucketCapacity, 0));
    }

    @Override
    public void close() {
        leakScheduler.shutdownNow();
    }
}
