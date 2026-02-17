---
name: kotlin-pekko-typed-cluster
description: Kotlin + Pekko Typed 클러스터 코드를 생성합니다. Kotlin으로 Pekko Typed 기반 클러스터를 구현하거나, 클러스터 멤버십, Singleton, Sharding, Distributed PubSub, ServiceKey + Receptionist, Split Brain Resolver 등 Pekko 클러스터 패턴을 작성할 때 사용합니다.
argument-hint: "[클러스터패턴] [요구사항]"
---

# Kotlin + Pekko Typed 클러스터 스킬

Kotlin + Apache Pekko Typed(1.4.x) 기반의 타입 안전한 클러스터 코드를 생성하는 스킬입니다.

## 참고 문서

- 기본 액터 스킬: [plugins/skill-actor-model/skills/kotlin-pekko-typed/SKILL.md](../kotlin-pekko-typed/SKILL.md)
- 클러스터 문서: [skill-maker/docs/actor/cluster/cluster.md](../../../../skill-maker/docs/actor/cluster/cluster.md)
- 크로스 플랫폼 비교: [skill-maker/docs/actor/05-cross-platform-comparison.md](../../../../skill-maker/docs/actor/05-cross-platform-comparison.md)

## 호환 버전

- **프레임워크**: Apache Pekko 1.4.x (기본 스킬과 동일)
- **언어**: Kotlin 1.9.x
- **빌드**: Gradle (Kotlin DSL)
- **라이선스**: Apache License 2.0
- **클러스터 패키지**:
  - `pekko-cluster-typed` — 클러스터 멤버십, Singleton, PubSub
  - `pekko-cluster-sharding-typed` — Cluster Sharding
- **HOCON 네임스페이스**: `pekko { }` (NOT `akka { }`)
- **프로토콜**: `pekko://`

## Kubernetes 운영 권장

- Kubernetes 환경에서 1.1.x -> 1.4.x 마이그레이션 시에는 **고정 `seed-nodes`보다 `kubernetes-api` discovery + cluster bootstrap 조합을 우선**합니다.
- `MemberUp`/`ClusterListener` 로직은 그대로 유지하면서, 조인 방식만 bootstrap으로 전환하면 코드 영향은 작고 운영 안정성은 크게 향상됩니다.
- 인프라 설정 상세는 `kotlin-pekko-typed-infra` 스킬의 마이그레이션 체크리스트를 따릅니다.

### Gradle 의존성 (Kotlin DSL)

```kotlin
val pekkoVersion = "1.4.0"
val scalaBinaryVersion = "2.13"

dependencies {
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-typed_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_$scalaBinaryVersion:$pekkoVersion")

    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion:$pekkoVersion")
}
```

## 핵심 특성

- **타입 안전 클러스터**: `ActorRef<T>` 기반 타입 체크가 클러스터에서도 유지
- **sealed class 메시지**: 클러스터 메시지도 sealed class 계층으로 정의
- **명시적 replyTo**: `replyTo: ActorRef<T>`로 응답 타입 컴파일 타임 검증
- **Behavior 기반 Singleton/Sharding**: `AbstractBehavior<T>` 확장

## 지원 패턴

### 1. 클러스터 설정 & 멤버십 (Cluster Membership)
- `Cluster.get(system)` 클러스터 확장
- `cluster.subscriptions().tell(Subscribe.create(...))` 이벤트 구독
- `ClusterEvent.MemberEvent` 계층: `MemberUp`, `MemberRemoved`, `MemberExited`
- `cluster.selfMember().hasRole("role")` 역할 체크
- `cluster.manager().tell(Leave.create(address))` graceful leave

