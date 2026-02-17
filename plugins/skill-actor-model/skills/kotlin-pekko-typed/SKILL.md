---
name: kotlin-pekko-typed
description: Kotlin + Pekko Typed 액터 모델 코드를 생성합니다. Kotlin으로 Pekko Typed 기반 액터를 구현하거나, sealed class 메시지 계층, AbstractBehavior, 상태 전환, DurableState 영속화, 클러스터, PubSub, SSE, WebSocket 등 Typed 액터 패턴을 작성할 때 사용합니다.
argument-hint: "[패턴명] [요구사항]"
---

# Kotlin + Pekko Typed 액터 모델 스킬

Kotlin + Apache Pekko Typed(1.4.x) 기반의 타입 안전한 액터 모델 코드를 생성하는 스킬입니다.

## 참고 문서

- 상세 패턴 가이드: [skill-maker/docs/actor/02-kotlin-pekko-typed/README.md](../../../../skill-maker/docs/actor/02-kotlin-pekko-typed/README.md)
- 액터모델 개요: [skill-maker/docs/actor/00-actor-model-overview.md](../../../../skill-maker/docs/actor/00-actor-model-overview.md)
- 크로스 플랫폼 비교: [skill-maker/docs/actor/05-cross-platform-comparison.md](../../../../skill-maker/docs/actor/05-cross-platform-comparison.md)

## 환경

- **프레임워크**: Apache Pekko 1.4.x (Akka 2.7.x 오픈소스 포크)
- **언어**: Kotlin
- **빌드**: Gradle (Kotlin DSL)
- **웹 프레임워크**: Spring Boot (WebFlux / MVC)
- **라이선스**: Apache License 2.0
- **패키지**: `org.apache.pekko.*` (Akka의 `akka.*`에 대응)

## 마이그레이션 메모 (1.1.x -> 1.4.x)

- 코어 액터 코드는 대부분 그대로 유지되며, 실무에서 변화 효과가 큰 지점은 **클러스터 조인 전략**입니다.
- Kubernetes 배포형 프로젝트는 `seed-nodes` 고정보다 `kubernetes-api` discovery + bootstrap 조합을 권장합니다.
- 관련 설정/인프라 템플릿은 `kotlin-pekko-typed-infra` 스킬을 함께 사용합니다.

## 핵심 특성

- **타입 안전**: `AbstractBehavior<T>` + `ActorRef<T>`로 컴파일 타임 메시지 타입 검증
- **sealed class**: 메시지 계층을 sealed class로 정의하여 exhaustive 검사
- **명시적 replyTo**: 응답 대상을 `replyTo: ActorRef<T>`로 메시지에 포함
- **Behavior 기반 상태 전환**: 핸들러가 `Behavior<T>`를 반환하여 상태 머신 구현

## 지원 패턴

### 1. 기본 Typed 액터 (Basic Typed Actor)
- `AbstractBehavior<T>` 상속, `createReceive()` 구현
- `sealed class` 명령 계층 + `data class` 메시지
- `Behaviors.setup { context -> }` 팩토리
- `newReceiveBuilder().onMessage(Class, handler).onSignal(Class, handler).build()`

```kotlin
sealed class HelloCommand
data class Hello(val message: String, val replyTo: ActorRef<HelloCommand>) : HelloCommand()
data class HelloResponse(val message: String) : HelloCommand()

class HelloActor private constructor(
    context: ActorContext<HelloCommand>,
) : AbstractBehavior<HelloCommand>(context) {

    companion object {
        fun create(): Behavior<HelloCommand> {
            return Behaviors.setup { context -> HelloActor(context) }
        }
    }

    override fun createReceive(): Receive<HelloCommand> {
        return newReceiveBuilder()
            .onMessage(Hello::class.java, this::onHello)
            .onMessage(HelloResponse::class.java, this::onHelloResponse)
            .onSignal(PostStop::class.java) { _ ->
                context.log.info("Actor stopped")
                this
            }
            .build()
    }

    private fun onHello(command: Hello): Behavior<HelloCommand> {
        context.log.info("Received: ${command.message}")
        command.replyTo.tell(HelloResponse("Kotlin"))
        return this
    }

    private fun onHelloResponse(command: HelloResponse): Behavior<HelloCommand> {
        context.log.info("Response: ${command.message}")
        return this
    }
}
```

### 2. 상태 전환 / FSM (State Switching)
- 내부 변수 기반 상태 관리 (단순 상태)
- Behavior 교체 기반 상태 전환 (`Behaviors.receive { }` 반환)
- BulkProcessor 패턴: idle/active 상태, 버퍼 축적 + 타임아웃 플러시

