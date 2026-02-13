# .NET + Akka.NET 액터 모델 완벽 가이드

> **프로젝트 정보**: NetCoreLabs (ActorLib, BlazorActorApp, McpServer, ActorLibTest)
> **런타임**: .NET 9.0 | **Akka.NET**: 1.5.40
> **주요 의존성**: Akka, Akka.Remote, Akka.Streams, Akka.DependencyInjection, Akka.Persistence.RavenDB

---

## 목차

1. [액터 모델 개요](#1-액터-모델-개요)
2. [기본 액터 (Basic Actor)](#2-기본-액터-basic-actor)
3. [ActorSystem 관리 및 DI 통합](#3-actorsystem-관리-및-di-통합)
4. [FSM 배치 처리 (Finite State Machine)](#4-fsm-배치-처리-finite-state-machine)
5. [흐름 제어 - Stream 기반 Throttle](#5-흐름-제어---stream-기반-throttle)
6. [흐름 제어 - Timer 기반 Throttle](#6-흐름-제어---timer-기반-throttle)
7. [라우팅 (Routing)](#7-라우팅-routing)
8. [디스패처 (Dispatcher)](#8-디스패처-dispatcher)
9. [메일박스 설정 (Mailbox)](#9-메일박스-설정-mailbox)
10. [감독 전략 (Supervision)](#10-감독-전략-supervision)
11. [영속화 (Persistence with RavenDB)](#11-영속화-persistence-with-ravendb)
12. [SSE (Server-Sent Events) 액터](#12-sse-server-sent-events-액터)
13. [MCP Server 액터](#13-mcp-server-액터)
14. [원격 액터 (Remote Actors)](#14-원격-액터-remote-actors)
15. [테스트 패턴 (TestKit)](#15-테스트-패턴-testkit)
16. [ASP.NET Core 통합 패턴](#16-aspnet-core-통합-패턴)

---

## 1. 액터 모델 개요

### 액터 모델이란?

액터 모델은 동시성(concurrency) 처리를 위한 수학적 모델이다. 각 액터는 독립적인 연산 단위로, 다음 세 가지 행동만 수행한다:

- **메시지 수신**: 다른 액터로부터 메시지를 받는다
- **상태 변경**: 내부 상태를 변경한다
- **새 액터 생성 또는 메시지 전송**: 자식 액터를 만들거나 다른 액터에게 메시지를 보낸다

### Akka.NET의 특징 (JVM Akka와의 차이)

| 항목 | JVM Akka (Scala/Java) | Akka.NET (C#) |
|------|----------------------|---------------|
| 설정 형식 | HOCON (application.conf) | HOCON (코드 내 문자열 또는 파일) |
| 액터 베이스 클래스 | `AbstractActor` | `ReceiveActor`, `UntypedActor` |
| 패턴 매칭 | Scala pattern matching | `Receive<T>()` 메서드 체이닝 |
| DI 통합 | Guice, Spring | `Akka.DependencyInjection`, `Akka.Hosting` |
| 네트워크 전송 | Netty (Java) | DotNetty (C# 포팅) |
| 테스트 프레임워크 | `akka-testkit` | `Akka.TestKit.Xunit2` |
| Persistence 백엔드 | 다양함 | RavenDB, SQL Server 등 |

### 프로젝트 의존성 구조

```xml
<!-- ActorLib.csproj - 핵심 라이브러리 -->
<PackageReference Include="Akka" Version="1.5.40" />
<PackageReference Include="Akka.Remote" Version="1.5.40" />
<PackageReference Include="Akka.Streams" Version="1.5.40" />
<PackageReference Include="Akka.DependencyInjection" Version="1.5.40" />
<PackageReference Include="Akka.Persistence.RavenDB" Version="1.5.37" />

<!-- ActorLibTest.csproj - 테스트 -->
<PackageReference Include="Akka.TestKit.Xunit2" Version="1.5.40" />
```

---

## 2. 기본 액터 (Basic Actor)

### 개념

`ReceiveActor`는 Akka.NET에서 가장 많이 사용되는 액터 기본 클래스이다. 생성자에서 `Receive<T>()` 메서드를 호출하여 메시지 타입별 핸들러를 등록한다. 각 메시지는 타입에 따라 자동으로 라우팅되며, 하나의 액터는 한 번에 하나의 메시지만 처리한다(단일 스레드 보장).

### 메시지 모델 정의

메시지는 **불변 객체(immutable object)** 로 설계하는 것이 원칙이다. 액터 간 공유 상태 없이 메시지만으로 통신하기 때문이다.

```csharp
// TestModels.cs
public class DelayCommand
{
    public int Delay { get; set; }
    public string Message { get; set; }
    public DelayCommand(string message, int delay)
    {
        Delay = delay;
        Message = message;
    }
}

public class MessageCommand
{
    public string Message { get; set; }
    public MessageCommand(string message)
    {
        Message = message;
    }
}

public class RemoteCommand
{
    public string Message { get; set; }
    public RemoteCommand(string message)
    {
        Message = message;
    }
}
```

### BasicActor 구현

```csharp
// ActorLib/Actor/Test/BasicActor.cs
public class BasicActor : ReceiveActor
{
    private readonly ILoggingAdapter logger = Context.GetLogger();
    private IActorRef? testProbe;

    public BasicActor()
    {
        // 1) IActorRef 메시지: 테스트 프로브 등록
        Receive<IActorRef>(actorRef =>
        {
            testProbe = actorRef;
            testProbe.Tell("done");
        });

        // 2) Todo 메시지 처리
        Receive<Todo>(msg =>
        {
            if (testProbe != null)
            {
                testProbe.Tell(msg);
            }
        });

        // 3) 비동기 메시지 처리 (ReceiveAsync)
        ReceiveAsync<DelayCommand>(async msg =>
        {
            await Task.Delay(msg.Delay);
            if (testProbe != null)
                testProbe.Tell("world");
            else
                Sender.Tell("world");
        });

        // 4) 문자열 메시지 처리
        Receive<string>(msg =>
        {
            if (testProbe != null)
                testProbe.Tell("world");
            else
                Sender.Tell("world");
        });

        // 5) MessageCommand 처리
        Receive<MessageCommand>(msg =>
        {
            if (testProbe != null)
                testProbe.Tell(msg.Message);
            else
                Sender.Tell(msg.Message);
        });

        // 6) Issue 메시지 (우선순위 메일박스 테스트용)
        Receive<Issue>(msg =>
        {
            logger.Info($"ReceiveIssue:{msg.ToJsonString()}");
            if (testProbe != null)
            {
                Task.Delay(200).Wait();
                testProbe.Tell(msg);
            }
            else
                Sender.Tell(msg);
        });

        // 7) RemoteCommand 처리
        Receive<RemoteCommand>(msg =>
        {
            logger.Info($"ReceiveRemoteCommand:{msg.Message} Path:{Self.Path}");
        });
    }

    // 액터 라이프사이클 후크
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

### 핵심 API 설명

| API | 설명 |
|-----|------|
| `Receive<T>(Action<T>)` | 특정 타입 `T`의 메시지를 동기적으로 처리하는 핸들러 등록 |
| `ReceiveAsync<T>(Func<T, Task>)` | 비동기 메시지 핸들러 등록 (`await` 사용 가능) |
| `Sender` | 현재 메시지를 보낸 액터의 `IActorRef` |
| `Self` | 자기 자신의 `IActorRef` |
| `Context` | 액터의 실행 컨텍스트 (자식 생성, 로거 접근 등) |
| `Tell(message)` | 비동기 메시지 전송 (fire-and-forget) |
| `Ask(message)` | 응답을 기다리는 메시지 전송 (`Task<object>` 반환) |
| `Props.Create<T>()` | 액터 인스턴스 생성을 위한 팩토리 설정 |
| `PreStart()` | 액터 시작 시 호출되는 라이프사이클 훅 |
| `PostStop()` | 액터 종료 시 호출되는 라이프사이클 훅 |

### 액터 생성과 메시지 전송

```csharp
// ActorSystem에서 액터 생성
var actorSystem = ActorSystem.Create("MySystem");
var basicActor = actorSystem.ActorOf(Props.Create(() => new BasicActor()), "basic");

// 메시지 전송 (fire-and-forget)
basicActor.Tell("hello");

// 응답을 기다리는 메시지 전송
var response = await basicActor.Ask<string>("hello", TimeSpan.FromSeconds(3));
```

> **Akka.NET 주의사항**: `ReceiveAsync`를 사용하면 해당 메시지 처리가 끝날 때까지 다음 메시지를 처리하지 않는다. 액터의 단일 스레드 보장은 유지되지만, `await` 중 메일박스에 쌓인 메시지는 대기하게 된다.

---

## 3. ActorSystem 관리 및 DI 통합

### 개념

`ActorSystem`은 Akka.NET의 최상위 컨테이너이다. 모든 액터는 특정 `ActorSystem` 내에서 생성되며, 시스템은 스레드 풀, 디스패처, 메일박스 등을 관리한다. 하나의 애플리케이션에서 여러 `ActorSystem`을 운영할 수 있다.

### AkkaService - ActorSystem 관리 클래스

```csharp
// ActorLib/AkkaService.cs
public class AkkaService
{
    private Dictionary<string, ActorSystem> actorSystems = new Dictionary<string, ActorSystem>();
    private Dictionary<string, IActorRef> actors = new Dictionary<string, IActorRef>();

    public ActorSystem CreateActorSystem(string name, int port = 0)
    {
        if (!actorSystems.ContainsKey(name))
        {
            if (port == 0)
            {
                // 기본 설정으로 생성
                actorSystems[name] = ActorSystem.Create(name);
            }
            else
            {
                // 원격(Remote) 모드로 생성 - HOCON 설정 포함
                string akkaConfig = @"
                akka {
                    loggers = [""Akka.Logger.NLog.NLogLogger, Akka.Logger.NLog""]
                    actor {
                        provider = remote
                    }
                    remote {
                        dot-netty.tcp {
                            port = $port
                            hostname = ""127.0.0.1""
                        }
                    }
                }
                my-custom-mailbox {
                    mailbox-type : ""ActorLib.Actor.Test.IssueTrackerMailbox, ActorLib""
                }
                ";

                akkaConfig = akkaConfig.Replace("$port", port.ToString());
                var config = ConfigurationFactory.ParseString(akkaConfig);
                actorSystems[name] = ActorSystem.Create(name, config);
            }
        }
        else
        {
            throw new Exception($"{name} actorsystem has already been created.");
        }
        return actorSystems[name];
    }

    public void SetDeafaultSystem(ActorSystem _actorSystem)
    {
        if (actorSystems.Count == 0)
            actorSystems["default"] = _actorSystem;
        else
            throw new Exception("The actor system has already been created.");
    }

    public ActorSystem GetActorSystem(string name = "default")
    {
        ActorSystem firstOrDefault = null;
        if (!actorSystems.ContainsKey(name))
        {
            firstOrDefault = string.IsNullOrEmpty(name)
                ? CreateActorSystem("ActorSystem")
                : CreateActorSystem(name);
        }
        else
        {
            firstOrDefault = actorSystems[name];
        }
        return firstOrDefault;
    }

    // 액터 레지스트리 기능
    public void AddActor(string name, IActorRef actor)
    {
        if (!actors.ContainsKey(name))
            actors[name] = actor;
    }

    public IActorRef GetActor(string name)
    {
        return actors.ContainsKey(name) ? actors[name] : null;
    }
}
```

### 설계 포인트

- **다중 ActorSystem 지원**: `Dictionary<string, ActorSystem>`으로 여러 시스템을 이름 기반으로 관리한다
- **액터 레지스트리**: 주요 액터를 이름으로 등록/조회할 수 있어 DI 컨테이너에서 접근이 용이하다
- **원격 모드 자동 전환**: `port` 파라미터가 0이 아니면 자동으로 `Akka.Remote` 설정을 활성화한다

### ASP.NET Core에서의 등록

```csharp
// BlazorActorApp/Program.cs
var builder = WebApplication.CreateBuilder(args);

// AkkaService를 싱글톤으로 등록
builder.Services.AddSingleton<AkkaService>();

var app = builder.Build();

// ActorSystem 초기화
var akkaService = app.Services.GetRequiredService<AkkaService>();

// 포트 9000으로 원격 가능한 ActorSystem 생성
var actorSystem = akkaService.CreateActorSystem("default", 9000);

// 액터 생성 및 등록
var defaultMonitor = actorSystem.ActorOf(Props.Create<SimpleMonitorActor>());
akkaService.AddActor("defaultMonitor", defaultMonitor);

// 두 번째 ActorSystem (원격 테스트용)
var actorSystem2 = akkaService.CreateActorSystem("default2", 9001);

// 원격 테스트용 액터 생성
actorSystem.ActorOf(Props.Create<BasicActor>(), "someActor");
actorSystem2.ActorOf(Props.Create<BasicActor>(), "someActor");
```

---

## 4. FSM 배치 처리 (Finite State Machine)

### 개념

FSM(Finite State Machine, 유한 상태 기계)은 액터가 **상태(State)** 와 **데이터(Data)** 를 가지며, 현재 상태에 따라 메시지를 다르게 처리하는 패턴이다. Akka.NET은 `FSM<TState, TData>` 기본 클래스를 제공하며, 상태 전환 시 자동으로 `OnTransition` 콜백이 호출된다.

배치 처리에 FSM을 활용하면, 개별 메시지를 즉시 처리하는 대신 일정 시간 동안 버퍼에 모아 한꺼번에 처리할 수 있다.

### 상태와 메시지 모델

```csharp
// ActorLib/Actor/Tools/FSMBatch/FSMBatchModels.cs

// --- 상태 정의 ---
public enum State
{
    Idle,    // 대기 상태: 아무 메시지도 큐에 없음
    Active   // 활성 상태: 메시지가 큐에 쌓이는 중
}

// --- 데이터 인터페이스 ---
public interface IData { }

// 초기화되지 않은 상태의 데이터
public class Uninitialized : IData
{
    public static Uninitialized Instance = new();
    private Uninitialized() { }
}

// 배치 대상 큐를 가진 데이터
public class Todo : IData
{
    public Todo(IActorRef target, ImmutableList<object> queue)
    {
        Target = target;
        Queue = queue;
    }
    public IActorRef Target { get; }
    public ImmutableList<object> Queue { get; }
    public Todo Copy(ImmutableList<object> queue) => new Todo(Target, queue);
}

// --- 수신 이벤트 (들어오는 메시지) ---
public class SetTarget
{
    public SetTarget(IActorRef @ref) { Ref = @ref; }
    public IActorRef Ref { get; }
}

public class Queue
{
    public Queue(object obj) { Obj = obj; }
    public Object Obj { get; }
}

public class Flush { }

// --- 송신 이벤트 (나가는 메시지) ---
public class Batch
{
    public Batch(ImmutableList<object> obj) { Obj = obj; }
    public ImmutableList<object> Obj { get; }
}
```

### FSMBatchActor 구현

```csharp
// ActorLib/Actor/Tools/FSMBatch/FSMBatchActor.cs
public class FSMBatchActor : FSM<State, IData>
{
    private readonly ILoggingAdapter _log = Context.GetLogger();

    public FSMBatchActor()
    {
        // 초기 상태: Idle, 초기 데이터: Uninitialized
        StartWith(State.Idle, Uninitialized.Instance);

        // Idle 상태에서의 메시지 처리
        When(State.Idle, state =>
        {
            if (state.FsmEvent is SetTarget target
                && state.StateData is Uninitialized)
            {
                // 배치 결과를 받을 대상 설정, 상태는 Idle 유지
                return Stay().Using(new Todo(target.Ref, ImmutableList<object>.Empty));
            }
            return null;
        });

        // Active 상태에서의 메시지 처리 (1초 타임아웃 설정)
        When(State.Active, state =>
        {
            if (state.FsmEvent is Flush or StateTimeout
                && state.StateData is Todo t)
            {
                // Flush 또는 타임아웃 -> Idle로 돌아감, 큐 비움
                return GoTo(State.Idle).Using(t.Copy(ImmutableList<object>.Empty));
            }
            return null;
        }, TimeSpan.FromSeconds(1));  // 1초 후 자동 StateTimeout

        // 처리되지 않은 메시지 핸들러
        WhenUnhandled(state =>
        {
            if (state.FsmEvent is Queue q && state.StateData is Todo t)
            {
                // 큐에 메시지 추가, Active 상태로 전환
                return GoTo(State.Active).Using(t.Copy(t.Queue.Add(q.Obj)));
            }
            else
            {
                _log.Warning("Received unhandled request {0} in state {1}/{2}",
                    state.FsmEvent, StateName, state.StateData);
                return Stay();
            }
        });

        // 상태 전환 시 콜백
        OnTransition((initialState, nextState) =>
        {
            if (initialState == State.Active && nextState == State.Idle)
            {
                if (StateData is Todo todo)
                {
                    // Active -> Idle 전환 시 배치 전송
                    todo.Target.Tell(new Batch(todo.Queue));
                }
            }
        });

        Initialize();
    }
}
```

### 상태 전환 흐름도

```
[Idle] --Queue(msg)--> [Active] --Queue(msg)--> [Active] (메시지 계속 추가)
                            |
                            |-- Flush 또는 1초 타임아웃 -->
                            |
                        [OnTransition: Batch 전송]
                            |
                            v
                         [Idle]
```

### 테스트 코드

```csharp
// ActorLibTest/Actors/Tools/FSMBatch/FSMBatchActorTest.cs
[Fact]
public void Simple_finite_state_machine_must_batch_correctly()
{
    var buncher = Sys.ActorOf(Props.Create<FSMBatchActor>());
    buncher.Tell(new SetTarget(TestActor));

    buncher.Tell(new Queue(42));
    buncher.Tell(new Queue(43));
    // 1초 타임아웃 후 Batch 수신
    ExpectMsg<Batch>().Obj.Should()
        .BeEquivalentTo(ImmutableList.Create(42, 43));

    buncher.Tell(new Queue(44));
    buncher.Tell(new Flush());     // 수동 Flush
    buncher.Tell(new Queue(45));

    ExpectMsg<Batch>().Obj.Should()
        .BeEquivalentTo(ImmutableList.Create(44));
    ExpectMsg<Batch>().Obj.Should()
        .BeEquivalentTo(ImmutableList.Create(45));
}
```

### FSM 핵심 API

| API | 설명 |
|-----|------|
| `StartWith(state, data)` | 초기 상태와 데이터 설정 |
| `When(state, handler, timeout?)` | 특정 상태에서의 메시지 핸들러 등록. 선택적 타임아웃 설정 |
| `WhenUnhandled(handler)` | 어떤 상태에서도 처리되지 않은 메시지 핸들러 |
| `Stay()` | 현재 상태 유지 |
| `GoTo(state)` | 다른 상태로 전환 |
| `.Using(data)` | 상태 데이터 변경 |
| `OnTransition(callback)` | 상태 전환 시 실행되는 콜백 |
| `StateTimeout` | `When`에 설정된 타임아웃 만료 시 발생하는 이벤트 |
| `Initialize()` | FSM 초기화 (생성자 마지막에 반드시 호출) |

---

## 5. 흐름 제어 - Stream 기반 Throttle

### 개념

`ThrottleActor`는 Akka.Streams를 활용하여 메시지 처리 속도를 제한한다. Source-Flow-Sink 파이프라인을 구성하여, 초당 처리 가능한 메시지 수를 설정한다. 외부 API 호출 제한, 데이터베이스 부하 분산 등에 유용하다.

### 메시지 모델

```csharp
// ActorLib/Actor/Tools/Throttle/ThrottleModels.cs
public class SetTarget
{
    public SetTarget(IActorRef @ref) { Ref = @ref; }
    public IActorRef Ref { get; }
}

public class Todo
{
    public string Id { get; set; }
    public string Title { get; set; }
}

public class TodoQueue
{
    public Todo Todo { get; set; }
}

public class ChangeTPS
{
    public int processCouuntPerSec { get; set; }
}

public class TPSInfoReq { }

public class EventCmd
{
    public string Message { get; set; }
}

public class Flush { }
```

### ThrottleActor 구현

```csharp
// ActorLib/Actor/Tools/Throttle/ThrottleActor.cs
public class ThrottleActor : ReceiveActor
{
    private readonly ILoggingAdapter logger = Context.GetLogger();
    private IActorRef? consumer;
    private IActorRef _throttler;
    private readonly IMaterializer _materializer;
    private int _processCouuntPerSec;

    public ThrottleActor(int processCouuntPerSec)
    {
        _materializer = Context.Materializer();
        _processCouuntPerSec = processCouuntPerSec;

        // Akka.Streams 파이프라인 구성:
        // Source.ActorRef -> Throttle -> Sink.ActorRef(Self)
        _throttler =
            Source.ActorRef<object>(1000, OverflowStrategy.DropNew)
                  .Throttle(_processCouuntPerSec,
                            TimeSpan.FromSeconds(1),
                            _processCouuntPerSec,
                            ThrottleMode.Shaping)
                  .To(Sink.ActorRef<object>(Self, NotUsed.Instance))
                  .Run(_materializer);

        // 소비자(결과를 받을 액터) 설정
        Receive<SetTarget>(target =>
        {
            consumer = target.Ref;
        });

        // 현재 TPS 조회
        Receive<TPSInfoReq>(target =>
        {
            Sender.Tell(_processCouuntPerSec);
        });

        // 런타임 TPS 변경
        Receive<ChangeTPS>(msg =>
        {
            logger.Info($"Tps Changed {_processCouuntPerSec} -> {msg.processCouuntPerSec}");
            _processCouuntPerSec = msg.processCouuntPerSec;

            // 새 Throttle 파이프라인 재구성
            _throttler =
                Source.ActorRef<object>(1000, OverflowStrategy.DropNew)
                      .Throttle(_processCouuntPerSec,
                                TimeSpan.FromSeconds(1),
                                _processCouuntPerSec,
                                ThrottleMode.Shaping)
                      .To(Sink.ActorRef<object>(Self, NotUsed.Instance))
                      .Run(_materializer);
        });

        // 메시지를 Throttle 파이프라인에 투입
        Receive<TodoQueue>(msg =>
        {
            _throttler.Tell(new Todo()
            {
                Id = msg.Todo.Id,
                Title = msg.Todo.Title
            });
        });

        // Throttle을 거쳐 Self로 돌아온 메시지 처리
        Receive<Todo>(msg =>
        {
            logger.Info($"{msg.Id} - {msg.Title}");
            if (consumer != null)
                consumer.Tell(msg);
        });
    }
}
```

### Stream 기반 Throttle 동작 원리

```
[외부] --TodoQueue--> ThrottleActor
                         |
                   _throttler (Source.ActorRef)
                         |
                   .Throttle(N/sec, Shaping)
                         |
                   Sink.ActorRef(Self)
                         |
                   ThrottleActor --Todo--> [consumer]
```

1. 외부에서 `TodoQueue` 메시지가 들어오면 `_throttler`에 전달한다
2. `Source.ActorRef`가 메시지를 받아 Stream 파이프라인에 넣는다
3. `Throttle` 연산자가 초당 `N`개로 속도를 제한한다
4. `Sink.ActorRef(Self)`를 통해 다시 자기 자신에게 메시지가 돌아온다
5. `Receive<Todo>` 핸들러가 실제 처리를 수행한다

### Throttle 테스트

```csharp
// ActorLibTest/Actors/Tools/Throttle/ThrottleActorTest.cs
[Theory(DisplayName = "초당 5회 소비제약 -StreamBase")]
[InlineData(50, 5, false)]
public void ThrottleTest(int givenTestCount, int processCouuntPerSec, bool isPerformTest)
{
    var actorSystem = _akkaService.GetActorSystem();

    int expectedCompletedMaxSecond = givenTestCount * processCouuntPerSec + 5;

    // Throttle 액터 생성 (초당 5건 처리)
    throttleActor = actorSystem.ActorOf(
        Props.Create(() => new ThrottleActor(processCouuntPerSec)));

    // 소비자로 TestProbe 연결
    var probe = this.CreateTestProbe();
    throttleActor.Tell(new SetTarget(probe));

    Within(TimeSpan.FromSeconds(expectedCompletedMaxSecond), () =>
    {
        // 50건의 메시지를 한꺼번에 전송
        for (int i = 0; i < givenTestCount; i++)
        {
            throttleActor.Tell(new TodoQueue()
            {
                Todo = new Todo { Id = i.ToString(), Title = "ThrottleLimitTest" }
            });
        }

        // 초당 5건씩 처리되므로 50건 처리에 약 10초 소요
        for (int i = 0; i < givenTestCount; i++)
        {
            probe.ExpectMsg<Todo>(message =>
            {
                Assert.Equal("ThrottleLimitTest", message.Title);
            });
        }
    }, EpsilonValueForWithins);
}
```

---

## 6. 흐름 제어 - Timer 기반 Throttle

### 개념

`ThrottleTimerActor`는 Akka.Streams 없이 `Scheduler`와 `Queue`를 사용하여 직접 속도 제한을 구현한다. 매초 타이머가 발동하여 큐에서 메시지를 하나씩 꺼내 처리하는 방식이다. 더 단순하지만, Shaping/Enforcing 같은 세밀한 제어는 어렵다.

### ThrottleTimerActor 구현

```csharp
// ActorLib/Actor/Tools/Throttle/ThrottleTimerActor.cs
public class ThrottleTimerActor : ReceiveActor
{
    private readonly ILoggingAdapter logger = Context.GetLogger();
    private IActorRef? consumer;
    private Queue<object> eventQueue = new Queue<object>();
    private DateTime lastExecuteDt;

    public ThrottleTimerActor(int element, int second, int maxBust)
    {
        lastExecuteDt = DateTime.Now;

        // 매초 Self에게 Flush 메시지를 보내는 스케줄러
        Context.System.Scheduler
            .ScheduleTellRepeatedly(
                TimeSpan.FromSeconds(0),        // 초기 지연 없음
                TimeSpan.FromSeconds(1),        // 1초 간격
                Self, new Flush(), ActorRefs.NoSender);

        // 소비자 설정
        Receive<SetTarget>(target =>
        {
            consumer = target.Ref;
        });

        // 이벤트를 큐에 저장
        Receive<EventCmd>(message =>
        {
            if (eventQueue.Count > maxBust)
            {
                logger.Warning(
                    $"ThrottleActor MaxBust : {eventQueue.Count}/{maxBust}");
            }
            eventQueue.Enqueue(message);
        });

        // 타이머에 의해 매초 실행: 큐에서 하나 꺼내 처리
        Receive<Flush>(message =>
        {
            if (eventQueue.Count > 0)
            {
                var eventData = eventQueue.Dequeue();
                if (consumer != null)
                    consumer.Tell(eventData);
                lastExecuteDt = DateTime.Now;
            }
        });
    }
}
```

### Stream 기반 vs Timer 기반 비교

| 항목 | Stream 기반 (ThrottleActor) | Timer 기반 (ThrottleTimerActor) |
|------|---------------------------|-------------------------------|
| 의존성 | Akka.Streams 필요 | 기본 Akka만 필요 |
| 속도 제어 방식 | Backpressure + Shaping | 단순 타이머 폴링 |
| 버스트 처리 | `ThrottleMode.Shaping/Enforcing` | `maxBust` 수동 제한 |
| 오버플로우 전략 | `OverflowStrategy` (DropNew 등) | 수동 큐 크기 관리 |
| 런타임 TPS 변경 | 가능 (`ChangeTPS`) | 코드 수정 필요 |
| 복잡도 | 상대적으로 높음 | 단순 |

---

## 7. 라우팅 (Routing)

### 개념

라우터(Router)는 메시지를 여러 액터(routee)에게 분배하는 패턴이다. Akka.NET은 다양한 라우팅 전략을 기본 제공하며, Pool 방식(라우터가 자식 액터를 생성)과 Group 방식(기존 액터를 라우티로 등록)을 지원한다.

### 라우터 종류

| 라우터 | 설명 |
|--------|------|
| `RoundRobinPool` | 순서대로 돌아가면서 메시지 분배 |
| `RandomPool` | 무작위로 메시지 분배 |
| `BroadcastPool` | 모든 routee에게 메시지 복사 전송 |
| `ScatterGatherFirstCompletedPool` | 모든 routee에게 보내고 **가장 먼저 응답한 결과**만 사용 |
| `SmallestMailboxPool` | 메일박스가 가장 적은 routee에게 전송 |
| `ConsistentHashingPool` | 해시 기반 분배 |
| `TailChoppingPool` | 순서대로 보내되 응답 없으면 다음에게 전송 |

### RoundRobinPool 사용

```csharp
// ActorLibTest/Actors/Intro/RoutersTest.cs
[Theory(DisplayName = "RoundRobinPoolTest")]
[InlineData(3, 1000)]
public void RoundRobinPoolTest(int nodeCount, int testCount, bool isPerformTest = false)
{
    var actorSystem = _akkaService.GetActorSystem();
    TestProbe testProbe = this.CreateTestProbe(actorSystem);

    // RoundRobinPool: 3개의 BasicActor를 자식으로 생성
    var props = new RoundRobinPool(nodeCount)
        .Props(Props.Create(() => new BasicActor()));

    var actor = actorSystem.ActorOf(props);

    // 각 routee에 TestProbe 등록
    for (int i = 0; i < nodeCount; i++)
    {
        actor.Tell(testProbe.Ref);
        testProbe.ExpectMsg("done");
    }

    Within(TimeSpan.FromMilliseconds(3000), () =>
    {
        // 1000건의 메시지가 3개 routee에 균등 분배
        for (int i = 0; i < testCount; i++)
            actor.Tell("hello" + i);

        for (int i = 0; i < testCount; i++)
            testProbe.ExpectMsg("world");
    });
}
```

### RandomPool 사용

```csharp
var props = new RandomPool(nodeCount)
    .Props(Props.Create(() => new BasicActor()));

var actor = actorSystem.ActorOf(props);
```

### ScatterGatherFirstCompletedPool - 실전 예제

상담원 시스템에서 가장 빨리 응답하는 상담원에게 작업을 배정하는 패턴이다.

```csharp
// ActorLibTest/Actors/Case/Counselors/SuperVisorActor.cs
public class SuperVisorActor : ReceiveActor
{
    private IActorRef? routerFirstCompleted;

    public SuperVisorActor(SuperVisorInfo superVisorInfo, ITestOutputHelper testOutputHelper)
    {
        var within = TimeSpan.FromSeconds(10);

        // ScatterGatherFirstCompletedPool: 모든 routee에 물어보고 가장 빠른 응답 사용
        routerFirstCompleted = Context.ActorOf(
            Props.Create(() =>
                new CounselorsActor(
                    new CounselorInfo() { Id = 0, Name = "na" },
                    testOutputHelper))
            .WithRouter(new ScatterGatherFirstCompletedPool(0, within)),
            "routerFirstCompleted"
        );

        // 상담원 동적 추가
        Receive<CreateCounselor>(message =>
        {
            string uniqueId = $"{message.Counselor.Name}-{message.Counselor.Id}";

            var counselorActor = Context.ActorOf(
                Props.Create(() =>
                    new CounselorsActor(
                        new CounselorInfo() { Id = 1, Name = "test1" },
                        testOutputHelper)),
                uniqueId);

            // 라우터에 routee 동적 추가
            var routee = Routee.FromActorRef(counselorActor);
            routerFirstCompleted.Tell(new AddRoutee(routee));
            Sender.Tell("CreateCounselor");
        });

        // 업무 배정 요청 -> 라우터가 모든 상담원에게 물어봄
        Receive<CheckTakeTask>(message =>
        {
            routerFirstCompleted.Tell(message);
        });

        // 가장 빠른 응답 처리
        Receive<WishTask>(message =>
        {
            message.WishActor.Tell(new AssignTask() { TaskId = 1 });
        });
    }
}
```

### ASP.NET Core에서 라우터 등록

```csharp
// BlazorActorApp/Program.cs

// RoundRobin 라우터
var roundrobin = actorSystem.ActorOf(
    Props.Create<BasicActor>().WithRouter(new RoundRobinPool(0)),
    "roundrobin");
akkaService.AddActor("roundrobin", roundrobin);

// Broadcast 라우터
var broadcast = actorSystem.ActorOf(
    Props.Create<BasicActor>().WithRouter(new BroadcastPool(0)),
    "broadcast");
akkaService.AddActor("broadcast", broadcast);

// Random 라우터
var random = actorSystem.ActorOf(
    Props.Create<BasicActor>().WithRouter(new RandomPool(0)),
    "random");
akkaService.AddActor("random", random);
```

> **Pool(0)의 의미**: `RoundRobinPool(0)`처럼 크기 0으로 생성하면 라우터만 만들고 routee는 나중에 `AddRoutee`로 동적 추가할 수 있다.

---

## 8. 디스패처 (Dispatcher)

### 개념

디스패처(Dispatcher)는 액터에게 스레드 자원을 할당하는 엔진이다. 어떤 디스패처를 사용하느냐에 따라 액터의 실행 특성이 달라진다. HOCON 설정으로 커스텀 디스패처를 정의하고, 액터 생성 시 `.WithDispatcher()`로 지정한다.

### HOCON 디스패처 설정

```hocon
# 기본 디스패처: ThreadPool 기반, throughput 100
custom-dispatcher {
    type = Dispatcher
    throughput = 100
}

# Task 기반 디스패처: .NET Task 활용
custom-task-dispatcher {
    type = TaskDispatcher
    throughput = 100
}

# 전용 스레드 디스패처: 액터 하나에 스레드 하나 고정
custom-dedicated-dispatcher {
    type = PinnedDispatcher
}

# ForkJoin 디스패처: 전용 스레드풀 사용
fork-join-dispatcher {
    type = ForkJoinDispatcher
    throughput = 1
    dedicated-thread-pool {
        thread-count = 1
        deadlock-timeout = 3s
        threadtype = background
    }
}

# 동기화 디스패처: SynchronizationContext 활용 (UI 스레드 등)
synchronized-dispatcher {
    type = SynchronizedDispatcher
    throughput = 100
}
```

### 디스패처 유형별 특징

| 디스패처 | 설명 | 용도 |
|----------|------|------|
| `Dispatcher` | .NET ThreadPool 기반, 범용 | 일반적인 액터 처리 |
| `TaskDispatcher` | .NET Task/TPL 기반 | async/await 패턴 활용 시 |
| `PinnedDispatcher` | 액터당 전용 스레드 1개 | I/O 바운드, 블로킹 작업 |
| `ForkJoinDispatcher` | 전용 스레드풀 | 격리된 스레드풀이 필요할 때 |
| `SynchronizedDispatcher` | SynchronizationContext | UI 스레드 접근 필요 시 |

### 디스패처 적용 테스트

```csharp
// ActorLibTest/Actors/Intro/DisPatcherTest.cs
[Theory(DisplayName = "Dispatcher - Thread")]
[InlineData(3, "synchronized-dispatcher")]
[InlineData(3, "fork-join-dispatcher")]
[InlineData(3, "custom-dispatcher")]
[InlineData(3, "custom-task-dispatcher")]
[InlineData(3, "custom-dedicated-dispatcher")]
public void DispatcherTestAreOK(int nodeCount, string disPatcherName)
{
    var actorSystem = _akkaService.GetActorSystem();
    TestProbe testProbe = this.CreateTestProbe(actorSystem);

    // 라우터 + 디스패처 조합
    var props = new RoundRobinPool(nodeCount)
        .WithDispatcher(disPatcherName)   // 디스패처 지정
        .Props(Props.Create(() => new BasicActor()));

    var actor = actorSystem.ActorOf(props, "worker");

    for (int i = 0; i < nodeCount; i++)
    {
        actor.Tell(testProbe.Ref);
        testProbe.ExpectMsg("done");
    }

    int givenTestCount = 1000;
    int givenBlockTimePerTest = 10;
    int cutOff = givenTestCount * givenBlockTimePerTest;

    Within(TimeSpan.FromMilliseconds(cutOff), () =>
    {
        // 10ms 지연이 있는 명령을 1000건 전송
        for (int i = 0; i < givenTestCount; i++)
            actor.Tell(new DelayCommand("slowCommand" + i, 10));

        for (int i = 0; i < givenTestCount; i++)
        {
            string resultMessage = testProbe.ExpectMsg<string>();
        }
    });
}
```

> **throughput 파라미터**: 디스패처가 한 액터의 메일박스에서 연속으로 처리할 최대 메시지 수이다. 높으면 해당 액터가 오래 독점하고, 낮으면 공정하게 분배된다.

---

## 9. 메일박스 설정 (Mailbox)

### 개념

메일박스(Mailbox)는 액터가 수신한 메시지를 저장하는 큐이다. 기본은 FIFO(선입선출)이지만, 커스텀 메일박스를 구현하면 **메시지 우선순위**를 지정할 수 있다. 보안 결함이나 긴급 버그 같은 중요한 메시지를 먼저 처리하는 시나리오에 유용하다.

### 커스텀 우선순위 메일박스 구현

```csharp
// ActorLib/Actor/Test/IssueTrackerMailbox.cs
public class IssueTrackerMailbox : UnboundedPriorityMailbox
{
    public IssueTrackerMailbox(Settings settings, Config config)
        : base(settings, config)
    {
    }

    protected override int PriorityGenerator(object message)
    {
        var issue = message as Issue;

        if (issue != null)
        {
            if (issue.IsSecurityFlaw)
                return 0;    // 최고 우선순위: 보안 결함

            if (issue.IsBug)
                return 1;    // 중간 우선순위: 버그
        }

        return 2;            // 최저 우선순위: 일반 메시지
    }
}

// 메시지 모델
public class Issue : IJsonSerializable
{
    public bool IsSecurityFlaw { get; set; }
    public bool IsBug { get; set; }
}
```

### HOCON 메일박스 설정

```hocon
my-custom-mailbox {
    mailbox-type : "ActorLib.Actor.Test.IssueTrackerMailbox, ActorLib"
}
```

### 메일박스 적용

```csharp
// 액터 생성 시 메일박스 지정
var mailBoxActor = actorSystem.ActorOf(
    Props.Create(() => new BasicActor())
         .WithMailbox("my-custom-mailbox")
);
```

### 우선순위 메일박스 테스트

```csharp
// ActorLibTest/Actors/Intro/MailBoxTest.cs
[Theory(DisplayName = "메시지 우선순위 MailBox 테스트")]
[InlineData(7, 3000)]
public void HelloWorldAreOK(int testCount, int cutoff, bool isPerformTest = false)
{
    var actorSystem = _akkaService.GetActorSystem();
    TestProbe testProbe = this.CreateTestProbe(actorSystem);

    var mailBoxActor = actorSystem.ActorOf(
        Props.Create(() => new BasicActor())
             .WithMailbox("my-custom-mailbox"));

    mailBoxActor.Tell(testProbe.Ref);
    testProbe.ExpectMsg("done");

    Within(TimeSpan.FromMilliseconds(cutoff), () =>
    {
        // 다양한 우선순위의 Issue를 섞어서 전송
        mailBoxActor.Tell(new Issue() { IsBug = true });         // 우선순위 1
        mailBoxActor.Tell(new Issue());                          // 우선순위 2
        mailBoxActor.Tell(new Issue() { IsSecurityFlaw = true }); // 우선순위 0 (최우선)
        mailBoxActor.Tell(new Issue() { IsBug = true });         // 우선순위 1
        mailBoxActor.Tell(new Issue() { IsBug = true });         // 우선순위 1
        mailBoxActor.Tell(new Issue() { IsSecurityFlaw = true }); // 우선순위 0 (최우선)
        mailBoxActor.Tell(new Issue());                          // 우선순위 2

        // 결과: SecurityFlaw -> Bug -> Normal 순으로 처리됨
        for (int i = 0; i < testCount; i++)
        {
            var issue = testProbe.ExpectMsg<Issue>();
            var jsonString = JsonSerializer.Serialize(issue);
            output.WriteLine($"Issue: {jsonString}");
        }
    });
}
```

### 메일박스 종류

| 메일박스 | 설명 |
|----------|------|
| `UnboundedMailbox` | 기본 메일박스, 무제한 FIFO |
| `BoundedMailbox` | 크기 제한 있는 큐 (가득 차면 메시지 드롭 또는 블록) |
| `UnboundedPriorityMailbox` | 우선순위 기반 무제한 큐 |
| `BoundedPriorityMailbox` | 우선순위 기반 + 크기 제한 |

---

## 10. 감독 전략 (Supervision)

### 개념

Akka.NET의 감독(Supervision)은 "Let it crash" 철학을 구현한다. 부모 액터가 자식 액터의 실패를 감시하고, 실패 시 어떻게 대응할지 **감독 전략(SupervisorStrategy)** 으로 정의한다.

### 감독 지시어 (Directive)

| 지시어 | 설명 |
|--------|------|
| `Resume` | 자식 액터의 상태를 유지한 채 처리를 계속한다 |
| `Restart` | 자식 액터를 재시작한다 (상태 초기화) |
| `Stop` | 자식 액터를 영구 중지한다 |
| `Escalate` | 실패를 상위 부모에게 전파한다 |

### 전략 유형

| 전략 | 설명 |
|------|------|
| `OneForOneStrategy` | 실패한 자식 액터에만 지시어를 적용 |
| `AllForOneStrategy` | 하나가 실패하면 모든 자식에게 지시어를 적용 |

### Become/Unbecome 패턴을 활용한 상태 전환

`CounselorsActor`는 `UntypedActor`를 상속받아 `Become()`으로 상태를 전환하는 패턴을 보여준다. 이는 FSM보다 가벼운 상태 관리 방법이다.

```csharp
// ActorLibTest/Actors/Case/Counselors/CounselorsActor.cs
public class CounselorsActor : UntypedActor
{
    private CounselorsState counselorsState = CounselorsState.Offline;
    private int[] skills { get; set; } = new int[0];
    private int assignedTaskCount = 0;

    protected override void OnReceive(object message)
    {
        switch (message)
        {
            case SetCounselorsState counselor:
                if (counselor.State == CounselorsState.Online)
                {
                    counselorsState = counselor.State;
                    Sender.Tell("SetCounselorsState");
                    skills = counselor.Skills;
                    assignedTaskCount = Random.Next(0, 10);
                    Become(Online);   // Online 상태로 전환
                }
                else if (counselor.State == CounselorsState.Offline)
                {
                    counselorsState = counselor.State;
                    skills = counselor.Skills;
                    Sender.Tell("SetCounselorsState");
                    Become(OffLine);  // Offline 상태로 전환
                }
                break;
        }
    }

    private void Online(object message)
    {
        switch (message)
        {
            case CheckTakeTask checkTask:
                if (skills.Contains(checkTask.SkillType))
                {
                    Sender.Tell(new WishTask() { WishActor = Self });
                }
                break;

            case AssignTask assignTask:
                assignedTaskCount++;
                Sender.Tell("I Take Task");
                break;

            case CompletedTask completedTask:
                assignedTaskCount--;
                Sender.Tell("I Did Task");
                break;

            case SetCounselorsState counselor:
                if (counselor.State == CounselorsState.Offline)
                {
                    counselorsState = counselor.State;
                    Sender.Tell("SetCounselorsState");
                    Become(OffLine);  // Offline으로 전환
                }
                break;
        }
    }

    private void OffLine(object message)
    {
        switch (message)
        {
            case CheckTakeTask checkTask:
                // Offline 상태이므로 응답하지 않음
                break;

            case SetCounselorsState counselor:
                if (counselor.State == CounselorsState.Online)
                {
                    counselorsState = counselor.State;
                    Sender.Tell("SetCounselorsState");
                    Become(Online);  // 다시 Online으로
                }
                break;
        }
    }
}
```

### 감독 전략 HOCON 설정 예시

```hocon
akka {
    actor {
        # 기본 감독 전략 설정
        guardian-supervisor-strategy =
            "Akka.Actor.DefaultSupervisorStrategy"

        deployment {
            /myActor {
                # 커스텀 감독 전략
                supervisor-strategy =
                    "MyApp.MySupervisorStrategy, MyApp"
            }
        }
    }
}
```

### 프로그래밍 방식 감독 전략

```csharp
// 코드에서 감독 전략 정의
protected override SupervisorStrategy SupervisorStrategy()
{
    return new OneForOneStrategy(
        maxNrOfRetries: 10,               // 최대 재시도 횟수
        withinTimeRange: TimeSpan.FromMinutes(1), // 시간 범위
        localOnlyDecider: ex =>
        {
            switch (ex)
            {
                case ArithmeticException _:
                    return Directive.Resume;     // 계속 처리
                case NullReferenceException _:
                    return Directive.Restart;    // 재시작
                case ArgumentException _:
                    return Directive.Stop;       // 중지
                default:
                    return Directive.Escalate;   // 상위로 전파
            }
        });
}
```

---

## 11. 영속화 (Persistence with RavenDB)

### 개념

Akka.Persistence는 액터의 상태를 영구 저장소에 보존하여, 시스템 재시작 후에도 상태를 복구할 수 있게 한다. **이벤트 소싱(Event Sourcing)** 패턴을 기반으로 하며, 두 가지 저장 방식을 지원한다:

- **Journal (이벤트 저널)**: 모든 이벤트를 순서대로 저장
- **Snapshot (스냅샷)**: 특정 시점의 전체 상태를 저장하여 복구 시간을 단축

이 프로젝트는 **RavenDB**를 Persistence 백엔드로 사용한다.

### 상태 및 이벤트 모델

```csharp
// ActorLib/Persistent/Model/SalesActorState.cs

// 이벤트: 영속화되는 단위
public class Sale(long pricePaid, string productBrand)
{
    public long Price { get; set; } = pricePaid;
    public string Brand { get; set; } = productBrand;
}

// 시뮬레이터 제어 메시지
public class StartSimulate { }
public class StopSimulate { }

// 내부 상태: 스냅샷으로 저장됨
class SalesActorState
{
    public long totalSales { get; set; }

    public override string ToString()
    {
        return $"[SalesActorState: Total sales are {totalSales}]";
    }
}
```

### SalesActor - ReceivePersistentActor 패턴

```csharp
// ActorLib/Persistent/Actor/SalesActor.cs
public class SalesActor : ReceivePersistentActor
{
    // 필수: 고유한 Persistence ID
    public override string PersistenceId => "sales-actor";

    // 스냅샷으로 저장될 상태
    private SalesActorState _state;

    public SalesActor(long expectedProfit, TaskCompletionSource<bool> taskCompletion)
    {
        _state = new SalesActorState { totalSales = 0 };

        // === Command 핸들러 (새로운 이벤트 처리) ===
        Command<Sale>(saleInfo =>
        {
            if (_state.totalSales < expectedProfit)
            {
                // 이벤트를 RavenDB에 영속화
                Persist(saleInfo, _ =>
                {
                    // 영속화 성공 후 상태 업데이트
                    _state.totalSales += saleInfo.Price;

                    // 5건마다 스냅샷 저장
                    if (LastSequenceNr != 0 && LastSequenceNr % 5 == 0)
                    {
                        SaveSnapshot(_state.totalSales);
                    }
                });
            }
            else if (!taskCompletion.Task.IsCompleted)
            {
                Sender.Tell(new StopSimulate());
                taskCompletion.TrySetResult(true);
            }
        });

        // 스냅샷 저장 성공 핸들러
        Command<SaveSnapshotSuccess>(success =>
        {
            ConsoleHelper.WriteToConsole(ConsoleColor.Blue,
                $"Snapshot saved at sequence number {success.Metadata.SequenceNr}");
        });

        // === Recover 핸들러 (재시작 시 복구) ===

        // 이벤트 복구
        Recover<Sale>(saleInfo =>
        {
            _state.totalSales += saleInfo.Price;
        });

        // 스냅샷 복구 (있으면 이벤트 리플레이 대신 사용)
        Recover<SnapshotOffer>(offer =>
        {
            var salesFromSnapshot = (long)offer.Snapshot;
            _state.totalSales = salesFromSnapshot;
        });
    }
}
```

### SalesSimulatorActor - 주기적 이벤트 생성

```csharp
// ActorLib/Persistent/Actor/SalesSimulatorActor.cs
public class SalesSimulatorActor : ReceiveActor
{
    private readonly IActorRef _salesActor;
    private ICancelable scheduler;

    public SalesSimulatorActor(IActorRef salesActor)
    {
        _salesActor = salesActor;

        // 2초 간격으로 판매 시뮬레이션
        scheduler = Context.System.Scheduler
            .ScheduleTellRepeatedlyCancelable(
                TimeSpan.Zero,
                TimeSpan.FromSeconds(2),
                Self, new StartSimulate(), Self);

        Receive<StartSimulate>(HandleStart);
        Receive<StopSimulate>(HandleStop);
    }

    private void HandleStart(StartSimulate message)
    {
        Random random = new Random();
        string[] products = { "Apple", "Google", "Nokia", "Xiaomi", "Huawei" };
        var randomBrand = products[random.Next(products.Length)];
        var randomPrice = random.Next(1, 6) * 100;

        var nextSale = new Sale(randomPrice, randomBrand);
        _salesActor.Tell(nextSale);
    }

    private void HandleStop(StopSimulate message)
    {
        scheduler.Cancel();
    }
}
```

### RavenDB Persistence HOCON 설정

```hocon
akka.persistence {
    # Journal (이벤트 저널) 설정
    journal {
        plugin = "akka.persistence.journal.ravendb"
        ravendb {
            class = "Akka.Persistence.RavenDb.Journal.RavenDbJournal, Akka.Persistence.RavenDb"
            plugin-dispatcher = "akka.actor.default-dispatcher"
            urls = ["http://localhost:9000"]
            name = "net-core-labs"            # RavenDB 데이터베이스 이름
            auto-initialize = false
            # certificate-path = "\\path\\to\\cert.pfx"  # TLS 인증서
            # save-changes-timeout = 30s
        }
    }

    # Snapshot (스냅샷) 설정
    snapshot-store {
        plugin = "akka.persistence.snapshot-store.ravendb"
        ravendb {
            class = "Akka.Persistence.RavenDb.Snapshot.RavenDbSnapshotStore, Akka.Persistence.RavenDb"
            plugin-dispatcher = "akka.actor.default-dispatcher"
            urls = ["http://localhost:9000"]
            name = "net-core-labs"
            auto-initialize = false
        }
    }

    # Query (읽기 쪽 저널) 설정
    query {
        ravendb {
            class = "Akka.Persistence.RavenDb.Query.RavenDbReadJournalProvider, Akka.Persistence.RavenDb"
            # refresh-interval = 3s
            # max-buffer-size = 65536
        }
    }
}
```

### Persistence 복구 흐름

```
[시스템 재시작]
    |
    v
1. SnapshotOffer 복구 (가장 최근 스냅샷)
    |
    v
2. Sale 이벤트 리플레이 (스냅샷 이후의 이벤트만)
    |
    v
3. 복구 완료 -> Command 핸들러 활성화
    |
    v
4. 새로운 Sale 이벤트 -> Persist() -> Journal 저장
    |
    v
5. 5건마다 SaveSnapshot() -> Snapshot Store 저장
```

### ReceivePersistentActor 핵심 API

| API | 설명 |
|-----|------|
| `PersistenceId` | 액터의 고유 식별자 (복구 시 이 ID로 이벤트를 찾음) |
| `Command<T>(handler)` | 새로운 메시지(명령) 핸들러 |
| `Recover<T>(handler)` | 복구 시 이벤트/스냅샷 핸들러 |
| `Persist(event, handler)` | 이벤트를 저널에 저장 후 핸들러 실행 |
| `SaveSnapshot(state)` | 현재 상태를 스냅샷으로 저장 |
| `LastSequenceNr` | 마지막으로 저장된 이벤트의 시퀀스 번호 |
| `DeleteMessages(seqNr)` | 특정 시퀀스까지의 이벤트 삭제 |

---

## 12. SSE (Server-Sent Events) 액터

### 개념

SSE(Server-Sent Events)는 서버에서 클라이언트로 실시간 이벤트를 푸시하는 HTTP 기반 프로토콜이다. 이 프로젝트에서는 각 사용자별로 `SSEUserActor`를 생성하여 알림 메시지를 관리한다. 액터의 메일박스가 자연스럽게 메시지 버퍼 역할을 하며, `PipeTo` 패턴으로 비동기 대기를 구현한다.

### SSEUserActor - 사용자별 알림 관리

```csharp
// BlazorActorApp/Service/SSE/Actor/SSEUserActor.cs
public class SSEUserActor : ReceiveActor
{
    private Queue<Notification> notifications = new Queue<Notification>();
    private string IdentyValue { get; set; }
    private IActorRef testProbe;

    public SSEUserActor(string identyValue)
    {
        IdentyValue = identyValue;

        // 웰컴 메시지 자동 생성
        notifications.Enqueue(new Notification()
        {
            Id = IdentyValue,
            Message = $"[{IdentyValue}] 웰컴메시지... by sse",
            MessageTime = DateTime.Now,
        });

        // 알림 메시지 수신 -> 큐에 저장
        ReceiveAsync<Notification>(async msg =>
        {
            if (msg.IsProcessed == false)
                notifications.Enqueue(msg);
        });

        // 하트비트 응답
        ReceiveAsync<HeartBeatNotification>(async msg =>
        {
            if (testProbe != null)
                testProbe.Tell(new HeartBeatNotification());
            else
                Sender.Tell(new HeartBeatNotification());
        });

        // 알림 확인 요청 -> 큐에서 꺼내 반환
        ReceiveAsync<CheckNotification>(async msg =>
        {
            if (notifications.Count > 0)
            {
                if (testProbe != null)
                    testProbe.Tell(notifications.Dequeue());
                else
                    Sender.Tell(notifications.Dequeue());
            }
            else
            {
                // 알림이 없으면 1초 후 하트비트 반환 (PipeTo 패턴)
                HeartBeatNotification heatBeatNotification = new HeartBeatNotification();
                Task.Delay(1000)
                    .ContinueWith(tr => heatBeatNotification)
                    .PipeTo(Self, Sender);
            }
        });
    }
}
```

### SSEService - 액터 관리 서비스

```csharp
// BlazorActorApp/Service/SSE/Actor/SSEService.cs
public class SSEService
{
    private AkkaService AkkaService { get; set; }

    public SSEService(AkkaService actorSystem)
    {
        AkkaService = actorSystem;
    }

    // 사용자 액터 조회/생성
    private async Task<IActorRef> findUserByIdenty(string actorName)
    {
        IActorRef myActor = AkkaService.GetActor(actorName);
        if (myActor == null)
        {
            myActor = AkkaService.GetActorSystem()
                .ActorOf(Props.Create<SSEUserActor>(actorName));
            AkkaService.AddActor(actorName, myActor);
        }
        return myActor;
    }

    // 알림 확인 (Ask 패턴)
    public async Task<object> CheckNotification(string actorName)
    {
        var myActor = await findUserByIdenty(actorName);
        return await myActor.Ask(new CheckNotification(), TimeSpan.FromSeconds(3));
    }

    // 알림 전송 (Tell 패턴)
    public async Task PushNotification(string actorName, Notification noti)
    {
        var myActor = await findUserByIdenty(actorName);
        myActor.Tell(noti);
    }
}
```

### SSEController - REST API

```csharp
// BlazorActorApp/Controllers/SSEController.cs
[Route("api/sse")]
public class SSEController : Controller
{
    private SSEService SSEService { get; set; }
    private string PreFix = "sse-";

    public SSEController(SSEService sseService)
    {
        SSEService = sseService;
    }

    // GET api/sse/message/{identy} - SSE 이벤트 수신
    [HttpGet("message/{identy}")]
    public async Task<ActionResult> GetMessageByActor(string identy)
    {
        var stringBuilder = new StringBuilder();
        string actorName = $"{PreFix}{identy}";

        object message = await SSEService.CheckNotification(actorName);

        if (message is Notification)
        {
            var serializedData = JsonSerializer.Serialize(message as Notification);
            stringBuilder.AppendFormat("data: {0}\n\n", serializedData);
            return Content(stringBuilder.ToString(), "text/event-stream");
        }
        else if (message is HeartBeatNotification)
        {
            stringBuilder.AppendFormat("data: null");
            return Content(stringBuilder.ToString(), "text/event-stream");
        }
        else
        {
            stringBuilder.AppendFormat("data: null \n\n");
            return Content(stringBuilder.ToString(), "text/event-stream");
        }
    }

    // POST api/sse/webhook - 알림 전송
    [HttpPost("webhook")]
    public async Task<IActionResult> PushNotification(string identy, string message)
    {
        string actorName = $"{PreFix}{identy}";
        var noti = new Notification()
        {
            Id = identy,
            IsProcessed = false,
            Message = message,
            MessageTime = DateTime.Now,
        };

        await SSEService.PushNotification(actorName, noti);
        return Ok();
    }
}
```

### PipeTo 패턴 설명

```csharp
// SSEUserActor에서 사용된 PipeTo 패턴
Task.Delay(1000)
    .ContinueWith(tr => heatBeatNotification)
    .PipeTo(Self, Sender);
```

`PipeTo`는 비동기 작업(`Task`)의 결과를 액터 메시지로 변환하여 전달하는 패턴이다. `await`와 달리 액터의 메일박스를 통해 결과가 전달되므로 스레드 안전성이 보장된다. 위 코드에서는 1초 후에 `HeartBeatNotification`을 `Self`에게 보내되, 원래 `Sender`를 보존한다.

---

## 13. MCP Server 액터

### 개념

MCP(Model Context Protocol)는 AI 모델과 외부 도구를 연결하는 프로토콜이다. 이 프로젝트에서는 Akka.NET 액터를 MCP 도구(Tool)의 백엔드로 활용한다. AI 에이전트가 MCP Tool을 호출하면, 내부적으로 액터에게 메시지를 보내 처리한다.

### 액터 명령 모델

```csharp
// McpServer/Actor/Model/ActorCommand.cs
public class ActorCommand { }

public class AddNoteCommand : ActorCommand
{
    [Required] public string Title { get; set; }
    [Required] public string Content { get; set; }
    public string? Category { get; set; }
    public double? Latitude { get; set; }
    public double? Longitude { get; set; }
    public RavenVector<float>? TagsEmbeddedAsSingle { get; set; }
}

public class SearchNoteByTextCommand : ActorCommand
{
    public string? Title { get; set; }
    public string? Content { get; set; }
    public string? Category { get; set; }
}

public class SearchNoteByRadiusActorCommand : ActorCommand
{
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public double Radius { get; set; }
}

public class SearchNoteByVectorCommand : ActorCommand
{
    [Required] public float[] Vector { get; set; }
    public int TopN { get; set; } = 10;
}

public class SearchNoteActorResult : ActorCommand
{
    [Required] public List<NoteDocument> Notes { get; set; }
}

public class SetHistoryActorCommand : ActorCommand
{
    [Required] public IActorRef HistoryActor { get; set; }
}
```

### HistoryActor - Queue 기반 이력 관리

```csharp
// McpServer/Actor/HistoryActor.cs
public class HistoryActor : ReceiveActor
{
    private readonly ILoggingAdapter logger = Context.GetLogger();
    private Queue<NoteDocument> noteQueue;
    private Queue<NoteDocument> noteSearchQueue;

    // 최근 N건만 유지하는 큐 관리
    private void EnqueueNote(NoteDocument note)
    {
        noteQueue.Enqueue(note);
        if (noteQueue.Count > 10)  // 최근 10건 유지
            noteQueue.Dequeue();
    }

    private void EnqueueSearchNote(NoteDocument note)
    {
        noteSearchQueue.Enqueue(note);
        if (noteSearchQueue.Count > 50)  // 최근 50건 유지
            noteSearchQueue.Dequeue();
    }

    public HistoryActor()
    {
        noteQueue = new Queue<NoteDocument>();
        noteSearchQueue = new Queue<NoteDocument>();

        // 노트 추가 이력
        Receive<AddNoteCommand>(msg =>
        {
            EnqueueNote(new NoteDocument()
            {
                Content = msg.Content,
                Category = msg.Category,
                Latitude = msg.Latitude,
                Longitude = msg.Longitude,
                Title = msg.Title,
                TagsEmbeddedAsSingle = msg.TagsEmbeddedAsSingle,
                CreatedAt = DateTime.UtcNow
            });
        });

        // 검색 결과 이력 (SearchActor에서 전송)
        Receive<List<NoteDocument>>(notes =>
        {
            foreach (var note in notes)
                EnqueueSearchNote(note);
        });

        // 추가 이력 조회
        Receive<GetNoteHistoryCommand>(msg =>
        {
            Sender.Tell(new SearchNoteActorResult()
            {
                Notes = noteQueue.ToList()
            });
        });

        // 검색 이력 조회
        Receive<GetNoteSearchHistoryCommand>(msg =>
        {
            Sender.Tell(new SearchNoteActorResult()
            {
                Notes = noteSearchQueue.ToList()
            });
        });
    }
}
```

### RecordActor - 데이터 저장

```csharp
// McpServer/Actor/RecordActor.cs
public class RecordActor : ReceiveActor
{
    private IActorRef? historyActor;
    private readonly NoteRepository noteRepository;

    public RecordActor()
    {
        noteRepository = new NoteRepository();

        // HistoryActor 연결 설정
        Receive<SetHistoryActorCommand>(msg =>
        {
            historyActor = msg.HistoryActor;
        });

        // 노트 저장 -> DB에 저장하고 HistoryActor에도 전달
        Receive<AddNoteCommand>(msg =>
        {
            var note = new NoteDocument
            {
                Content = msg.Content,
                Category = msg.Category,
                Latitude = msg.Latitude,
                Longitude = msg.Longitude,
                Title = msg.Title,
                TagsEmbeddedAsSingle = msg.TagsEmbeddedAsSingle,
                CreatedAt = DateTime.UtcNow
            };

            noteRepository.AddNote(note);

            // HistoryActor에 이력 전달
            if (historyActor != null)
                historyActor.Tell(msg);
        });
    }
}
```

### SearchActor - 다양한 검색 지원

```csharp
// McpServer/Actor/SearchActor.cs
public class SearchActor : ReceiveActor
{
    private readonly NoteRepository noteRepository;
    private IActorRef? historyActor;

    public SearchActor()
    {
        noteRepository = new NoteRepository();

        Receive<SetHistoryActorCommand>(msg =>
        {
            historyActor = msg.HistoryActor;
        });

        // 텍스트 검색
        Receive<SearchNoteByTextCommand>(command =>
        {
            var notes = noteRepository.SearchByText(
                command.Title, command.Content, command.Category);

            Sender.Tell(new SearchNoteActorResult { Notes = notes });

            // 검색 이력을 HistoryActor에 전달
            if (historyActor != null)
                historyActor.Tell(notes);
        });

        // 반경 검색 (위치 기반)
        Receive<SearchNoteByRadiusActorCommand>(command =>
        {
            var notes = noteRepository.SearchByRadius(
                command.Latitude, command.Longitude, command.Radius);

            Sender.Tell(new SearchNoteActorResult { Notes = notes });

            if (historyActor != null)
                historyActor.Tell(notes);
        });

        // 벡터 검색 (유사도 기반)
        Receive<SearchNoteByVectorCommand>(command =>
        {
            var notes = noteRepository.SearchByVector(command.Vector, command.TopN);

            Sender.Tell(new SearchNoteActorResult { Notes = notes });

            if (historyActor != null)
                historyActor.Tell(notes);
        });
    }
}
```

### MCP Tool과 액터 연동

```csharp
// McpServer/Tools/NoteTool.cs
[McpServerToolType]
public static class NoteTool
{
    [McpServerTool, Description("웹노리 노트에 노트를 추가합니다.")]
    public static async Task<string> AddNote(
        ActorService actorService,
        [Description("노트의 제목")] string title,
        [Description("노트의 컨텐츠")] string content,
        [Description("노트의 카테고리")] string? category,
        [Description("위도")] double? latitude,
        [Description("경도")] double? longitude,
        [Description("임베딩 벡터")] float[]? tagsEmbeddedAsSingle)
    {
        // Tell 패턴: fire-and-forget
        actorService.RecordActor.Tell(new AddNoteCommand()
        {
            Title = title,
            Category = category,
            Content = content,
            Latitude = latitude,
            Longitude = longitude,
            TagsEmbeddedAsSingle = new RavenVector<float>(tagsEmbeddedAsSingle)
        }, ActorRefs.NoSender);

        return JsonSerializer.Serialize(note);
    }

    [McpServerTool, Description("웹노리 노트에서 Text검색을 합니다.")]
    public static async Task<string> SearchNoteByText(
        ActorService actorService,
        string? title, string? content, string? category)
    {
        // Ask 패턴: 응답 대기
        var result = await actorService.SearchActor.Ask(
            new SearchNoteByTextCommand()
            {
                Title = title,
                Content = content,
                Category = category
            }, TimeSpan.FromSeconds(5));

        if (result is SearchNoteActorResult searchResult)
            return JsonSerializer.Serialize(searchResult.Notes);

        return "Failed to get note history.";
    }

    [McpServerTool, Description("웹노리 노트에서 반경검색을 합니다.")]
    public static async Task<string> SearchNoteByRadius(
        ActorService actorService,
        double latitude, double longitude, double radius)
    {
        var result = await actorService.SearchActor.Ask(
            new SearchNoteByRadiusActorCommand()
            {
                Latitude = latitude,
                Longitude = longitude,
                Radius = radius
            }, TimeSpan.FromSeconds(5));

        if (result is SearchNoteActorResult searchResult)
            return JsonSerializer.Serialize(searchResult.Notes);

        return "Failed to get note history.";
    }

    [McpServerTool, Description("최근 추가된 노트 이력을 가져옵니다.")]
    public static async Task<string> GetNoteHistory(ActorService actorService)
    {
        var result = await actorService.HistoryActor.Ask(
            new GetNoteHistoryCommand(), TimeSpan.FromSeconds(5));

        if (result is SearchNoteActorResult searchResult)
            return JsonSerializer.Serialize(searchResult.Notes);

        return "Failed to get note history.";
    }
}
```

### MCP 액터 아키텍처

```
[AI Agent] --MCP Protocol--> [MCP Server (NoteTool)]
                                    |
                    +---------------+----------------+
                    |               |                |
              RecordActor     SearchActor      HistoryActor
                    |               |                ^
                    |               |                |
                    +--Tell(AddNote)-->  (이력 전달)--+
                    |               |                |
                    v               v                |
              [RavenDB]       [RavenDB]        [In-Memory Queue]
              (저장)           (검색)            (최근 이력 캐시)
```

---

## 14. 원격 액터 (Remote Actors)

### 개념

Akka.Remote는 네트워크를 통해 서로 다른 프로세스의 액터끼리 메시지를 교환할 수 있게 한다. 원격 액터 참조(`IActorRef`)는 로컬 액터와 동일한 API를 사용하므로, **위치 투명성(Location Transparency)** 이 보장된다.

### ActorService - Server/Client 모드 전환

```csharp
// McpServer/Service/ActorService.cs
public class ActorService
{
    private readonly ActorSystem actorSystem;

    public IActorRef SearchActor { get; set; }
    public IActorRef RecordActor { get; set; }
    public IActorRef HistoryActor { get; set; }

    public ActorService(bool serverMode)
    {
        // HOCON 설정: 원격 통신 활성화
        var config = ConfigurationFactory.ParseString($@"
            akka {{
                actor {{
                    provider = ""Akka.Remote.RemoteActorRefProvider, Akka.Remote""
                }}
                remote {{
                    dot-netty.tcp {{
                        hostname = ""127.0.0.1""
                        port = {(serverMode ? 5500 : 0)}
                    }}
                }}
            }}
        ");

        actorSystem = ActorSystem.Create("MyActorSystem", config);

        if (serverMode)
        {
            // 서버 모드: 액터를 직접 생성
            SearchActor = actorSystem.ActorOf<SearchActor>("search-actor");
            RecordActor = actorSystem.ActorOf<RecordActor>("record-actor");
            HistoryActor = actorSystem.ActorOf<HistoryActor>("history-actor");

            // 액터 간 연결 설정
            RecordActor.Tell(new SetHistoryActorCommand { HistoryActor = HistoryActor });
            SearchActor.Tell(new SetHistoryActorCommand { HistoryActor = HistoryActor });
        }
        else
        {
            // 클라이언트 모드: 원격 액터 참조 획득
            var remoteAddress = "akka.tcp://MyActorSystem@127.0.0.1:5500";

            SearchActor = actorSystem
                .ActorSelection($"{remoteAddress}/user/search-actor")
                .ResolveOne(TimeSpan.FromSeconds(3)).Result;

            RecordActor = actorSystem
                .ActorSelection($"{remoteAddress}/user/record-actor")
                .ResolveOne(TimeSpan.FromSeconds(3)).Result;

            HistoryActor = actorSystem
                .ActorSelection($"{remoteAddress}/user/history-actor")
                .ResolveOne(TimeSpan.FromSeconds(3)).Result;
        }
    }
}
```

### IHostedService로 ActorService 초기화

```csharp
// McpServer/Config/ActorServiceInitializer.cs
public class ActorServiceInitializer : IHostedService
{
    private readonly ActorService _actorService;

    public ActorServiceInitializer(ActorService actorService)
    {
        _actorService = actorService;
    }

    public Task StartAsync(CancellationToken cancellationToken)
    {
        // ActorService는 생성자에서 이미 초기화됨
        return Task.CompletedTask;
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        return Task.CompletedTask;
    }
}
```

### Program.cs - 실행 모드 설정

```csharp
// McpServer/Program.cs
var builder = Host.CreateApplicationBuilder(args);

builder.Services
    .AddMcpServer()
    .WithStdioServerTransport()
    .WithToolsFromAssembly();

// 실행 인자로 Server/Client 모드 전환
var clientMode = args.Contains("--clientMode");
builder.Services.AddSingleton<ActorService>(
    provider => new ActorService(!clientMode));

builder.Services.AddHostedService<ActorServiceInitializer>();

await builder.Build().RunAsync();
```

### 원격 액터 주소 형식

```
akka.tcp://{SystemName}@{hostname}:{port}/user/{actorName}
```

| 구성 요소 | 예시 | 설명 |
|-----------|------|------|
| 프로토콜 | `akka.tcp` | DotNetty TCP 전송 |
| SystemName | `MyActorSystem` | ActorSystem 이름 (양쪽이 같아야 함) |
| hostname | `127.0.0.1` | 서버 IP 주소 |
| port | `5500` | 서버 리스닝 포트 |
| path | `/user/search-actor` | 액터 경로 (`/user/`는 사용자 생성 액터의 루트) |

### 다중 ActorSystem 원격 통신 예시

```csharp
// BlazorActorApp/Program.cs
// 두 개의 ActorSystem을 다른 포트로 생성
var actorSystem = akkaService.CreateActorSystem("default", 9000);
var actorSystem2 = akkaService.CreateActorSystem("default2", 9001);

// 각 시스템에 동일한 이름의 액터 생성
actorSystem.ActorOf(Props.Create<BasicActor>(), "someActor");
actorSystem2.ActorOf(Props.Create<BasicActor>(), "someActor");

// 원격 참조로 접근 가능:
// akka.tcp://default@127.0.0.1:9000/user/someActor
// akka.tcp://default2@127.0.0.1:9001/user/someActor
```

### ActorSelection vs ResolveOne

| 방식 | 설명 | 반환 타입 |
|------|------|-----------|
| `ActorSelection(path)` | 경로 기반 선택 (와일드카드 가능) | `ActorSelection` |
| `.ResolveOne(timeout)` | Selection에서 단일 `IActorRef` 해석 | `Task<IActorRef>` |
| `Tell()` | `ActorSelection`으로도 직접 전송 가능 | - |

> **주의사항**: `ResolveOne`은 원격 액터가 아직 생성되지 않았거나 네트워크가 불안정하면 타임아웃이 발생한다. 프로덕션에서는 재시도 로직을 추가하는 것이 좋다.

---

## 15. 테스트 패턴 (TestKit)

### 개념

`Akka.TestKit.Xunit2`는 Akka.NET 액터를 xUnit 환경에서 테스트하기 위한 프레임워크이다. `TestKit` 기본 클래스를 상속받으면 `TestActor`(기본 테스트 프로브)와 `ExpectMsg`, `Within` 등의 어설션 메서드를 사용할 수 있다.

### TestKitXunit - 테스트 베이스 클래스

```csharp
// ActorLibTest/TestKitXunit.cs
[assembly: CollectionBehavior(DisableTestParallelization = true)]

public abstract class TestKitXunit : TestKit
{
    protected readonly AkkaService _akkaService;
    protected readonly ITestOutputHelper output;
    protected readonly ILoggingAdapter _logger;

    public TestKitXunit(ITestOutputHelper output) : base(GetConfig())
    {
        this.output = output;
        _akkaService = new AkkaService();

        // TestKit의 ActorSystem을 AkkaService에 등록
        _akkaService.SetDeafaultSystem(this.Sys);
        _logger = this.Sys.Log;
    }

    public static Config GetConfig()
    {
        return ConfigurationFactory.ParseString(@"
            akka {
                loglevel = DEBUG
                loggers = [""Akka.Logger.NLog.NLogLogger, Akka.Logger.NLog""]
            }

            my-custom-mailbox {
                mailbox-type : ""ActorLib.Actor.Test.IssueTrackerMailbox, ActorLib""
            }

            custom-dispatcher {
                type = Dispatcher
                throughput = 100
            }

            # ... 기타 디스패처 및 Persistence 설정 ...

            akka.persistence {
                journal {
                    plugin = ""akka.persistence.journal.ravendb""
                    ravendb {
                        class = ""Akka.Persistence.RavenDb.Journal.RavenDbJournal, Akka.Persistence.RavenDb""
                        urls = [""http://localhost:9000""]
                        name = ""net-core-labs""
                        auto-initialize = false
                    }
                }
                snapshot-store {
                    plugin = ""akka.persistence.snapshot-store.ravendb""
                    ravendb {
                        class = ""Akka.Persistence.RavenDb.Snapshot.RavenDbSnapshotStore, Akka.Persistence.RavenDb""
                        urls = [""http://localhost:9000""]
                        name = ""net-core-labs""
                        auto-initialize = false
                    }
                }
            }
        ");
    }

    protected override void Dispose(bool disposing)
    {
        output.WriteLine(_textWriter.ToString());
        Console.SetOut(_originalOut);
        base.Dispose(disposing);
    }
}
```

### 기본 액터 테스트

```csharp
// ActorLibTest/Actors/Intro/BasicTest.cs
public class BasicTest : TestKitXunit
{
    public BasicTest(ITestOutputHelper output) : base(output) { }

    [Theory(DisplayName = "Hello에 응답하는 액터테스트")]
    [InlineData(10, 3000)]
    public void HelloWorldAreOK(int testCount, int cutoff, bool isPerformTest = false)
    {
        var actorSystem = _akkaService.GetActorSystem();

        // TestProbe 생성: 메시지 수신을 검증하는 테스트 액터
        TestProbe testProbe = this.CreateTestProbe(actorSystem);

        var basicActor = actorSystem.ActorOf(
            Props.Create(() => new BasicActor()));

        // TestProbe를 BasicActor에 등록
        basicActor.Tell(testProbe.Ref);
        testProbe.ExpectMsg("done");

        // 시간 제한 내 테스트 실행
        Within(TimeSpan.FromMilliseconds(cutoff), () =>
        {
            for (int i = 0; i < testCount; i++)
                basicActor.Tell("hello");

            for (int i = 0; i < testCount; i++)
                testProbe.ExpectMsg("world");
        });
    }
}
```

### MCP 액터 통합 테스트

```csharp
// ActorLibTest/McpServer/McpServerTest.cs
[Fact(DisplayName = "AddNoteAreOk")]
public void AddNoteAreOk()
{
    var actorSystem = _akkaService.GetActorSystem();

    // 두 개의 TestProbe 생성
    TestProbe testProbe = this.CreateTestProbe(actorSystem);
    TestProbe testProbeHistory = this.CreateTestProbe(actorSystem);

    var recordActor = actorSystem.ActorOf(
        Props.Create(() => new RecordActor()));
    var historyActor = actorSystem.ActorOf(
        Props.Create(() => new HistoryActor()));

    // 프로브 등록
    recordActor.Tell(testProbe.Ref);
    testProbe.ExpectMsg("done-ready");

    historyActor.Tell(testProbeHistory.Ref);
    testProbeHistory.ExpectMsg("done-ready");

    // 액터 간 연결
    recordActor.Tell(new SetHistoryActorCommand { HistoryActor = historyActor });
    testProbe.ExpectMsg("done-set-history");

    Within(TimeSpan.FromMilliseconds(3000), () =>
    {
        // 노트 추가
        recordActor.Tell(new AddNoteCommand()
        {
            Content = "test",
            Category = "test",
            Title = "test",
            Latitude = 37.7749,
            Longitude = -122.4194,
            TagsEmbeddedAsSingle = new RavenVector<float>(
                new float[] { 1.0f, 2.0f, 3.0f })
        });

        testProbe.ExpectMsg("done-add");

        // 이력 확인
        historyActor.Tell(new GetNoteHistoryCommand());
        testProbeHistory.ExpectMsg<SearchNoteActorResult>();
    });
}
```

### TestKit 핵심 API

| API | 설명 |
|-----|------|
| `CreateTestProbe()` | 메시지 수신을 검증하는 TestProbe 생성 |
| `ExpectMsg<T>()` | 특정 타입 메시지 수신 대기 및 검증 |
| `ExpectMsg(string)` | 특정 문자열 메시지 수신 대기 |
| `ExpectNoMsg(timeout)` | 지정 시간 동안 메시지가 오지 않음을 검증 |
| `Within(timeout, action)` | 시간 제한 내 테스트 실행 |
| `TestActor` | 테스트 클래스 자체가 갖는 기본 프로브 |
| `Sys` | TestKit이 제공하는 ActorSystem |

### NBench 성능 테스트 통합

이 프로젝트는 `Pro.NBench.xUnit`을 사용하여 기능 테스트와 성능 테스트를 동일 테스트 클래스에서 실행한다.

```csharp
// 성능 테스트 예시
[NBenchFact]
[PerfBenchmark(NumberOfIterations = 3, RunMode = RunMode.Throughput,
    RunTimeMilliseconds = 1000, TestMode = TestMode.Test)]
[CounterThroughputAssertion("TestCounter", MustBe.GreaterThan, 1000.0d)]
[CounterTotalAssertion("TestCounter", MustBe.GreaterThan, 1500.0d)]
[CounterMeasurement("TestCounter")]
public void HelloWorldPerformanceTest()
{
    HelloWorldAreOK(100, 3000, true);
}

// GC 성능 측정
[NBenchFact]
[PerfBenchmark(RunMode = RunMode.Iterations, TestMode = TestMode.Test)]
[GcThroughputAssertion(GcMetric.TotalCollections, GcGeneration.Gen0,
    MustBe.LessThan, 600)]
[GcThroughputAssertion(GcMetric.TotalCollections, GcGeneration.Gen1,
    MustBe.LessThan, 300)]
[GcThroughputAssertion(GcMetric.TotalCollections, GcGeneration.Gen2,
    MustBe.LessThan, 20)]
public void GarbageCollections_Test()
{
    RunTest(1);
}
```

---

## 16. ASP.NET Core 통합 패턴

### 패턴 A: 수동 싱글톤 방식 (이 프로젝트 방식)

`AkkaService`를 `Singleton`으로 등록하고, `Program.cs`에서 직접 `ActorSystem`과 액터를 초기화한다.

```csharp
// 1. 서비스 등록
builder.Services.AddSingleton<AkkaService>();
builder.Services.AddScoped<SSEService>();

// 2. ActorSystem 및 액터 초기화
var app = builder.Build();
var akkaService = app.Services.GetRequiredService<AkkaService>();
var actorSystem = akkaService.CreateActorSystem("default", 9000);

// 3. 액터 생성 및 등록
var throttlerouter = actorSystem.ActorOf(
    Props.Create(() => new ThrottleActor(5)));
akkaService.AddActor("throttlerouter", throttlerouter);
```

**장점**: 단순하고 명시적이다.
**단점**: 액터 라이프사이클이 DI 컨테이너와 분리되어 있다.

### 패턴 B: IHostedService 방식

`IHostedService`를 구현하여 ActorSystem의 시작과 종료를 호스트 라이프사이클에 연결한다.

```csharp
// McpServer/Config/ActorServiceInitializer.cs
public class ActorServiceInitializer : IHostedService
{
    private readonly ActorService _actorService;

    public ActorServiceInitializer(ActorService actorService)
    {
        _actorService = actorService;
    }

    public Task StartAsync(CancellationToken cancellationToken)
    {
        // ActorService 초기화 (생성자에서 이미 수행)
        return Task.CompletedTask;
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        // 정상 종료 처리 가능
        return Task.CompletedTask;
    }
}

// Program.cs 등록
builder.Services.AddSingleton<ActorService>(
    provider => new ActorService(!clientMode));
builder.Services.AddHostedService<ActorServiceInitializer>();
```

### 패턴 C: Akka.Hosting 방식 (권장)

`Akka.Hosting` 패키지를 사용하면 ASP.NET Core DI와 완전히 통합된다. 아래는 이 프로젝트의 의존성에 포함된 `Akka.DependencyInjection`을 활용한 권장 패턴이다.

```csharp
// Akka.Hosting을 사용한 통합 예시
using Akka.Hosting;
using Akka.DependencyInjection;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddAkka("MyActorSystem", (configurationBuilder, provider) =>
{
    configurationBuilder
        .WithRemoting("127.0.0.1", 9000)
        .WithActors((system, registry, resolver) =>
        {
            // DI를 통한 액터 생성
            var basicActorProps = resolver.Props<BasicActor>();
            var basicActor = system.ActorOf(basicActorProps, "basic");
            registry.Register<BasicActor>(basicActor);

            // 라우터와 결합
            var routerProps = basicActorProps
                .WithRouter(new RoundRobinPool(5));
            var router = system.ActorOf(routerProps, "router");
            registry.Register<BasicActor>(router);
        });
});
```

### Controller에서 액터 사용

```csharp
// SSEController 패턴 - 서비스 계층을 통한 접근
[Route("api/sse")]
public class SSEController : Controller
{
    private SSEService SSEService { get; set; }

    public SSEController(SSEService sseService)
    {
        SSEService = sseService;
    }

    [HttpGet("message/{identy}")]
    public async Task<ActionResult> GetMessageByActor(string identy)
    {
        // SSEService -> AkkaService -> Actor
        object message = await SSEService.CheckNotification($"sse-{identy}");

        if (message is Notification notification)
        {
            var serializedData = JsonSerializer.Serialize(notification);
            return Content($"data: {serializedData}\n\n", "text/event-stream");
        }

        return Content("data: null\n\n", "text/event-stream");
    }
}
```

### MCP Tool에서의 DI 주입 패턴

MCP Server에서는 `ActorService`가 DI를 통해 Tool 메서드에 자동 주입된다.

```csharp
[McpServerToolType]
public static class NoteTool
{
    // ActorService가 DI 컨테이너에서 자동 주입됨
    [McpServerTool]
    public static async Task<string> SearchNoteByText(
        ActorService actorService,  // DI 주입
        string? title, string? content, string? category)
    {
        // Ask 패턴으로 동기적 응답 대기
        var result = await actorService.SearchActor.Ask(
            new SearchNoteByTextCommand { Title = title, Content = content, Category = category },
            TimeSpan.FromSeconds(5));

        if (result is SearchNoteActorResult searchResult)
            return JsonSerializer.Serialize(searchResult.Notes);

        return "Failed";
    }
}
```

---

## 부록: HOCON 설정 전체 예시

아래는 이 프로젝트에서 사용되는 전체 HOCON 설정을 통합 정리한 것이다.

```hocon
akka {
    # 로깅 설정
    loglevel = DEBUG
    loggers = ["Akka.Logger.NLog.NLogLogger, Akka.Logger.NLog"]

    # 액터 프로바이더 (원격 활성화 시)
    actor {
        provider = "Akka.Remote.RemoteActorRefProvider, Akka.Remote"
    }

    # 원격 통신 설정
    remote {
        dot-netty.tcp {
            hostname = "127.0.0.1"
            port = 9000
        }
    }

    # Persistence 설정 (RavenDB)
    persistence {
        journal {
            plugin = "akka.persistence.journal.ravendb"
            ravendb {
                class = "Akka.Persistence.RavenDb.Journal.RavenDbJournal, Akka.Persistence.RavenDb"
                plugin-dispatcher = "akka.actor.default-dispatcher"
                urls = ["http://localhost:9000"]
                name = "net-core-labs"
                auto-initialize = false
            }
        }
        snapshot-store {
            plugin = "akka.persistence.snapshot-store.ravendb"
            ravendb {
                class = "Akka.Persistence.RavenDb.Snapshot.RavenDbSnapshotStore, Akka.Persistence.RavenDb"
                plugin-dispatcher = "akka.actor.default-dispatcher"
                urls = ["http://localhost:9000"]
                name = "net-core-labs"
                auto-initialize = false
            }
        }
        query {
            ravendb {
                class = "Akka.Persistence.RavenDb.Query.RavenDbReadJournalProvider, Akka.Persistence.RavenDb"
            }
        }
    }
}

# 커스텀 메일박스
my-custom-mailbox {
    mailbox-type : "ActorLib.Actor.Test.IssueTrackerMailbox, ActorLib"
}

# 디스패처 설정
custom-dispatcher {
    type = Dispatcher
    throughput = 100
}

custom-task-dispatcher {
    type = TaskDispatcher
    throughput = 100
}

custom-dedicated-dispatcher {
    type = PinnedDispatcher
}

fork-join-dispatcher {
    type = ForkJoinDispatcher
    throughput = 1
    dedicated-thread-pool {
        thread-count = 1
        deadlock-timeout = 3s
        threadtype = background
    }
}

synchronized-dispatcher {
    type = SynchronizedDispatcher
    throughput = 100
}
```

---

## 부록: 핵심 용어 정리

| 용어 | 설명 |
|------|------|
| **ActorSystem** | 모든 액터의 최상위 컨테이너. 스레드풀, 디스패처 등 인프라를 관리 |
| **IActorRef** | 액터에 대한 불변 참조. 로컬/원격 구분 없이 동일 API 사용 |
| **Props** | 액터 인스턴스를 생성하기 위한 설정(팩토리 패턴) |
| **Tell** | 비동기 메시지 전송 (fire-and-forget) |
| **Ask** | 응답을 기다리는 메시지 전송 (`Task<object>` 반환) |
| **Mailbox** | 액터가 수신한 메시지를 보관하는 큐 |
| **Dispatcher** | 액터에게 스레드를 할당하는 엔진 |
| **Router/Routee** | 메시지를 분배하는 라우터와 실제 처리하는 routee |
| **Supervision** | 부모 액터가 자식 액터의 실패를 관리하는 전략 |
| **FSM** | 유한 상태 기계 패턴의 액터 |
| **Persistence** | 액터 상태를 영구 저장소에 보존하는 메커니즘 |
| **Journal** | 이벤트를 순서대로 저장하는 영속화 저장소 |
| **Snapshot** | 특정 시점의 전체 상태를 저장하는 체크포인트 |
| **HOCON** | Human-Optimized Config Object Notation. Akka.NET의 설정 형식 |
| **PipeTo** | 비동기 작업 결과를 액터 메시지로 변환하여 전달하는 패턴 |
| **Become/Unbecome** | 런타임에 메시지 핸들러를 교체하는 상태 전환 패턴 |
| **Location Transparency** | 로컬/원격 액터를 동일한 방식으로 다루는 특성 |