```kotlin
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.cluster.ClusterEvent
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.cluster.typed.Subscribe

sealed class ClusterListenerCommand
data class MemberChange(val event: ClusterEvent.MemberEvent) : ClusterListenerCommand()

class ClusterListenerActor private constructor(
    context: ActorContext<ClusterListenerCommand>
) : AbstractBehavior<ClusterListenerCommand>(context) {

    companion object {
        fun create(): Behavior<ClusterListenerCommand> {
            return Behaviors.setup { context ->
                val cluster = Cluster.get(context.system)

                // MemberEvent 구독을 위한 메시지 어댑터
                val memberEventAdapter: ActorRef<ClusterEvent.MemberEvent> =
                    context.messageAdapter(ClusterEvent.MemberEvent::class.java) { event ->
                        MemberChange(event)
                    }
                cluster.subscriptions().tell(Subscribe.create(memberEventAdapter, ClusterEvent.MemberEvent::class.java))

                context.log.info("Cluster listener started. Self: {}", cluster.selfMember())
                ClusterListenerActor(context)
            }
        }
    }

    override fun createReceive(): Receive<ClusterListenerCommand> {
        return newReceiveBuilder()
            .onMessage(MemberChange::class.java) { msg ->
                when (val event = msg.event) {
                    is ClusterEvent.MemberUp ->
                        context.log.info("Member Up: {}", event.member())
                    is ClusterEvent.MemberRemoved ->
                        context.log.info("Member Removed: {}", event.member())
                    else ->
                        context.log.info("Cluster event: {}", event)
                }
                this
            }
            .build()
    }
}
```

#### 역할 기반 액터 배포

```kotlin
val selfMember = Cluster.get(context.system).selfMember()
if (selfMember.hasRole("backend")) {
    context.spawn(WorkerActor.create(), "worker")
}
if (selfMember.hasRole("frontend")) {
    context.spawn(FrontendActor.create(), "frontend")
}
```

### 2. 클러스터 Singleton (Cluster Singleton)
- `ClusterSingleton.get(system).init()` 싱글턴 초기화
- `SingletonActor.of(behavior, name)` 싱글턴 정의
- `.withStopMessage(msg)` graceful 종료 메시지
- `Behaviors.supervise()` 래핑으로 장애 복구 전략

```kotlin
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.cluster.typed.ClusterSingleton
import org.apache.pekko.cluster.typed.SingletonActor
import java.time.Duration

sealed class CounterCommand
object Increment : CounterCommand()
data class GetCount(val replyTo: ActorRef<CountValue>) : CounterCommand()
data class CountValue(val value: Int) : CounterCommand()
object StopCounter : CounterCommand()

class CounterSingletonActor private constructor(
    context: ActorContext<CounterCommand>
) : AbstractBehavior<CounterCommand>(context) {

    companion object {
        fun create(): Behavior<CounterCommand> {
            return Behaviors.setup { context -> CounterSingletonActor(context) }
        }
    }

    private var count = 0

    override fun createReceive(): Receive<CounterCommand> {
        return newReceiveBuilder()
            .onMessage(Increment::class.java) { _ ->
                count++
                context.log.info("Counter incremented to {}", count)
                this
            }
            .onMessage(GetCount::class.java) { msg ->
                msg.replyTo.tell(CountValue(count))
                this
            }
            .onMessageEquals(StopCounter) {
                Behaviors.stopped()
            }
            .build()
    }
}
```

#### Singleton 초기화 & 사용

```kotlin
// Singleton 초기화 (supervision 포함)
val singletonManager = ClusterSingleton.get(system)
val proxy: ActorRef<CounterCommand> = singletonManager.init(
    SingletonActor.of(
        Behaviors.supervise(CounterSingletonActor.create())
            .onFailure(
                SupervisorStrategy.restartWithBackoff(
                    Duration.ofSeconds(1), Duration.ofSeconds(10), 0.2
                )
            ),
        "GlobalCounter"
    ).withStopMessage(StopCounter)
)

// Proxy를 통해 메시지 전송
proxy.tell(Increment)
proxy.tell(GetCount(replyTo))
```

| 항목 | 설명 |
|------|------|
| `SingletonActor.of(behavior, name)` | 싱글턴 Behavior + 이름 등록 |
| `.withStopMessage(msg)` | 핸드오버 시 graceful 종료 메시지 |
| `Behaviors.supervise().onFailure()` | 장애 시 자동 재시작 전략 |
| `restartWithBackoff` | 지수 백오프 재시작 (DDoS 방지) |

### 3. 클러스터 Sharding (Cluster Sharding)
- `EntityTypeKey.create(Class, name)` 엔티티 타입 키
- `ClusterSharding.get(system).init(Entity.of(typeKey) { ... })` 샤딩 초기화
- `sharding.entityRefFor(typeKey, entityId)` 엔티티 참조
- `ShardingEnvelope` 또는 `EntityRef` 직접 사용
- 패시베이션: 유휴 엔티티 자동 종료
- `.withRole("role")` 역할 기반 샤딩