```kotlin
// Behavior 교체 기반 FSM
private fun idle(): Behavior<BatchCommand> {
    return Behaviors.receive { context, message ->
        when (message) {
            is AddItem -> {
                buffer.add(message.item)
                active()  // 상태 전환
            }
            else -> Behaviors.same()
        }
    }
}

private fun active(): Behavior<BatchCommand> {
    return Behaviors.receive { context, message ->
        when (message) {
            is Flush -> {
                processBuffer()
                idle()  // 상태 전환
            }
            else -> Behaviors.same()
        }
    }
}
```

#### BulkProcessor (강화된 FSM)

FlushTimeout + threshold 기반 자동 flush가 포함된 실전 패턴:

```kotlin
sealed class BulkProcessorCommand
data class DataEvent(val data: Any, val replyTo: ActorRef<Any>) : BulkProcessorCommand()
object Flush : BulkProcessorCommand()
private object FlushTimeout : BulkProcessorCommand()

class BulkProcessor private constructor(
    context: ActorContext<BulkProcessorCommand>,
    private val buffer: MutableList<Any> = mutableListOf()
) : AbstractBehavior<BulkProcessorCommand>(context) {

    companion object {
        fun create(): Behavior<BulkProcessorCommand> {
            return Behaviors.setup { context -> BulkProcessor(context) }
        }
    }

    override fun createReceive(): Receive<BulkProcessorCommand> = idle()

    private fun idle(): Receive<BulkProcessorCommand> {
        return newReceiveBuilder()
            .onMessage(DataEvent::class.java) { event ->
                buffer.add(event.data)
                startFlushTimer()
                active()  // IDLE → ACTIVE
            }
            .build()
    }

    private fun active(): Receive<BulkProcessorCommand> {
        return newReceiveBuilder()
            .onMessage(DataEvent::class.java) { event ->
                buffer.add(event.data)
                if (buffer.size >= 100) { flushBuffer(); idle() }  // 임계치 도달
                else Behaviors.same()
            }
            .onMessage(Flush::class.java) { flushBuffer(); idle() }
            .onMessageEquals(FlushTimeout) { flushBuffer(); idle() }  // 타임아웃
            .build()
    }

    private var flushTimer: Cancellable? = null

    private fun startFlushTimer() {
        flushTimer = context.scheduleOnce(Duration.ofSeconds(3), context.self, FlushTimeout)
    }

    private fun flushBuffer() {
        context.log.info("Processing ${buffer.size} events.")
        buffer.clear()
        flushTimer?.cancel()
    }
}
```

| 패턴 | 설명 |
|------|------|
| `onMessageEquals(FlushTimeout)` | `private object`에 대한 동등성 비교. 타이머 메시지 처리에 적합 |
| `context.scheduleOnce()` | 일회성 스케줄링. `Cancellable`을 직접 관리 |

#### FSM + SQLite 배치 인서트 (sample16 확장)

- `IDLE/ACTIVE` 상태에서 이벤트(`event1~event5`)를 버퍼링
- `context.scheduleOnce(3s)` 타이머 또는 `buffer.size >= 100` 임계치에서 flush
- flush 시 `minOf(100, buffer.size)` 청크 단위로 반복 INSERT
- 종료 메시지(`Stop`) 수신 시 잔여 버퍼를 강제 flush

```kotlin
private fun flushBuffered(reason: String) {
    flushTimer?.cancel()
    flushTimer = null
    while (buffer.isNotEmpty()) {
        val chunkSize = minOf(100, buffer.size)
        val chunk = buffer.take(chunkSize)
        buffer.subList(0, chunkSize).clear()
        repo.insertBatch(chunk) // SQLite transaction batch insert
    }
}
```

### 3. 타이머 (Timer)
- `Behaviors.withTimers { timers -> }` 래핑
- `timers.startTimerAtFixedRate()`, `startSingleTimer()`
- `timers.cancel(key)`, `timers.isTimerActive(key)`

```kotlin
companion object {
    fun create(): Behavior<TimerCommand> {
        return Behaviors.withTimers { timers ->
            Behaviors.setup { context -> TimerActor(context, timers) }
        }
    }
}

init {
    timers.startTimerAtFixedRate(TimerLoop, Duration.ofSeconds(10))
}
```

### 4. 감독 전략 (Supervision)
- `Behaviors.supervise(childBehavior).onFailure(strategy)` 래핑
- `SupervisorStrategy.restart()`, `.resume()`, `.stop()`
- `.withLimit(maxRetries, duration)` 재시작 횟수 제한
- `restartWithBackoff(minBackoff, maxBackoff, randomFactor)` 지수 백오프
- `context.watch(childRef)` + `Terminated` 시그널 감시

```kotlin
val childActor = context.spawn(
    Behaviors.supervise(ChildActor.create())
        .onFailure(SupervisorStrategy.restart()
            .withLimit(10, Duration.ofMinutes(1))
            .withLoggingEnabled(true)),
    "child"
)
context.watch(childActor)
```

