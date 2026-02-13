# Actor Model 크로스 플랫폼 비교

> Java (Akka Classic) vs Kotlin (Pekko Typed) vs C# (Akka.NET)

이 문서는 Actor Model의 세 가지 주요 구현체를 동일한 패턴별로 나란히 비교합니다. 각 플랫폼의 코드 스타일, 타입 안전성, API 설계 철학의 차이를 이해하는 것이 목표입니다.

---

## 목차

1. [기본 액터 정의 비교](#1-기본-액터-정의-비교)
2. [메시지 전달 패턴 비교](#2-메시지-전달-패턴-비교)
3. [감독 전략 비교](#3-감독-전략-비교)
4. [라우팅 비교](#4-라우팅-비교)
5. [타이머 비교](#5-타이머-비교)
6. [배치/FSM 처리 비교](#6-배치fsm-처리-비교)
7. [영속화 비교](#7-영속화-비교)
8. [스트림/흐름 제어 비교](#8-스트림흐름-제어-비교)
9. [클러스터 비교](#9-클러스터-비교)
10. [프레임워크 통합 비교](#10-프레임워크-통합-비교)
11. [설정 방식 비교](#11-설정-방식-비교)
12. [테스트 비교](#12-테스트-비교)
13. [종합 비교 테이블](#13-종합-비교-테이블)

---

## 1. 기본 액터 정의 비교

가장 기본적인 "Hello" 액터를 세 플랫폼에서 어떻게 정의하는지 비교합니다.

### Java (Akka Classic)

```java
public class GreetingActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props Props() {
        return Props.create(GreetingActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, s -> {
                log.info("Received String message: {}", s);
            })
            .matchAny(o -> log.info("received unknown message"))
            .build();
    }
}

// 생성
ActorRef greetActor = actorSystem.actorOf(GreetingActor.Props(), "greetActor");
greetActor.tell("Hello World!", ActorRef.noSender());
```

**특징:**
- `AbstractActor`를 상속하고 `createReceive()`를 구현
- `receiveBuilder()`에서 `.match(Class, handler)` 체인으로 메시지 타입 분기
- 메시지 타입은 `Object`로 수신 -- 타입 안전성이 낮음 (untyped)
- `Props.create()`로 액터 생성 설정을 캡슐화
- `getSender()`, `getSelf()`로 송신자/자신 참조

### Kotlin (Pekko Typed)

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
            .build()
    }

    private fun onHello(command: Hello): Behavior<HelloCommand> {
        context.log.info("Received Hello: ${command.message}")
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

// 생성
val helloActor: ActorRef<HelloCommand> = context.spawn(HelloActor.create(), "hello")
helloActor.tell(Hello("Hello", replyTo))
```

**특징:**
- `AbstractBehavior<T>`를 상속 -- 제네릭 타입 파라미터로 수신 가능 메시지 제한
- `sealed class`로 메시지 계층 정의 -- 컴파일 타임 타입 안전성
- 응답 대상을 `replyTo: ActorRef<T>`로 메시지에 명시적 포함
- `Behaviors.setup { }`으로 액터 팩토리 정의
- 각 핸들러가 `Behavior<T>`를 반환 -- 상태 전환 가능

### C# (Akka.NET)

```csharp
public class BasicActor : ReceiveActor
{
    private readonly ILoggingAdapter logger = Context.GetLogger();

    public BasicActor()
    {
        Receive<string>(msg =>
        {
            Sender.Tell("world");
        });

        Receive<Todo>(msg =>
        {
            logger.Info($"Received Todo: {msg.Title}");
        });
    }

    protected override void PreStart()
    {
        logger.Info("BasicActor is starting.");
        base.PreStart();
    }

    protected override void PostStop()
    {
        logger.Info("BasicActor is stopping.");
        base.PostStop();
    }
}

// 생성
var basicActor = actorSystem.ActorOf(Props.Create(() => new BasicActor()), "basic");
basicActor.Tell("hello");
```

**특징:**
- `ReceiveActor`를 상속하고 생성자에서 `Receive<T>()` 등록
- 제네릭 `Receive<T>()`로 Java보다 나은 타입 분기 제공
- `Sender`, `Self` 프로퍼티로 간결한 참조
- `ReceiveAsync<T>()`로 비동기 처리 네이티브 지원
- `PreStart()`, `PostStop()` 등 라이프사이클 메서드 직접 override

### 비교 분석

| 관점 | Java (Akka Classic) | Kotlin (Pekko Typed) | C# (Akka.NET) |
|------|---------------------|---------------------|----------------|
| **타입 안전성** | 낮음 - `Object` 기반 | 높음 - `Behavior<T>` 제네릭 | 중간 - `Receive<T>()` 제네릭 |
| **메시지 정의** | 일반 클래스, 제약 없음 | `sealed class` 계층 | 일반 클래스/record |
| **응답 패턴** | `getSender().tell()` 암묵적 | `replyTo: ActorRef<T>` 명시적 | `Sender.Tell()` 암묵적 |
| **핸들러 등록** | `receiveBuilder().match()` | `newReceiveBuilder().onMessage()` | 생성자에서 `Receive<T>()` |
| **비동기 처리** | `CompletableFuture` + pipe | Coroutine + `pipeToSelf` | `ReceiveAsync<T>()` 네이티브 |
| **액터 생성** | `Props.create(Class)` | `Behaviors.setup { }` | `Props.Create<T>()` |

---

## 2. 메시지 전달 패턴 비교

### 2.1 Tell (Fire-and-Forget)

가장 기본적인 비동기 메시지 전달 패턴입니다. 응답을 기다리지 않습니다.

#### Java
```java
actorRef.tell("Hello", ActorRef.noSender());      // 송신자 없음
actorRef.tell("Hello", getSelf());                  // 자기 자신이 송신자
```

#### Kotlin (Typed)
```kotlin
actorRef.tell(Hello("Hello", replyTo))              // 응답 대상은 메시지에 포함
actorRef.tell(HelloResponse("Kotlin"))              // 단순 전달
```

#### C#
```csharp
actorRef.Tell("Hello");                             // 송신자 자동 설정
actorRef.Tell("Hello", ActorRefs.NoSender);         // 송신자 없음
```

**차이점:** Java/C#에서는 `sender()`가 시스템 수준에서 암묵적으로 전달되지만, Pekko Typed에서는 응답 대상(`replyTo`)을 메시지 자체에 명시적으로 포함해야 합니다. 이는 타입 안전성을 높이지만 코드가 더 장황해집니다.

### 2.2 Ask (Request-Response)

응답을 기다리는 패턴입니다. 내부적으로 임시 액터를 생성하고 `Future`/`Task`를 반환합니다.

#### Java
```java
import static akka.pattern.Patterns.ask;

CompletionStage<Object> future = ask(actorRef, "Hello", Duration.ofSeconds(3));
future.thenAccept(response -> {
    System.out.println("Response: " + response);
});
```

#### Kotlin (Typed)
```kotlin
import org.apache.pekko.actor.typed.javadsl.AskPattern

// Spring Controller에서 Coroutine 브릿지와 함께 사용
suspend fun helloCommand(): String {
    val response = AskPattern.ask(
        helloState,
        { replyTo: ActorRef<Any> -> Hello("Hello", replyTo) },
        Duration.ofSeconds(3),
        scheduler
    ).await()                                       // CompletableFuture -> suspend

    val helloResponse = response as HelloResponse
    return helloResponse.message
}
```

#### C#
```csharp
// Ask는 Task<T>를 반환
var response = await actorRef.Ask<string>("Hello", TimeSpan.FromSeconds(3));
Console.WriteLine($"Response: {response}");
```

**차이점:**
- **Java:** `Patterns.ask()`가 `CompletionStage<Object>`를 반환, 타입 캐스팅 필요
- **Kotlin:** `AskPattern.ask()`에 `replyTo` 팩토리 함수 전달, `CompletableFuture`를 코루틴 `await()`로 변환
- **C#:** 가장 간결 -- `Ask<T>()`가 제네릭 타입을 직접 반환, `async/await` 네이티브 지원

### 2.3 Forward

메시지를 원래 송신자 정보를 유지한 채 다른 액터에게 전달합니다.

#### Java
```java
// ParentActor에서 forward 사용
sender().forward(CMD_MESSAGE_REPLY, getContext());
```

#### Kotlin (Typed)
```kotlin
// Typed에서는 replyTo가 메시지에 포함되므로 별도의 forward가 불필요
// 메시지를 그대로 다른 액터에 전달하면 replyTo가 원래 송신자를 가리킴
otherActor.tell(command)  // command.replyTo는 원래 송신자
```

#### C#
```csharp
// 원래 Sender를 유지하며 전달
anotherActor.Forward(message);
```

**차이점:** Pekko Typed에서는 `forward` 개념이 사실상 불필요합니다. 응답 대상이 메시지 자체에 `replyTo`로 포함되어 있으므로, 메시지를 그대로 전달하면 원래 요청자에게 응답이 갑니다.

### 2.4 Pipe

비동기 작업의 결과를 액터에게 전달합니다.

#### Java
```java
import static akka.pattern.Patterns.pipe;

CompletionStage<String> future = someAsyncWork();
pipe(future, context().dispatcher()).to(getSender());
```

#### Kotlin (Typed)
```kotlin
// pipeToSelf를 사용하여 비동기 결과를 자기 자신에게 메시지로 변환
context.pipeToSelf(completableFuture) { result ->
    when {
        result.isSuccess -> ProcessedResult(result.get())
        else -> ProcessingFailed(result.exceptionOrNull()!!)
    }
}
```

#### C#
```csharp
// Task의 결과를 액터에게 파이프
Task<string> future = SomeAsyncWorkAsync();
future.PipeTo(Sender);

// ContinueWith로 변환 후 파이프
future.ContinueWith(t => new ResultMessage(t.Result)).PipeTo(Self);
```

**차이점:**
- **Kotlin Typed:** `pipeToSelf`는 성공/실패 결과를 메시지로 변환하는 함수를 받아 타입 안전하게 처리
- **Java Classic:** `pipe().to()`로 결과를 특정 액터에 전달
- **C#:** `PipeTo()` 확장 메서드로 간결하게 파이프

---

## 3. 감독 전략 비교

액터 계층에서 자식 액터의 실패를 처리하는 감독(Supervision) 전략을 비교합니다.

### Java (Akka Classic)

```java
public class ParentActor extends AbstractActor {

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(
            10,                                     // maxNrOfRetries
            Duration.ofMinutes(1),                  // withinTimeRange
            DeciderBuilder
                .match(ArithmeticException.class, e -> SupervisorStrategy.resume())
                .match(NullPointerException.class, e -> SupervisorStrategy.restart())
                .match(IllegalArgumentException.class, e -> SupervisorStrategy.stop())
                .matchAny(o -> SupervisorStrategy.escalate())
                .build()
        );
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, msg -> {
                ActorRef child = getContext().actorOf(Props.create(ChildActor.class));
                child.tell(msg, getSelf());
            })
            .build();
    }
}
```

**전략 종류:**
- `OneForOneStrategy`: 실패한 자식만 영향
- `AllForOneStrategy`: 모든 자식에 영향

**지시어(Directive):**
- `resume()` - 상태 유지하고 계속
- `restart()` - 상태 초기화하고 재시작
- `stop()` - 자식 중지
- `escalate()` - 부모에게 위임

### Kotlin (Pekko Typed)

```kotlin
class SupervisorActor private constructor(
    context: ActorContext<SupervisorCommand>,
) : AbstractBehavior<SupervisorCommand>(context) {

    companion object {
        fun create(): Behavior<SupervisorCommand> {
            return Behaviors.setup { context -> SupervisorActor(context) }
        }
    }

    private fun onCreateChild(command: CreateChild): Behavior<SupervisorCommand> {
        // 자식 생성 시 감독 전략을 Behaviors.supervise()로 감싸서 적용
        val childActor = context.spawn(
            Behaviors.supervise(ChildActor.create())
                .onFailure(SupervisorStrategy.restart()
                    .withLoggingEnabled(true)),
            command.name
        )
        context.watch(childActor)    // Terminated 시그널 수신
        return this
    }
}

// 고급 감독 전략 예시
Behaviors.supervise(childBehavior)
    .onFailure(
        SupervisorStrategy.restart()
            .withLimit(10, Duration.ofMinutes(1))   // 최대 재시작 횟수 제한
            .withLoggingEnabled(true)
    )

// Backoff 전략 (클러스터 싱글턴 등에서 유용)
Behaviors.supervise(CounterActor.create("singleId"))
    .onFailure(SupervisorStrategy.restartWithBackoff(
        Duration.ofSeconds(1),     // minBackoff
        Duration.ofSeconds(2),     // maxBackoff
        0.2                        // randomFactor
    ))
```

**차이점:**
- Typed에서는 `Behaviors.supervise()`로 자식의 Behavior를 감싸는 방식
- `OneForOne`/`AllForOne` 구분 없이 각 자식에 개별 전략 적용
- `restartWithBackoff`로 지수 백오프 재시작 지원

### C# (Akka.NET)

```csharp
public class ParentActor : ReceiveActor
{
    public ParentActor()
    {
        Receive<string>(msg =>
        {
            var child = Context.ActorOf(Props.Create<ChildActor>());
            child.Tell(msg);
        });
    }

    protected override SupervisorStrategy SupervisorStrategy()
    {
        return new OneForOneStrategy(
            maxNrOfRetries: 10,
            withinTimeRange: TimeSpan.FromMinutes(1),
            localOnlyDecider: ex =>
            {
                if (ex is ArithmeticException) return Directive.Resume;
                if (ex is NullReferenceException) return Directive.Restart;
                if (ex is ArgumentException) return Directive.Stop;
                return Directive.Escalate;
            }
        );
    }
}
```

### 비교 분석

| 관점 | Java (Classic) | Kotlin (Typed) | C# (Akka.NET) |
|------|---------------|---------------|---------------|
| **전략 위치** | 부모 액터의 `supervisorStrategy()` | 자식 생성 시 `Behaviors.supervise()` | 부모 액터의 `SupervisorStrategy()` |
| **적용 범위** | `OneForOne` / `AllForOne` | 개별 자식마다 설정 | `OneForOne` / `AllForOne` |
| **Backoff** | 별도 구성 필요 | `restartWithBackoff()` 내장 | `BackoffSupervisor` 사용 |
| **Decider** | `DeciderBuilder.match()` | 전략 객체에 제한 조건 추가 | 람다 함수로 예외별 분기 |
| **종료 감시** | `context().watch()` | `context.watch()` + `Terminated` 시그널 | `Context.Watch()` |

---

## 4. 라우팅 비교

라우팅은 메시지를 여러 액터에게 분배하는 패턴입니다.

### 4.1 Pool Router (액터 자동 생성)

#### Java
```java
// 코드 기반 Pool Router
ActorRef router = actorSystem.actorOf(
    new RoundRobinPool(5).props(Props.create(WorkerActor.class)),
    "roundRobinRouter"
);

for (int i = 0; i < 100; i++) {
    router.tell("work-" + i, ActorRef.noSender());
}
```

#### Kotlin (Typed)
```kotlin
// Pekko Typed에서는 GroupRouter 사용 (ServiceKey 기반)
import org.apache.pekko.actor.typed.receptionist.Receptionist
import org.apache.pekko.actor.typed.receptionist.ServiceKey

val serviceKey = ServiceKey.create(WorkerCommand::class.java, "worker")

// 워커 등록
context.system.receptionist().tell(
    Receptionist.register(serviceKey, workerActor)
)

// GroupRouter 생성
val group = Routers.group(serviceKey)
    .withRoundRobinRouting()
val router = context.spawn(group, "worker-group")
```

#### C#
```csharp
// 코드 기반 Pool Router
var roundRobin = actorSystem.ActorOf(
    Props.Create<BasicActor>().WithRouter(new RoundRobinPool(5)),
    "roundrobin"
);

// Broadcast Router
var broadcast = actorSystem.ActorOf(
    Props.Create<BasicActor>().WithRouter(new BroadcastPool(5)),
    "broadcast"
);

// Random Router
var random = actorSystem.ActorOf(
    Props.Create<BasicActor>().WithRouter(new RandomPool(5)),
    "random"
);
```

### 4.2 Group Router (기존 액터 경로 기반)

#### Java
```java
// 기존 자식 액터를 경로 기반으로 라우팅
ActorRef w1 = context().actorOf(ChildActor.Props(), "w1");
ActorRef w2 = context().actorOf(ChildActor.Props(), "w2");
ActorRef w3 = context().actorOf(ChildActor.Props(), "w3");

List<String> paths = Arrays.asList("/user/parent/w1", "/user/parent/w2", "/user/parent/w3");
ActorRef router = getContext().actorOf(new RoundRobinGroup(paths).props(), "router");
```

#### C#
```csharp
// 동적으로 Routee 추가
var scatterGather = Context.ActorOf(
    Props.Create(() => new WorkerActor())
        .WithRouter(new ScatterGatherFirstCompletedPool(0, TimeSpan.FromSeconds(10))),
    "scatterRouter"
);

// 런타임에 Routee 추가
var routee = Routee.FromActorRef(newWorkerActor);
scatterGather.Tell(new AddRoutee(routee));
```

### 사용 가능한 라우팅 전략

| 전략 | Java (Akka) | Kotlin (Pekko) | C# (Akka.NET) |
|------|------------|---------------|---------------|
| **RoundRobin** | `RoundRobinPool/Group` | `withRoundRobinRouting()` | `RoundRobinPool/Group` |
| **Random** | `RandomPool/Group` | `withRandomRouting()` | `RandomPool/Group` |
| **Balancing** | `BalancingPool` | 미지원 (GroupRouter 대체) | `TailChoppingPool` |
| **SmallestMailbox** | `SmallestMailboxPool` | 미지원 | `SmallestMailboxPool` |
| **Broadcast** | `BroadcastPool/Group` | 별도 구현 필요 | `BroadcastPool/Group` |
| **ScatterGather** | `ScatterGatherFirstCompleted` | 별도 구현 필요 | `ScatterGatherFirstCompletedPool` |
| **ConsistentHash** | `ConsistentHashingPool` | `withConsistentHashingRouting()` | `ConsistentHashingPool` |

**핵심 차이:** Pekko Typed에서는 `Pool` 라우터 대신 `Receptionist` + `ServiceKey` 기반의 `GroupRouter`를 사용합니다. 이는 클러스터 환경에서 노드 간 액터 발견과 자연스럽게 통합됩니다.

---

## 5. 타이머 비교

액터 내부에서 주기적 또는 일회성 타이머를 설정하는 방법을 비교합니다.

### Java (AbstractActorWithTimers)

```java
public class TimerActor extends AbstractActorWithTimers {

    private static final Object TICK_KEY = "TickKey";

    public TimerActor() {
        // 일회성 타이머
        getTimers().startSingleTimer(TICK_KEY, new FirstTick(), Duration.ofMillis(500));
    }

    public static Props Props() {
        return Props.create(TimerActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(FirstTick.class, message -> {
                log.info("First Tick");
                // 반복 타이머로 전환
                getTimers().startPeriodicTimer(TICK_KEY, new Tick(), Duration.ofSeconds(1));
            })
            .match(Tick.class, message -> {
                log.info("Tick");
            })
            .build();
    }

    private static final class FirstTick {}
    private static final class Tick {}
}
```

### Kotlin (Behaviors.withTimers)

```kotlin
sealed class TimerActorCommand
object TimerLoop : TimerActorCommand()
object TimerStop : TimerActorCommand()
object TimerResume : TimerActorCommand()

class TimerActor private constructor(
    private val context: ActorContext<TimerActorCommand>,
    private val timers: TimerScheduler<TimerActorCommand>,
) : AbstractBehavior<TimerActorCommand>(context) {

    private var timerKey: Unit
    private var timerCount = 0

    companion object {
        fun create(): Behavior<TimerActorCommand> {
            return Behaviors.withTimers { timers ->          // TimerScheduler 주입
                Behaviors.setup { context -> TimerActor(context, timers) }
            }
        }
    }

    init {
        // 고정 간격 반복 타이머
        timerKey = timers.startTimerAtFixedRate(TimerLoop, Duration.ofSeconds(10))
    }

    override fun createReceive(): Receive<TimerActorCommand> {
        return newReceiveBuilder()
            .onMessage(TimerLoop::class.java, this::onTimerLoop)
            .onMessage(TimerStop::class.java, this::onTimerStop)
            .onMessage(TimerResume::class.java, this::onTimerResumed)
            .build()
    }

    private fun onTimerLoop(command: TimerLoop): Behavior<TimerActorCommand> {
        timerCount++
        context.log.info("Timer loop - $timerCount")
        return this
    }

    private fun onTimerStop(command: TimerStop): Behavior<TimerActorCommand> {
        if (timers.isTimerActive(timerKey)) {
            timers.cancel(timerKey)
        }
        return this
    }

    private fun onTimerResumed(command: TimerResume): Behavior<TimerActorCommand> {
        if (!timers.isTimerActive(timerKey)) {
            timerKey = timers.startTimerAtFixedRate(TimerLoop, Duration.ofSeconds(1))
        }
        return this
    }
}
```

### C# (Akka.NET Scheduler)

```csharp
public class ThrottleTimerActor : ReceiveActor
{
    private readonly ILoggingAdapter logger = Context.GetLogger();

    public ThrottleTimerActor(int element, int second, int maxBust)
    {
        // Scheduler를 통한 반복 타이머
        Context.System.Scheduler.ScheduleTellRepeatedly(
            TimeSpan.FromSeconds(0),        // initialDelay
            TimeSpan.FromSeconds(1),        // interval
            Self,
            new Flush(),
            ActorRefs.NoSender
        );

        Receive<Flush>(message =>
        {
            logger.Info("Flush tick");
        });
    }
}

// IWithTimers 인터페이스 사용 (Akka.NET 1.5+)
public class MyTimerActor : ReceiveActor, IWithTimers
{
    public ITimerScheduler Timers { get; set; }

    public MyTimerActor()
    {
        // 일회성 타이머
        Timers.StartSingleTimer("key", new Tick(), TimeSpan.FromSeconds(1));
        // 반복 타이머
        Timers.StartPeriodicTimer("repeat-key", new Tick(), TimeSpan.FromSeconds(5));

        Receive<Tick>(_ => { /* 처리 */ });
    }
}
```

### 비교 분석

| 관점 | Java (Classic) | Kotlin (Typed) | C# (Akka.NET) |
|------|---------------|---------------|---------------|
| **기반 클래스** | `AbstractActorWithTimers` | `Behaviors.withTimers { }` | `IWithTimers` 인터페이스 |
| **일회성 타이머** | `startSingleTimer()` | `startSingleTimer()` | `StartSingleTimer()` |
| **반복 타이머** | `startPeriodicTimer()` | `startTimerAtFixedRate()` | `StartPeriodicTimer()` |
| **타이머 취소** | 같은 키로 새 타이머 시작 시 자동 | `timers.cancel(key)` | `Timers.Cancel("key")` |
| **활성 확인** | 직접 확인 불가 | `timers.isTimerActive()` | `Timers.IsTimerActive()` |
| **Scheduler** | `getContext().system().scheduler()` | `Behaviors.withTimers` 내장 | `Context.System.Scheduler` |

---

## 6. 배치/FSM 처리 비교

메시지를 모아서 일괄 처리하는 배치 패턴과 유한 상태 머신(FSM) 패턴을 비교합니다.

### Java (Timer 기반 배치)

```java
public class SafeBatchActor extends AbstractActorWithTimers {

    private static final Object TICK_KEY = "TickKey";
    private final ArrayList<String> batchList;

    public SafeBatchActor() {
        batchList = new ArrayList<>();
        getTimers().startSingleTimer(TICK_KEY, new FirstTick(), Duration.ofMillis(0));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, message -> {
                batchList.add(message);          // 메시지 축적
            })
            .match(FirstTick.class, message -> {
                // 반복 타이머 시작
                getTimers().startPeriodicTimer(TICK_KEY, new Tick(), Duration.ofSeconds(1));
            })
            .match(Tick.class, message -> {
                log.info("Batch Flush: {}", batchList.size());
                batchList.clear();               // 주기적 일괄 처리 후 초기화
            })
            .build();
    }
}
```

**설명:** 타이머 기반으로 메시지를 축적한 후 주기적으로 일괄 처리합니다. 단순하고 예측 가능하지만 상태 전환이 명시적이지 않습니다.

### Kotlin (Typed - 상태 기반 배치)

```kotlin
// Kotlin Typed에서는 Behavior 전환으로 FSM 패턴 구현
sealed class BatchCommand
data class AddItem(val item: String) : BatchCommand()
object FlushBatch : BatchCommand()
object TimerFlush : BatchCommand()

class BatchActor private constructor(
    private val context: ActorContext<BatchCommand>,
    private val timers: TimerScheduler<BatchCommand>,
) : AbstractBehavior<BatchCommand>(context) {

    companion object {
        fun create(): Behavior<BatchCommand> {
            return Behaviors.withTimers { timers ->
                Behaviors.setup { context -> BatchActor(context, timers) }
            }
        }
    }

    private val batch = mutableListOf<String>()

    init {
        timers.startTimerAtFixedRate(TimerFlush, Duration.ofSeconds(1))
    }

    override fun createReceive(): Receive<BatchCommand> {
        return newReceiveBuilder()
            .onMessage(AddItem::class.java) { cmd ->
                batch.add(cmd.item)
                this
            }
            .onMessage(FlushBatch::class.java) { _ -> flush() }
            .onMessage(TimerFlush::class.java) { _ -> flush() }
            .build()
    }

    private fun flush(): Behavior<BatchCommand> {
        if (batch.isNotEmpty()) {
            context.log.info("Flushing batch of ${batch.size} items")
            // 일괄 처리 수행
            batch.clear()
        }
        return this
    }
}
```

### C# (FSM 기반 배치)

```csharp
// 정식 FSM 프레임워크를 활용한 배치 처리
public class FSMBatchActor : FSM<State, IData>
{
    public FSMBatchActor()
    {
        // 초기 상태: Idle
        StartWith(State.Idle, Uninitialized.Instance);

        // Idle 상태 처리
        When(State.Idle, state =>
        {
            if (state.FsmEvent is SetTarget target && state.StateData is Uninitialized)
            {
                return Stay().Using(new Todo(target.Ref, ImmutableList<object>.Empty));
            }
            return null;
        });

        // Active 상태 처리 (1초 타임아웃)
        When(State.Active, state =>
        {
            if (state.FsmEvent is Flush or StateTimeout
                && state.StateData is Todo t)
            {
                return GoTo(State.Idle).Using(t.Copy(ImmutableList<object>.Empty));
            }
            return null;
        }, TimeSpan.FromSeconds(1));             // 1초 후 자동 StateTimeout

        // 모든 상태에서 Queue 메시지 처리
        WhenUnhandled(state =>
        {
            if (state.FsmEvent is Queue q && state.StateData is Todo t)
            {
                return GoTo(State.Active).Using(t.Copy(t.Queue.Add(q.Obj)));
            }
            return Stay();
        });

        // 상태 전환 시 배치 전송
        OnTransition((initialState, nextState) =>
        {
            if (initialState == State.Active && nextState == State.Idle)
            {
                if (StateData is Todo todo)
                {
                    todo.Target.Tell(new Batch(todo.Queue));
                }
            }
        });

        Initialize();
    }
}

// 테스트
buncher.Tell(new SetTarget(TestActor));
buncher.Tell(new Queue(42));
buncher.Tell(new Queue(43));
// 1초 후 자동 Flush -> Batch([42, 43]) 수신
ExpectMsg<Batch>().Obj.Should().BeEquivalentTo(ImmutableList.Create(42, 43));
```

### 비교 분석

| 관점 | Java (Classic) | Kotlin (Typed) | C# (Akka.NET) |
|------|---------------|---------------|---------------|
| **FSM 지원** | `AbstractFSM` 클래스 존재 | Behavior 전환으로 구현 | `FSM<TState, TData>` 내장 |
| **상태 정의** | enum 기반 | sealed class / Behavior | enum 기반 |
| **타임아웃** | 타이머 수동 관리 | 타이머 수동 관리 | `StateTimeout` 자동 |
| **전환 콜백** | 직접 구현 | Behavior 반환으로 처리 | `OnTransition()` 콜백 |
| **불변 데이터** | 직접 관리 | data class copy | `ImmutableList` 사용 |

**핵심 차이:** C#의 `FSM<TState, TData>`는 가장 완성도 높은 FSM 프레임워크를 제공합니다. `When()`, `GoTo()`, `Stay()`, `OnTransition()` 등의 DSL로 상태 머신을 선언적으로 정의할 수 있습니다. Java에도 `AbstractFSM`이 있지만, Kotlin Typed에서는 별도의 FSM 클래스 없이 `Behavior` 전환으로 상태 머신을 구현합니다.

---

## 7. 영속화 비교

액터의 상태를 외부 저장소에 영속화하는 패턴을 비교합니다.

### Kotlin (DurableStateBehavior + R2DBC/PostgreSQL)

```kotlin
// Pekko Typed의 DurableStateBehavior를 이용한 상태 영속화
data class HelloState @JsonCreator constructor(
    @JsonProperty("state") val state: State,
    @JsonProperty("helloCount") val helloCount: Int,
    @JsonProperty("helloTotalCount") val helloTotalCount: Int
) : PersitenceSerializable

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

    // 초기 상태
    override fun emptyState(): HelloState = HelloState(State.HAPPY, 0, 0)

    // 태그 (CQRS 읽기 모델용)
    override fun tag(): String = "tag1"

    override fun commandHandler(): CommandHandler<HelloPersistentStateActorCommand, HelloState> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(HelloPersistentDurable::class.java) { state, command ->
                val newState = state.copy(
                    helloCount = state.helloCount + 1,
                    helloTotalCount = state.helloTotalCount + 1
                )
                Effect().persist(newState).thenRun {
                    command.replyTo.tell(HelloResponse("Kotlin"))
                }
            }
            .onCommand(ResetHelloCount::class.java) { state, _ ->
                val newState = state.copy(helloCount = 0)
                Effect().persist(newState)
            }
            .build()
    }
}
```

**HOCON 설정 (R2DBC + PostgreSQL):**
```hocon
pekko {
  persistence {
    state {
      plugin = "pekko.persistence.r2dbc.state"
    }
    r2dbc {
      state {
        class = "org.apache.pekko.persistence.r2dbc.state.R2dbcDurableStateStoreProvider"
        table = "durable_state"
      }
      dialect = "postgres"
      connection-factory {
        driver = "postgres"
        host = "localhost"
        database = "neo"
        user = "postgres"
        password = "postgres"
      }
    }
  }
}
```

### Kotlin (Custom Store 방식 -- Repository 기반)

```kotlin
// DurableStateBehavior 대신 Repository를 직접 사용하는 방식
class HelloStateStoreActor private constructor(
    private val context: ActorContext<HelloStateStoreActorCommand>,
    private val persistenceId: String,
    private val durableRepository: DurableRepository,
) : AbstractBehavior<HelloStateStoreActorCommand>(context) {

    private lateinit var helloStoreState: HelloStoreState

    init {
        initStoreState()   // 기동 시 저장소에서 상태 복원
    }

    private fun onHello(command: HelloStore): Behavior<HelloStateStoreActorCommand> {
        helloStoreState.helloCount++
        helloStoreState.helloTotalCount++
        persist(helloStoreState, true)          // 비동기 영속화
        command.replyTo.tell(HelloResponseStore("Kotlin"))
        return this
    }

    // Stream 기반 비동기 영속화
    private fun persistAsync(newState: HelloStoreState) {
        Source.single(newState)
            .mapAsync(1) { state ->
                durableRepository.createOrUpdateDurableStateEx(persistenceId, 1L, state).toFuture()
            }
            .runWith(Sink.ignore(), materializer)
    }

    // 기동 시 상태 복원
    private fun initStoreState() {
        runBlocking {
            val result = durableRepository.findByIdEx<HelloStoreState>(persistenceId, 1L)
                .awaitFirstOrNull()
            helloStoreState = result ?: HelloStoreState(HappyState.HAPPY, 0, 0)
        }
    }
}
```

### C# (ReceivePersistentActor + RavenDB)

```csharp
public class SalesActor : ReceivePersistentActor
{
    // 고유 영속화 ID
    public override string PersistenceId => "sales-actor";

    // 스냅샷으로 저장될 상태
    private SalesActorState _state;

    public SalesActor(long expectedProfit, TaskCompletionSource<bool> taskCompletion)
    {
        _state = new SalesActorState { totalSales = 0 };

        // 명령 처리: Sale 이벤트 영속화
        Command<Sale>(saleInfo =>
        {
            // 이벤트를 RavenDB에 저장
            Persist(saleInfo, _ =>
            {
                _state.totalSales += saleInfo.Price;

                // 5개 이벤트마다 스냅샷 저장
                if (LastSequenceNr != 0 && LastSequenceNr % 5 == 0)
                {
                    SaveSnapshot(_state.totalSales);
                }
            });
        });

        // 스냅샷 저장 성공 처리
        Command<SaveSnapshotSuccess>(success =>
        {
            Console.WriteLine($"Snapshot saved at seq {success.Metadata.SequenceNr}");
        });

        // 이벤트 복구
        Recover<Sale>(saleInfo =>
        {
            _state.totalSales += saleInfo.Price;
        });

        // 스냅샷 복구
        Recover<SnapshotOffer>(offer =>
        {
            var salesFromSnapshot = (long)offer.Snapshot;
            _state.totalSales = salesFromSnapshot;
        });
    }
}
```

### Java (없음 -- Kotlin 예제 컨버트)

Java Akka Classic에서 Event Sourcing을 사용하려면 `AbstractPersistentActor`를 상속합니다. 프로젝트에 직접적인 Java 영속화 예제는 없으나, C#의 패턴을 Java로 컨버트하면 다음과 같습니다:

```java
// C# SalesActor를 Java로 컨버트한 예시
public class SalesActor extends AbstractPersistentActor {

    private SalesActorState state = new SalesActorState(0);

    @Override
    public String persistenceId() {
        return "sales-actor";
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
            .match(Sale.class, sale -> {
                state.totalSales += sale.getPrice();
            })
            .match(SnapshotOffer.class, offer -> {
                state.totalSales = (Long) offer.snapshot();
            })
            .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Sale.class, sale -> {
                persist(sale, evt -> {
                    state.totalSales += evt.getPrice();
                    if (lastSequenceNr() % 5 == 0) {
                        saveSnapshot(state.totalSales);
                    }
                });
            })
            .build();
    }
}
```

### 비교 분석

| 관점 | Java (Akka Classic) | Kotlin (Pekko Typed) | C# (Akka.NET) |
|------|---------------------|---------------------|----------------|
| **영속화 방식** | Event Sourcing | DurableState (상태 직접 저장) | Event Sourcing |
| **기반 클래스** | `AbstractPersistentActor` | `DurableStateBehavior<C, S>` | `ReceivePersistentActor` |
| **저장소** | Cassandra, JDBC 등 | R2DBC (PostgreSQL) | RavenDB, SQL Server 등 |
| **스냅샷** | `saveSnapshot()` 수동 | 불필요 (상태 자체가 저장) | `SaveSnapshot()` 수동 |
| **복구** | `createReceiveRecover()` | `emptyState()` + 자동 로드 | `Recover<T>()` |
| **CQRS 태그** | 별도 구성 | `tag()` 메서드 | 별도 구성 |

**핵심 차이:**
- **Kotlin:** `DurableStateBehavior`는 이벤트가 아닌 *현재 상태*를 직접 저장합니다. 이벤트 리플레이 없이 마지막 상태만 로드하므로 복구가 빠릅니다.
- **C#/Java:** Event Sourcing 방식으로 모든 이벤트를 저장하고 리플레이하여 상태를 복구합니다. 스냅샷으로 리플레이 구간을 줄입니다.

---

## 8. 스트림/흐름 제어 비교

스트림 기반의 데이터 흐름 제어와 속도 조절(Throttle) 패턴을 비교합니다.

### Java (Akka Streams)

```java
// Throttle: 초당 processCouuntPerSec 개의 메시지만 통과
final Materializer materializer = ActorMaterializer.create(actorSystem);

int processCouuntPerSec = 3;
final ActorRef throttler =
    Source.actorRef(1000, OverflowStrategy.dropNew())
        .throttle(processCouuntPerSec,
            FiniteDuration.create(1, TimeUnit.SECONDS),
            processCouuntPerSec, ThrottleMode.shaping())
        .to(Sink.actorRef(targetActor, akka.NotUsed.getInstance()))
        .run(materializer);

// 사용: throttler에게 메시지를 보내면 초당 3개씩 targetActor에 전달
for (int i = 0; i < 100; i++) {
    throttler.tell("Hello " + i, ActorRef.noSender());
}
```

### Kotlin (Pekko Streams)

```kotlin
// Throttle + Buffer: 초당 제한 + 오버플로우 전략
private val materializer = Materializer.createMaterializer(context.system)

// Source.queue 기반 스로틀링
private val helloLimitSource = Source.queue<HelloLimit>(100, OverflowStrategy.backpressure())
    .throttle(3, Duration.ofSeconds(1))
    .to(Sink.foreach { cmd ->
        context.self.tell(Hello(cmd.message, cmd.replyTo))
    })
    .run(materializer)

// 사용
private fun onHelloLimit(command: HelloLimit): Behavior<HelloStateActorCommand> {
    helloLimitSource.offer(command).thenAccept { result ->
        when (result) {
            is QueueOfferResult.`Enqueued$` -> context.log.info("Enqueued")
            is QueueOfferResult.`Dropped$` -> context.log.error("Dropped")
            is QueueOfferResult.Failure -> context.log.error("Failed", result.cause())
            is QueueOfferResult.`QueueClosed$` -> context.log.error("Queue closed")
        }
    }
    return this
}

// Flow 기반 그래프 처리
Source.single(number)
    .via(operation)                               // Flow<Int, Int, *> 적용
    .buffer(1000, OverflowStrategy.dropHead())
    .throttle(10, Duration.ofSeconds(1))
    .runWith(Sink.foreach { result ->
        replyTo.tell(ProcessedNumber(result))
    }, materializer)
```

### C# (Akka.Streams)

```csharp
// Stream 기반 Throttle 액터
private readonly IMaterializer _materializer;

public ThrottleActor(int processCouuntPerSec)
{
    _materializer = Context.Materializer();

    _throttler =
        Source.ActorRef<object>(1000, OverflowStrategy.DropNew)
            .Throttle(processCouuntPerSec,
                TimeSpan.FromSeconds(1),
                processCouuntPerSec,
                ThrottleMode.Shaping)
            .To(Sink.ActorRef<object>(Self, NotUsed.Instance))
            .Run(_materializer);

    // 메시지를 _throttler에게 전달하면 초당 제한된 수만큼 Self로 전달됨
    Receive<TodoQueue>(msg =>
    {
        _throttler.Tell(new Todo { Id = msg.Todo.Id, Title = msg.Todo.Title });
    });

    Receive<Todo>(msg =>
    {
        logger.Info($"{msg.Id} - {msg.Title}");
        consumer?.Tell(msg);
    });

    // 런타임에 TPS 변경
    Receive<ChangeTPS>(msg =>
    {
        _processCouuntPerSec = msg.processCouuntPerSec;
        _throttler = Source.ActorRef<object>(1000, OverflowStrategy.DropNew)
            .Throttle(_processCouuntPerSec,
                TimeSpan.FromSeconds(1),
                _processCouuntPerSec,
                ThrottleMode.Shaping)
            .To(Sink.ActorRef<object>(Self, NotUsed.Instance))
            .Run(_materializer);
    });
}
```

### 비교 분석

| 관점 | Java (Akka Streams) | Kotlin (Pekko Streams) | C# (Akka.Streams) |
|------|--------------------|-----------------------|-------------------|
| **Source 유형** | `Source.actorRef()` | `Source.queue()` / `Source.single()` | `Source.ActorRef<T>()` |
| **Throttle API** | `.throttle(n, duration, burst, mode)` | `.throttle(n, duration)` | `.Throttle(n, duration, burst, mode)` |
| **Overflow** | `OverflowStrategy.dropNew()` | `OverflowStrategy.backpressure()` | `OverflowStrategy.DropNew` |
| **Materializer** | `ActorMaterializer.create()` | `Materializer.createMaterializer()` | `Context.Materializer()` |
| **Flow** | `Flow.of(Class)` | `Flow.of(Class)` | `Flow.Create<T>()` |
| **Sink** | `Sink.actorRef()` | `Sink.foreach { }` | `Sink.ActorRef<T>()` |

**핵심 차이:** 세 플랫폼 모두 거의 동일한 Reactive Streams API를 제공합니다. Kotlin의 `Source.queue()`는 `offer()`로 배압(backpressure) 결과를 직접 확인할 수 있어 흐름 제어에 더 세밀합니다. C#에서는 런타임에 `_throttler`를 교체하여 동적 TPS 변경이 가능합니다.

---

## 9. 클러스터 비교

분산 환경에서의 클러스터링 기능을 비교합니다.

### Kotlin (Pekko Cluster)

Kotlin 프로젝트에서는 다양한 클러스터 기능을 활용합니다:

#### Cluster Membership & Roles
```kotlin
// AkkaConfiguration.kt에서 클러스터 초기화
private fun initializeClusterRoles() {
    val selfMember = Cluster.get(mainStage).selfMember()
    if (selfMember.hasRole("seed")) logger.info("My Application Role Seed")
    if (selfMember.hasRole("helloA")) logger.info("My Application Role HelloA")
}
```

#### Cluster Singleton
```kotlin
private fun initializeClusterSingleton() {
    val single = ClusterSingleton.get(mainStage)
    singleCount = single.init(
        SingletonActor.of(
            Behaviors.supervise(CounterActor.create("singleId"))
                .onFailure(SupervisorStrategy.restartWithBackoff(
                    Duration.ofSeconds(1), Duration.ofSeconds(2), 0.2
                )),
            "GlobalCounter"
        )
    )
}
```

#### Cluster Sharding
```kotlin
private fun initializeClusterSharding() {
    val shardSystem = ClusterSharding.get(mainStage)
    for (i in 1..100) {
        val entityId = "test-$i"
        val typeKey = EntityTypeKey.create(CounterCommand::class.java, entityId)
        shardSystem.init(Entity.of(typeKey) { CounterActor.create(it.entityId) })
    }
}
```

#### Distributed PubSub
```kotlin
sealed class PubSubCommand : PersitenceSerializable
data class PublishMessage(val channel: String, val message: String) : PubSubCommand()
data class Subscribe(val channel: String, val subscriber: ActorRef<String>) : PubSubCommand()

class PubSubActor(context: ActorContext<PubSubCommand>) :
    AbstractBehavior<PubSubCommand>(context) {

    private val topics = mutableMapOf<String, ActorRef<Topic.Command<String>>>()

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

### C# (Akka.Cluster)

C# 프로젝트에서는 Akka.Remote을 활용하고 있으며, 전체 클러스터 기능도 사용 가능합니다:

#### Remote 설정
```csharp
// 리모트 액터 시스템 설정
string akkaConfig = @"
akka {
    actor { provider = remote }
    remote {
        dot-netty.tcp {
            port = $port
            hostname = ""127.0.0.1""
        }
    }
}";

var actorSystem = ActorSystem.Create("default", ConfigurationFactory.ParseString(akkaConfig));

// 리모트 액터 참조
actorSystem.ActorOf(Props.Create<BasicActor>(), "someActor");
```

#### 클러스터 사용 예시 (개념 코드)
```csharp
// Akka.Cluster 사용 (프로젝트에서 사용 가능하나 직접 예제는 없음)
// NuGet: Akka.Cluster, Akka.Cluster.Sharding, Akka.Cluster.Tools

// Cluster Singleton
var singletonProps = ClusterSingletonManager.Props(
    singletonProps: Props.Create<CounterActor>(),
    terminationMessage: PoisonPill.Instance,
    settings: ClusterSingletonManagerSettings.Create(system)
);
system.ActorOf(singletonProps, "singleton");

// Cluster Sharding
ClusterSharding.Get(system).Start(
    typeName: "Counter",
    entityProps: Props.Create<CounterActor>(),
    settings: ClusterShardingSettings.Create(system),
    messageExtractor: new CounterMessageExtractor()
);
```

### Java (Akka Cluster)

Java Akka Classic에서도 클러스터를 지원합니다. 프로젝트에 직접 예제는 없으나 Kotlin 예제를 기반으로 컨버트하면:

```java
// Cluster membership
Cluster cluster = Cluster.get(actorSystem);
cluster.selfMember().getRoles().forEach(role ->
    log.info("My role: " + role)
);

// Cluster Singleton
ClusterSingletonManager.props(
    Props.create(CounterActor.class),
    PoisonPill.getInstance(),
    ClusterSingletonManagerSettings.create(actorSystem)
);
```

### 비교 분석

| 관점 | Java (Akka) | Kotlin (Pekko) | C# (Akka.NET) |
|------|------------|---------------|---------------|
| **Cluster Membership** | 지원 | 지원 (사용 중) | 지원 |
| **Cluster Singleton** | 지원 | `ClusterSingleton.get().init()` 사용 중 | `ClusterSingletonManager` |
| **Cluster Sharding** | 지원 | `ClusterSharding.get().init()` 사용 중 | `ClusterSharding.Get().Start()` |
| **Distributed PubSub** | `DistributedPubSub` | `Topic.create()` Typed API 사용 중 | `DistributedPubSub` |
| **ServiceKey / Receptionist** | Typed에서 지원 | `Receptionist` 사용 | 별도 구현 필요 |
| **Split Brain Resolver** | 지원 | 지원 | 지원 (Akka.Cluster) |

---

## 10. 프레임워크 통합 비교

웹 프레임워크와 Actor System을 통합하는 방법을 비교합니다.

### Spring Boot (Kotlin)

```kotlin
@Configuration
class AkkaConfiguration {

    private lateinit var mainStage: ActorSystem<MainStageActorCommand>
    private lateinit var helloState: ActorSystem<HelloStateActorCommand>

    @Autowired lateinit var durableRepository: DurableRepository

    @PostConstruct
    fun init() {
        val finalConfig = loadConfiguration()
        mainStage = ActorSystem.create(MainStageActor.create(), "ClusterSystem", finalConfig)
        helloState = ActorSystem.create(
            HelloStateActor.create(HelloState.HAPPY), "HelloStateActor"
        )
    }

    @PreDestroy
    fun shutdown() {
        mainStage.terminate()
    }

    // Spring Bean으로 등록
    @Bean
    fun actorSystem(): ActorSystem<MainStageActorCommand> = mainStage

    // Getter 메서드
    fun getHelloState(): ActorSystem<HelloStateActorCommand> = helloState
    fun getScheduler() = mainStage.scheduler()
}

// Controller에서 사용 (Coroutine 브릿지)
@RestController
@RequestMapping("/api/actor")
class ActorController @Autowired constructor(private val akka: AkkaConfiguration) {

    private val helloState = akka.getHelloState()

    @PostMapping("/hello")
    suspend fun helloCommand(): String {
        val response = AskPattern.ask(
            helloState,
            { replyTo: ActorRef<Any> -> Hello("Hello", replyTo) },
            Duration.ofSeconds(3),
            akka.getScheduler()
        ).await()                                   // suspend 함수로 비동기 대기

        return "Response: ${(response as HelloResponse).message}"
    }
}
```

**특징:**
- `@Configuration`으로 ActorSystem을 Spring Bean으로 관리
- `@PostConstruct` / `@PreDestroy`로 라이프사이클 관리
- Kotlin Coroutine의 `suspend` + `.await()`로 비동기 Ask를 동기식처럼 작성
- `@Autowired`로 Repository 등 Spring 컴포넌트 주입

### ASP.NET Core (C#)

```csharp
// Program.cs에서 Actor System 초기화
var builder = WebApplication.CreateBuilder(args);

// AkkaService를 Singleton으로 등록
builder.Services.AddSingleton<AkkaService>();

var app = builder.Build();

// Actor System 생성
var akkaService = app.Services.GetRequiredService<AkkaService>();
var actorSystem = akkaService.CreateActorSystem("default", 9000);

// 액터 생성 및 등록
var defaultMonitor = actorSystem.ActorOf(Props.Create<SimpleMonitorActor>());
akkaService.AddActor("defaultMonitor", defaultMonitor);

// Router 생성
var roundrobin = actorSystem.ActorOf(
    Props.Create<BasicActor>().WithRouter(new RoundRobinPool(0)),
    "roundrobin"
);
akkaService.AddActor("roundrobin", roundrobin);

// Throttle Actor 생성
var throttlerouter = actorSystem.ActorOf(
    Props.Create(() => new ThrottleActor(5))
);
akkaService.AddActor("throttlerouter", throttlerouter);

app.Run();
```

```csharp
// AkkaService.cs - Actor System 관리 서비스
public class AkkaService
{
    private Dictionary<string, ActorSystem> actorSystems = new();
    private Dictionary<string, IActorRef> actors = new();

    public ActorSystem CreateActorSystem(string name, int port = 0)
    {
        if (port == 0)
        {
            actorSystems[name] = ActorSystem.Create(name);
        }
        else
        {
            string akkaConfig = @"akka {
                actor { provider = remote }
                remote { dot-netty.tcp { port = $port, hostname = ""127.0.0.1"" } }
            }".Replace("$port", port.ToString());
            actorSystems[name] = ActorSystem.Create(name,
                ConfigurationFactory.ParseString(akkaConfig));
        }
        return actorSystems[name];
    }

    public void AddActor(string name, IActorRef actor) => actors[name] = actor;
    public IActorRef GetActor(string name) => actors.GetValueOrDefault(name);
}
```

**Akka.Hosting 방식 (권장):**
```csharp
// Akka.Hosting을 사용한 DI 통합 (최신 방식)
builder.Services.AddAkka("MyActorSystem", configurationBuilder =>
{
    configurationBuilder
        .WithRemoting("localhost", 9000)
        .WithClustering()
        .WithActors((system, registry) =>
        {
            var helloActor = system.ActorOf(Props.Create<HelloActor>(), "hello");
            registry.Register<HelloActor>(helloActor);
        });
});

// Controller에서 사용
public class HelloController : ControllerBase
{
    private readonly IActorRef _helloActor;

    public HelloController(IRequiredActor<HelloActor> helloActor)
    {
        _helloActor = helloActor.ActorRef;
    }

    [HttpPost]
    public async Task<IActionResult> Hello()
    {
        var response = await _helloActor.Ask<string>("Hello", TimeSpan.FromSeconds(3));
        return Ok(response);
    }
}
```

### Spring Boot (Java) -- 개념

```java
@Configuration
public class AkkaConfig {

    @Bean
    public ActorSystem actorSystem() {
        return ActorSystem.create("MySystem", ConfigFactory.load());
    }

    @Bean
    public ActorRef greetingActor(ActorSystem system) {
        return system.actorOf(GreetingActor.Props(), "greeting");
    }

    @PreDestroy
    public void shutdown(@Autowired ActorSystem system) {
        system.terminate();
    }
}
```

### 비교 분석

| 관점 | Java/Kotlin (Spring) | C# (ASP.NET Core) |
|------|---------------------|-------------------|
| **DI 방식** | `@Autowired`, `@Bean` | `AddSingleton<T>()`, `IRequiredActor<T>` |
| **라이프사이클** | `@PostConstruct` / `@PreDestroy` | `IHostedService` 또는 수동 |
| **비동기 통합** | Coroutine `suspend` + `.await()` | `async/await` + `Task<T>` |
| **설정** | `application.conf` (HOCON) | HOCON 또는 `appsettings.json` |
| **라이브러리** | 직접 통합 | `Akka.Hosting` 패키지 |
| **Actor 참조** | `AkkaConfiguration` getter | `IRequiredActor<T>` DI |

---

## 11. 설정 방식 비교

세 플랫폼의 HOCON 설정을 비교합니다.

### Java (application.conf)

```hocon
akka {
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  loglevel = "DEBUG"
  stdout-loglevel = "DEBUG"
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
}

my-dispatcher {
  type = Dispatcher
  executor = "fork-join-executor"
  fork-join-executor {
    parallelism-min = 2
    parallelism-factor = 2.0
    parallelism-max = 10
  }
  throughput = 100
}

my-blocking-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 16
  }
  throughput = 5
}
```

### Kotlin (application.conf)

```hocon
pekko {
  loglevel = "INFO"
  loggers = ["org.apache.pekko.event.slf4j.Slf4jLogger"]
  logging-filter = "org.apache.pekko.event.slf4j.Slf4jLoggingFilter"

  actor {
    allow-java-serialization = on
    serializers {
      jackson-json = "org.apache.pekko.serialization.jackson.JacksonJsonSerializer"
      jackson-cbor = "org.apache.pekko.serialization.jackson.JacksonCborSerializer"
    }
    serialization-bindings {
      "org.example.kotlinbootreactivelabs.actor.PersitenceSerializable" = jackson-json
    }
    default-dispatcher {
      fork-join-executor {
        parallelism-min = 5
        parallelism-factor = 2.0
        parallelism-max = 10
      }
    }
  }

  persistence {
    state.plugin = "pekko.persistence.r2dbc.state"
    r2dbc {
      dialect = "postgres"
      connection-factory {
        driver = "postgres"
        host = "localhost"
        database = "neo"
        user = "postgres"
        password = "postgres"
      }
    }
  }
}
```

### C# (HOCON 코드 내 또는 별도 파일)

```csharp
// 코드 내 HOCON 문자열
string akkaConfig = @"
akka {
    loggers = [""Akka.Logger.NLog.NLogLogger, Akka.Logger.NLog""]
    actor {
        provider = remote
    }
    remote {
        dot-netty.tcp {
            port = 9000
            hostname = ""127.0.0.1""
        }
    }
}
my-custom-mailbox {
    mailbox-type : ""ActorLib.Actor.Test.IssueTrackerMailbox, ActorLib""
}
";

var config = ConfigurationFactory.ParseString(akkaConfig);
var actorSystem = ActorSystem.Create("default", config);

// 또는 별도 .conf 파일에서 로드
var config = ConfigurationFactory.ParseString(File.ReadAllText("akka.conf"));
```

### 비교 분석

| 관점 | Java (Akka) | Kotlin (Pekko) | C# (Akka.NET) |
|------|------------|---------------|---------------|
| **설정 형식** | HOCON | HOCON | HOCON |
| **파일 위치** | `src/main/resources/application.conf` | `src/main/resources/application.conf` | 코드 내 또는 별도 `.conf` 파일 |
| **접두사** | `akka { }` | `pekko { }` | `akka { }` |
| **Serializer** | Protobuf, Java | Jackson JSON/CBOR | Hyperion, Newtonsoft.Json |
| **Dispatcher** | `fork-join-executor` | `fork-join-executor` | `dot-netty.tcp` 기반 |
| **로더** | `ConfigFactory.load()` | `ConfigFactory.load()` | `ConfigurationFactory.ParseString()` |
| **설정 조합** | `.withFallback()` | `.withFallback()` | `.WithFallback()` |

**주요 차이:** Pekko는 Akka 포크이므로 설정 접두사가 `akka`에서 `pekko`로 변경되었습니다. C#에서는 HOCON을 코드 문자열로 직접 작성하거나 `Akka.Hosting`을 사용하여 fluent API로 설정하는 두 가지 방식을 지원합니다.

---

## 12. 테스트 비교

### Java (akka-testkit)

```java
public class SimpleActorTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setup() {
        actorSystem = ActorSystem.create("TestSystem", ConfigFactory.load("test"));
    }

    @Test
    @DisplayName("Actor - HelloWorld Test")
    public void HelloWorld() {
        new TestKit(actorSystem) {{
            ActorRef greetActor = actorSystem.actorOf(GreetingActor.Props(), "greetActor");
            greetActor.tell("Hello World!", ActorRef.noSender());
            expectNoMessage();
        }};
    }

    @Test
    @DisplayName("Actor - Ask and Response Test")
    public void AskResponse() {
        new TestKit(actorSystem) {{
            ActorRef actor = actorSystem.actorOf(GreetingActor.Props());
            actor.tell("Hello", getRef());              // TestKit 자신이 sender
            expectMsg("world");                          // 응답 확인
        }};
    }

    @Test
    @DisplayName("Actor - Timer Test with Duration")
    public void TimerTest() {
        new TestKit(actorSystem) {{
            ActorRef timerActor = actorSystem.actorOf(TestTimerActor.Props());
            timerActor.tell("Hello~ World", ActorRef.noSender());
            expectNoMessage(Duration.ofSeconds(10));     // 10초간 메시지 없음 확인
        }};
    }
}
```

### Kotlin (ActorTestKit for Typed)

```kotlin
class HelloActorTest {

    companion object {
        private val testKit = ActorTestKit.create()

        @BeforeAll @JvmStatic
        fun setup() {}

        @AfterAll @JvmStatic
        fun tearDown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun testHelloActor() {
        // Typed 액터 스폰
        val helloActor: ActorRef<HelloCommand> = testKit.spawn(HelloActor.create(), "hello-actor")

        // Typed TestProbe 생성 -- 수신 메시지 타입 지정
        val probe: TestProbe<HelloCommand> = testKit.createTestProbe()

        // 메시지 전송
        helloActor.tell(Hello("Hello", probe.ref))

        // 기대 메시지 확인 (타입 안전)
        probe.expectMessage(HelloResponse("Kotlin"))
    }

    @Test
    fun testHelloActorNoResponse() {
        val helloActor = testKit.spawn(HelloActor.create(), "hello-no-resp")
        val probe = testKit.createTestProbe<HelloCommand>()

        helloActor.tell(Hello("Goodbye", probe.ref))
        probe.expectNoMessage(Duration.ofSeconds(1))
    }
}
```

### C# (Akka.TestKit + xUnit)

```csharp
public class AkkaServiceTest : TestKitXunit
{
    public AkkaServiceTest(ITestOutputHelper output) : base(output) {}

    [Theory(DisplayName = "액터시스템 생성후 기본메시지 수행")]
    [InlineData(100)]
    public void CreateSystemAndMessageTestAreOK(int cutoff)
    {
        var actorSystem = _akkaService.GetActorSystem();
        var basicActor = actorSystem.ActorOf(Props.Create(() => new BasicActor()));

        Within(TimeSpan.FromMilliseconds(cutoff), () =>
        {
            basicActor.Tell("hello");
            ExpectMsg("world");
        });
    }

    [Fact]
    public void FSMBatchTest()
    {
        var buncher = Sys.ActorOf(Props.Create<FSMBatchActor>());
        buncher.Tell(new SetTarget(TestActor));
        buncher.Tell(new Queue(42));
        buncher.Tell(new Queue(43));

        // FluentAssertions로 검증
        ExpectMsg<Batch>().Obj.Should()
            .BeEquivalentTo(ImmutableList.Create(42, 43));
    }
}
```

### 비교 분석

| 관점 | Java (Akka TestKit) | Kotlin (ActorTestKit) | C# (Akka.TestKit) |
|------|--------------------|-----------------------|-------------------|
| **기반 클래스** | `TestKit` 익명 클래스 | `ActorTestKit` 정적 팩토리 | `TestKit` 상속 |
| **Probe** | `TestKit` 자체가 probe | `TestProbe<T>` 타입 지정 | `TestActor` 내장 |
| **액터 생성** | `actorSystem.actorOf()` | `testKit.spawn()` | `Sys.ActorOf()` |
| **메시지 검증** | `expectMsg()`, `expectNoMessage()` | `probe.expectMessage()` | `ExpectMsg<T>()` |
| **시간 제한** | `expectNoMessage(Duration)` | `probe.expectNoMessage(Duration)` | `Within(TimeSpan, action)` |
| **테스트 프레임워크** | JUnit | JUnit 5 | xUnit + FluentAssertions |
| **타입 안전성** | 낮음 (Object) | 높음 (제네릭 Probe) | 중간 (제네릭 ExpectMsg) |

---

## 13. 종합 비교 테이블

| 특성 | Java (Akka Classic) | Kotlin (Pekko Typed) | C# (Akka.NET) |
|------|---------------------|---------------------|----------------|
| **타입 안전성** | 낮음 (untyped) | 높음 (typed generic) | 중간 (generic Receive) |
| **메시지 정의** | 일반 클래스 | sealed class 계층 | 일반 클래스/record |
| **액터 기반 클래스** | `AbstractActor` | `AbstractBehavior<T>` | `ReceiveActor` |
| **액터 생성** | `Props.create(Class)` | `Behaviors.setup { }` | `Props.Create<T>()` |
| **응답 패턴** | `getSender()` 암묵적 | `replyTo` 명시적 | `Sender` 암묵적 |
| **비동기 처리** | `CompletableFuture` | Coroutine + `await()` | `async/await` 네이티브 |
| **FSM** | `AbstractFSM` | Behavior 전환 | `FSM<TState, TData>` 내장 |
| **설정** | HOCON (`akka { }`) | HOCON (`pekko { }`) | HOCON (`akka { }`) |
| **프레임워크** | Spring Boot | Spring Boot | ASP.NET Core |
| **패키지 관리** | Gradle / Maven | Gradle | NuGet |
| **라이선스** | BSL (2.7+) | Apache 2.0 | Apache 2.0 |
| **영속화** | Event Sourcing | DurableState + Custom Store | Event Sourcing (RavenDB) |
| **클러스터** | 지원 | Singleton, Sharding, PubSub | 지원 |
| **스트림** | Akka Streams | Pekko Streams | Akka.Streams |
| **테스트** | akka-testkit | ActorTestKit (Typed) | Akka.TestKit |
| **Serialization** | Java / Protobuf | Jackson JSON/CBOR | Hyperion / Newtonsoft |
| **Remote** | Artery | Artery | dot-netty.tcp |

---

## 플랫폼 선택 가이드

### Java (Akka Classic) 선택 시
- 기존 Java 프로젝트에 액터 모델 도입 시
- Lightbend 상용 지원이 필요한 경우
- 주의: Akka 2.7+ 부터 BSL 라이선스로 변경됨

### Kotlin (Pekko Typed) 선택 시
- 타입 안전성이 최우선인 경우
- 최신 Typed API를 활용하고 싶은 경우
- Apache 2.0 오픈소스 라이선스가 필요한 경우
- Coroutine 기반 비동기 프로그래밍 선호 시
- 클러스터 기능(Singleton, Sharding, PubSub)이 필요한 경우

### C# (Akka.NET) 선택 시
- .NET / ASP.NET Core 기반 프로젝트인 경우
- FSM, Persistence 등 풍부한 내장 도구가 필요한 경우
- `async/await` 패턴으로 자연스러운 비동기 처리 원할 때
- Apache 2.0 오픈소스 라이선스가 필요한 경우

---

## 참고 프로젝트

| 프로젝트 | 플랫폼 | 경로 |
|---------|-------|------|
| java-labs | Java (Akka Classic) | `https://github.com/psmon/java-labs` |
| KotlinBootReactiveLabs | Kotlin (Pekko Typed) | `https://github.com/psmon/kopring-reactive-labs/tree/main/KotlinBootReactiveLabs` |
| NetCoreLabs | C# (Akka.NET) | `https://github.com/psmon/NetCoreLabs` |

---

> 이 문서의 모든 코드 예제는 실제 프로젝트 소스코드에서 발췌하였으며, 각 플랫폼의 공식 문서와 함께 참고하시기 바랍니다.