```kotlin
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey

sealed class DeviceCommand
data class RecordTemperature(val value: Double, val replyTo: ActorRef<DeviceResponse>) : DeviceCommand()
data class GetTemperature(val replyTo: ActorRef<DeviceResponse>) : DeviceCommand()

sealed class DeviceResponse
data class TemperatureRecorded(val deviceId: String, val value: Double) : DeviceResponse()
data class CurrentTemperature(val deviceId: String, val value: Double) : DeviceResponse()

class DeviceEntity private constructor(
    context: ActorContext<DeviceCommand>,
    private val entityId: String
) : AbstractBehavior<DeviceCommand>(context) {

    companion object {
        val TYPE_KEY: EntityTypeKey<DeviceCommand> =
            EntityTypeKey.create(DeviceCommand::class.java, "Device")

        fun create(entityId: String): Behavior<DeviceCommand> {
            return Behaviors.setup { context -> DeviceEntity(context, entityId) }
        }
    }

    private var temperature: Double = 0.0

    override fun createReceive(): Receive<DeviceCommand> {
        return newReceiveBuilder()
            .onMessage(RecordTemperature::class.java) { msg ->
                temperature = msg.value
                context.log.info("Device {} temperature: {}", entityId, temperature)
                msg.replyTo.tell(TemperatureRecorded(entityId, temperature))
                this
            }
            .onMessage(GetTemperature::class.java) { msg ->
                msg.replyTo.tell(CurrentTemperature(entityId, temperature))
                this
            }
            .build()
    }
}
```

#### Sharding 초기화 & 사용

```kotlin
// Sharding 초기화
val sharding = ClusterSharding.get(system)
sharding.init(
    Entity.of(DeviceEntity.TYPE_KEY) { entityContext ->
        DeviceEntity.create(entityContext.entityId)
    }
)

// EntityRef를 통한 메시지 전송 (타입 안전)
val deviceRef = sharding.entityRefFor(DeviceEntity.TYPE_KEY, "sensor-1")
deviceRef.tell(RecordTemperature(23.5, replyTo))
deviceRef.tell(GetTemperature(replyTo))
```

#### 패시베이션 설정

```hocon
pekko.cluster.sharding {
  number-of-shards = 100

  passivation {
    # 기본: 유휴 타임아웃
    default-idle-strategy.idle-entity.timeout = 2 minutes

    # 고급: 활성 엔티티 수 제한 + LRU
    # strategy = default-strategy
    # default-strategy {
    #   active-entity-limit = 100000
    #   idle-entity.timeout = 10.minutes
    # }
  }
}
```

> **numberOfShards**: 계획된 최대 클러스터 노드 수의 10배를 권장합니다.

### 4. Distributed PubSub (분산 발행-구독)
- `Topic.create(Class, topicName)` 토픽 액터 생성
- `Topic.subscribe(actorRef)` 구독
- `Topic.publish(message)` 발행
- 클러스터 전체에 메시지 전파
- At-most-once 전달 보장

```kotlin
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.pubsub.Topic

sealed class PubSubManagerCommand
data class PublishMessage(val topicName: String, val message: String) : PubSubManagerCommand()
data class SubscribeToTopic(val topicName: String, val subscriber: ActorRef<String>) : PubSubManagerCommand()
data class UnsubscribeFromTopic(val topicName: String, val subscriber: ActorRef<String>) : PubSubManagerCommand()

class PubSubManagerActor private constructor(
    context: ActorContext<PubSubManagerCommand>
) : AbstractBehavior<PubSubManagerCommand>(context) {

    companion object {
        fun create(): Behavior<PubSubManagerCommand> {
            return Behaviors.setup { context -> PubSubManagerActor(context) }
        }
    }

    private val topics = mutableMapOf<String, ActorRef<Topic.Command<String>>>()

    override fun createReceive(): Receive<PubSubManagerCommand> {
        return newReceiveBuilder()
            .onMessage(PublishMessage::class.java) { msg ->
                val topic = getOrCreateTopic(msg.topicName)
                topic.tell(Topic.publish(msg.message))
                this
            }
            .onMessage(SubscribeToTopic::class.java) { msg ->
                val topic = getOrCreateTopic(msg.topicName)
                topic.tell(Topic.subscribe(msg.subscriber))
                this
            }
            .onMessage(UnsubscribeFromTopic::class.java) { msg ->
                val topic = getOrCreateTopic(msg.topicName)
                topic.tell(Topic.unsubscribe(msg.subscriber))
                this
            }
            .build()
    }

    private fun getOrCreateTopic(topicName: String): ActorRef<Topic.Command<String>> {
        return topics.getOrPut(topicName) {
            context.spawn(Topic.create(String::class.java, topicName), "topic-$topicName")
        }
    }
}
```