### 5. 라우팅 (Router)
- **Pool Router**: `Routers.pool(size, Behavior)` + 라우팅 전략
- **Group Router**: `ServiceKey` + `Receptionist` + `Routers.group(serviceKey)`
- **지원 전략**: RoundRobin, Random, ConsistentHashing, Broadcast(커스텀)
- 커스텀 스마트 라우터: 가중치 기반 분배

> **주의 (Kotlin 타입 추론)**: `Routers.pool(size)` 에 트레일링 람다 `{ Behavior }` 를 사용하면 Kotlin 컴파일러가 `Behavior<T>` 타입을 추론하지 못합니다.
> 반드시 `Routers.pool(size, Behavior)` 형태로 Behavior를 두 번째 인자로 직접 전달하고, `val pool: PoolRouter<T>` 타입을 명시해야 합니다.

```kotlin
// ❌ 컴파일 에러 - Kotlin 트레일링 람다 타입 추론 실패
val pool = Routers.pool(5) { HelloActor.create() }

// ✅ 올바른 사용법 - Behavior를 두 번째 인자로 직접 전달 + 타입 명시
val pool: PoolRouter<WorkerCommand> = Routers.pool(5, HelloWorkerActor.create())
    .withRoundRobinRouting()
```

#### Pool Router - 4가지 라우팅 전략

```kotlin
// 1) RoundRobin - 순환 분배 (1→2→3→4→5→1→...)
val roundRobin: PoolRouter<WorkerCommand> = Routers.pool(5, HelloWorkerActor.create())
    .withRoundRobinRouting()
val router1 = context.spawn(roundRobin, "roundrobin-pool")

// 2) Random - 무작위 분배
val random: PoolRouter<WorkerCommand> = Routers.pool(5, HelloWorkerActor.create())
    .withRandomRouting()
val router2 = context.spawn(random, "random-pool")

// 3) ConsistentHashing - 동일 key → 항상 동일 워커로 라우팅
//    메시지에서 해시 키를 추출하는 함수 제공 필요
val hashing: PoolRouter<WorkerCommand> = Routers.pool(5, HelloWorkerActor.create())
    .withConsistentHashingRouting(10) { message: WorkerCommand ->
        when (message) {
            is HelloTask -> message.key  // 해시 키 추출
            else -> ""
        }
    }
val router3 = context.spawn(hashing, "consistent-hashing-pool")

// 4) Broadcast - 커스텀 구현 (Pekko Typed PoolRouter에 내장 전략 없음)
//    N개 워커를 직접 스폰하고 메시지를 전체에게 fan-out
val router4 = context.spawn(BroadcastRouter.create(5), "broadcast-pool")
```

#### Broadcast Router (커스텀 구현)

Pekko Typed의 `PoolRouter`에는 Broadcast 전략이 내장되어 있지 않으므로,
워커를 직접 스폰하고 메시지를 전체에게 전달하는 커스텀 액터로 구현합니다.

```kotlin
class BroadcastRouter private constructor(
    context: ActorContext<WorkerCommand>,
    private val workers: List<ActorRef<WorkerCommand>>
) : AbstractBehavior<WorkerCommand>(context) {

    companion object {
        fun create(poolSize: Int): Behavior<WorkerCommand> {
            return Behaviors.setup { context ->
                val workers = (1..poolSize).map { i ->
                    context.spawn(HelloWorkerActor.create(), "broadcast-worker-$i")
                }
                BroadcastRouter(context, workers)
            }
        }
    }

    override fun createReceive(): Receive<WorkerCommand> {
        return newReceiveBuilder()
            .onMessage(HelloTask::class.java) { task ->
                workers.forEach { worker -> worker.tell(task) }
                this
            }
            .onMessage(StopWorker::class.java) { stop ->
                workers.forEach { worker -> worker.tell(stop) }
                Behaviors.stopped()
            }
            .build()
    }
}
```

| 전략 | API | 특징 |
|------|-----|------|
| RoundRobin | `.withRoundRobinRouting()` | 순환 분배, 균등 부하 |
| Random | `.withRandomRouting()` | 무작위, 통계적 균등 |
| ConsistentHashing | `.withConsistentHashingRouting(vnodes) { msg -> key }` | 동일 키 → 동일 워커 보장 |
| Broadcast | 커스텀 액터 (fan-out) | 1개 메시지 → 전체 워커 수신 |

#### Group Router (클러스터 환경)

```kotlin
val serviceKey = ServiceKey.create(WorkerCommand::class.java, "worker")
context.system.receptionist().tell(Receptionist.register(serviceKey, workerRef))
val group = Routers.group(serviceKey).withRoundRobinRouting()
val groupRouter = context.spawn(group, "worker-group")
```

### 6. 영속화 - DurableState (Persistence)
- `DurableStateBehavior<Command, State>` 상속
- 이벤트 소싱이 아닌 **현재 상태 직접 저장** 방식
- `emptyState()`, `commandHandler()`, `tag()` 구현
- `Effect().persist(newState).thenRun { }` 효과 체인
- R2DBC + PostgreSQL 백엔드
- Custom Store 방식: Repository 직접 사용 + Stream 비동기 영속화

