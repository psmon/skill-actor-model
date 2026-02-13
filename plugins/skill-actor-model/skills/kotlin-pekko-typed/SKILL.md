---
name: kotlin-pekko-typed
description: Kotlin + Pekko Typed 액터 모델 코드를 생성합니다. Kotlin으로 Pekko Typed 기반 액터를 구현하거나, sealed class 메시지 계층, AbstractBehavior, 상태 전환, DurableState 영속화, 클러스터, PubSub, SSE, WebSocket 등 Typed 액터 패턴을 작성할 때 사용합니다.
argument-hint: "[패턴명] [요구사항]"
---

# Kotlin + Pekko Typed 액터 모델 스킬

Kotlin + Apache Pekko Typed(1.1.x) 기반의 타입 안전한 액터 모델 코드를 생성하는 스킬입니다.

## 참고 문서

- 상세 패턴 가이드: [skill-maker/docs/actor/02-kotlin-pekko-typed/README.md](../../../../skill-maker/docs/actor/02-kotlin-pekko-typed/README.md)
- 액터모델 개요: [skill-maker/docs/actor/00-actor-model-overview.md](../../../../skill-maker/docs/actor/00-actor-model-overview.md)
- 크로스 플랫폼 비교: [skill-maker/docs/actor/05-cross-platform-comparison.md](../../../../skill-maker/docs/actor/05-cross-platform-comparison.md)

## 환경

- **프레임워크**: Apache Pekko 1.1.x (Akka 2.6.x 오픈소스 포크)
- **언어**: Kotlin
- **빌드**: Gradle (Kotlin DSL)
- **웹 프레임워크**: Spring Boot (WebFlux / MVC)
- **라이선스**: Apache License 2.0
- **패키지**: `org.apache.pekko.*` (Akka의 `akka.*`에 대응)

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
- **Pool Router**: `Routers.pool(size) { Behavior }` + `withRoundRobinRouting()`
- **Group Router**: `ServiceKey` + `Receptionist` + `Routers.group(serviceKey)`
- 커스텀 스마트 라우터: 가중치 기반 분배

```kotlin
// Pool Router
val pool = Routers.pool(5) { HelloActor.create() }
    .withRoundRobinRouting()
val router = context.spawn(pool, "hello-pool")

// Group Router (클러스터 환경)
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

### 7. Stash 패턴
- 비동기 초기화 중 메시지를 버퍼링
- `Behaviors.withStash(capacity) { stash -> }` 래핑
- `stash.stash(message)` 저장, `stash.unstashAll(behavior)` 일괄 처리

### 8. PubSub (발행-구독)
- `Topic.create(Class, topicName)` 토픽 생성
- `Topic.publish(message)`, `Topic.subscribe(actorRef)`
- 채널 기반 토픽 관리 (`mutableMapOf<String, ActorRef<Topic.Command<T>>>()`)
- 클러스터 환경에서 노드 간 메시지 전파

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

$ARGUMENTS