| API | 설명 |
|-----|------|
| `Topic.create(Class, name)` | 토픽 액터 생성 |
| `Topic.subscribe(actorRef)` | 구독자 등록 |
| `Topic.unsubscribe(actorRef)` | 구독 해제 |
| `Topic.publish(message)` | 모든 구독자에게 메시지 발행 |

### 5. ServiceKey + Receptionist (서비스 발견)
- `ServiceKey.create(Class, name)` 서비스 키 정의
- `context.system.receptionist().tell(Receptionist.register(...))` 서비스 등록
- `Routers.group(serviceKey)` 그룹 라우터 (클러스터 전체)
- 새 노드/액터 자동 발견 (Receptionist이 자동 전파)

```kotlin
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.receptionist.Receptionist
import org.apache.pekko.actor.typed.receptionist.ServiceKey
import org.apache.pekko.actor.typed.javadsl.Routers

sealed class WorkerCommand
data class ProcessTask(val taskId: String, val replyTo: ActorRef<TaskResult>) : WorkerCommand()
data class TaskResult(val taskId: String, val result: String)

class WorkerActor private constructor(
    context: ActorContext<WorkerCommand>
) : AbstractBehavior<WorkerCommand>(context) {

    companion object {
        // 서비스 키 정의 (클러스터 전체에서 고유)
        val SERVICE_KEY: ServiceKey<WorkerCommand> =
            ServiceKey.create(WorkerCommand::class.java, "worker-service")

        fun create(): Behavior<WorkerCommand> {
            return Behaviors.setup { context ->
                // Receptionist에 자신을 등록 (클러스터 전체 전파)
                context.system.receptionist().tell(
                    Receptionist.register(SERVICE_KEY, context.self)
                )
                WorkerActor(context)
            }
        }
    }

    override fun createReceive(): Receive<WorkerCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessTask::class.java) { msg ->
                val result = "Processed ${msg.taskId} on ${context.self.path()}"
                msg.replyTo.tell(TaskResult(msg.taskId, result))
                this
            }
            .build()
    }
}
```

#### Group Router를 통한 서비스 호출

```kotlin
// Group Router 생성 (ServiceKey 기반, 클러스터 전체 워커 자동 발견)
val groupRouter = Routers.group(WorkerActor.SERVICE_KEY)
    .withRoundRobinRouting()
val router: ActorRef<WorkerCommand> = context.spawn(groupRouter, "worker-group-router")

// 라우터를 통해 메시지 전송 (자동 로드밸런싱)
router.tell(ProcessTask("task-1", replyTo))
```

> **주의 (Kotlin 타입 추론)**: `Routers.group(serviceKey)` 반환 타입을 명시하지 않아도 되지만, `.withRoundRobinRouting()` 체인 후 `context.spawn()` 시 명시적 타입이 필요할 수 있습니다.

### 6. Split Brain Resolver (SBR)
- `SplitBrainResolverProvider` 네트워크 파티션 대응
- 전략: keep-majority, keep-oldest, keep-referee, down-all, lease-majority

```hocon
pekko.cluster {
  downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"

  split-brain-resolver {
    # 전략: keep-majority | keep-oldest | keep-referee | down-all | lease-majority
    active-strategy = keep-majority

    # 안정 상태 대기 시간
    stable-after = 20s

    keep-majority {
      role = ""
    }
  }
}
```

