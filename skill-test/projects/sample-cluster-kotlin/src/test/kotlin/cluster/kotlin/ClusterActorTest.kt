package cluster.kotlin

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Join
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

class ClusterActorTest {
    companion object {
        private lateinit var testKit: ActorTestKit

        @JvmStatic
        @BeforeAll
        fun setup() {
            val config = ConfigFactory.parseString("""
                pekko {
                  actor.provider = "cluster"
                  remote.artery {
                    canonical.hostname = "127.0.0.1"
                    canonical.port = 0
                  }
                  cluster {
                    seed-nodes = []
                    min-nr-of-members = 1
                    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
                  }
                  loglevel = "INFO"
                }
            """.trimIndent()).withFallback(ConfigFactory.load())

            testKit = ActorTestKit.create(config)
            // Single-node cluster: join self
            val cluster = Cluster.get(testKit.system())
            cluster.manager().tell(Join.create(cluster.selfMember().address()))
            // Wait for cluster to form
            Thread.sleep(3000)
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun `counter singleton actor counts correctly`() {
        val counter = testKit.spawn(CounterSingletonActor.create(), "counter-test")
        val probe = testKit.createTestProbe<CounterCommand>()

        counter.tell(Increment)
        counter.tell(Increment)
        counter.tell(Increment)
        counter.tell(GetCount(probe.ref()))

        val result = probe.expectMessageClass(CountValue::class.java)
        assert(result.value == 3) { "Expected 3, got ${result.value}" }
    }

    @Test
    fun `pubsub delivers message to subscriber`() {
        val pubSubManager = testKit.spawn(PubSubManagerActor.create(), "pubsub-test")
        val subscriberProbe = testKit.createTestProbe<String>()

        // Subscribe
        pubSubManager.tell(SubscribeToTopic("test-topic", subscriberProbe.ref()))

        // Wait for subscription to propagate
        Thread.sleep(1000)

        // Publish
        pubSubManager.tell(PublishMessage("test-topic", "hello-cluster"))

        // Verify subscriber received the message
        subscriberProbe.expectMessage(Duration.ofSeconds(5), "hello-cluster")
    }
}
