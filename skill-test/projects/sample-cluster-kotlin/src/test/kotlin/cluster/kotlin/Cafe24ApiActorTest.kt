package cluster.kotlin

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class Cafe24ApiActorTest {
    companion object {
        private lateinit var testKit: ActorTestKit
        private lateinit var dummyApi: DummyCafe24Api

        @JvmStatic
        @BeforeAll
        fun setup() {
            testKit = ActorTestKit.create(
                ConfigFactory.parseString(
                    """
                    pekko.actor.provider = "local"
                    pekko.loglevel = "INFO"
                    """.trimIndent()
                ).withFallback(ConfigFactory.load())
            )
            dummyApi = DummyCafe24Api(bucketCapacity = 10, leakRatePerSecond = 2)
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            dummyApi.close()
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun `hello should become world through safe caller`() {
        val metrics = testKit.spawn(Cafe24MetricsSingletonActor.create(), "cafe24-metrics-test")
        val manager = testKit.spawn(
            Cafe24ApiManagerActor.create(
                perMallMaxRequestsPerSecond = 2,
                dummyApi = dummyApi,
                metricsActor = metrics
            ),
            "cafe24-manager-test"
        )
        val responseProbe = testKit.createTestProbe<Cafe24ApiResponse>()

        manager.tell(Cafe24ApiRequest("mall-a", "hello", responseProbe.ref()))

        val response = responseProbe.expectMessageClass(Cafe24ApiResponse::class.java, Duration.ofSeconds(5))
        kotlin.test.assertEquals("mall-a", response.mallId)
        kotlin.test.assertEquals("world", response.result)
        kotlin.test.assertEquals(200, response.statusCode)
    }

    @Test
    fun `per mall backpressure should keep burst calls successful and isolated`() {
        val metrics = testKit.spawn(Cafe24MetricsSingletonActor.create(), "cafe24-metrics-test-2")
        val manager = testKit.spawn(
            Cafe24ApiManagerActor.create(
                perMallMaxRequestsPerSecond = 2,
                dummyApi = dummyApi,
                metricsActor = metrics
            ),
            "cafe24-manager-test-2"
        )
        val responseProbe = testKit.createTestProbe<Cafe24ApiResponse>()
        val metricsProbe = testKit.createTestProbe<Cafe24MetricsResponse>()

        repeat(6) { index ->
            manager.tell(Cafe24ApiRequest("mall-a", "a-$index", responseProbe.ref()))
            manager.tell(Cafe24ApiRequest("mall-b", "b-$index", responseProbe.ref()))
        }

        val responses = (0 until 12).map {
            responseProbe.expectMessageClass(Cafe24ApiResponse::class.java, Duration.ofSeconds(20))
        }
        kotlin.test.assertTrue(responses.all { it.statusCode == 200 })

        metricsProbe.awaitAssert(Duration.ofSeconds(5), Duration.ofMillis(200)) {
            metrics.tell(GetMallMetrics("mall-a", metricsProbe.ref()))
            val mallA = metricsProbe.expectMessageClass(Cafe24MetricsResponse::class.java, Duration.ofMillis(500))
            kotlin.test.assertEquals(6, mallA.totalCalls)
            kotlin.test.assertEquals(0, mallA.throttled429)
            "mall-a-ok"
        }

        metricsProbe.awaitAssert(Duration.ofSeconds(5), Duration.ofMillis(200)) {
            metrics.tell(GetMallMetrics("mall-b", metricsProbe.ref()))
            val mallB = metricsProbe.expectMessageClass(Cafe24MetricsResponse::class.java, Duration.ofMillis(500))
            kotlin.test.assertEquals(6, mallB.totalCalls)
            kotlin.test.assertEquals(0, mallB.throttled429)
            "mall-b-ok"
        }
    }
}