| 전략 | 설명 | 사용 시나리오 |
|------|------|-------------|
| `keep-majority` | 과반수 쪽 유지 | 일반적인 프로덕션 |
| `keep-oldest` | 최고참 노드 쪽 유지 | Singleton 보호 |
| `down-all` | 전체 다운 | 안전 최우선 |
| `lease-majority` | 분산 잠금 기반 | 외부 리스 서비스 사용 시 |

### 7. HOCON 클러스터 설정 템플릿

```hocon
pekko {
  actor {
    provider = "cluster"

    serialization-bindings {
      "com.example.CborSerializable" = jackson-json
    }
  }

  remote.artery {
    canonical {
      hostname = "127.0.0.1"
      port = 7354
    }
  }

  cluster {
    seed-nodes = [
      "pekko://ClusterSystem@127.0.0.1:7354",
      "pekko://ClusterSystem@127.0.0.1:7355"
    ]

    roles = ["backend"]

    min-nr-of-members = 1

    role {
      backend.min-nr-of-members = 1
    }

    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"

    failure-detector {
      threshold = 8.0
      acceptable-heartbeat-pause = 3s
    }

    # Singleton 설정
    singleton {
      singleton-name = "singleton"
      role = ""
      hand-over-retry-interval = 1s
      min-number-of-hand-over-retries = 15
    }

    # Singleton Proxy 설정
    singleton-proxy {
      singleton-name = ${pekko.cluster.singleton.singleton-name}
      role = ""
      singleton-identification-interval = 1s
      buffer-size = 1000
    }

    # Sharding 설정
    sharding {
      number-of-shards = 100
      role = ""
      passivation {
        default-idle-strategy.idle-entity.timeout = 2 minutes
      }
    }
  }
}
```

#### 단일 노드 테스트용 설정

```hocon
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
}
```

```kotlin
// 테스트용 단일 노드 클러스터 자동 조인
val cluster = Cluster.get(system)
cluster.manager().tell(org.apache.pekko.cluster.typed.Join.create(cluster.selfMember().address()))
```

### 8. 크로스노드 통신 & 멀티노드 테스트

단일 노드 API 사용법은 1~7번 섹션으로 충분하지만, 실제 멀티노드 클러스터 운용 시에는 크로스노드 통신에 필요한 추가 패턴이 있습니다.

#### 8-1. 크로스노드 액터 참조 (Receptionist)

Pekko Typed에서는 `ServiceKey` + `Receptionist`를 통해 타입 안전하게 크로스노드 액터를 발견합니다.

```kotlin
// 1. Seed 노드: 액터 생성 후 Receptionist에 등록
val serviceKey = ServiceKey.create(MyCommand::class.java, "my-service")
val actor = context.spawn(MyActor.create(), "my-actor")
context.system.receptionist().tell(Receptionist.register(serviceKey, actor))

// 2. Joining 노드: Receptionist 구독으로 크로스노드 액터 발견
val listingProbe = joiningTestKit.createTestProbe<Receptionist.Listing>()
joiningTestKit.system().receptionist().tell(
    Receptionist.subscribe(serviceKey, listingProbe.ref()))

val listing = listingProbe.expectMessageClass(Receptionist.Listing::class.java)
val remoteActor = listing.getServiceInstances(serviceKey).first()
remoteActor.tell(MyCommand(...))
```

| 방법 | 코드 | 설명 |
|------|------|------|
| `ServiceKey` | `ServiceKey.create(Class, name)` | 타입 안전한 서비스 키 정의 |
| 등록 | `receptionist().tell(Receptionist.register(key, ref))` | 클러스터 전체에 액터 등록 |
| 구독 | `receptionist().tell(Receptionist.subscribe(key, adapter))` | 서비스 인스턴스 목록 수신 |

> **Classic과의 차이**: Akka Classic의 `ActorSelection`(경로 기반) 대신, Typed에서는 `Receptionist`(타입 기반)를 사용합니다.

#### 8-2. 크로스노드 메시지 직렬화

크로스노드 통신 시 모든 메시지는 네트워크를 통해 전송되므로 **직렬화 가능**해야 합니다.

```kotlin
// sealed class 메시지에 Serializable 구현 필수
sealed class CounterCommand : java.io.Serializable
object Increment : CounterCommand()
data class GetCount(val replyTo: ActorRef<CounterCommand>) : CounterCommand()
data class CountValue(val value: Int) : CounterCommand()
```