```kotlin
class HelloPersistentActor(
    context: ActorContext<HelloCommand>,
    persistenceId: PersistenceId
) : DurableStateBehavior<HelloCommand, HelloState>(persistenceId) {

    override fun emptyState(): HelloState = HelloState(State.HAPPY, 0, 0)

    override fun commandHandler(): CommandHandler<HelloCommand, HelloState> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Hello::class.java) { state, command ->
                val newState = state.copy(helloCount = state.helloCount + 1)
                Effect().persist(newState).thenRun {
                    command.replyTo.tell(HelloResponse("Kotlin"))
                }
            }
            .build()
    }
}
```

#### Custom Store (pipeToSelf)

`DurableStateBehavior` 대신 Repository를 직접 사용하여 영속화하는 패턴:

```kotlin
// 저장 요청 시 pipeToSelf로 비동기 DB 호출
private fun onSaveState(message: SavaState): Behavior<HelloStashCommand> {
    context.pipeToSelf(
        durableRepository.createOrUpdateDurableStateEx<HelloStashState>(
            persistenceId, 1L, message.state
        ).toFuture(),
        { _, cause ->
            if (cause == null) SaveSuccess else DbError(RuntimeException(cause))
        }
    )
    return saving(message.state, message.replyTo)  // 저장 대기 상태로 전환
}
```

> `pipeToSelf`는 비동기 작업 결과를 자신에게 메시지로 전달하여 액터의 단일 스레드 보장을 유지합니다. Stash 패턴과 결합하면 저장 중 메시지를 버퍼링할 수 있습니다.

#### SQLite 직접 이벤트 저장 (로컬 단일 노드)

`DurableStateBehavior`를 사용하지 않고, SQLite(JDBC)에 이벤트를 직접 append하고 시작 시 replay로 상태를 복원하는 패턴:

```kotlin
data class CounterIncrementedEvent(val seqNr: Long, val amount: Int, val createdAt: Instant)

class SqliteEventStore(private val dbUrl: String) {
    init {
        DriverManager.getConnection(dbUrl).use { conn ->
            conn.createStatement().executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS actor_events (
                  persistence_id TEXT NOT NULL,
                  seq_nr INTEGER NOT NULL,
                  event_type TEXT NOT NULL,
                  amount INTEGER NOT NULL,
                  created_at TEXT NOT NULL,
                  PRIMARY KEY (persistence_id, seq_nr)
                )
                """.trimIndent()
            )
        }
    }

    fun loadEvents(persistenceId: String): List<CounterIncrementedEvent> = TODO()

    fun appendIncrementedEvent(persistenceId: String, amount: Int): CounterIncrementedEvent {
        DriverManager.getConnection(dbUrl).use { conn ->
            conn.autoCommit = false
            try {
                val nextSeq = nextSequence(conn, persistenceId) // MAX(seq_nr)+1
                // INSERT ... COMMIT
                conn.commit()
                return CounterIncrementedEvent(nextSeq, amount, Instant.now())
            } catch (ex: Exception) {
                conn.rollback()
                throw ex
            } finally {
                conn.autoCommit = true
            }
        }
    }
}

object PersistentCounterActor {
    fun create(persistenceId: String, store: SqliteEventStore): Behavior<CounterCommand> =
        Behaviors.setup { context ->
            val events = store.loadEvents(persistenceId)
            var state = CounterState(
                value = events.sumOf { it.amount },
                lastSeqNr = events.lastOrNull()?.seqNr ?: 0L,
                eventCount = events.size
            )
            context.log.info("[복구 완료] pid={}, value={}, lastSeqNr={}", persistenceId, state.value, state.lastSeqNr)
            Behaviors.receiveMessage { cmd ->
                when (cmd) {
                    is IncrementCounter -> {
                        val ev = store.appendIncrementedEvent(persistenceId, cmd.amount)
                        state = state.copy(
                            value = state.value + ev.amount,
                            lastSeqNr = ev.seqNr,
                            eventCount = state.eventCount + 1
                        )
                        cmd.replyTo.tell(CounterAck(persistenceId, ev.seqNr, state.value))
                        Behaviors.same()
                    }
                    is GetCounterState -> { cmd.replyTo.tell(state); Behaviors.same() }
                }
            }
        }
}
```

| 항목 | 권장 구현 |
|------|-----------|
| 재기동 복구 | `SELECT ... ORDER BY seq_nr ASC`로 replay |
| 순번 생성 | `MAX(seq_nr)+1`를 트랜잭션 안에서 계산 |
| 원자성 | `autoCommit=false` + `commit/rollback` |
| 적용 대상 | 단일 노드/로컬 개발용 경량 영속화 |

