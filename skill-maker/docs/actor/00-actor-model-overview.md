# 액터모델 개요 (Actor Model Overview)

> 이 문서는 액터 모델(Actor Model)의 핵심 개념을 소개하고, Java(Akka Classic), Kotlin(Pekko Typed), C#(Akka.NET) 세 플랫폼에서의 구현 차이를 비교합니다. 상세한 플랫폼별 패턴 문서로 안내하는 출발점 역할을 합니다.

---

## 목차

1. [액터모델이란?](#1-액터모델이란)
2. [액터의 핵심 개념](#2-액터의-핵심-개념)
3. [언어별 동시성 처리 비교](#3-언어별-동시성-처리-비교)
4. [플랫폼별 특성](#4-플랫폼별-특성)
5. [주요 패턴 분류](#5-주요-패턴-분류)
6. [문서 구성 안내](#6-문서-구성-안내)

---

## 1. 액터모델이란?

### 1.1 정의와 역사

액터 모델(Actor Model)은 1973년 **Carl Hewitt**가 제안한 동시성(Concurrency) 계산 모델이다. 이후 **Gul Agha**의 연구(1986)를 거쳐 이론적 기반이 확립되었고, **Erlang**(1986, Ericsson)에서 최초로 대규모 산업 적용이 이루어졌다. JVM 생태계에서는 **Akka**(2009, Lightbend)가 등장하면서 Scala/Java 개발자에게 액터 모델이 본격적으로 확산되었다.

> **핵심 아이디어**: 모든 것은 액터(Actor)이다. 액터는 메시지를 주고받으며, 각 액터는 자신만의 상태를 가지고, 동시에 실행되며, 락(Lock) 없이 안전한 동시성을 제공한다.

### 1.2 전통적인 스레드/락 기반 동시성 vs 액터 모델

| 구분 | 스레드/락 기반 | 액터 모델 |
|------|---------------|-----------|
| **상태 공유** | 공유 메모리(Shared Memory) | 격리된 상태(Isolated State) |
| **동기화** | Lock, Mutex, Semaphore | 메시지 패싱(Message Passing) |
| **교착 상태** | Deadlock 발생 가능 | 구조적으로 Deadlock 회피 |
| **확장성** | 스레드 수에 비례한 한계 | 수백만 액터 동시 운영 가능 |
| **장애 처리** | try-catch, 전역 에러 핸들링 | Supervision(감독) 전략 |
| **디버깅** | 경합 조건(Race Condition) 재현 어려움 | 메시지 순서 보장으로 예측 가능 |

전통적인 멀티스레드 프로그래밍에서는 공유 자원에 대한 접근을 `synchronized`, `lock`, `Mutex` 등으로 제어해야 한다. 이는 **경합 조건(Race Condition)**, **교착 상태(Deadlock)**, **우선순위 역전(Priority Inversion)** 등의 복잡한 문제를 유발한다.

액터 모델은 이러한 문제를 **근본적으로 회피**한다. 각 액터는 자신만의 상태를 가지고, 외부에서 직접 접근할 수 없으며, 오직 **메시지**를 통해서만 상호작용한다.

```
전통적 방식 (Shared State + Lock):

  Thread-A ──┐
              ├── Lock ──► [Shared State] ◄── Lock ──┤
  Thread-B ──┘                                       └── Thread-C

액터 모델 (Message Passing):

  Actor-A ──── msg ────► [Actor-B (Private State)]
  Actor-C ──── msg ──┘        │
                               └──── msg ────► [Actor-D (Private State)]
```

### 1.3 핵심 원칙

액터 모델의 세 가지 핵심 원칙:

1. **메시지 패싱(Message Passing)**: 액터 간 통신은 오직 비동기 메시지를 통해서만 이루어진다. 직접적인 메서드 호출이 아닌, 메일박스(Mailbox)에 메시지를 넣는 방식이다.

2. **격리된 상태(Isolated State)**: 각 액터는 자신만의 상태를 가지며, 다른 액터가 이 상태에 직접 접근할 수 없다. 상태 변경은 수신한 메시지를 처리하는 과정에서만 발생한다.

3. **비동기 처리(Asynchronous Processing)**: 메시지를 보내는 행위(Tell)는 즉시 반환된다. 발신자는 수신자의 처리 완료를 기다리지 않으며, 각 액터는 자신의 속도로 메시지를 처리한다.

---

## 2. 액터의 핵심 개념

### 2.1 Actor의 구성 요소

하나의 액터는 다음 세 가지 요소로 구성된다:

```
┌─────────────────────────────────┐
│           Actor                 │
│  ┌───────────────────────────┐  │
│  │  Mailbox (메시지 큐)       │  │  ◄── 외부에서 메시지 수신
│  └───────────┬───────────────┘  │
│              ▼                  │
│  ┌───────────────────────────┐  │
│  │  Behavior (행위/메시지핸들러)│  │  ◄── 메시지 타입에 따른 처리 로직
│  └───────────┬───────────────┘  │
│              ▼                  │
│  ┌───────────────────────────┐  │
│  │  State (내부 상태)          │  │  ◄── 외부 접근 불가, 메시지 처리 중에만 변경
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

- **State(상태)**: 액터 내부의 변수, 데이터. 외부에서 직접 접근할 수 없으며, 메시지 처리 과정에서만 변경된다.
- **Behavior(행위)**: 메시지를 받았을 때 어떻게 처리할지 정의하는 로직. 메시지 타입에 따라 다른 행위를 수행한다.
- **Mailbox(메일박스)**: 액터에게 전달된 메시지가 도착 순서대로 대기하는 큐(Queue). 한 번에 하나의 메시지만 처리한다.

### 2.2 메시지 전달 패턴

#### Tell (Fire-and-Forget)

가장 기본적인 메시지 전달 방식. 응답을 기다리지 않는 **단방향** 메시지 전송이다.

```
Actor-A ────── Tell(msg) ──────► Actor-B
  (즉시 반환, 응답 없음)
```

**Kotlin (Pekko Typed)**:
```kotlin
actorRef.tell(Hello("Hello", replyTo))
```

**Java (Akka Classic)**:
```java
actorRef.tell("Hello", getSelf());
```

**C# (Akka.NET)**:
```csharp
actorRef.Tell("Hello");
```

#### Ask (Request-Response)

응답을 기다리는 **양방향** 메시지 전달. 내부적으로 임시 액터를 생성하여 `Future`/`Task`로 결과를 반환한다.

```
Actor-A ────── Ask(msg) ──────► Actor-B
   │                              │
   │◄──── Future/Task(response) ──┘
```

**Kotlin (Pekko Typed)**:
```kotlin
val future: CompletionStage<HelloResponse> = AskPattern.ask(
    actorRef,
    { replyTo -> Hello("Hello", replyTo) },
    Duration.ofSeconds(3),
    system.scheduler()
)
```

**Java (Akka Classic)**:
```java
CompletionStage<Object> future = Patterns.ask(actorRef, "Hello", Duration.ofSeconds(3));
```

**C# (Akka.NET)**:
```csharp
var response = await actorRef.Ask<string>("Hello", TimeSpan.FromSeconds(3));
```

#### Forward

받은 메시지를 **원래 발신자 정보를 유지**한 채 다른 액터에게 전달한다.

```
Actor-A ── msg ──► Actor-B ── Forward(msg) ──► Actor-C
                     (Sender는 여전히 Actor-A)
```

#### Pipe

비동기 작업(Future/Task)의 결과를 다른 액터에게 메시지로 전달한다.

```
Actor-A ── msg ──► Actor-B ── (async work) ── Pipe(result) ──► Actor-C
```

### 2.3 액터 계층 구조 (Actor Hierarchy)

액터 시스템은 **트리 구조**로 구성된다. 모든 액터는 부모 액터(Parent Actor)에 의해 생성되며, 최상위에는 루트 가디언(Root Guardian)이 존재한다.

```
                    [Root Guardian]
                    /user    /system
                     │
              [User Guardian]
               /       \
        [Parent-A]    [Parent-B]
         /    \           |
   [Child-1] [Child-2] [Child-3]
```

- **/user Guardian**: 사용자가 생성한 모든 액터의 최상위 부모
- **/system Guardian**: 시스템 내부 액터의 최상위 부모
- **부모-자식 관계**: 부모가 자식을 생성하고, 자식의 장애를 감독한다

### 2.4 감독 전략 (Supervision Strategy)

부모 액터는 자식 액터의 장애를 감독하며, 장애 발생 시 **감독 전략**에 따라 대응한다.

| 전략 | 설명 |
|------|------|
| **Resume** | 장애를 무시하고 현재 상태를 유지한 채 계속 진행 |
| **Restart** | 액터를 재시작. 상태 초기화 후 재생성 |
| **Stop** | 액터를 영구 종료 |
| **Escalate** | 부모 액터에게 장애를 전파 (자신도 처리할 수 없는 경우) |

```
[Supervisor (Parent)]
        │
        │  ── 자식 장애 감지 ──►  결정: Resume / Restart / Stop / Escalate
        │
   [Child Actor]  (장애 발생!)
```

**Kotlin (Pekko Typed) 감독 전략 적용**:
```kotlin
val childActor = context.spawn(
    Behaviors.supervise(HelloStateStoreActor.create(persistId, durableRepository))
        .onFailure(SupervisorStrategy.restart().withLoggingEnabled(true)),
    childName
)
```

**C# (Akka.NET) 감독 전략 적용**:
```csharp
protected override SupervisorStrategy SupervisorStrategy()
{
    return new OneForOneStrategy(
        maxNrOfRetries: 3,
        withinTimeRange: TimeSpan.FromMinutes(1),
        localOnlyDecider: ex =>
        {
            if (ex is ArithmeticException) return Directive.Resume;
            if (ex is ArgumentException) return Directive.Stop;
            return Directive.Restart;
        });
}
```

### 2.5 액터 생명주기 (Lifecycle)

```
  ┌──────────┐
  │ 생성 요청  │
  └────┬─────┘
       ▼
  ┌──────────┐
  │ PreStart │ ◄── 최초 시작 시 1회 호출
  └────┬─────┘
       ▼
  ┌──────────┐
  │ 메시지 처리 │ ◄── 정상 동작 (Mailbox에서 메시지를 꺼내 처리)
  └────┬─────┘
       │
  ┌────┴──── 장애 발생 시 ─────┐
  │                            ▼
  │                     ┌──────────────┐
  │                     │ PreRestart   │ ◄── 재시작 전 정리 작업
  │                     └──────┬───────┘
  │                            ▼
  │                     ┌──────────────┐
  │                     │ PostRestart  │ ◄── 재시작 후 초기화
  │                     └──────┬───────┘
  │                            │
  │◄───────────────────────────┘
  │
  ▼ (종료 시)
  ┌──────────┐
  │ PostStop │ ◄── 종료 시 정리 작업
  └──────────┘
```

| Lifecycle Hook | 설명 | Akka Classic (Java) | Pekko Typed (Kotlin) | Akka.NET (C#) |
|----------------|------|---------------------|---------------------|---------------|
| **PreStart** | 액터 최초 시작 | `preStart()` | `Behaviors.setup { }` | `PreStart()` |
| **PostStop** | 액터 종료 시 정리 | `postStop()` | `onSignal(PostStop)` | `PostStop()` |
| **PreRestart** | 재시작 전 정리 | `preRestart()` | `SupervisorStrategy` 설정 | `PreRestart()` |
| **PostRestart** | 재시작 후 초기화 | `postRestart()` | `SupervisorStrategy` 설정 | `PostRestart()` |

---

## 3. 언어별 동시성 처리 비교

### 3.1 Java/Kotlin

#### JVM 기반 동시성 기본 도구

| 도구 | 설명 |
|------|------|
| `Thread` | OS 스레드에 1:1 매핑. 스레드 생성 비용이 크다 |
| `ExecutorService` | 스레드 풀 관리. `submit()`, `invokeAll()` |
| `CompletableFuture` | 비동기 연산 조합. `thenApply()`, `thenCompose()` |
| `synchronized` / `ReentrantLock` | 전통적 락 기반 동기화 |
| Kotlin `Coroutine` | 경량 스레드. `suspend`, `launch`, `async` |

#### Akka Classic (Java) - Untyped API

Java에서 Akka Classic을 사용할 때는 `AbstractActor`를 상속하고, `receiveBuilder().match()` 패턴으로 메시지를 처리한다. 메시지 타입에 대한 컴파일 타임 검증이 없는 **Untyped** 방식이다.

```java
// Java + Akka Classic: AbstractActor 기반
public class HelloWorld extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props Props() {
        return Props.create(HelloWorld.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, s -> {
                log.info("Received: {}", s);
                getSender().tell("world", getSelf());
            })
            .match(ActorRef.class, actorRef -> {
                this.probe = actorRef;
                getSender().tell("done", getSelf());
            })
            .matchAny(o -> log.info("received unknown message"))
            .build();
    }
}
```

#### Akka/Pekko Typed (Kotlin) - Typed API

Kotlin에서 Pekko Typed를 사용하면 `AbstractBehavior<T>`를 상속하고, **sealed class**로 메시지 계층을 정의한다. 컴파일 타임에 메시지 타입이 검증되는 **Typed** 방식이다.

```kotlin
// Kotlin + Pekko Typed: AbstractBehavior<T> 기반
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
            .onSignal(PostStop::class.java, this::onPostStop)
            .build()
    }

    private fun onHello(command: Hello): Behavior<HelloCommand> {
        context.log.info("Received: ${command.message}")
        command.replyTo.tell(HelloResponse("Kotlin"))
        return this
    }
}
```

#### Kotlin Coroutine과 액터의 조합

Kotlin Coroutine의 `Channel`과 `actor` 빌더를 통해 경량 액터 패턴을 구현할 수도 있지만, 프로덕션 레벨의 감독 전략, 클러스터링, 영속화 등은 Pekko/Akka가 훨씬 강력하다. 실무에서는 **Pekko Typed + Kotlin Coroutine**을 조합하여 사용하는 것이 효과적이다.

### 3.2 C#/.NET

#### .NET 동시성 기본 도구

| 도구 | 설명 |
|------|------|
| `Task` / `Task<T>` | .NET의 비동기 작업 단위 |
| `async` / `await` | 비동기 프로그래밍 키워드 |
| `Channel<T>` | 고성능 생산자-소비자 큐 |
| `TPL Dataflow` | 데이터 흐름 기반 병렬 처리 |
| `lock` / `SemaphoreSlim` | 동기화 프리미티브 |

#### Akka.NET (C#) - ReceiveActor 기반

C#에서 Akka.NET을 사용할 때는 `ReceiveActor`를 상속하고, `Receive<T>()` 제네릭 메서드로 메시지 핸들러를 등록한다. .NET DI(Dependency Injection)와 HOCON 설정 파일을 통해 시스템을 구성한다.

```csharp
// C# + Akka.NET: ReceiveActor 기반
public class BasicActor : ReceiveActor
{
    private readonly ILoggingAdapter logger = Context.GetLogger();

    public BasicActor()
    {
        Receive<string>(msg =>
        {
            logger.Info($"Received: {msg}");
            Sender.Tell("world");
        });

        Receive<Todo>(msg =>
        {
            logger.Info($"Received Todo: {msg.Title}");
            Sender.Tell(msg);
        });

        ReceiveAsync<DelayCommand>(async msg =>
        {
            await Task.Delay(msg.Delay);
            Sender.Tell("completed");
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
```

### 3.3 공통점과 차이점 요약표

| 항목 | Akka Classic (Java) | Pekko Typed (Kotlin) | Akka.NET (C#) |
|------|---------------------|---------------------|---------------|
| **기반 클래스** | `AbstractActor` | `AbstractBehavior<T>` | `ReceiveActor` |
| **메시지 타입** | Untyped (`Object`) | Typed (`sealed class`) | Semi-Typed (`Receive<T>`) |
| **액터 참조** | `ActorRef` (untyped) | `ActorRef<T>` (typed) | `IActorRef` (untyped) |
| **생성 방식** | `Props.create(Class)` | `Behaviors.setup { }` | `Props.Create(() => new Actor())` |
| **메시지 매칭** | `receiveBuilder().match()` | `newReceiveBuilder().onMessage()` | `Receive<T>(handler)` |
| **비동기 처리** | `CompletableFuture` | `CompletionStage` + Coroutine | `Task` + `async/await` |
| **설정 방식** | `application.conf` (HOCON) | `application.conf` (HOCON) | HOCON 설정 파일 |
| **의존성 주입** | Spring 연동 | Spring 연동 | .NET DI 기본 지원 |
| **빌드 도구** | Gradle / Maven | Gradle (Kotlin DSL) | .NET CLI / MSBuild |

---

## 4. 플랫폼별 특성

### 4.1 Akka Classic (Java)

| 항목 | 내용 |
|------|------|
| **개발사** | Lightbend (구 Typesafe) |
| **언어** | Scala, Java |
| **버전** | Akka 2.7.x (현재) |
| **라이선스** | BSL (Business Source License) - 2.7.x부터 변경 |
| **특징** | Untyped API, `receiveBuilder().match()` 패턴 |

Akka Classic은 오랜 역사를 가진 성숙한 프레임워크이다. Java에서 사용할 때는 `AbstractActor`를 상속하며, 모든 메시지가 `Object` 타입으로 전달되는 **Untyped** 방식이다. 2022년 Lightbend가 라이선스를 BSL로 변경하면서 오픈소스 커뮤니티에서 Apache Pekko가 포크되었다.

```java
// Akka Classic 특징: match() 기반 패턴 매칭
@Override
public Receive createReceive() {
    return receiveBuilder()
        .match(String.class, msg -> { /* 문자열 처리 */ })
        .match(Integer.class, msg -> { /* 숫자 처리 */ })
        .matchAny(msg -> log.info("Unknown: {}", msg))
        .build();
}
```

### 4.2 Apache Pekko (Kotlin)

| 항목 | 내용 |
|------|------|
| **개발** | Apache Software Foundation |
| **기원** | Akka 2.6.x의 오픈소스 포크 |
| **버전** | Pekko 1.1.x |
| **라이선스** | Apache License 2.0 |
| **특징** | Typed API 중심, Akka와 거의 1:1 호환 (import 패키지만 다름) |

Apache Pekko는 Akka의 오픈소스 포크이다. Akka와 거의 동일한 API를 제공하며, 주요 차이는 **패키지 이름**뿐이다:

```
Akka:   import akka.actor.typed.javadsl.AbstractBehavior
Pekko:  import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
```

Kotlin에서 Pekko Typed를 사용하면 `sealed class`로 명령 계층을 정의하여 컴파일 타임 타입 안전성을 확보할 수 있다.

```kotlin
// Pekko Typed 특징: sealed class 명령 계층
sealed class HelloCommand
data class Hello(val message: String, val replyTo: ActorRef<HelloCommand>) : HelloCommand()
data class HelloResponse(val message: String) : HelloCommand()

// Typed ActorRef - 잘못된 메시지 타입은 컴파일 에러
val actorRef: ActorRef<HelloCommand> = ...
actorRef.tell(Hello("Hi", replyTo))    // OK
// actorRef.tell("plain string")       // 컴파일 에러!
```

### 4.3 Akka.NET (C#)

| 항목 | 내용 |
|------|------|
| **개발사** | Petabridge |
| **언어** | C#, F# |
| **버전** | Akka.NET 1.5.x |
| **라이선스** | Apache License 2.0 |
| **특징** | .NET 생태계 독립 구현, HOCON 설정, .NET DI 통합 |

Akka.NET은 JVM Akka의 개념을 .NET 생태계에 맞게 **독립적으로 구현**한 프레임워크이다. 포트(port)가 아닌 재구현(re-implementation)이므로 .NET의 `async/await`, DI 컨테이너, HOCON 설정 등을 자연스럽게 지원한다.

```csharp
// Akka.NET 특징: Receive<T>() 제네릭 패턴 + HOCON 설정

// 액터 정의
public class MyActor : ReceiveActor
{
    public MyActor()
    {
        Receive<string>(msg => Sender.Tell($"Echo: {msg}"));
        Receive<int>(msg => Sender.Tell(msg * 2));
    }
}

// HOCON 설정 (app.conf)
// akka {
//     actor {
//         provider = remote
//     }
//     remote {
//         dot-netty.tcp {
//             port = 8081
//             hostname = "localhost"
//         }
//     }
// }
```

### 4.4 플랫폼 비교 요약

```
┌────────────────────────────────────────────────────────────────────┐
│                      Actor Model Ecosystem                        │
├────────────────┬───────────────────┬───────────────────────────────┤
│ Akka Classic   │ Apache Pekko      │ Akka.NET                      │
│ (Java)         │ (Kotlin)          │ (C#)                          │
├────────────────┼───────────────────┼───────────────────────────────┤
│ Lightbend      │ Apache Foundation │ Petabridge                    │
│ BSL License    │ Apache 2.0        │ Apache 2.0                    │
│ Untyped API    │ Typed API         │ ReceiveActor                  │
│ JVM            │ JVM               │ .NET CLR                      │
│ Akka 2.7.x     │ Pekko 1.1.x       │ Akka.NET 1.5.x                │
│ Scala/Java     │ Kotlin/Scala/Java │ C#/F#                         │
└────────────────┴───────────────────┴───────────────────────────────┘
```

---

## 5. 주요 패턴 분류

이 프로젝트에서 다루는 액터 모델 패턴을 플랫폼별로 정리한 표이다. 각 패턴의 상세 구현은 플랫폼별 하위 문서에서 다룬다.

### 5.1 기본 메시징 패턴

| 패턴 | 설명 | Java (Akka Classic) | Kotlin (Pekko Typed) | C# (Akka.NET) |
|------|------|:-------------------:|:--------------------:|:--------------:|
| **Tell** | 단방향 메시지 전달 (Fire-and-Forget) | O | O | O |
| **Ask** | 응답 대기 메시지 (Request-Response) | O | O | O |
| **Forward** | 원본 Sender 유지하며 메시지 전달 | O | O | O |
| **Pipe** | 비동기 결과를 메시지로 전달 | O | O | O |

### 5.2 구조 및 관리 패턴

| 패턴 | 설명 | Java (Akka Classic) | Kotlin (Pekko Typed) | C# (Akka.NET) |
|------|------|:-------------------:|:--------------------:|:--------------:|
| **Router** | 메시지 라우팅 (Round-Robin, Scatter-Gather 등) | O | O | O |
| **Supervision** | 장애 감독 및 복구 전략 | O | O | O |
| **Timer** | 타이머 기반 스케줄링 처리 | O | O | O |
| **FSM/Batch** | 유한 상태 기계(FSM) 기반 배치 처리 | O | O | O |

### 5.3 고급 패턴

| 패턴 | 설명 | Java (Akka Classic) | Kotlin (Pekko Typed) | C# (Akka.NET) |
|------|------|:-------------------:|:--------------------:|:--------------:|
| **Persistence** | 상태 영속화 (이벤트 소싱 / Durable State) | - | O (DurableState) | O (RavenDB) |
| **Cluster** | 멀티 노드 클러스터링 | O | O | - |
| **PubSub** | 발행-구독 (Publish-Subscribe) 패턴 | - | O | - |

### 5.4 스트리밍 및 실시간 패턴

| 패턴 | 설명 | Java (Akka Classic) | Kotlin (Pekko Typed) | C# (Akka.NET) |
|------|------|:-------------------:|:--------------------:|:--------------:|
| **Streams/Throttle** | 스트림 처리 및 흐름 제어 (Back-pressure) | O | O | O |
| **SSE** | Server-Sent Events 실시간 스트리밍 | - | O | O |
| **WebSocket** | WebSocket 세션 관리 | - | O | - |

### 5.5 AI 에이전트 패턴

| 패턴 | 설명 | Java (Akka Classic) | Kotlin (Pekko Typed) | C# (Akka.NET) |
|------|------|:-------------------:|:--------------------:|:--------------:|
| **AI-Agent** | AI 에이전트 파이프라인 (LLM 통합) | - | - | O (Memorizer) |

> **범례**: `O` = 해당 프로젝트에서 구현 사례 있음, `-` = 해당 프로젝트에서 미구현

### 5.6 패턴별 핵심 코드 비교 (Tell 패턴)

세 플랫폼에서 가장 기본적인 **Tell** 패턴의 코드 차이를 비교한다:

#### Java (Akka Classic)
```java
// 액터 생성
ActorRef helloActor = system.actorOf(HelloWorld.Props(), "helloActor");

// 메시지 전송 (Tell)
helloActor.tell("Hello", ActorRef.noSender());
```

#### Kotlin (Pekko Typed)
```kotlin
// 액터 생성
val helloActor: ActorRef<HelloCommand> = context.spawn(HelloActor.create(), "helloActor")

// 메시지 전송 (Tell)
helloActor.tell(Hello("Hello", replyTo))
```

#### C# (Akka.NET)
```csharp
// 액터 생성
IActorRef helloActor = system.ActorOf(Props.Create<BasicActor>(), "helloActor");

// 메시지 전송 (Tell)
helloActor.Tell("Hello");
```

---

## 6. 문서 구성 안내

이 문서를 기점으로 각 플랫폼의 상세 패턴 문서가 하위 디렉토리에 구성되어 있다.

### 6.1 디렉토리 구조

```
docs/actor/
├── 00-actor-model-overview.md          ◄── 현재 문서 (액터모델 개요)
├── 01-java-akka-classic/              ◄── Java + Akka Classic 패턴 문서
│   ├── 01-tell-ask-forward.md
│   ├── 02-router.md
│   ├── 03-supervision.md
│   ├── 04-timer.md
│   ├── 05-fsm-batch.md
│   ├── 06-streams-throttle.md
│   └── 07-cluster.md
├── 02-kotlin-pekko-typed/             ◄── Kotlin + Pekko Typed 패턴 문서
│   ├── 01-tell-ask-forward.md
│   ├── 02-router.md
│   ├── 03-supervision.md
│   ├── 04-timer.md
│   ├── 05-fsm-batch.md
│   ├── 06-persistence-durable-state.md
│   ├── 07-cluster.md
│   ├── 08-pubsub.md
│   ├── 09-streams-throttle.md
│   ├── 10-sse.md
│   └── 11-websocket.md
├── 03-dotnet-akka-net/                ◄── .NET + Akka.NET 패턴 문서
│   ├── 01-tell-ask-forward.md
│   ├── 02-router.md
│   ├── 03-supervision.md
│   ├── 04-timer.md
│   ├── 05-fsm-batch.md
│   ├── 06-persistence-ravendb.md
│   ├── 07-streams-throttle.md
│   └── 08-sse.md
├── 04-memorizer-ai-agent/             ◄── AI-Agent 활용 사례 (Memorizer)
│   ├── 01-ai-agent-pipeline.md
│   └── 02-memorizer-architecture.md
└── 05-cross-platform-comparison/      ◄── 크로스 플랫폼 비교 문서
    ├── 01-concurrency-model-comparison.md
    └── 02-pattern-migration-guide.md
```

### 6.2 문서별 역할

| 문서 경로 | 설명 | 주요 내용 |
|-----------|------|-----------|
| **01-java-akka-classic/** | Java + Akka Classic | `AbstractActor`, `receiveBuilder().match()`, `Props`, Untyped `ActorRef`, Streams/Throttle, Cluster |
| **02-kotlin-pekko-typed/** | Kotlin + Pekko Typed | `AbstractBehavior<T>`, `sealed class`, Typed `ActorRef<T>`, DurableState, Cluster, PubSub, SSE, WebSocket |
| **03-dotnet-akka-net/** | .NET + Akka.NET | `ReceiveActor`, `Receive<T>()`, `IActorRef`, HOCON 설정, .NET DI, RavenDB Persistence, SSE |
| **04-memorizer-ai-agent/** | AI-Agent 활용 사례 | Akka.NET 기반 AI 에이전트 파이프라인, LLM 통합, Memorizer 아키텍처 |
| **05-cross-platform-comparison/** | 크로스 플랫폼 비교 | 동시성 모델 비교, 플랫폼 간 패턴 마이그레이션 가이드 |

### 6.3 읽기 순서 권장

```
1. 00-actor-model-overview.md  (현재 문서)
   │
   ├── 2a. 01-java-akka-classic/       (Java 개발자)
   ├── 2b. 02-kotlin-pekko-typed/      (Kotlin 개발자)
   └── 2c. 03-dotnet-akka-net/         (C# 개발자)
           │
           ├── 3. 04-memorizer-ai-agent/       (AI-Agent에 관심이 있는 경우)
           └── 4. 05-cross-platform-comparison/ (멀티 플랫폼 비교가 필요한 경우)
```

### 6.4 참고 프로젝트

이 문서 시리즈에서 분석하고 참고하는 소스 프로젝트:

| 프로젝트 | 언어/플랫폼 | 경로                                                                                |
|----------|------------|-----------------------------------------------------------------------------------|
| **java-labs/springweb** | Java + Akka Classic + Spring | `https://github.com/psmon/java-labs`                                              |
| **java-labs/KotlinBootLabs** | Kotlin + Akka Typed + Spring Boot | `https://github.com/psmon/java-labs/tree/master/KotlinBootLabs`                     |
| **KotlinBootReactiveLabs** | Kotlin + Pekko Typed + Spring WebFlux | `https://github.com/psmon/kopring-reactive-labs/tree/main/KotlinBootReactiveLabs` |
| **NetCoreLabs** | C# + Akka.NET + ASP.NET Core | `https://github.com/psmon/NetCoreLabs`                                                     |
| **memorizer-v1** | C# + Akka.NET + AI Agent | `https://github.com/psmon/memorizer-v1`                                                         |

---

## 부록: 용어 정리

| 용어 | 영문 | 설명 |
|------|------|------|
| 액터 | Actor | 상태 + 행위 + 메일박스를 가진 동시성 처리 단위 |
| 메일박스 | Mailbox | 액터에게 전달된 메시지가 순서대로 대기하는 FIFO 큐 |
| 감독 | Supervision | 부모 액터가 자식 액터의 장애를 감시하고 대응하는 메커니즘 |
| 액터 시스템 | ActorSystem | 액터를 생성하고 관리하는 최상위 컨테이너 |
| 행위 | Behavior | 액터가 메시지를 수신했을 때 수행하는 처리 로직 |
| Props | Props | 액터 생성에 필요한 설정(클래스, 인자 등)을 담는 객체 |
| HOCON | Human-Optimized Config Object Notation | Akka/Pekko/Akka.NET에서 사용하는 설정 파일 형식 |
| Typed Actor | Typed Actor | 메시지 타입이 컴파일 타임에 검증되는 액터 (Pekko Typed) |
| Untyped Actor | Untyped Actor | 메시지가 `Object`로 전달되어 런타임에 매칭하는 액터 (Akka Classic) |
| DurableState | Durable State | 액터의 상태를 영구 저장소에 저장하는 영속화 방식 |
| Back-pressure | Back-pressure | 소비자가 처리할 수 있는 속도에 맞춰 생산자의 속도를 조절하는 흐름 제어 |
| FSM | Finite State Machine | 유한 상태 기계. 상태 전이를 통해 복잡한 로직을 관리 |
| SSE | Server-Sent Events | 서버에서 클라이언트로의 단방향 실시간 스트리밍 프로토콜 |

---

> **다음 단계**: 자신의 개발 환경에 맞는 플랫폼별 문서로 이동하여 구체적인 패턴 구현 코드를 확인하세요.