```hocon
pekko.actor {
  allow-java-serialization = on
  warn-about-java-serializer-usage = off
}
```

> **주의**: Java 직렬화는 개발/테스트용입니다. 프로덕션에서는 Jackson JSON 또는 Protobuf를 권장합니다.

#### 8-3. 멀티노드 HOCON 설정 템플릿

**Seed 노드** (고정 포트):

```hocon
pekko {
  actor {
    provider = "cluster"
    allow-java-serialization = on
    warn-about-java-serializer-usage = off
  }
  remote.artery {
    canonical.hostname = "127.0.0.1"
    canonical.port = 25521
  }
  cluster {
    seed-nodes = ["pekko://ClusterSystem@127.0.0.1:25521"]
    min-nr-of-members = 2
    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
    jmx.multi-mbeans-in-same-jvm = on
  }
}
```

**Joining 노드** (자동 포트):

```hocon
pekko {
  actor {
    provider = "cluster"
    allow-java-serialization = on
    warn-about-java-serializer-usage = off
  }
  remote.artery {
    canonical.hostname = "127.0.0.1"
    canonical.port = 0
  }
  cluster {
    seed-nodes = ["pekko://ClusterSystem@127.0.0.1:25521"]
    min-nr-of-members = 2
    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
    jmx.multi-mbeans-in-same-jvm = on
  }
}
```

| 설정 | Seed 노드 | Joining 노드 |
|------|-----------|-------------|
| `canonical.port` | 고정 포트 (예: 25521) | `0` (자동 할당) |
| `seed-nodes` | 자기 자신 포함 | seed 노드만 지정 |
| `min-nr-of-members` | `2` (멀티노드) | `2` (동일) |
| `jmx.multi-mbeans-in-same-jvm` | `on` (같은 JVM 테스트용) | `on` (동일) |

#### 8-4. 2-시스템 클러스터 테스트 패턴

JUnit 5 + `ActorTestKit`에서 `companion object`로 2개의 테스트킷을 관리합니다.

```kotlin
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TwoNodeClusterTest {
    companion object {
        private lateinit var seedTestKit: ActorTestKit
        private lateinit var joiningTestKit: ActorTestKit

        @JvmStatic
        @BeforeAll
        fun setup() {
            seedTestKit = ActorTestKit.create("ClusterSystem",
                ConfigFactory.load("two-node-seed"))
            joiningTestKit = ActorTestKit.create("ClusterSystem",
                ConfigFactory.load("two-node-joining"))
            waitForClusterUp(seedTestKit, 2, 15)
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            // joining 먼저 shutdown
            joiningTestKit.shutdownTestKit()
            seedTestKit.shutdownTestKit()
        }

        private fun waitForClusterUp(testKit: ActorTestKit,
                expectedMembers: Int, timeoutSeconds: Int) {
            val cluster = Cluster.get(testKit.system())
            val deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L)
            while (System.currentTimeMillis() < deadline) {
                val upCount = cluster.state().members
                    .count { it.status() == MemberStatus.up() }
                if (upCount >= expectedMembers) return
                Thread.sleep(500)
            }
            throw RuntimeException(
                "Cluster did not form within ${timeoutSeconds}s")
        }
    }
}
```

> **Kotlin `count()` 확장**: Java의 `StreamSupport.stream()` 변환 대신, Kotlin에서는 Scala `Set`에 직접 `.count { }` 확장함수를 사용할 수 있습니다.

#### 8-5. 크로스노드 PubSub

기존 4번 PubSub과 달리, 크로스노드 전파 시 **양쪽 노드에 Topic 액터가 존재**해야 합니다.

```kotlin
// 1. 양쪽 노드에 PubSubManager (또는 Topic) 액터 생성
val seedPubSub = seedTestKit.spawn(PubSubManagerActor.create(), "pubsub-seed")
val joiningPubSub = joiningTestKit.spawn(PubSubManagerActor.create(), "pubsub-joining")

// 2. 양쪽에서 동일 토픽에 구독 → Topic 액터가 양쪽 노드에 생성됨
joiningPubSub.tell(SubscribeToTopic("my-topic", subscriberRef))
seedPubSub.tell(SubscribeToTopic("my-topic", dummyRef))

// 3. Topic 클러스터 전파 대기 (Receptionist 기반, 약 3~5초)
Thread.sleep(5000)

// 4. Seed 노드에서 발행 → Joining 노드 구독자에게 크로스노드 전달
seedPubSub.tell(PublishMessage("my-topic", "cross-node-hello"))
```