### 7. Stash 패턴
- 비동기 초기화 중 메시지를 버퍼링
- `Behaviors.withStash(capacity) { stash -> }` 래핑
- `stash.stash(message)` 저장, `stash.unstashAll(behavior)` 일괄 처리
- `pipeToSelf`와 결합: 비동기 DB 로드 → stash → 완료 후 unstashAll

```kotlin
class HelloStashActor private constructor(
    private val context: ActorContext<HelloStashCommand>,
    private val durableRepository: DurableRepository,
    private val buffer: StashBuffer<HelloStashCommand>
) {
    companion object {
        fun create(persistenceId: String, repo: DurableRepository): Behavior<HelloStashCommand> {
            return Behaviors.withStash(100) { stashBuffer ->
                Behaviors.setup { context ->
                    // pipeToSelf: 비동기 DB 로드 결과를 메시지로 수신
                    context.pipeToSelf(
                        repo.findByIdEx<HelloStashState>(persistenceId, 1L).toFuture(),
                        { value, cause ->
                            if (cause == null) InitialState(value) else DbError(RuntimeException(cause))
                        }
                    )
                    HelloStashActor(context, repo, stashBuffer).start()
                }
            }
        }
    }

    // 시작 상태: DB 초기화 대기, 나머지 메시지는 stash
    private fun start(): Behavior<HelloStashCommand> {
        return Behaviors.receive(HelloStashCommand::class.java)
            .onMessage(InitialState::class.java) { msg ->
                buffer.unstashAll(active(msg.toState()))  // stash된 메시지 재처리
            }
            .onMessage(HelloStashCommand::class.java) { msg ->
                buffer.stash(msg)  // 초기화 중 메시지 보관
                Behaviors.same()
            }
            .build()
    }

    // 활성 상태: 정상 메시지 처리
    private fun active(state: HelloStashState): Behavior<HelloStashCommand> {
        return Behaviors.receive(HelloStashCommand::class.java)
            .onMessage(GetState::class.java) { msg ->
                msg.replyTo.tell(state)
                Behaviors.same()
            }
            .onMessage(SavaState::class.java) { msg ->
                context.pipeToSelf(repo.save(msg.state).toFuture(), { _, cause ->
                    if (cause == null) SaveSuccess else DbError(RuntimeException(cause))
                })
                saving(msg.state, msg.replyTo)  // 저장 대기로 전환
            }
            .build()
    }

    // 저장 상태: DB 저장 중, 메시지 stash
    private fun saving(state: HelloStashState, replyTo: ActorRef<Done>): Behavior<HelloStashCommand> {
        return Behaviors.receive(HelloStashCommand::class.java)
            .onMessage(SaveSuccess::class.java) { _ ->
                replyTo.tell(Done.getInstance())
                buffer.unstashAll(active(state))
            }
            .onMessage(HelloStashCommand::class.java) { msg ->
                buffer.stash(msg)
                Behaviors.same()
            }
            .build()
    }
}
```

| API | 설명 |
|-----|------|
| `Behaviors.withStash(capacity)` | StashBuffer 생성. capacity는 최대 보관 메시지 수 |
| `buffer.stash(message)` | 메시지를 보관 |
| `buffer.unstashAll(behavior)` | 보관된 모든 메시지를 지정된 Behavior에서 재처리 |
| `context.pipeToSelf(future, mapper)` | 비동기 작업 결과를 자기 자신에게 메시지로 전달 |

### 8. PubSub (발행-구독)
- `Topic.create(Class, topicName)` 토픽 생성
- `Topic.publish(message)`, `Topic.subscribe(actorRef)`
- 채널 기반 토픽 관리 (`mutableMapOf<String, ActorRef<Topic.Command<T>>>()`)
- 클러스터 환경에서 노드 간 메시지 전파

```kotlin
sealed class PubSubCommand : PersitenceSerializable
data class PublishMessage(val channel: String, val message: String) : PubSubCommand()
data class Subscribe(val channel: String, val subscriber: ActorRef<String>) : PubSubCommand()

class PubSubActor(
    context: ActorContext<PubSubCommand>
) : AbstractBehavior<PubSubCommand>(context) {

    companion object {
        fun create(): Behavior<PubSubCommand> {
            return Behaviors.setup { context -> PubSubActor(context) }
        }
    }

    // 채널별 Topic 액터를 관리
    private val topics = mutableMapOf<String, ActorRef<Topic.Command<String>>>()

    override fun createReceive(): Receive<PubSubCommand> {
        return newReceiveBuilder()
            .onMessage(PublishMessage::class.java, this::onPublishMessage)
            .onMessage(Subscribe::class.java, this::onSubscribe)
            .build()
    }

    private fun onPublishMessage(command: PublishMessage): Behavior<PubSubCommand> {
        val topic = topics.getOrPut(command.channel) {
            context.spawn(Topic.create(String::class.java, command.channel), command.channel)
        }
        topic.tell(Topic.publish(command.message))
        return this
    }

    private fun onSubscribe(command: Subscribe): Behavior<PubSubCommand> {
        val topic = topics.getOrPut(command.channel) {
            context.spawn(Topic.create(String::class.java, command.channel), command.channel)
        }
        topic.tell(Topic.subscribe(command.subscriber))
        return this
    }
}
```

