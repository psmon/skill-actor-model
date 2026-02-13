# Kotlin + Pekko Typed Actor 완전 가이드

## 목차

1. [개요 및 환경 설정](#1-개요-및-환경-설정)
2. [기본 Typed 액터](#2-기본-typed-액터)
3. [상태 전환 (State Switching / FSM)](#3-상태-전환-state-switching--fsm)
4. [벌크 프로세서 (FSM 패턴)](#4-벌크-프로세서-fsm-패턴)
5. [감독 전략 (Supervision)](#5-감독-전략-supervision)
6. [라우팅 (Routing)](#6-라우팅-routing)
7. [영속화 (Persistence - DurableState)](#7-영속화-persistence---durablestate)
8. [Stash 패턴](#8-stash-패턴)
9. [PubSub (발행-구독)](#9-pubsub-발행-구독)
10. [클러스터 (Cluster)](#10-클러스터-cluster)
11. [스트림 액터 (Stream Actor)](#11-스트림-액터-stream-actor)
12. [WebSocket 액터 시스템](#12-websocket-액터-시스템)
13. [SSE (Server-Sent Events)](#13-sse-server-sent-events)
14. [클러스터 확장 패턴](#14-클러스터-확장-패턴)
15. [Spring Boot 통합](#15-spring-boot-통합)
16. [Pekko vs Akka 차이점](#16-pekko-vs-akka-차이점)
17. [Classic vs Typed 비교](#17-classic-vs-typed-비교)

---

## 1. 개요 및 환경 설정

### Pekko란?

Apache Pekko는 Akka의 오픈소스 포크(fork)로, Akka가 BSL(Business Source License)로 전환된 이후 Apache 재단에서 관리하는 프로젝트이다. Akka Typed와 거의 동일한 API를 제공하며, 패키지명만 `akka.*` 에서 `org.apache.pekko.*` 로 변경되었다.

### 참조 프로젝트

| 프로젝트 | Akka/Pekko 버전 | Spring Boot | 특징 |
|---------|----------------|-------------|------|
| **KotlinBootLabs** | Akka 2.7.0 / Pekko 1.1.2 | 3.3.4 | 듀얼 빌드 (Akka + Pekko), WebSocket 상담 시스템 |
| **KotlinBootReactiveLabs** | Pekko 1.1.5 | 3.3.5 | WebFlux 기반, 클러스터/샤딩, SSE, Stash 패턴 |

### build.gradle.kts 의존성 설정

**Akka 기반 (KotlinBootLabs/build.gradle.kts)**

```kotlin
val scalaVersion = "2.13"
val akkaVersion = "2.7.0"
val akkaR2DBC = "1.1.2"

dependencies {
    // Actor System - Akka BOM
    implementation(platform("com.typesafe.akka:akka-bom_$scalaVersion:$akkaVersion"))

    // Typed Actor
    implementation("com.typesafe.akka:akka-actor-typed_$scalaVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-serialization-jackson_$scalaVersion:$akkaVersion")
    implementation("com.typesafe.akka:akka-stream_$scalaVersion:$akkaVersion")

    // Cluster
    implementation("com.typesafe.akka:akka-cluster-typed_$scalaVersion:$akkaVersion")

    // Persistence
    implementation("com.typesafe.akka:akka-persistence-typed_$scalaVersion:$akkaVersion")
    implementation("com.lightbend.akka:akka-persistence-r2dbc_$scalaVersion:$akkaR2DBC")

    // TestKit
    testImplementation("com.typesafe.akka:akka-actor-testkit-typed_$scalaVersion:$akkaVersion")
}
```

**Pekko 기반 (KotlinBootReactiveLabs/build.gradle.kts)**

```kotlin
val scala_version = "2.13"
val pekko_version = "1.1.5"

dependencies {
    // Actor System - Pekko BOM
    implementation(platform("org.apache.pekko:pekko-bom_$scala_version:$pekko_version"))

    // Typed Actor
    implementation("org.apache.pekko:pekko-actor-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-serialization-jackson_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-stream_$scala_version:$pekko_version")

    // Cluster
    implementation("org.apache.pekko:pekko-cluster-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_$scala_version:$pekko_version")

    // Persistence
    implementation("org.apache.pekko:pekko-persistence-typed_$scala_version:$pekko_version")
    implementation("org.apache.pekko:pekko-persistence-r2dbc_$scala_version:1.1.0-M1")

    // TestKit
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scala_version:$pekko_version")
}
```

> **핵심 차이**: 아티팩트 그룹만 `com.typesafe.akka` -> `org.apache.pekko`, 접두사만 `akka-` -> `pekko-` 로 변경된다. API는 동일하다.

---

## 2. 기본 Typed 액터

Typed 액터의 가장 기본적인 형태이다. Classic 액터와 달리, 액터가 수신할 수 있는 메시지 타입이 컴파일 타임에 결정된다.

### 핵심 구성 요소

- **sealed class**: 액터가 처리할 수 있는 명령(Command)의 집합을 정의
- **AbstractBehavior**: Typed 액터의 기본 클래스 (Java DSL)
- **companion object factory**: `create()` 팩토리 메서드를 통한 `Behavior` 생성
- **ActorRef\<T\>**: 타입이 지정된 액터 참조 - `T` 타입의 메시지만 전송 가능

### 코드 예제 (Akka - KotlinBootLabs)

```kotlin
// ===== 명령 정의 =====
/** HelloActor가 처리할 수 있는 명령들 */
sealed class HelloActorCommand
data class Hello(
    val message: String,
    val replyTo: ActorRef<HelloActorResponse>
) : HelloActorCommand()
data class GetHelloCount(
    val replyTo: ActorRef<HelloActorResponse>
) : HelloActorCommand()

/** HelloActor가 반환할 수 있는 응답들 */
sealed class HelloActorResponse
data class HelloResponse(val message: String) : HelloActorResponse()
data class HelloCountResponse(val count: Int) : HelloActorResponse()

// ===== 액터 구현 =====
class HelloActor private constructor(
    private val context: ActorContext<HelloActorCommand>,
) : AbstractBehavior<HelloActorCommand>(context) {

    companion object {
        fun create(): Behavior<HelloActorCommand> {
            return Behaviors.setup { context -> HelloActor(context) }
        }
    }

    private var helloCount: Int = 0

    override fun createReceive(): Receive<HelloActorCommand> {
        return newReceiveBuilder()
            .onMessage(Hello::class.java, this::onHello)
            .onMessage(GetHelloCount::class.java, this::onGetHelloCount)
            .build()
    }

    private fun onHello(command: Hello): Behavior<HelloActorCommand> {
        if (command.message == "Hello") {
            helloCount++
            context.log.info("[${context.self.path()}] Count incremented to $helloCount")
            command.replyTo.tell(HelloResponse("Kotlin"))
        } else if (command.message == "InvalidMessage") {
            throw RuntimeException("Invalid message received!")
        }
        return this
    }

    private fun onGetHelloCount(command: GetHelloCount): Behavior<HelloActorCommand> {
        command.replyTo.tell(HelloCountResponse(helloCount))
        return this
    }
}
```

### 코드 예제 (Pekko - KotlinBootReactiveLabs)

```kotlin
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.PostStop
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

sealed class HelloCommand
data class Hello(val message: String, val replyTo: ActorRef<HelloCommand>) : HelloCommand()
data class HelloResponse(val message: String) : HelloCommand()

class HelloActor private constructor(
    context: ActorContext<HelloCommand>,
) : AbstractBehavior<HelloCommand>(context) {

    companion object {
        fun create(): Behavior<HelloCommand> {
            return Behaviors.withTimers { timers ->
                Behaviors.setup { context -> HelloActor(context) }
            }
        }
    }

    override fun createReceive(): Receive<HelloCommand> {
        return newReceiveBuilder()
            .onMessage(Hello::class.java, this::onHello)
            .onMessage(HelloResponse::class.java, this::onHelloResponse)
            .onSignal(PostStop::class.java, this::onPostStop)
            .build()
    }

    private fun onPostStop(command: PostStop): Behavior<HelloCommand> {
        context.log.info("HelloActor onPostStop - ${context.self.path().name()}")
        return this
    }

    private fun onHello(command: Hello): Behavior<HelloCommand> {
        if (command.message == "Hello") {
            command.replyTo.tell(HelloResponse("Kotlin"))
        }
        return this
    }

    private fun onHelloResponse(command: HelloResponse): Behavior<HelloCommand> {
        context.log.info("Received HelloResponse: ${command.message}")
        return this
    }
}
```

### 설계 원칙 해설

| 요소 | 설명 |
|------|------|
| `sealed class` | Kotlin의 sealed class는 허용된 하위 타입만 존재할 수 있으므로, `when` 표현식에서 exhaustive check가 가능하다. 액터가 처리할 수 있는 명령의 범위를 컴파일 타임에 확정한다. |
| `private constructor` | 외부에서 직접 인스턴스화를 막고, 반드시 `companion object`의 `create()` 팩토리를 통해 `Behavior`를 생성하도록 강제한다. |
| `Behaviors.setup` | `ActorContext`를 제공하며, 액터 초기화 시점의 로직을 담당한다. |
| `ActorRef<T>` | 타입 안전한 액터 참조이다. `ActorRef<HelloActorResponse>`에는 `HelloActorResponse` 타입의 메시지만 전송할 수 있다. |
| `replyTo` 패턴 | Ask 패턴 지원을 위해 명령에 응답 대상 `ActorRef`를 포함한다. |
| `return this` | 동일한 Behavior를 유지한다. 상태 전환 시에는 다른 Behavior를 반환할 수 있다. |
| `onSignal` | `PostStop`, `PreRestart` 등 시스템 시그널을 처리한다. Typed에서는 별도 메서드 대신 `onSignal`로 등록한다. |

---

## 3. 상태 전환 (State Switching / FSM)

Typed 액터에서 상태 전환(FSM)은 액터 내부의 변수를 변경하거나, 완전히 다른 `Behavior`를 반환하는 방식으로 구현한다. Classic 액터의 `become/unbecome`에 대응하는 패턴이다.

### 방식 1: 내부 변수 기반 상태 전환

가장 간단한 방식으로, `enum`으로 상태를 정의하고 `when` 표현식으로 분기한다.

```kotlin
/** 상태 정의 */
enum class HelloState {
    HAPPY, ANGRY
}

/** 명령 정의 */
sealed class HelloStateActorCommand
data class Hello(val message: String, val replyTo: ActorRef<Any>) : HelloStateActorCommand()
data class GetHelloCount(val replyTo: ActorRef<Any>) : HelloStateActorCommand()
data class GetHelloTotalCount(val replyTo: ActorRef<Any>) : HelloStateActorCommand()
data class ChangeState(val newHelloState: HelloState) : HelloStateActorCommand()
data class HelloLimit(val message: String, val replyTo: ActorRef<Any>) : HelloStateActorCommand()
object ResetHelloCount : HelloStateActorCommand()
object StopResetTimer : HelloStateActorCommand()

/** 응답 정의 */
sealed class HelloStateActorResponse
data class HelloResponse(val message: String) : HelloStateActorResponse()
data class HelloCountResponse(val count: Int) : HelloStateActorResponse()

/** HelloStateActor 구현 */
class HelloStateActor private constructor(
    private val context: ActorContext<HelloStateActorCommand>,
    private val timers: TimerScheduler<HelloStateActorCommand>,
    private var helloState: HelloState
) : AbstractBehavior<HelloStateActorCommand>(context) {

    companion object {
        fun create(initialHelloState: HelloState): Behavior<HelloStateActorCommand> {
            return Behaviors.withTimers { timers ->
                Behaviors.setup { context ->
                    HelloStateActor(context, timers, initialHelloState)
                }
            }
        }
    }

    init {
        // 10초마다 주기적으로 카운트를 리셋하는 타이머
        timers.startTimerAtFixedRate(ResetHelloCount, Duration.ofSeconds(10))
    }

    private var helloCount: Int = 0
    private var helloTotalCount: Int = 0

    override fun createReceive(): Receive<HelloStateActorCommand> {
        return newReceiveBuilder()
            .onMessage(Hello::class.java, this::onHello)
            .onMessage(HelloLimit::class.java, this::onHelloLimit)
            .onMessage(GetHelloCount::class.java, this::onGetHelloCount)
            .onMessage(ChangeState::class.java, this::onChangeState)
            .onMessage(ResetHelloCount::class.java, this::onResetHelloCount)
            .onMessage(StopResetTimer::class.java) {
                timers.cancel(ResetHelloCount)
                Behaviors.same()
            }
            .build()
    }

    private fun onHello(command: Hello): Behavior<HelloStateActorCommand> {
        when (helloState) {
            HelloState.HAPPY -> {
                if (command.message == "Hello") {
                    helloCount++
                    helloTotalCount++
                    command.replyTo.tell(HelloResponse("Kotlin"))
                }
            }
            HelloState.ANGRY -> {
                command.replyTo.tell(HelloResponse("Don't talk to me!"))
            }
        }
        return this
    }

    private fun onChangeState(command: ChangeState): Behavior<HelloStateActorCommand> {
        helloState = command.newHelloState
        return this
    }

    private fun onResetHelloCount(command: ResetHelloCount): Behavior<HelloStateActorCommand> {
        helloCount = 0
        return this
    }
}
```

### 타이머 (TimerScheduler) 핵심 사항

```kotlin
// Behaviors.withTimers로 TimerScheduler를 주입받는다
Behaviors.withTimers { timers ->
    Behaviors.setup { context -> MyActor(context, timers) }
}

// 주기적 타이머: 일정 간격으로 자기 자신에게 메시지를 보낸다
timers.startTimerAtFixedRate(ResetHelloCount, Duration.ofSeconds(10))

// 타이머 취소
timers.cancel(ResetHelloCount)
```

- `Behaviors.withTimers`는 `Behaviors.setup` 바깥에 위치해야 한다
- 타이머 메시지는 `sealed class`의 멤버여야 한다 (`object`로 정의하면 싱글톤 키로 사용 가능)
- Classic 액터의 `context.system.scheduler()` 직접 사용 대신, 타입 안전한 `TimerScheduler`를 사용한다

### Streams Throttle 통합

액터 내부에서 Pekko Streams의 throttle을 사용하여 처리량을 제한할 수 있다.

```kotlin
private val materializer = Materializer.createMaterializer(context.system)

// 초당 3개로 처리량 제한하는 Source Queue
private val helloLimitSource = Source.queue<HelloLimit>(100, OverflowStrategy.backpressure())
    .throttle(3, Duration.ofSeconds(1))
    .to(Sink.foreach { cmd ->
        context.self.tell(Hello(cmd.message, cmd.replyTo))
    })
    .run(materializer)

private fun onHelloLimit(command: HelloLimit): Behavior<HelloStateActorCommand> {
    helloLimitSource.offer(command).thenAccept { result ->
        when (result) {
            is QueueOfferResult.`Enqueued$` -> context.log.info("Command enqueued")
            is QueueOfferResult.`Dropped$` -> context.log.error("Command dropped")
            is QueueOfferResult.Failure -> context.log.error("Failed", result.cause())
            is QueueOfferResult.`QueueClosed$` -> context.log.error("Queue closed")
        }
    }
    return this
}
```

이 패턴은 외부 API 호출이나 DB 쓰기처럼 처리량을 제한해야 하는 경우에 유용하다. 액터 자체의 mailbox는 무제한이지만, `Source.queue`에 backpressure를 걸어 실질적인 rate limiting을 구현한다.

---

## 4. 벌크 프로세서 (FSM 패턴)

Behavior 자체를 교체하는 방식의 FSM 패턴이다. `idle()`과 `active()` 두 가지 Behavior를 가지며, 조건에 따라 상태를 전환한다. Classic 액터의 `context.become()`에 대응한다.

```kotlin
sealed class BulkProcessorCommand
data class DataEvent(val data: Any, val replyTo: ActorRef<Any>) : BulkProcessorCommand()
object Flush : BulkProcessorCommand()
private object FlushTimeout : BulkProcessorCommand() // 내부 전용 타임아웃 메시지

sealed class BulkProcessorResponse
data class BulkTaskCompleted(val message: String) : BulkProcessorResponse()

class BulkProcessor private constructor(
    context: ActorContext<BulkProcessorCommand>,
    private val buffer: MutableList<Any> = mutableListOf()
) : AbstractBehavior<BulkProcessorCommand>(context) {

    companion object {
        fun create(): Behavior<BulkProcessorCommand> {
            return Behaviors.setup { context -> BulkProcessor(context) }
        }
    }

    // 초기 상태는 idle
    override fun createReceive(): Receive<BulkProcessorCommand> {
        return idle()
    }

    // ===== IDLE 상태 =====
    // 데이터를 받으면 버퍼에 추가하고 active로 전환
    private fun idle(): Receive<BulkProcessorCommand> {
        return newReceiveBuilder()
            .onMessage(DataEvent::class.java) { event ->
                if (event.data == "testend") {
                    event.replyTo.tell(BulkTaskCompleted("Bulk task completed"))
                    flushBuffer()
                    idle()  // idle 상태 유지
                } else {
                    buffer.add(event.data)
                    startFlushTimer()
                    active()  // active 상태로 전환!
                }
            }
            .onMessage(Flush::class.java) {
                Behaviors.same()  // idle에서는 Flush 무시
            }
            .build()
    }

    // ===== ACTIVE 상태 =====
    // 버퍼가 가득 차거나 타임아웃 시 flush 후 idle로 복귀
    private fun active(): Receive<BulkProcessorCommand> {
        return newReceiveBuilder()
            .onMessage(DataEvent::class.java) { event ->
                if (event.data == "testend") {
                    event.replyTo.tell(BulkTaskCompleted("Bulk task completed"))
                    flushBuffer()
                    idle()  // idle로 복귀
                } else {
                    buffer.add(event.data)
                    if (buffer.size >= 100) {
                        flushBuffer()
                        idle()  // 임계치 도달 -> flush 후 idle로
                    } else {
                        Behaviors.same()  // active 유지
                    }
                }
            }
            .onMessage(Flush::class.java) {
                flushBuffer()
                idle()  // 수동 flush -> idle로
            }
            .onMessageEquals(FlushTimeout) {
                flushBuffer()
                idle()  // 타임아웃 -> 자동 flush 후 idle로
            }
            .build()
    }

    private fun flushBuffer() {
        context.log.info("Processing ${buffer.size} events.")
        buffer.clear()
        stopFlushTimer()
    }

    // 타이머 관리
    private var flushTimer: Cancellable? = null

    private fun startFlushTimer() {
        flushTimer = context.scheduleOnce(
            Duration.ofSeconds(3),
            context.self,
            FlushTimeout
        )
    }

    private fun stopFlushTimer() {
        flushTimer?.cancel()
        flushTimer = null
    }
}
```

### FSM 상태 전이 다이어그램

```
                DataEvent
    [IDLE] ──────────────> [ACTIVE]
      ^                       |
      |   Flush / Timeout /   |
      |   Buffer Full         |
      └───────────────────────┘
```

### 핵심 포인트

| 패턴 | 설명 |
|------|------|
| `idle()` / `active()` | 각각 `Receive<T>`를 반환하는 메서드로, 상태마다 다른 메시지 핸들러를 정의한다 |
| `onMessageEquals(FlushTimeout)` | `private object`에 대한 정확한 동등성 비교. 타이머 메시지 처리에 적합하다 |
| `context.scheduleOnce()` | 일회성 스케줄링. `TimerScheduler`와 달리 `Cancellable`을 직접 관리한다 |
| `Behaviors.same()` | 현재 Behavior를 유지한다. `return this`와 유사하지만 Functional 스타일에서 사용한다 |

---

## 5. 감독 전략 (Supervision)

Typed 액터에서 감독(Supervision)은 자식 액터 생성 시 `Behaviors.supervise()`로 래핑하여 설정한다. Classic 액터의 `supervisorStrategy` 오버라이드와 달리, 각 자식 액터별로 개별 전략을 지정할 수 있다.

### SupervisorActor 구현

```kotlin
/** 명령 정의 */
sealed class SupervisorCommand
data class CreateChild(val name: String) : SupervisorCommand()
data class SendHello(
    val childName: String,
    val message: String,
    val replyTo: ActorRef<HelloActorResponse>
) : SupervisorCommand()
data class GetChildCount(val replyTo: ActorRef<Int>) : SupervisorCommand()
data class TerminateChild(val name: String) : SupervisorCommand()

class SupervisorActor private constructor(
    context: ActorContext<SupervisorCommand>
) : AbstractBehavior<SupervisorCommand>(context) {

    companion object {
        fun create(): Behavior<SupervisorCommand> {
            return Behaviors.setup { context -> SupervisorActor(context) }
        }
    }

    private val children = mutableMapOf<String, ActorRef<HelloActorCommand>>()

    override fun createReceive(): Receive<SupervisorCommand> {
        return newReceiveBuilder()
            .onMessage(CreateChild::class.java, this::onCreateChild)
            .onMessage(SendHello::class.java, this::onSendHello)
            .onMessage(GetChildCount::class.java, this::onGetChildCount)
            .onMessage(TerminateChild::class.java, this::onTerminateChild)
            .onSignal(Terminated::class.java, this::onChildTerminated)
            .build()
    }

    private fun onCreateChild(command: CreateChild): Behavior<SupervisorCommand> {
        val childActor = context.spawn(
            // 핵심: Behaviors.supervise()로 감독 전략 래핑
            Behaviors.supervise(HelloActor.create())
                .onFailure(SupervisorStrategy.restart()),
            command.name
        )
        // context.watch()로 종료 시그널 감시
        context.watch(childActor)
        children[command.name] = childActor
        return this
    }

    private fun onSendHello(command: SendHello): Behavior<SupervisorCommand> {
        val child = children[command.childName]
        if (child != null) {
            child.tell(Hello(command.message, command.replyTo))
        } else {
            context.log.warn("Child actor [${command.childName}] does not exist.")
        }
        return this
    }

    private fun onGetChildCount(command: GetChildCount): Behavior<SupervisorCommand> {
        command.replyTo.tell(children.size)
        return this
    }

    // Terminated 시그널 처리 - 자식 액터 종료 감지
    private fun onChildTerminated(terminated: Terminated): Behavior<SupervisorCommand> {
        val childName = terminated.ref.path().name()
        children.remove(childName)
        context.log.info("Child actor terminated: $childName")
        return this
    }

    private fun onTerminateChild(command: TerminateChild): Behavior<SupervisorCommand> {
        val child = children[command.name]
        if (child != null) {
            context.stop(child)
        }
        return this
    }
}
```

### 감독 전략 종류

```kotlin
// 재시작: 액터의 상태를 초기화하고 재시작
Behaviors.supervise(HelloActor.create())
    .onFailure(SupervisorStrategy.restart())

// 재개: 예외를 무시하고 같은 상태에서 계속 처리
Behaviors.supervise(HelloActor.create())
    .onFailure(SupervisorStrategy.resume())

// 중단: 액터를 종료
Behaviors.supervise(HelloActor.create())
    .onFailure(SupervisorStrategy.stop())

// Backoff 재시작: 지수적 지연을 두고 재시작 (프로덕션 권장)
Behaviors.supervise(CounterActor.create("singleId"))
    .onFailure(SupervisorStrategy.restartWithBackoff(
        Duration.ofSeconds(1),    // minBackoff
        Duration.ofSeconds(2),    // maxBackoff
        0.2                       // randomFactor
    ))

// 로깅 활성화
Behaviors.supervise(HelloStateStoreActor.create(persistId, durableRepository))
    .onFailure(SupervisorStrategy.restart().withLoggingEnabled(true))
```

### Classic vs Typed 감독 비교

| 항목 | Classic | Typed |
|------|---------|-------|
| 전략 위치 | 부모 액터에서 `supervisorStrategy` 오버라이드 | 자식 `spawn` 시 `Behaviors.supervise()` 래핑 |
| 적용 범위 | 모든 자식에 일괄 적용 | 자식별 개별 전략 지정 가능 |
| 종료 감시 | `context.watch(ref)` + `Terminated` 메시지 | `context.watch(ref)` + `.onSignal(Terminated::class.java)` |
| 에스컬레이션 | `SupervisorStrategy.Escalate` | Typed에서는 `onFailure`에서 처리 불가 시 자동 에스컬레이션 |

### PreRestart 시그널 활용 (고급)

```kotlin
// 자식 액터에서 재시작 시그널을 받아 부모에게 알리는 패턴
private fun onPreRestart(signal: PreRestart): Behavior<HelloStateStoreActorCommand> {
    context.log.info("Actor restart detected")
    val parentPath = context.self.path().parent()
    val parentRef: ActorRef<SupervisorCommand> =
        ActorRefResolver.get(context.system).resolveActorRef(parentPath.toString())
    parentRef.tell(RestartChild(context.self.path().name(), context.self))
    return this
}
```

---

## 6. 라우팅 (Routing)

Typed 액터에서 라우팅은 `Routers.pool()` (Pool Router) 또는 `Routers.group()` (Group Router)로 구현한다. Classic 액터의 `Router`/`RoutingLogic`과 대응된다.

### Pool Router

동일한 Behavior의 액터를 N개 생성하여 부하를 분산한다.

```kotlin
sealed class HelloManagerCommand
data class DistributedHelloMessage(
    val message: String,
    val replyTo: ActorRef<HelloActorResponse>
) : HelloManagerCommand()

class HelloManagerActor private constructor(
    context: ActorContext<HelloManagerCommand>,
    private var router: PoolRouter<HelloActorCommand>,
    private val routerRef: ActorRef<HelloActorCommand>
) : AbstractBehavior<HelloManagerCommand>(context) {

    companion object {
        fun create(): Behavior<HelloManagerCommand> {
            return Behaviors.setup { context ->
                // 5개의 HelloActor 풀 생성 + 감독 전략 + Round Robin 라우팅
                val router = Routers.pool(5,
                    Behaviors.supervise(HelloActor.create())
                        .onFailure(SupervisorStrategy.restart()))
                    .withRoundRobinRouting()

                val routerRef = context.spawn(router, "hello-actor-pool")
                HelloManagerActor(context, router, routerRef)
            }
        }
    }

    override fun createReceive(): Receive<HelloManagerCommand> {
        return newReceiveBuilder()
            .onMessage(DistributedHelloMessage::class.java, this::onSendHelloMessage)
            .build()
    }

    private fun onSendHelloMessage(command: DistributedHelloMessage): Behavior<HelloManagerCommand> {
        routerRef.tell(Hello(command.message, command.replyTo))
        return this
    }
}
```

### Pool Router 라우팅 전략

```kotlin
val router = Routers.pool(poolSize, behavior)
    .withRoundRobinRouting()        // 순차적 분배
    // .withRandomRouting()          // 랜덤 분배
    // .withConsistentHashingRouting(0, { cmd -> cmd.hashCode() })
    // .withBroadcastPredicate({ cmd -> cmd is SomeCommand })
```

### Custom Smart Router (비즈니스 로직 기반)

실무에서는 기본 라우팅 전략으로 충분하지 않은 경우가 많다. 스킬 기반 상담원 분배 같은 복잡한 로직은 별도의 라우터 클래스를 구현한다.

```kotlin
data class CounselingRequestInfo(
    val skillCode1: Int,
    val skillCode2: Int,
    val skillCode3: Int
) {
    fun generateHashCode(): String = "skill-$skillCode1-$skillCode2-$skillCode3"
}

data class CounselingGroup(
    val id: String = UUID.randomUUID().toString(),
    val hashCodes: Array<String>,
    var availableCounselors: List<ActorRef<CounselorCommand>>,
    val lastAssignmentTime: Long,
    var availableSlots: Int = 100
) {
    private var lastAssignedCounselorIndex = 0

    // 그룹 내 Round Robin 분배
    fun findNextAvailableCounselor(): ActorRef<CounselorCommand>? {
        if (availableCounselors.isEmpty()) return null
        val counselor = availableCounselors[lastAssignedCounselorIndex]
        lastAssignedCounselorIndex = (lastAssignedCounselorIndex + 1) % availableCounselors.size
        return counselor
    }

    fun decreaseAvailableSlots() {
        if (availableSlots > 0) availableSlots -= 1
    }
}

data class CounselingRouter(
    val counselingGroups: List<CounselingGroup>
) {
    // 가중치 기반 우선순위 그룹 선택
    fun findHighestPriorityGroup(hashCode: String): CounselingGroup? {
        return counselingGroups
            .filter { it.availableSlots > 0 }
            .maxByOrNull { calculateWeight(it) }
    }

    private fun calculateWeight(group: CounselingGroup): Int {
        val skillWeight = (group.hashCodes.size) * 1000
        val lastAssignmentWeight = (System.currentTimeMillis() - group.lastAssignmentTime).toInt()
        val availableSlotsWeight = group.availableSlots * 100
        return skillWeight + lastAssignmentWeight + availableSlotsWeight
    }
}
```

### Classic vs Typed 라우팅 비교

| 항목 | Classic | Typed |
|------|---------|-------|
| Pool 생성 | `Props.withRouter(RoundRobinPool(5))` | `Routers.pool(5, behavior)` |
| Group 생성 | `Props.withRouter(RoundRobinGroup(paths))` | `Routers.group(serviceKey)` |
| 감독 결합 | 라우터 설정에 포함 | `Behaviors.supervise()` 별도 래핑 |
| 메시지 타입 | `Any` | `T` (타입 안전) |
| 스케일링 | 설정으로 조정 | 코드에서 `poolSize` 파라미터로 |

---

## 7. 영속화 (Persistence - DurableState)

`DurableStateBehavior`는 이벤트 소싱이 아닌 **현재 상태 전체를 저장**하는 방식의 영속화이다. R2DBC/JDBC를 통해 PostgreSQL, MySQL 등의 DB에 상태를 저장한다.

### DurableStateBehavior 구현

```kotlin
enum class State { HAPPY, ANGRY }

// 영속화할 상태 - data class + PersistenceSerializable
data class HelloState @JsonCreator constructor(
    @JsonProperty("state") val state: State,
    @JsonProperty("helloCount") val helloCount: Int,
    @JsonProperty("helloTotalCount") val helloTotalCount: Int
) : PersitenceSerializable

// 명령 정의
sealed class HelloPersistentStateActorCommand : PersitenceSerializable
data class HelloPersistentDurable(
    val message: String,
    val replyTo: ActorRef<Any>
) : HelloPersistentStateActorCommand()
data class GetHelloCountPersistentDurable(
    val replyTo: ActorRef<Any>
) : HelloPersistentStateActorCommand()
data class ChangeState(val state: State) : HelloPersistentStateActorCommand()
object ResetHelloCount : HelloPersistentStateActorCommand()

// 응답 정의
sealed class HelloPersistentStateActorResponse : PersitenceSerializable
data class HelloResponse(val message: String) : HelloPersistentStateActorResponse()
data class HelloCountResponse(val count: Number) : HelloPersistentStateActorResponse()

// DurableStateBehavior 구현
class HelloPersistentDurableStateActor private constructor(
    private val context: ActorContext<HelloPersistentStateActorCommand>,
    private val persistenceId: PersistenceId
) : DurableStateBehavior<HelloPersistentStateActorCommand, HelloState>(persistenceId) {

    companion object {
        fun create(persistenceId: PersistenceId): Behavior<HelloPersistentStateActorCommand> {
            return Behaviors.setup { context ->
                HelloPersistentDurableStateActor(context, persistenceId)
            }
        }
    }

    // 태그 (CQRS read-side projection에 사용)
    override fun tag(): String = "tag1"

    // 초기 상태
    override fun emptyState(): HelloState = HelloState(State.HAPPY, 0, 0)

    // 명령 핸들러 - 상태에 따라 다른 처리
    override fun commandHandler(): CommandHandler<HelloPersistentStateActorCommand, HelloState> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(HelloPersistentDurable::class.java) { state, command ->
                onHello(state, command)
            }
            .onCommand(GetHelloCountPersistentDurable::class.java) { state, command ->
                onGetHelloCount(state, command)
            }
            .onCommand(ChangeState::class.java) { state, command ->
                onChangeState(state, command)
            }
            .onCommand(ResetHelloCount::class.java) { state, _ ->
                onResetHelloCount(state)
            }
            .build()
    }

    private fun onHello(state: HelloState, command: HelloPersistentDurable): Effect<HelloState> {
        return when (state.state) {
            State.HAPPY -> {
                if (command.message == "Hello") {
                    val newState = state.copy(
                        helloCount = state.helloCount + 1,
                        helloTotalCount = state.helloTotalCount + 1,
                    )
                    // 상태를 영속화하고, 완료 후 응답을 보낸다
                    Effect().persist(newState).thenRun {
                        command.replyTo.tell(HelloResponse("Kotlin"))
                    }
                } else {
                    Effect().none()
                }
            }
            State.ANGRY -> {
                command.replyTo.tell(HelloResponse("Don't talk to me!"))
                Effect().none()
            }
        }
    }

    private fun onGetHelloCount(
        state: HelloState,
        command: GetHelloCountPersistentDurable
    ): Effect<HelloState> {
        command.replyTo.tell(HelloCountResponse(state.helloCount))
        return Effect().none()
    }

    private fun onChangeState(state: HelloState, command: ChangeState): Effect<HelloState> {
        return Effect().persist(state.copy(state = command.state))
    }

    private fun onResetHelloCount(state: HelloState): Effect<HelloState> {
        return Effect().persist(state.copy(helloCount = 0))
    }
}
```

### Effect API

| 메서드 | 설명 |
|--------|------|
| `Effect().persist(newState)` | 새 상태를 DB에 저장한다 |
| `Effect().none()` | 상태 변경 없이 명령만 처리한다 |
| `.thenRun { ... }` | 영속화 완료 후 실행할 콜백 (응답 전송 등) |
| `.thenReply(replyTo) { state -> response }` | 영속화 후 응답 전송의 축약형 |

### Jackson 직렬화 설정

Pekko 클러스터에서 원격 전송을 위해 `PersitenceSerializable` 마커 인터페이스와 `@JsonCreator`/`@JsonProperty`를 사용한다.

```kotlin
// 마커 인터페이스
interface PersitenceSerializable

// data class에 Jackson 어노테이션 적용
data class HelloState @JsonCreator constructor(
    @JsonProperty("state") val state: State,
    @JsonProperty("helloCount") val helloCount: Int,
    @JsonProperty("helloTotalCount") val helloTotalCount: Int
) : PersitenceSerializable
```

### application.conf (R2DBC 백엔드)

```hocon
pekko.persistence.state.plugin = "pekko.persistence.r2dbc.state"
pekko.persistence.r2dbc {
  connection-factory {
    driver = "postgres"
    host = "localhost"
    port = 5432
    database = "pekko"
    user = "pekko"
    password = "pekko"
  }
}
```

---

## 8. Stash 패턴

Stash 패턴은 액터가 아직 준비되지 않은 상태(예: DB 초기화 중)에서 도착한 메시지를 임시 버퍼에 저장했다가, 준비가 완료되면 한꺼번에 처리하는 패턴이다. Classic 액터의 `Stash` trait에 대응한다.

### 구현 흐름

```
[시작] --> DB에서 상태 로드 (비동기)
  |
  |-- 로딩 중 다른 메시지 도착 --> stash.stash(message) (임시 보관)
  |
  |-- DB 로드 완료 (InitialState) --> buffer.unstashAll(active(state))
  |
[active] --> 정상 메시지 처리
  |
  |-- 저장 요청 --> DB 저장 (비동기) --> saving 상태로 전환
  |    |-- 저장 중 다른 메시지 --> stash
  |    |-- 저장 완료 --> buffer.unstashAll(active(state))
```

### 코드 예제

```kotlin
sealed class HelloStashCommand
data class InitialState(
    val happyState: HappyStashState,
    val helloCount: Int,
    val helloTotalCount: Int
) : HelloStashCommand()
data class DbError(val error: RuntimeException) : HelloStashCommand()
data class SavaState(val state: HelloStashState, val replyTo: ActorRef<Done>) : HelloStashCommand()
data class GetState(val replyTo: ActorRef<HelloStashState>) : HelloStashCommand()
data object SaveSuccess : HelloStashCommand()

class HelloStashActor private constructor(
    private val context: ActorContext<HelloStashCommand>,
    private val persistenceId: String,
    private val durableRepository: DurableRepository,
    private val buffer: StashBuffer<HelloStashCommand>
) {
    companion object {
        fun create(
            persistenceId: String,
            durableRepository: DurableRepository,
            buffer: StashBuffer<HelloStashCommand>
        ): Behavior<HelloStashCommand> {
            // Behaviors.withStash로 StashBuffer를 생성
            return Behaviors.withStash(100) {
                Behaviors.setup { context ->
                    // pipeToSelf: 비동기 작업 결과를 자기 자신에게 메시지로 전달
                    context.pipeToSelf(
                        durableRepository.findByIdEx<HelloStashState>(
                            persistenceId, 1L
                        ).toFuture(),
                        { value, cause ->
                            if (cause == null) {
                                if (value == null) {
                                    InitialState(HappyStashState.HAPPY, 0, 0)
                                } else {
                                    InitialState(
                                        value.happyState,
                                        value.helloCount,
                                        value.helloTotalCount
                                    )
                                }
                            } else {
                                DbError(RuntimeException(cause))
                            }
                        }
                    )
                    HelloStashActor(
                        context, persistenceId, durableRepository, buffer
                    ).start()
                }
            }
        }
    }

    // ===== 시작 상태: DB 초기화 대기 =====
    private fun start(): Behavior<HelloStashCommand> {
        return Behaviors.receive(HelloStashCommand::class.java)
            .onMessage(InitialState::class.java, this::onInitialState)
            .onMessage(DbError::class.java, this::onDBError)
            // 나머지 모든 메시지는 stash!
            .onMessage(HelloStashCommand::class.java, this::stashOtherCommand)
            .build()
    }

    private fun onInitialState(command: InitialState): Behavior<HelloStashCommand> {
        val state = HelloStashState(
            command.happyState, command.helloCount, command.helloTotalCount
        )
        // 초기화 완료 -> stash된 모든 메시지를 active 상태에서 재처리
        return buffer.unstashAll(active(state))
    }

    private fun stashOtherCommand(command: HelloStashCommand): Behavior<HelloStashCommand> {
        buffer.stash(command)
        return Behaviors.same()
    }

    // ===== 활성 상태: 정상 메시지 처리 =====
    private fun active(state: HelloStashState): Behavior<HelloStashCommand> {
        return Behaviors.receive(HelloStashCommand::class.java)
            .onMessage(GetState::class.java) { message ->
                message.replyTo.tell(state)
                Behaviors.same()
            }
            .onMessage(SavaState::class.java, this::onSaveState)
            .build()
    }

    // ===== 저장 상태: DB 저장 중 =====
    private fun saving(
        state: HelloStashState,
        replyTo: ActorRef<Done>
    ): Behavior<HelloStashCommand> {
        return Behaviors.receive(HelloStashCommand::class.java)
            .onMessage(SaveSuccess::class.java) { _ ->
                replyTo.tell(Done.getInstance())
                buffer.unstashAll(active(state))
            }
            .onMessage(DbError::class.java, this::onDBError)
            .onMessage(HelloStashCommand::class.java, this::stashOtherCommand)
            .build()
    }

    private fun onSaveState(message: SavaState): Behavior<HelloStashCommand> {
        context.pipeToSelf(
            durableRepository.createOrUpdateDurableStateEx<HelloStashState>(
                persistenceId, 1L, message.state
            ).toFuture(),
            { _, cause ->
                if (cause == null) SaveSuccess else DbError(RuntimeException(cause))
            }
        )
        return saving(message.state, message.replyTo)
    }

    private fun onDBError(command: DbError): Behavior<HelloStashCommand> {
        throw command.error
    }
}
```

### 핵심 API

| API | 설명 |
|-----|------|
| `Behaviors.withStash(capacity) { buffer -> ... }` | StashBuffer 생성. capacity는 최대 보관 메시지 수 |
| `buffer.stash(message)` | 메시지를 임시 보관 |
| `buffer.unstashAll(behavior)` | 보관된 모든 메시지를 지정된 Behavior에서 재처리 |
| `context.pipeToSelf(future, mapper)` | 비동기 작업 결과를 자기 자신에게 메시지로 전달 |

### Stash 패턴 사용 시기

- DB 초기화가 필요한 액터에서, 초기화 완료 전에 도착한 요청을 잃지 않으려 할 때
- 외부 리소스(API, 파일 등) 로딩이 비동기인 경우
- 저장 중 추가 요청이 올 수 있는 경우 (낙관적 동시성 제어)

---

## 9. PubSub (발행-구독)

`Topic` 액터를 통한 발행-구독 패턴이다. 같은 ActorSystem 내에서뿐 아니라, 클러스터 환경에서도 노드 간 메시지 브로드캐스트가 가능하다.

### 구현

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
        // 채널에 해당하는 Topic 액터를 가져오거나 생성
        val topic = topics.getOrPut(command.channel) {
            context.spawn(
                Topic.create(String::class.java, command.channel),
                command.channel
            )
        }
        // 메시지 발행
        topic.tell(Topic.publish(command.message))
        return this
    }

    private fun onSubscribe(command: Subscribe): Behavior<PubSubCommand> {
        val topic = topics.getOrPut(command.channel) {
            context.spawn(
                Topic.create(String::class.java, command.channel),
                command.channel
            )
        }
        // 구독자 등록
        topic.tell(Topic.subscribe(command.subscriber))
        return this
    }
}
```

### Topic API

```kotlin
// Topic 생성 (메시지 타입, 토픽 이름)
val topic = context.spawn(
    Topic.create(String::class.java, "my-topic"),
    "my-topic"
)

// 구독
topic.tell(Topic.subscribe(subscriberActorRef))

// 발행
topic.tell(Topic.publish("hello everyone"))

// 구독 해제
topic.tell(Topic.unsubscribe(subscriberActorRef))
```

### 클러스터 환경에서의 PubSub

클러스터 모드에서 `Topic`은 자동으로 다른 노드의 동일 이름 Topic과 연결된다. 추가 설정 없이 클러스터 전체에 메시지가 전파된다.

```kotlin
// 클러스터 Singleton을 통한 Topic 관리 (고급)
val singleton = ClusterSingleton.get(context.system)
val topicProxy: ActorRef<Topic.Command<UserEventCommand>> = singleton.init(
    SingletonActor.of(
        Topic.create(UserEventCommand::class.java, "brand-topic"),
        "brand-topic"
    )
)
topicProxy.tell(Topic.subscribe(userEventActor))
topicProxy.tell(Topic.publish(AddEvent("new event")))
```

---

## 10. 클러스터 (Cluster)

### ServiceKey + Receptionist (서비스 발견)

클러스터 환경에서 액터를 발견하기 위해 `ServiceKey`와 `Receptionist`를 사용한다. Classic 액터의 `ClusterClient`에 대응한다.

```kotlin
sealed class HelloActorACommand : PersitenceSerializable
data class HelloA(
    val message: String,
    val replyTo: ActorRef<HelloActorAResponse>
) : HelloActorACommand()

class ClusterHelloActorA private constructor(
    private val context: ActorContext<HelloActorACommand>,
) : AbstractBehavior<HelloActorACommand>(context) {

    companion object {
        // 서비스 키 정의 - 클러스터 전체에서 이 키로 액터를 찾을 수 있다
        var ClusterHelloActorAKey: ServiceKey<HelloActorACommand> =
            ServiceKey.create(HelloActorACommand::class.java, "ClusterHelloActorA")

        fun create(): Behavior<HelloActorACommand> {
            return Behaviors.setup { context -> ClusterHelloActorA(context) }
        }
    }

    init {
        // 1. Receptionist에 자신을 등록
        context.system.receptionist().tell(
            Receptionist.register(ClusterHelloActorAKey, context.self)
        )

        // 2. GroupRouter 생성 - ServiceKey 기반으로 클러스터 전체에서 라우팅
        val group: GroupRouter<HelloActorACommand> =
            Routers.group(ClusterHelloActorAKey)
        val router: ActorRef<HelloActorACommand> =
            context.spawn(group, "worker-group")
    }

    override fun createReceive(): Receive<HelloActorACommand> {
        return newReceiveBuilder()
            .onMessage(HelloA::class.java, this::onHello)
            .build()
    }

    private fun onHello(command: HelloA): Behavior<HelloActorACommand> {
        if (command.message == "Hello") {
            helloCount++
            command.replyTo.tell(HelloAResponse("Kotlin"))
        }
        return this
    }

    private var helloCount: Int = 0
}
```

### DistributedPubSub (크로스 노드 메시징)

역할(role) 기반으로 특정 노드 그룹에만 메시지를 보내는 패턴이다.

```kotlin
init {
    // Classic API의 DistributedPubSub을 Typed에서 사용
    val mediator = DistributedPubSub.get(context.system).mediator()

    // "roleA" 토픽 구독 - roleA 역할의 모든 노드에서 메시지를 받음
    mediator.tell(
        DistributedPubSubMediator.Subscribe(
            "roleA",
            Adapter.toClassic(context.self)  // Typed -> Classic 어댑터
        ),
        null
    )
}
```

### GroupRouter vs PoolRouter

| 항목 | PoolRouter | GroupRouter |
|------|-----------|-------------|
| 액터 생성 | 라우터가 직접 생성 | 외부에서 등록 |
| 범위 | 로컬 | 클러스터 전체 |
| 확장 | 고정 크기 | ServiceKey 등록에 따라 동적 |
| 사용처 | 로컬 병렬 처리 | 클러스터 부하 분산 |

### 클러스터 역할 확인

```kotlin
private fun initializeClusterRoles() {
    val selfMember = Cluster.get(mainStage).selfMember()
    if (selfMember.hasRole("seed")) logger.info("My Role: Seed")
    if (selfMember.hasRole("helloA")) logger.info("My Role: HelloA")
    if (selfMember.hasRole("shard")) logger.info("My Role: Shard")
}
```

---

## 11. 스트림 액터 (Stream Actor)

액터 내부에서 Pekko Streams의 `Source`, `Flow`, `Sink`를 관리하며, 런타임에 스트림 파이프라인을 동적으로 변경하는 패턴이다.

```kotlin
sealed class GraphCommand
data class ProcessNumber(
    val number: Int,
    val replyTo: ActorRef<GraphCommand>
) : GraphCommand()
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
                // 초기 연산: +1
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
            .onSignal(PostStop::class.java, this::onPostStop)
            .build()
    }

    private fun onProcessNumber(command: ProcessNumber): Behavior<GraphCommand> {
        // 스트림 파이프라인 실행
        Source.single(command.number)
            .via(operation)                                    // 현재 Flow 적용
            .buffer(1000, OverflowStrategy.dropHead())
            .throttle(10, Duration.ofSeconds(1))               // 처리량 제한
            .runWith(
                Sink.foreach { result ->
                    command.replyTo.tell(ProcessedNumber(result))
                },
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

    private fun onPostStop(command: PostStop): Behavior<GraphCommand> {
        context.log.info("GraphActor stopped")
        return this
    }
}
```

### Stream Actor 패턴의 장점

- **동적 파이프라인**: 런타임에 `Flow`를 교체하여 처리 로직을 변경할 수 있다
- **Backpressure**: Pekko Streams의 backpressure가 자동으로 적용된다
- **Throttling**: `throttle()`로 처리량을 제어한다
- **Buffer 관리**: `buffer()` + `OverflowStrategy`로 메모리를 관리한다

---

## 12. WebSocket 액터 시스템

실시간 상담 시스템을 위한 액터 계층 구조이다. 각 레이어가 독립적인 액터로 구현되어 있어 확장성과 격리성이 뛰어나다.

### 액터 계층 구조

```
MainStageActor (최상위 가디언)
├── UserSessionManagerActor (세션 관리)
│   ├── PersonalRoomActor-user1 (개인 채팅방)
│   ├── PersonalRoomActor-user2
│   └── ...
└── SupervisorChannelActor (채널 감독)
    ├── CounselorManagerActor-channel1 (상담원 관리)
    │   ├── CounselorActor-agent1 (상담원)
    │   ├── CounselorActor-agent2
    │   ├── CounselorRoomActor-room1 (상담방)
    │   └── CounselorRoomActor-room2
    └── CounselorManagerActor-channel2
```

### MainStageActor (최상위 가디언)

시스템의 진입점. 핵심 자식 액터들을 생성하고 감독한다.

```kotlin
sealed class MainStageActorCommand : PersitenceSerializable
data class CreateSocketSessionManager(
    val replyTo: ActorRef<MainStageActorResponse>
) : MainStageActorCommand()
data class CreateSupervisorChannelActor(
    val replyTo: ActorRef<MainStageActorResponse>
) : MainStageActorCommand()

sealed class MainStageActorResponse : PersitenceSerializable
data class SocketSessionManagerCreated(
    val actorRef: ActorRef<UserSessionCommand>
) : MainStageActorResponse()
data class SupervisorChannelActorCreated(
    val actorRef: ActorRef<SupervisorChannelCommand>
) : MainStageActorResponse()

class MainStageActor private constructor(
    private val context: ActorContext<MainStageActorCommand>,
) : AbstractBehavior<MainStageActorCommand>(context) {

    companion object {
        fun create(): Behavior<MainStageActorCommand> {
            return Behaviors.setup { context -> MainStageActor(context) }
        }
    }

    override fun createReceive(): Receive<MainStageActorCommand> {
        return newReceiveBuilder()
            .onMessage(CreateSocketSessionManager::class.java,
                this::onSocketSessionManager)
            .onMessage(CreateSupervisorChannelActor::class.java,
                this::onCreateSupervisorChannelActor)
            .build()
    }

    private fun onSocketSessionManager(
        command: CreateSocketSessionManager
    ): Behavior<MainStageActorCommand> {
        val sessionManagerActor = context.spawn(
            Behaviors.supervise(UserSessionManagerActor.create())
                .onFailure(SupervisorStrategy.resume()),
            "sessionManagerActor"
        )
        context.watch(sessionManagerActor)
        command.replyTo.tell(SocketSessionManagerCreated(sessionManagerActor))
        return this
    }

    private fun onCreateSupervisorChannelActor(
        command: CreateSupervisorChannelActor
    ): Behavior<MainStageActorCommand> {
        val supervisorChannelActor = context.spawn(
            Behaviors.supervise(SupervisorChannelActor.create())
                .onFailure(SupervisorStrategy.resume()),
            "supervisorChannelActor"
        )
        context.watch(supervisorChannelActor)
        command.replyTo.tell(SupervisorChannelActorCreated(supervisorChannelActor))
        return this
    }
}
```

### UserSessionManagerActor (세션 관리)

WebSocket 세션의 생명주기를 관리하고, 인증된 사용자에 대해 개인 채팅방(PersonalRoomActor)을 동적으로 생성한다.

```kotlin
sealed class UserSessionCommand
data class AddSession(val session: WebSocketSession) : UserSessionCommand()
data class RemoveSession(val session: WebSocketSession) : UserSessionCommand()
data class UpdateSession(
    val session: WebSocketSession,
    val claims: TokenClaims
) : UserSessionCommand()
data class SendMessageToAll(val message: String) : UserSessionCommand()
data class SendMessageToSession(
    val sessionId: String,
    val message: String
) : UserSessionCommand()
data class GetPersonalRoomActor(
    val identifier: String,
    val replyTo: ActorRef<UserSessionResponse>
) : UserSessionCommand()

class UserSessionManagerActor private constructor(
    context: ActorContext<UserSessionCommand>
) : AbstractBehavior<UserSessionCommand>(context) {

    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    // 인증 후 개인 채팅방 액터를 동적으로 생성
    private fun createPrivacyRoom(identifier: String, session: WebSocketSession) {
        val actorName = "PrivacyRoomActor-$identifier"
        val roomActor = getPrivacyRoomActor(identifier)

        if (roomActor != null) {
            // 이미 존재하면 세션만 업데이트
            roomActor.tell(SetSocketSession(session))
            return
        }

        // 새 PersonalRoomActor 생성 + 감독 전략
        val childRoomActor = context.spawn(
            Behaviors.supervise(PersonalRoomActor.create(identifier))
                .onFailure(SupervisorStrategy.resume()),
            actorName
        )
        context.watch(childRoomActor)
        childRoomActor.tell(SetSocketSession(session))
    }

    // 이름으로 자식 액터 검색 + 타입 캐스팅
    private fun getPrivacyRoomActor(
        identifier: String
    ): ActorRef<PersonalRoomCommand>? {
        val actorName = "PrivacyRoomActor-$identifier"
        return context.children
            .find { it.path().name() == actorName }
            ?.unsafeUpcast<PersonalRoomCommand>()
    }
}
```

### PersonalRoomActor (개인 채팅방)

각 사용자에 대응하는 개인 채팅방. 타이머 기반 자동 메시지, WebSocket 세션 관리, 상담방 연결을 담당한다.

```kotlin
sealed class PersonalRoomCommand
data class SendMessage(
    val message: String,
    val replyTo: ActorRef<WsHelloActorResponse>
) : PersonalRoomCommand()
data class SendTextMessage(val message: String) : PersonalRoomCommand()
data object AutoOnceProcess : PersonalRoomCommand()
data class SetSocketSession(val socketSession: WebSocketSession) : PersonalRoomCommand()
data object ClearSocketSession : PersonalRoomCommand()
data class SetCounselorRoom(
    val counselorRoomActor: ActorRef<CounselorRoomCommand>
) : PersonalRoomCommand()

class PersonalRoomActor private constructor(
    context: ActorContext<PersonalRoomCommand>,
    private val identifier: String,
    private val timers: TimerScheduler<PersonalRoomCommand>
) : AbstractBehavior<PersonalRoomCommand>(context) {

    companion object {
        fun create(identifier: String): Behavior<PersonalRoomCommand> {
            return Behaviors.withTimers { timers ->
                Behaviors.setup { context ->
                    PersonalRoomActor(context, identifier, timers)
                }
            }
        }
    }

    private var socketSession: WebSocketSession? = null
    private lateinit var counselorRoomActor: ActorRef<CounselorRoomCommand>

    init {
        // 랜덤 시작 지연으로 자동 프로세스 타이머 설정
        val randomStart = Duration.ofSeconds(
            ThreadLocalRandom.current().nextLong(3, 6)
        )
        timers.startTimerAtFixedRate(AutoOnceProcess, randomStart, Duration.ofSeconds(60))
    }

    // WebSocket 세션이 없으면 타이머를 멈추고,
    // 세션이 설정되면 타이머를 다시 시작한다
    private fun onSetSocketSession(command: SetSocketSession): Behavior<PersonalRoomCommand> {
        socketSession = command.socketSession
        if (!isRunTimer) {
            timers.startTimerAtFixedRate(AutoOnceProcess, Duration.ofSeconds(5))
            isRunTimer = true
        }
        return this
    }

    private fun onClearSocketSession(command: ClearSocketSession): Behavior<PersonalRoomCommand> {
        socketSession = null
        return this
    }
}
```

### CounselorRoomActor (상담방)

고객(PersonalRoom)과 상담원(Counselor) 사이의 메시지 중계 역할을 한다.

```kotlin
enum class CounselorRoomStatus { WAITING, IN_PROGRESS, COMPLETED }

sealed class CounselorRoomCommand
data class InvitePersonalRoomActor(
    val personalRoomActor: ActorRef<PersonalRoomCommand>,
    val replyTo: ActorRef<CounselorRoomResponse>
) : CounselorRoomCommand()
data class AssignCounselor(
    val counselorActor: ActorRef<CounselorCommand>
) : CounselorRoomCommand()
data class SendMessageToPersonalRoom(val message: String) : CounselorRoomCommand()
data class SendToCounselor(val message: String) : CounselorRoomCommand()
data class InviteObserver(
    val observer: ActorRef<CounselorCommand>
) : CounselorRoomCommand()

class CounselorRoomActor private constructor(
    context: ActorContext<CounselorRoomCommand>,
    private val name: String
) : AbstractBehavior<CounselorRoomCommand>(context) {

    private var status: CounselorRoomStatus = CounselorRoomStatus.WAITING
    private lateinit var personalRoom: ActorRef<PersonalRoomCommand>
    private lateinit var counselor: ActorRef<CounselorCommand>
    private val observerCounselors: MutableList<ActorRef<CounselorCommand>> = mutableListOf()

    private fun onSendToCounselor(cmd: SendToCounselor): Behavior<CounselorRoomCommand>? {
        if (::counselor.isInitialized) {
            counselor.tell(SendToCounselorHandlerTextMessage(cmd.message))
            // 옵저버에게도 동일 메시지 전달
            observerCounselors.forEach {
                it.tell(SendToCounselorHandlerTextMessage(cmd.message))
            }
        }
        return this
    }

    private fun onSendMessageToPersonalRoom(
        cmd: SendMessageToPersonalRoom
    ): Behavior<CounselorRoomCommand> {
        if (::personalRoom.isInitialized) {
            personalRoom.tell(SendTextMessage(cmd.message))
        }
        return this
    }
}
```

### SupervisorChannelActor (채널 감독)

여러 CounselorManager를 관리하는 최상위 상담 관리자.

```kotlin
sealed class SupervisorChannelCommand
data class CreateCounselorManager(
    val channel: String,
    val replyTo: ActorRef<SupervisorChannelResponse>
) : SupervisorChannelCommand()
data class GetCounselorManager(
    val channel: String,
    val replyTo: ActorRef<SupervisorChannelResponse>
) : SupervisorChannelCommand()
data class GetCounselorFromManager(
    val channel: String,
    val counselorName: String,
    val replyTo: ActorRef<SupervisorChannelResponse>
) : SupervisorChannelCommand()

class SupervisorChannelActor private constructor(
    context: ActorContext<SupervisorChannelCommand>
) : AbstractBehavior<SupervisorChannelCommand>(context) {

    private val counselorManagers =
        mutableMapOf<String, ActorRef<CounselorManagerCommand>>()

    private fun onGetCounselorFromManager(
        command: GetCounselorFromManager
    ): Behavior<SupervisorChannelCommand> {
        val manager = counselorManagers[command.channel]
        if (manager != null) {
            // 부모 -> 자식에게 Ask 패턴으로 조회
            AskPattern.ask(
                manager,
                { replyTo: ActorRef<CounselorManagerResponse> ->
                    GetCounselor(command.counselorName, replyTo)
                },
                Duration.ofSeconds(3),
                context.system.scheduler()
            ).whenComplete { res, _ ->
                if (res is CounselorFound) {
                    command.replyTo.tell(
                        CounselorActorFound(res.name, res.actorRef)
                    )
                }
            }
        }
        return this
    }
}
```

### 상담 연결 플로우

```
1. 고객이 WebSocket으로 접속
   -> UserSessionManagerActor.AddSession

2. JWT 인증 후 세션 업데이트
   -> UserSessionManagerActor.UpdateSession
   -> PersonalRoomActor 자동 생성

3. 상담 요청
   -> CounselorManagerActor.RequestCounseling
   -> SmartRouter로 가용 상담원 탐색
   -> CounselorRoomActor 생성
   -> PersonalRoom <-> CounselorRoom <-> Counselor 연결

4. 메시지 교환
   고객 -> PersonalRoom -> CounselorRoom -> Counselor (+ Observers)
   상담원 -> CounselorRoom -> PersonalRoom -> 고객 WebSocket
```

---

## 13. SSE (Server-Sent Events)

액터를 이벤트 큐로 활용하여 SSE 스트리밍을 구현하는 패턴이다. 각 사용자별 이벤트 액터를 ClusterSingleton으로 관리한다.

### UserEventActor

```kotlin
sealed class UserEventCommand : PersitenceSerializable

data class AddEvent(val message: String) : UserEventCommand()
data class GetEvent(val replyTo: ActorRef<Any>) : UserEventCommand()

class UserEventActor(
    context: ActorContext<UserEventCommand>,
    private val brandId: String,
    private val userId: String
) : AbstractBehavior<UserEventCommand>(context) {

    // 이벤트 큐 (FIFO)
    private val eventQueue: Queue<String> = LinkedList()

    companion object {
        fun create(brandId: String, userId: String): Behavior<UserEventCommand> {
            return Behaviors.setup { context ->
                UserEventActor(context, brandId, userId)
            }
        }
    }

    override fun createReceive(): Receive<UserEventCommand> {
        return newReceiveBuilder()
            .onMessage(AddEvent::class.java) { command ->
                eventQueue.add(command.message)
                this
            }
            .onMessage(GetEvent::class.java) { command ->
                val event = eventQueue.poll()
                if (event != null) {
                    command.replyTo.tell(event)
                } else {
                    command.replyTo.tell("No events available")
                }
                this
            }
            .build()
    }
}
```

### SSE Controller (Spring WebFlux)

```kotlin
@RestController
class SseController(private val akka: AkkaConfiguration) {

    private val mainStageActor: ActorRef<MainStageActorCommand> = akka.getMainStage()

    @GetMapping("/api/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    suspend fun streamEvents(
        @RequestParam brandId: String,
        @RequestParam userId: String
    ): List<String> {
        // 1. UserEventActor를 가져오거나 생성
        val response = AskPattern.ask(
            mainStageActor,
            { replyTo: ActorRef<Any> ->
                GetOrCreateUserEventActor(brandId, userId, replyTo)
            },
            Duration.ofSeconds(3),
            akka.getScheduler()
        ).await()

        val userEventActor = response as ActorRef<UserEventCommand>

        // 2. 이벤트 폴링
        val events = mutableListOf<String>()
        repeat(10) {
            val eventResponse = AskPattern.ask(
                userEventActor,
                { replyTo: ActorRef<Any> -> GetEvent(replyTo) },
                Duration.ofSeconds(3),
                akka.getScheduler()
            ).await()
            events.add(eventResponse as String)
        }
        return events
    }
}
```

### Topic 기반 이벤트 배포 구조

```kotlin
// MainStageActor에서 Topic과 UserEventActor를 ClusterSingleton으로 관리
private fun onGetOrCreateUserEventActor(
    command: GetOrCreateUserEventActor
): Behavior<MainStageActorCommand> {
    val singleton = ClusterSingleton.get(context.system)

    // brandId 토픽 (브랜드 전체 이벤트)
    val brandTopic = topics.getOrPut(command.brandId) {
        singleton.init(SingletonActor.of(
            Topic.create(UserEventCommand::class.java, command.brandId),
            command.brandId
        ))
    }

    // userId 토픽 (개인 이벤트)
    val userTopic = topics.getOrPut(command.userId) {
        singleton.init(SingletonActor.of(
            Topic.create(UserEventCommand::class.java, command.userId),
            command.userId
        ))
    }

    // UserEventActor를 ClusterSingleton으로 생성/조회
    val actorKey = "${command.brandId}-${command.userId}"
    val userEventActor = userEventActors.computeIfAbsent(actorKey) {
        singleton.init(SingletonActor.of(
            UserEventActor.create(command.brandId, command.userId),
            actorKey
        ))
    }

    // 양쪽 토픽 모두 구독
    brandTopic.tell(Topic.subscribe(userEventActor))
    userTopic.tell(Topic.subscribe(userEventActor))

    command.replyTo.tell(userEventActor)
    return this
}
```

---

## 14. 클러스터 확장 패턴

### ClusterSingleton

클러스터 전체에서 단 하나의 인스턴스만 존재하도록 보장하는 패턴이다. 노드 장애 시 자동으로 다른 노드에서 재생성된다.

```kotlin
private fun initializeClusterSingleton() {
    val single = ClusterSingleton.get(mainStage)
    singleCount = single.init(
        SingletonActor.of(
            Behaviors.supervise(CounterActor.create("singleId"))
                .onFailure(SupervisorStrategy.restartWithBackoff(
                    Duration.ofSeconds(1),   // 최소 대기 시간
                    Duration.ofSeconds(2),   // 최대 대기 시간
                    0.2                      // 랜덤 팩터
                )),
            "GlobalCounter"
        )
    )
}
```

### ClusterSharding

엔터티(Entity)를 클러스터 전체에 분산 배치하는 패턴이다. 엔터티 ID 기반으로 자동으로 적절한 노드에 라우팅된다.

```kotlin
private fun initializeClusterSharding() {
    val selfMember = Cluster.get(mainStage).selfMember()
    if (selfMember.hasRole("shard")) {
        val shardSystem = ClusterSharding.get(mainStage)

        // 100개의 엔터티를 샤딩으로 관리
        for (i in 1..100) {
            val entityId = "test-$i"
            val typeKey = EntityTypeKey.create(
                CounterCommand::class.java, entityId
            )
            shardSystem.init(
                Entity.of(typeKey) { entityContext ->
                    CounterActor.create(entityContext.entityId)
                }
            )
        }
    }
}
```

### CounterActor (Sharding Entity)

```kotlin
sealed class CounterCommand : PersitenceSerializable

data class Increment @JsonCreator constructor(
    @JsonProperty("value") val value: Int
) : CounterCommand()

data class GetCount @JsonCreator constructor(
    @JsonProperty("replyTo") val replyTo: ActorRef<CounterState>
) : CounterCommand()

object GoodByeCounter : CounterCommand()

data class CounterState @JsonCreator constructor(
    @JsonProperty("count") val count: Int
) : PersitenceSerializable

class CounterActor(
    context: ActorContext<CounterCommand>,
    val entityId: String
) : AbstractBehavior<CounterCommand>(context) {

    companion object {
        fun create(entityId: String): Behavior<CounterCommand> {
            return Behaviors.setup { context -> CounterActor(context, entityId) }
        }
    }

    private var count = 0

    override fun createReceive(): Receive<CounterCommand> {
        return newReceiveBuilder()
            .onMessage(Increment::class.java) { command ->
                count += command.value
                this
            }
            .onMessage(GetCount::class.java) { command ->
                command.replyTo.tell(CounterState(count))
                this
            }
            .onMessage(GoodByeCounter::class.java) {
                throw IllegalStateException("crash..")
            }
            .build()
    }
}
```

### 클러스터 설정 파일 구조

```
src/main/resources/
├── application.conf          # 기본 Pekko 설정
├── standalone.conf           # 단일 노드 개발용
├── cluster-seed.conf         # Seed 노드
├── cluster-helloA.conf       # HelloA 역할 노드
└── cluster-helloB.conf       # HelloB 역할 노드
```

실행 시 클러스터 설정 선택:

```bash
# Seed 노드
./gradlew bootRun -PclusterConfig=cluster-seed -PserverPort=8080

# HelloA 역할 노드
./gradlew bootRun -PclusterConfig=cluster-helloA -PserverPort=8081
```

---

## 15. Spring Boot 통합

### AkkaConfiguration - 핵심 설정 클래스

```kotlin
@Configuration
class AkkaConfiguration {

    private lateinit var mainStage: ActorSystem<MainStageActorCommand>
    private lateinit var sessionManagerActor: CompletableFuture<ActorRef<UserSessionCommand>>
    private lateinit var supervisorChannelActor: CompletableFuture<ActorRef<SupervisorChannelCommand>>

    @Autowired
    lateinit var durableRepository: DurableRepository

    @PostConstruct
    fun init() {
        val finalConfig = loadConfiguration()
        initializeMainStage(finalConfig)
        initializeActors()
        initializeClusterSingleton()
        initializeClusterSharding()
    }

    private fun initializeMainStage(config: Config) {
        mainStage = ActorSystem.create(
            MainStageActor.create(), "ClusterSystem", config
        )
    }

    private fun initializeActors() {
        // Ask 패턴으로 MainStageActor에 자식 액터 생성 요청
        sessionManagerActor = AskPattern.ask(
            mainStage,
            { replyTo: ActorRef<MainStageActorResponse> ->
                CreateSocketSessionManager(replyTo)
            },
            Duration.ofSeconds(3),
            mainStage.scheduler()
        ).toCompletableFuture().thenApply { res ->
            when (res) {
                is SocketSessionManagerCreated -> res.actorRef
                else -> throw IllegalStateException("Failed to create actor")
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        mainStage.terminate()
    }

    // ===== Spring Bean으로 등록 =====

    @Bean
    fun actorSystem(): ActorSystem<MainStageActorCommand> = mainStage

    @Bean
    fun sessionManagerActor(): ActorRef<UserSessionCommand> =
        sessionManagerActor.get()

    @Bean
    fun supervisorChannelActor(): ActorRef<SupervisorChannelCommand> =
        supervisorChannelActor.get()
}
```

### AkkaUtils - Coroutine + Reactor 브릿지

액터와 Spring WebFlux/Coroutine을 연결하는 유틸리티이다.

```kotlin
object AkkaUtils {

    // ===== 1. Coroutine suspend 함수 =====
    // Kotlin Coroutine에서 사용. CompletionStage.await()로 비동기 대기
    suspend fun <T, R> askActor(
        actor: ActorRef<T>,
        message: (ActorRef<R>) -> T,
        timeout: Duration,
        actorSystem: ActorSystem<*>
    ): R {
        return AskPattern.ask(
            actor,
            message,
            timeout,
            actorSystem.scheduler()
        ).await()  // kotlinx-coroutines-future
    }

    // ===== 2. Reactor Mono 반환 =====
    // WebFlux Controller에서 사용. Mono로 변환하여 리액티브 체인에 결합
    fun <T, R> askActorByMono(
        actor: ActorRef<T>,
        message: (ActorRef<R>) -> T,
        timeout: Duration,
        actorSystem: ActorSystem<*>
    ): Mono<R> {
        return Mono.fromCompletionStage(
            AskPattern.ask(
                actor,
                message,
                timeout,
                actorSystem.scheduler()
            )
        )
    }

    // ===== 3. 블로킹 호출 =====
    // 동기 코드 (MVC Controller 등)에서 사용
    fun <T, R> runBlockingAsk(
        actor: ActorRef<T>,
        message: (ActorRef<R>) -> T,
        timeout: Duration,
        actorSystem: ActorSystem<*>
    ): R = runBlocking {
        askActor(actor, message, timeout, actorSystem)
    }
}
```

### Controller에서 사용 예제

```kotlin
// ===== Coroutine suspend Controller =====
@RestController
@RequestMapping("/api/actor")
class ActorController @Autowired constructor(
    private val akka: AkkaConfiguration
) {
    private val helloState: ActorSystem<HelloStateActorCommand> = akka.getHelloState()

    @PostMapping("/hello")
    suspend fun helloCommand(): String {
        // AskPattern을 직접 사용하는 방식
        val response = AskPattern.ask(
            helloState,
            { replyTo: ActorRef<Any> -> Hello("Hello", replyTo) },
            Duration.ofSeconds(3),
            akka.getScheduler()
        ).await()

        val helloResponse = response as HelloResponse
        return "message: ${helloResponse.message}"
    }
}

// ===== AkkaUtils를 사용하는 방식 =====
@PostMapping("/hello-v2")
suspend fun helloCommandV2(): String {
    val response = AkkaUtils.askActor<HelloStateActorCommand, Any>(
        helloState,
        { replyTo -> Hello("Hello", replyTo) },
        Duration.ofSeconds(3),
        akka.getMainStage()
    )
    return "message: ${(response as HelloResponse).message}"
}
```

### 의존성 주입 흐름

```
@PostConstruct
  └── ActorSystem.create(MainStageActor.create(), "ClusterSystem", config)
       └── AskPattern.ask(...) 으로 자식 액터 생성 요청
            ├── SessionManagerActor 생성 -> @Bean으로 등록
            └── SupervisorChannelActor 생성 -> @Bean으로 등록

@Controller / @Service
  └── @Autowired로 ActorRef<T> 주입받아 사용
```

---

## 16. Pekko vs Akka 차이점

### Import 변경

```kotlin
// Akka (BSL 라이선스)
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.actor.typed.pubsub.Topic
import akka.persistence.typed.state.javadsl.DurableStateBehavior

// Pekko (Apache 2.0 라이선스)
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.persistence.typed.state.javadsl.DurableStateBehavior
```

### build.gradle.kts 변경

```kotlin
// Akka
val akkaVersion = "2.7.0"
implementation(platform("com.typesafe.akka:akka-bom_$scalaVersion:$akkaVersion"))
implementation("com.typesafe.akka:akka-actor-typed_$scalaVersion:$akkaVersion")

// Pekko
val pekkoVersion = "1.1.5"
implementation(platform("org.apache.pekko:pekko-bom_$scalaVersion:$pekkoVersion"))
implementation("org.apache.pekko:pekko-actor-typed_$scalaVersion:$pekkoVersion")
```

### 듀얼 빌드 전략

KotlinBootLabs 프로젝트에서는 동일한 소스 코드에 대해 두 가지 빌드 파일을 유지한다:

```
KotlinBootLabs/
├── build.gradle.kts         # Akka 2.7.0 빌드
├── build.gradle.pekko.kts   # Pekko 1.1.2 빌드
└── src/                     # 동일한 소스 코드
```

### 주요 차이점 요약

| 항목 | Akka | Pekko |
|------|------|-------|
| 라이선스 | BSL (상용 시 유료) | Apache 2.0 (완전 무료) |
| 패키지 | `akka.*` | `org.apache.pekko.*` |
| 아티팩트 그룹 | `com.typesafe.akka` | `org.apache.pekko` |
| 설정 접두사 | `akka.` | `pekko.` |
| API | 동일 | 동일 |
| 버전 | 2.7.x, 2.8.x, 2.9.x | 1.0.x, 1.1.x |
| Persistence R2DBC | `com.lightbend.akka:akka-persistence-r2dbc` | `org.apache.pekko:pekko-persistence-r2dbc` |

> **마이그레이션 팁**: IDE의 전체 바꾸기(Find & Replace)로 import를 일괄 변경하면 대부분의 마이그레이션이 완료된다. API 사용법은 동일하다.

---

## 17. Classic vs Typed 비교

### 전체 비교 표

| 항목 | Classic (Untyped) | Typed |
|------|-------------------|-------|
| 메시지 타입 | `Any` | `T` (컴파일 타임 검증) |
| 기본 클래스 | `AbstractActor` | `AbstractBehavior<T>` |
| 액터 참조 | `ActorRef` (untyped) | `ActorRef<T>` (typed) |
| 메시지 수신 | `createReceive()` + `match()` | `createReceive()` + `onMessage(T::class.java)` |
| 상태 전환 | `context.become(behavior)` | 다른 `Receive<T>` 반환 또는 `Behaviors.receive()` |
| 감독 전략 | 부모에서 `supervisorStrategy` 오버라이드 | `Behaviors.supervise().onFailure()` 래핑 |
| 생명주기 | `preStart()`, `postStop()` 등 | `PostStop`, `PreRestart` Signal |
| 자식 생성 | `context.actorOf(Props.create())` | `context.spawn(Behavior, name)` |
| 종료 감시 | `context.watch(ref)` + `Terminated` msg | `context.watch(ref)` + `.onSignal(Terminated::class.java)` |
| 라우팅 | `Props.withRouter()` | `Routers.pool()` / `Routers.group()` |
| Ask | `Patterns.ask()` | `AskPattern.ask()` |
| PubSub | `DistributedPubSubMediator` | `Topic` 액터 |
| Stash | `Stash` trait | `Behaviors.withStash()` |
| 영속화 | `PersistentActor` | `DurableStateBehavior` / `EventSourcedBehavior` |
| 클러스터 싱글톤 | `ClusterSingletonManager.props()` | `ClusterSingleton.get(system).init(SingletonActor.of())` |
| 클러스터 샤딩 | `ClusterSharding(system).start()` | `ClusterSharding.get(system).init(Entity.of())` |

### Typed API의 장점

1. **컴파일 타임 안전성**: `ActorRef<HelloCommand>`에 `GoodbyeCommand`를 보내면 컴파일 에러
2. **sealed class + when**: Kotlin의 exhaustive when과 결합하여 처리되지 않은 메시지를 컴파일 타임에 감지
3. **명확한 프로토콜**: sealed class가 액터의 인터페이스(프로토콜) 역할을 한다
4. **Behavior 중심 설계**: 액터의 동작이 명시적인 `Behavior` 객체로 표현된다

### Kotlin에서 Typed API 사용 시 팁

```kotlin
// 1. sealed class로 명령과 응답을 분리 정의
sealed class MyCommand
sealed class MyResponse

// 2. data class로 불변 메시지 정의 (copy(), equals() 자동 생성)
data class DoSomething(val data: String, val replyTo: ActorRef<MyResponse>) : MyCommand()

// 3. object로 싱글톤 메시지 (타이머 메시지 등)
object Tick : MyCommand()

// 4. companion object로 팩토리 패턴 강제
companion object {
    fun create(): Behavior<MyCommand> =
        Behaviors.setup { context -> MyActor(context) }
}

// 5. 메서드 레퍼런스로 깔끔한 핸들러 등록
override fun createReceive(): Receive<MyCommand> {
    return newReceiveBuilder()
        .onMessage(DoSomething::class.java, this::onDoSomething)
        .build()
}

// 6. Behaviors.withTimers + Behaviors.setup 중첩 패턴
Behaviors.withTimers { timers ->
    Behaviors.setup { context ->
        MyTimerActor(context, timers)
    }
}
```

---

## 부록: 프로젝트 파일 경로 참조

### KotlinBootLabs (Akka 2.7.0 / Pekko 1.1.2)

```
src/main/kotlin/com/example/kotlinbootlabs/
├── actor/
│   ├── HelloActor.kt                          # 기본 Typed 액터
│   ├── hellostate/HelloStateActor.kt           # 상태 전환 + 타이머
│   ├── bulkprocessor/BulkProcessor.kt          # FSM 벌크 프로세서
│   ├── supervisor/SupervisorActor.kt           # 감독 전략
│   ├── router/HelloManagerActor.kt             # Pool 라우터
│   ├── persistent/HelloPersistentDurableStateActor.kt  # DurableState 영속화
│   ├── eventbus/PubSubActor.kt                 # PubSub
│   ├── cluster/ClusterHelloActorA.kt           # 클러스터 액터 A
│   ├── cluster/ClusterHelloActorB.kt           # 클러스터 액터 B
│   └── MainStageActor.kt                       # 최상위 가디언
├── ws/actor/
│   ├── UserSessionManagerActor.kt              # WebSocket 세션 관리
│   ├── PersonalRoomActor.kt                    # 개인 채팅방
│   ├── CounselorRoomActor.kt                   # 상담방
│   ├── CounselorManagerActor.kt                # 상담원 관리
│   ├── SupervisorChannelActor.kt               # 채널 감독
│   └── SmartRouter.kt                          # 커스텀 라우팅
├── config/AkkaConfiguration.kt                 # Spring 설정
└── module/AkkaUtils.kt                         # Coroutine/Reactor 브릿지
```

### KotlinBootReactiveLabs (Pekko 1.1.5)

```
src/main/kotlin/org/example/kotlinbootreactivelabs/
├── actor/
│   ├── core/HelloActor.kt                      # Pekko 기본 액터
│   ├── state/HelloStateActor.kt                # 상태 전환 (Pekko)
│   ├── state/store/HelloStateStoreActor.kt     # R2DBC 상태 저장
│   ├── state/store/HelloStashActor.kt          # Stash 패턴
│   ├── bulkprocessor/BulkProcessor.kt          # FSM 벌크 프로세서 (Pekko)
│   ├── supervisor/SupervisorActor.kt           # 감독 전략 (Pekko)
│   ├── router/HelloRouter.kt                   # Pool 라우터 (Pekko)
│   ├── persistent/durable/HelloPersistentDurableStateActor.kt  # DurableState
│   ├── cluster/PubSubActor.kt                  # PubSub (Pekko)
│   ├── cluster/CounterActor.kt                 # Sharding Entity
│   ├── stream/GraphActor.kt                    # 스트림 액터
│   ├── sse/UserEventActor.kt                   # SSE 이벤트 액터
│   └── MainStageActor.kt                       # 최상위 가디언 (Pekko)
├── ws/actor/chat/
│   ├── UserSessionManagerActor.kt              # WebSocket 세션 관리 (Pekko)
│   ├── PersonalRoomActor.kt                    # 개인 채팅방 (Pekko)
│   ├── CounselorRoomActor.kt                   # 상담방 (Pekko)
│   ├── CounselorManagerActor.kt                # 상담원 관리 (Pekko)
│   ├── SupervisorChannelActor.kt               # 채널 감독 (Pekko)
│   └── router/SmartRouter.kt                   # 스킬 기반 라우팅
├── config/AkkaConfiguration.kt                 # Spring 설정 (Pekko)
├── module/AkkaUtils.kt                         # Coroutine/Reactor 브릿지
├── controller/actor/ActorController.kt         # REST API 예제
└── controller/sse/SseController.kt             # SSE API 예제
```