| 주의사항 | 설명 |
|---------|------|
| 양쪽 Topic 생성 | 양쪽 노드에서 동일 토픽에 구독/발행하여 Topic 액터를 생성해야 함 |
| Receptionist 전파 | Topic 액터는 Receptionist를 통해 자동 디스커버리 (3~5초 대기) |
| Classic과의 차이 | mediator 대신 `Topic.create()` 액터 기반, 양쪽 노드에 Topic이 필요 |

## 코드 생성 규칙

1. **패키지는 `org.apache.pekko.*`**를 사용합니다 (`akka.*`가 아님).
2. **HOCON 설정**은 `pekko { }` 블록으로 작성합니다 (`akka { }`가 아님).
3. **프로토콜**은 `pekko://`를 사용합니다 (`akka://`가 아님).
4. **Singleton**은 `ClusterSingleton.get(system).init(SingletonActor.of(...))` 패턴을 사용합니다.
5. **Sharding**은 `EntityTypeKey.create()` + `ClusterSharding.get(system).init(Entity.of(...))` 패턴을 사용합니다.
6. **PubSub**은 `Topic.create()` 액터 기반으로 구현합니다 (Classic의 mediator와 다름).
7. **서비스 발견**은 `ServiceKey` + `Receptionist` + `Routers.group()` 조합을 사용합니다.
8. **메시지는 `sealed class` 계층**으로 정의하고, `replyTo: ActorRef<T>`를 포함합니다.
9. **프로덕션 환경**에서는 반드시 Split Brain Resolver를 설정합니다.
10. **Kotlin 타입 추론 주의**: `Routers.pool(size, behavior)` 형태로 Behavior를 두 번째 인자로 직접 전달합니다. 트레일링 람다 금지.
11. **단일 노드 테스트** 시 `cluster.manager().tell(Join.create(address))`로 자기 자신에게 조인합니다.
12. **크로스노드 통신** 시 모든 메시지 클래스는 `java.io.Serializable`을 구현하고, HOCON에 `allow-java-serialization = on`을 설정합니다.
13. **멀티노드 테스트** 시 seed 노드(고정 포트)와 joining 노드(포트 0)의 HOCON을 분리하고, `min-nr-of-members = 2`를 설정합니다.
14. **크로스노드 PubSub**은 양쪽 노드에 Topic 액터를 생성하여 Receptionist 기반 자동 디스커버리를 활성화합니다.

$ARGUMENTS

## WebApplication 통합 업데이트 (2026-02-17)

- 콘솔 엔트리 중심 샘플을 웹 API 중심으로 확장할 때 아래 기본 API를 우선 제공합니다.
  - `GET /api/heath`
  - `GET /api/actor/hello`
  - `GET /api/cluster/info`
  - `POST /api/kafka/fire-event`
- Kafka 실행은 스케줄러 자동 실행보다 API 트리거 방식을 우선합니다.
- Swagger/OpenAPI와 파일 기반 로깅 구성을 기본 포함합니다.
- 플랫폼별 권장 웹 모드:
  - .NET: ASP.NET Core (.NET 10)
  - Java: Spring Boot MVC (Java 21, Spring Boot 3.5.x)
  - Kotlin: Spring WebFlux + Coroutine (Spring Boot 3.5.x)

## Web 클러스터 연동 주의사항 (2026-02)

1. cluster join 완료 전에 `/api/cluster/info`가 빈 값일 수 있으므로, readiness 이후 검증합니다.
2. singleton actor 경로(manager/proxy)와 API 호출 대상(proxy)을 일치시킵니다.
3. 클러스터 멤버 조건(min members)과 readiness 조건을 동일한 기준으로 맞춥니다.
4. seed-node 주소는 pod DNS 기준으로 고정하고, 런타임에서 hostname 우선순위를 명시합니다.
5. 클러스터 이벤트 로그(`Member is Up`)를 API 테스트 결과와 함께 검증합니다.