```kotlin
// Topic API 요약
val topic = context.spawn(Topic.create(String::class.java, "my-topic"), "my-topic")
topic.tell(Topic.subscribe(subscriberActorRef))  // 구독
topic.tell(Topic.publish("hello everyone"))       // 발행
topic.tell(Topic.unsubscribe(subscriberActorRef)) // 구독 해제
```

### 9. 클러스터 (Cluster)
- **Cluster Membership**: `Cluster.get(system).selfMember()`, 역할(role) 관리
- **Cluster Singleton**: `ClusterSingleton.get(system).init(SingletonActor.of(behavior, name))`
- **Cluster Sharding**: `ClusterSharding.get(system).init(Entity.of(typeKey) { behavior })`
- **ServiceKey + Receptionist**: 클러스터 내 액터 발견

```kotlin
// Cluster Singleton
val single = ClusterSingleton.get(mainStage)
singleCount = single.init(
    SingletonActor.of(
        Behaviors.supervise(CounterActor.create("singleId"))
            .onFailure(SupervisorStrategy.restartWithBackoff(
                Duration.ofSeconds(1), Duration.ofSeconds(2), 0.2)),
        "GlobalCounter"
    )
)

// Cluster Sharding
val shardSystem = ClusterSharding.get(mainStage)
val typeKey = EntityTypeKey.create(CounterCommand::class.java, "counter")
shardSystem.init(Entity.of(typeKey) { CounterActor.create(it.entityId) })
```

### 10. Stream / Throttle
- `Source.queue()` + `.throttle()` + `Sink.foreach { }` 파이프라인
- `QueueOfferResult` 처리 (Enqueued, Dropped, Failure, QueueClosed)
- `Flow` 기반 그래프 처리, 런타임 Flow 교체
- `Materializer.createMaterializer(system)`

```kotlin
sealed class GraphCommand
data class ProcessNumber(val number: Int, val replyTo: ActorRef<GraphCommand>) : GraphCommand()
data class ProcessedNumber(val result: Int) : GraphCommand()
object SwitchToMultiply : GraphCommand()
object SwitchToAdd : GraphCommand()

class GraphActor private constructor(
    context: ActorContext<GraphCommand>,
    private var operation: Flow<Int, Int, *>    // 동적으로 교체 가능한 Flow
) : AbstractBehavior<GraphCommand>(context) {

    companion object {
        fun create(): Behavior<GraphCommand> {
            return Behaviors.setup { context ->
                val initialOperation = Flow.of(Int::class.java).map { it + 1 }
                GraphActor(context, initialOperation)
            }
        }
    }

    private val materializer = Materializer.createMaterializer(context.system)

    override fun createReceive(): Receive<GraphCommand> {
        return newReceiveBuilder()
            .onMessage(ProcessNumber::class.java, this::onProcessNumber)
            .onMessage(ProcessedNumber::class.java, this::onProcessedNumber)
            .onMessage(SwitchToMultiply::class.java, this::onSwitchToMultiply)
            .onMessage(SwitchToAdd::class.java, this::onSwitchToAdd)
            .build()
    }

    private fun onProcessNumber(command: ProcessNumber): Behavior<GraphCommand> {
        Source.single(command.number)
            .via(operation)                                    // 현재 Flow 적용
            .buffer(1000, OverflowStrategy.dropHead())
            .throttle(10, Duration.ofSeconds(1))               // 처리량 제한
            .runWith(
                Sink.foreach { result -> command.replyTo.tell(ProcessedNumber(result)) },
                materializer
            )
        return this
    }

    // 런타임에 연산(Flow)을 변경
    private fun onSwitchToMultiply(command: SwitchToMultiply): Behavior<GraphCommand> {
        operation = Flow.of(Int::class.java).map { it * 2 }
        return this
    }

    private fun onSwitchToAdd(command: SwitchToAdd): Behavior<GraphCommand> {
        operation = Flow.of(Int::class.java).map { it + 1 }
        return this
    }

    private fun onProcessedNumber(command: ProcessedNumber): Behavior<GraphCommand> {
        context.log.info("Processed result: ${command.result}")
        return this
    }
}
```

#### WorkingWithGraph (Broadcast + Merge)

`Source(Random 1~100)`를 `Broadcast(2)`로 분기하고 fan1(`+2`), fan2(`+10`) 처리 후 `Merge(2)`로 합치는 패턴:

```kotlin
val source = Source.from((1..8).map { Random.nextInt(1, 101) })
val sink = Sink.foreach<Int> { out -> println("[Out] merged=$out") }

val graph = RunnableGraph.fromGraph(
    GraphDSL.create(sink) { builder, out ->
        val src = builder.add(source)
        val bcast = builder.add(Broadcast.create<Int>(2))
        val merge = builder.add(Merge.create<Int>(2))
        val fan1 = builder.add(Flow.of(Int::class.java).map { n -> n + 2 })
        val fan2 = builder.add(Flow.of(Int::class.java).map { n -> n + 10 })

        builder.from(src).toInlet(bcast.`in`())
        builder.from(bcast.out(0)).via(fan1).toInlet(merge.`in`(0))
        builder.from(bcast.out(1)).via(fan2).toInlet(merge.`in`(1))
        builder.from(merge.out()).to(out)
        ClosedShape.getInstance()
    }
)
graph.run(materializer)
```

| 구성요소 | 역할 |
|---------|------|
| `Broadcast(2)` | 입력 1건을 두 분기(fan1/fan2)로 복제 |
| `fan1` | `src + 2` 변환 |
| `fan2` | `src + 10` 변환 |
| `Merge(2)` | 두 분기 결과를 단일 출력으로 병합 |

#### Queue 기반 Throttle (Source.queue)

`Source.queue()`로 큐를 생성하고 `QueueOfferResult`로 오버플로우를 감지하는 패턴입니다.
GraphActor와 달리 **생산자가 큐에 직접 offer**하고, 버퍼 초과 시 드롭을 추적할 수 있습니다.

```kotlin
sealed class ThrottleCommand
data class EmitEvent(val eventId: Int, val payload: String) : ThrottleCommand()
data class EventProcessed(val eventId: Int) : ThrottleCommand()
data class GetStats(val replyTo: ActorRef<ThrottleStats>) : ThrottleCommand()
object Shutdown : ThrottleCommand()

data class ThrottleStats(val totalEmitted: Int, val totalProcessed: Int, val totalDropped: Int)

class ThrottleEventActor private constructor(
    context: ActorContext<ThrottleCommand>
) : AbstractBehavior<ThrottleCommand>(context) {

    companion object {
        private const val THROTTLE_RATE = 3   // 초당 처리량
        private const val BUFFER_SIZE = 100   // 버퍼 크기 (초과 시 드롭)

        fun create(): Behavior<ThrottleCommand> {
            return Behaviors.setup { ctx -> ThrottleEventActor(ctx) }
        }
    }

    private val materializer = Materializer.createMaterializer(context.system)
    private val processedCount = AtomicInteger(0)
    private val droppedCount = AtomicInteger(0)
    private val emittedCount = AtomicInteger(0)

    // Source.queue → throttle → Sink 파이프라인
    // dropNew: 버퍼가 가득 차면 새로 들어오는 이벤트를 거부
    private val queue: SourceQueueWithComplete<Pair<Int, String>> = Source
        .queue<Pair<Int, String>>(BUFFER_SIZE, OverflowStrategy.dropNew())
        .throttle(THROTTLE_RATE, Duration.ofSeconds(1))
        .to(Sink.foreach { (eventId, _) ->
            processedCount.incrementAndGet()
            context.self.tell(EventProcessed(eventId))
        })
        .run(materializer)

    override fun createReceive(): Receive<ThrottleCommand> {
        return newReceiveBuilder()
            .onMessage(EmitEvent::class.java, this::onEmitEvent)
            .onMessage(GetStats::class.java, this::onGetStats)
            .onMessageEquals(Shutdown) { queue.complete(); Behaviors.stopped() }
            .build()
    }

    // offer() 후 QueueOfferResult로 큐 진입 여부 확인
    private fun onEmitEvent(command: EmitEvent): Behavior<ThrottleCommand> {
        emittedCount.incrementAndGet()
        queue.offer(Pair(command.eventId, command.payload)).thenAccept { result ->
            when (result) {
                is QueueOfferResult.`Enqueued$` -> { /* 큐에 추가됨 */ }
                is QueueOfferResult.`Dropped$` -> droppedCount.incrementAndGet()
                is QueueOfferResult.Failure -> context.log.error("Queue offer failed")
                is QueueOfferResult.`QueueClosed$` -> context.log.warn("Queue already closed")
            }
        }
        return this
    }

    private fun onGetStats(command: GetStats): Behavior<ThrottleCommand> {
        command.replyTo.tell(ThrottleStats(
            emittedCount.get(), processedCount.get(), droppedCount.get()))
        return this
    }
}
```

> **Kotlin에서 Scala 싱글턴 접근**: `QueueOfferResult.Enqueued`, `Dropped`, `QueueClosed`는 Scala object이므로 Kotlin에서 백틱으로 `\`Enqueued$\``, `\`Dropped$\``, `\`QueueClosed$\``로 접근합니다. `Failure`는 case class이므로 그대로 사용합니다.

| QueueOfferResult | 의미 | 대응 전략 |
|------------------|------|-----------|
| `Enqueued$` | 큐에 정상 추가됨 | 별도 처리 불필요 |
| `Dropped$` | 버퍼 초과로 거부됨 | 카운터 증가, 로깅, 재시도 등 |
| `Failure` | 스트림 오류 발생 | 에러 로깅, 복구 처리 |
| `QueueClosed$` | 스트림이 이미 종료됨 | 큐 재생성 또는 종료 |

### 11. WebSocket 세션 관리
- 다층 액터 구조: MainStage -> SessionManager -> PersonalRoom -> CounselorRoom
- 세션 라이프사이클 관리, 타이머 기반 자동 처리

### 12. SSE (Server-Sent Events)
- UserEventActor 큐 패턴
- ClusterSingleton 기반 사용자별 이벤트 액터
- Topic 기반 이벤트 분배 (brand + user 토픽)

### 13. Spring Boot 통합
- `@Configuration` + `@PostConstruct`로 ActorSystem 초기화
- `AskPattern.ask()` + Kotlin Coroutine `await()` 비동기 브릿지
- `@Bean`으로 ActorRef 등록

```kotlin
@RestController
class ActorController @Autowired constructor(private val akka: AkkaConfiguration) {
    @PostMapping("/hello")
    suspend fun hello(): String {
        val response = AskPattern.ask(
            akka.getHelloState(),
            { replyTo: ActorRef<Any> -> Hello("Hello", replyTo) },
            Duration.ofSeconds(3),
            akka.getScheduler()
        ).await()
        return (response as HelloResponse).message
    }
}
```

### 14. 테스트 (ActorTestKit)
- `ActorTestKit.create()` 정적 팩토리
- `testKit.spawn(behavior, name)` 타입 안전한 액터 생성
- `testKit.createTestProbe<T>()` 타입 지정 프로브
- `probe.expectMessage()`, `probe.expectNoMessage()`

## 코드 생성 규칙

1. **메시지는 반드시 `sealed class` 계층**으로 정의합니다. 각 메시지는 `data class` 또는 `object`입니다.
2. **응답이 필요한 메시지는 `replyTo: ActorRef<ResponseType>`**을 포함합니다.
3. **액터는 `AbstractBehavior<T>` 상속** + `companion object { fun create() }` 팩토리입니다.
4. **핸들러는 `Behavior<T>`를 반환**합니다. 상태 유지는 `this`, 전환은 새 Behavior 반환.
5. **패키지는 `org.apache.pekko.*`**를 사용합니다 (Akka의 `akka.*`가 아님).
6. **HOCON 설정**은 `pekko { }` 블록으로 작성합니다 (`akka { }`가 아님).
7. **직렬화**: Jackson JSON/CBOR 사용, `PersitenceSerializable` 마커 인터페이스.
8. 타이머가 필요하면 `Behaviors.withTimers { timers -> Behaviors.setup { } }` 패턴을 사용합니다.
9. 감독 전략은 자식 생성 시 `Behaviors.supervise(behavior).onFailure(strategy)`로 개별 적용합니다.
10. Spring 통합 시 Coroutine의 `suspend` + `.await()`로 Ask 패턴을 사용합니다.
11. **`Routers.pool(size, behavior)` 형태로 Behavior를 두 번째 인자로 직접 전달**합니다. 트레일링 람다 `Routers.pool(size) { behavior }` 는 Kotlin 타입 추론 실패로 컴파일 에러가 발생합니다. 반드시 `val pool: PoolRouter<T>` 타입도 명시합니다.
12. **Broadcast 라우팅**은 Pekko Typed PoolRouter에 내장 전략이 없으므로, 워커를 직접 스폰하고 `workers.forEach { it.tell(msg) }` 로 fan-out하는 커스텀 액터로 구현합니다.

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

## Web + Actor 통합 주의사항 (2026-02)

1. 웹 진입점과 ActorSystem 수명주기를 분리하지 말고, 시작/종료 훅(HostedService, Spring Lifecycle)으로 일원화합니다.
2. `/api/cluster/info`는 반드시 ActorSystem 상태에서 조회하며, API 계층 캐시를 두지 않습니다.
3. Ask 패턴은 언어별 표준을 고정합니다.
   - Dotnet classic: `Sender` 응답 + `Ask(object, timeout)`
   - Java classic: `PatternsCS.ask(..., Duration)`
   - Kotlin typed: `AskPattern.ask(...).await()`
4. `/api/kafka/fire-event`는 스케줄 자동 발행 대신 API 수동 트리거를 기본으로 둡니다.
5. Swagger 버전은 런타임 메이저와 일치시킵니다(특히 .NET).
6. 파일 로깅은 `logback-spring.xml`/Serilog file sink로 구성하고, 콘솔 로깅과 함께 유지합니다.
