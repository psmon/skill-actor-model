---
name: dotnet-akka-net
description: C# + Akka.NET 액터 모델 코드를 생성합니다. C#으로 Akka.NET 기반 액터를 구현하거나, ReceiveActor, FSM 배치처리, 라우팅, Dispatcher, Mailbox, Persistence(RavenDB/SQLite), Streams Throttle, SSE, Remote, MCP Server 등 Akka.NET 패턴을 작성할 때 사용합니다.
argument-hint: "[패턴명] [요구사항]"
---

# C# + Akka.NET 액터 모델 스킬

C# + Akka.NET(1.5.x) 기반의 액터 모델 코드를 생성하는 스킬입니다.

## 참고 문서

- 상세 패턴 가이드: [skill-maker/docs/actor/03-dotnet-akka-net/README.md](../../../../skill-maker/docs/actor/03-dotnet-akka-net/README.md)
- 액터모델 개요: [skill-maker/docs/actor/00-actor-model-overview.md](../../../../skill-maker/docs/actor/00-actor-model-overview.md)
- 크로스 플랫폼 비교: [skill-maker/docs/actor/05-cross-platform-comparison.md](../../../../skill-maker/docs/actor/05-cross-platform-comparison.md)

## 환경

- **프레임워크**: Akka.NET 1.5.x
- **언어**: C# (.NET 9.0)
- **웹 프레임워크**: ASP.NET Core
- **라이선스**: Apache License 2.0
- **주요 패키지**: Akka, Akka.Remote, Akka.Streams, Akka.DependencyInjection, Akka.Persistence, Akka.Persistence.RavenDB, Akka.Persistence.Sqlite

## 지원 패턴

### 1. 기본 액터 (BasicActor)
- `ReceiveActor` 상속, 생성자에서 `Receive<T>(handler)` 등록
- 동기 `Receive<T>()` + 비동기 `ReceiveAsync<T>()`
- `Props.Create<T>()` 또는 `Props.Create(() => new Actor(args))`
- `Sender.Tell()`, `Self.Tell()`, `Context.ActorOf()`
- 생명주기: `PreStart()`, `PostStop()`, `PreRestart()`, `PostRestart()`

```csharp
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
            logger.Info($"Todo: {msg.Title}");
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
        logger.Info("BasicActor starting.");
        base.PreStart();
    }

    protected override void PostStop()
    {
        logger.Info("BasicActor stopping.");
        base.PostStop();
    }
}
```

### 2. FSM 배치 처리 (Finite State Machine)
- `FSM<TState, TData>` 상속 -- 가장 완성도 높은 FSM 프레임워크
- `StartWith()`, `When()`, `WhenUnhandled()`, `OnTransition()`
- `Stay()`, `GoTo()`, `Using()` DSL
- `StateTimeout` 자동 타임아웃
- 불변 데이터: `ImmutableList<T>` 사용

```csharp
public class FSMBatchActor : FSM<State, IData>
{
    public FSMBatchActor()
    {
        StartWith(State.Idle, Uninitialized.Instance);

        When(State.Idle, state =>
        {
            if (state.FsmEvent is SetTarget target && state.StateData is Uninitialized)
                return Stay().Using(new Todo(target.Ref, ImmutableList<object>.Empty));
            return null;
        });

        When(State.Active, state =>
        {
            if (state.FsmEvent is Flush or StateTimeout && state.StateData is Todo t)
                return GoTo(State.Idle).Using(t.Copy(ImmutableList<object>.Empty));
            return null;
        }, TimeSpan.FromSeconds(1));

        WhenUnhandled(state =>
        {
            if (state.FsmEvent is Queue q && state.StateData is Todo t)
                return GoTo(State.Active).Using(t.Copy(t.Queue.Add(q.Obj)));
            return Stay();
        });

        OnTransition((from, to) =>
        {
            if (from == State.Active && to == State.Idle)
                if (StateData is Todo todo)
                    todo.Target.Tell(new Batch(todo.Queue));
        });

        Initialize();
    }
}
```

### 3. Streams 기반 Throttle (흐름 제어)
- `Source.ActorRef<T>()` -> `.Throttle()` -> `Sink.ActorRef<T>()` 파이프라인
- `OverflowStrategy.DropNew` 오버플로우 전략
- `ThrottleMode.Shaping` 정형화 모드
- 런타임 TPS 동적 변경 (스로틀러 교체)
- `Context.Materializer()` 매터리얼라이저

```csharp
_throttler = Source.ActorRef<object>(1000, OverflowStrategy.DropNew)
    .Throttle(processCouuntPerSec, TimeSpan.FromSeconds(1),
        processCouuntPerSec, ThrottleMode.Shaping)
    .To(Sink.ActorRef<object>(Self, NotUsed.Instance))
    .Run(_materializer);

// 런타임 TPS 변경
Receive<ChangeTPS>(msg =>
{
    _throttler = Source.ActorRef<object>(1000, OverflowStrategy.DropNew)
        .Throttle(msg.processCouuntPerSec, TimeSpan.FromSeconds(1),
            msg.processCouuntPerSec, ThrottleMode.Shaping)
        .To(Sink.ActorRef<object>(Self, NotUsed.Instance))
        .Run(_materializer);
});
```

### 4. 타이머 기반 Throttle
- `Context.System.Scheduler.ScheduleTellRepeatedly()` 스케줄러
- 수동 큐 관리 + `maxBust` 파라미터
- Streams보다 단순하지만 유연성 낮음

### 5. 라우팅 (Router)
- **Pool Router**: `RoundRobinPool`, `RandomPool`, `BroadcastPool`, `ScatterGatherFirstCompletedPool`, `ConsistentHashingPool`, `SmallestMailboxPool`, `TailChoppingPool`
- `Props.Create<T>().WithRouter(new RoundRobinPool(5))`
- 동적 Routee 추가: `router.Tell(new AddRoutee(Routee.FromActorRef(actor)))`
- `Pool(0)` 패턴: 라우터만 생성, routee는 동적 추가

```csharp
// Pool Router
var roundRobin = system.ActorOf(
    Props.Create<BasicActor>().WithRouter(new RoundRobinPool(5)),
    "roundrobin"
);

// Broadcast Router
var broadcast = system.ActorOf(
    Props.Create<BasicActor>().WithRouter(new BroadcastPool(5)),
    "broadcast"
);

// ScatterGather
var scatter = system.ActorOf(
    Props.Create(() => new WorkerActor())
        .WithRouter(new ScatterGatherFirstCompletedPool(0, TimeSpan.FromSeconds(10))),
    "scatter"
);

// ConsistentHashing Pool Router
// 동일 해시키를 가진 메시지는 항상 같은 routee에게 전달
var consistentHash = system.ActorOf(
    Props.Create<BasicActor>().WithRouter(new ConsistentHashingPool(5)),
    "consistenthash"
);

// Random Pool Router
var random = system.ActorOf(
    Props.Create<BasicActor>().WithRouter(new RandomPool(5)),
    "random"
);
```

#### ConsistentHashingPool 메시지 정의

ConsistentHashing 라우터를 사용하려면 메시지가 `IConsistentHashable` 인터페이스를 구현해야 합니다.

```csharp
using Akka.Routing;

// ConsistentHashing용 메시지 (IConsistentHashable 구현 필수)
public record HashedMessage(string Content, string HashKey) : IConsistentHashable
{
    public object ConsistentHashKey => HashKey;
}
```

#### Pool(0) + 동적 AddRoutee

```csharp
// Pool(0): 라우터만 생성, routee는 동적으로 추가
var roundrobin = actorSystem.ActorOf(
    Props.Create<BasicActor>().WithRouter(new RoundRobinPool(0)),
    "roundrobin");

// 나중에 routee 동적 추가
var routee = Routee.FromActorRef(counselorActor);
roundrobin.Tell(new AddRoutee(routee));
```

> **Pool(0)의 의미**: 크기 0으로 생성하면 라우터만 만들고 routee는 나중에 `AddRoutee`로 동적 추가할 수 있습니다.

### 6. Dispatcher 설정
- HOCON 기반 5가지 Dispatcher 타입
- `Props.Create<T>().WithDispatcher("my-dispatcher")`
- `throughput` 파라미터로 액터당 메시지 배치 크기 제어

```hocon
# 기본 디스패처: ThreadPool 기반
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

```csharp
// 디스패처 적용
var actor = actorSystem.ActorOf(
    Props.Create<BasicActor>().WithDispatcher("custom-dispatcher"),
    "myActor");

// 라우터 + 디스패처 결합
var router = actorSystem.ActorOf(
    Props.Create<BasicActor>()
        .WithRouter(new RoundRobinPool(5))
        .WithDispatcher("fork-join-dispatcher"),
    "routerWithDispatcher");
```

| 디스패처 | 설명 | 용도 |
|----------|------|------|
| `Dispatcher` | .NET ThreadPool 기반, 범용 | 일반적인 액터 처리 |
| `TaskDispatcher` | .NET Task/TPL 기반 | async/await 패턴 |
| `PinnedDispatcher` | 액터당 전용 스레드 1개 | I/O 바운드, 블로킹 작업 |
| `ForkJoinDispatcher` | 전용 스레드풀 | 격리된 스레드풀 필요 시 |
| `SynchronizedDispatcher` | SynchronizationContext | UI 스레드 접근 필요 시 |

### 7. 커스텀 Mailbox
- `UnboundedPriorityMailbox` 상속으로 우선순위 메일박스
- `PriorityGenerator` 오버라이드: 0(최우선) ~ N(낮음)
- HOCON `mailbox-type` 설정 + `Props.WithMailbox("config-key")`

```csharp
public class IssueTrackerMailbox : UnboundedPriorityMailbox
{
    protected override int PriorityGenerator(object message)
    {
        return message switch
        {
            SecurityIssue => 0,   // 최우선
            BugReport => 1,
            _ => 2
        };
    }
}
```

#### HOCON 메일박스 바인딩

```hocon
my-custom-mailbox {
    mailbox-type : "MyApp.IssueTrackerMailbox, MyApp"
}
```

```csharp
// 액터 생성 시 메일박스 지정
var mailBoxActor = actorSystem.ActorOf(
    Props.Create(() => new BasicActor())
         .WithMailbox("my-custom-mailbox"));
```

### 8. 감독 전략 (Supervision)
- `OneForOneStrategy` / `AllForOneStrategy`
- `SupervisorStrategy()` 오버라이드
- `Directive.Resume`, `Directive.Restart`, `Directive.Stop`, `Directive.Escalate`
- `Become()` / `Unbecome()` 상태 전환 (FSM보다 경량)

```csharp
protected override SupervisorStrategy SupervisorStrategy()
{
    return new OneForOneStrategy(
        maxNrOfRetries: 10,
        withinTimeRange: TimeSpan.FromMinutes(1),
        localOnlyDecider: ex => ex switch
        {
            ArithmeticException => Directive.Resume,
            NullReferenceException => Directive.Restart,
            ArgumentException => Directive.Stop,
            _ => Directive.Escalate
        }
    );
}
```

### 8-1. Become/Unbecome 상태 전환
- FSM보다 경량인 상태 관리 방법
- `Become(handler)`: 메시지 핸들러를 교체하여 상태 전환
- `UntypedActor` 또는 `ReceiveActor`에서 사용

```csharp
public class CounselorsActor : UntypedActor
{
    private CounselorsState counselorsState = CounselorsState.Offline;

    protected override void OnReceive(object message)
    {
        switch (message)
        {
            case SetCounselorsState counselor:
                if (counselor.State == CounselorsState.Online)
                {
                    counselorsState = counselor.State;
                    Become(Online);   // Online 상태로 전환
                }
                else
                {
                    counselorsState = counselor.State;
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
                Sender.Tell(new WishTask() { WishActor = Self });
                break;
            case AssignTask assignTask:
                Sender.Tell("I Take Task");
                break;
            case SetCounselorsState counselor when counselor.State == CounselorsState.Offline:
                Become(OffLine);
                break;
        }
    }

    private void OffLine(object message)
    {
        switch (message)
        {
            case CheckTakeTask: break; // Offline 상태이므로 무시
            case SetCounselorsState counselor when counselor.State == CounselorsState.Online:
                Become(Online);
                break;
        }
    }
}
```

| 사용 기준 | Become/Unbecome | FSM<TState, TData> |
|-----------|----------------|---------------------|
| 상태 수 | 2~3개 | 3개 이상 |
| 타임아웃 | 수동 구현 | `StateTimeout` 내장 |
| 상태 데이터 | 필드 직접 관리 | `Using(data)` DSL |
| 전이 콜백 | 없음 | `OnTransition()` 내장 |

### 9. Persistence (RavenDB / SQLite)
- `ReceivePersistentActor` 상속, `PersistenceId` 필수
- **이벤트 소싱**: `Persist(event, handler)` + `Recover<T>(handler)`
- **스냅샷**: `SaveSnapshot(state)`, N개 이벤트마다 자동 스냅샷
- **복구 흐름**: SnapshotOffer -> Event replay -> Command handling
- RavenDB 저널/스냅샷 스토어

```csharp
public class SalesActor : ReceivePersistentActor
{
    public override string PersistenceId => "sales-actor";
    private SalesActorState _state = new() { totalSales = 0 };

    public SalesActor()
    {
        Command<Sale>(sale =>
        {
            Persist(sale, _ =>
            {
                _state.totalSales += sale.Price;
                if (LastSequenceNr % 5 == 0) SaveSnapshot(_state.totalSales);
            });
        });

        Recover<Sale>(sale => _state.totalSales += sale.Price);
        Recover<SnapshotOffer>(offer => _state.totalSales = (long)offer.Snapshot);
    }
}
```

#### SQLite 저널/스냅샷 (로컬 파일 기반)

- `Akka.Persistence.Sqlite` 패키지 사용
- `akka.persistence.journal.sqlite` / `akka.persistence.snapshot-store.sqlite` 플러그인 지정
- `auto-initialize = on`으로 SQLite 테이블 자동 생성
- `ReceivePersistentActor` 코드는 동일, HOCON만 SQLite로 교체 가능

```csharp
var hocon = @"
akka.persistence.journal.plugin = ""akka.persistence.journal.sqlite""
akka.persistence.snapshot-store.plugin = ""akka.persistence.snapshot-store.sqlite""

akka.persistence.journal.sqlite {
  class = ""Akka.Persistence.Sqlite.Journal.SqliteJournal, Akka.Persistence.Sqlite""
  plugin-dispatcher = ""akka.actor.default-dispatcher""
  connection-string = ""Filename=sample12-data/akka-persistence.db;Mode=ReadWriteCreate""
  auto-initialize = on
}

akka.persistence.snapshot-store.sqlite {
  class = ""Akka.Persistence.Sqlite.Snapshot.SqliteSnapshotStore, Akka.Persistence.Sqlite""
  plugin-dispatcher = ""akka.actor.default-dispatcher""
  connection-string = ""Filename=sample12-data/akka-persistence.db;Mode=ReadWriteCreate""
  auto-initialize = on
}";
```

```csharp
public sealed class PersistentCounterActor : ReceivePersistentActor
{
    public override string PersistenceId => "sample12-counter-1";
    private int _value;
    private long _eventCount;

    public PersistentCounterActor()
    {
        Command<IncrementCounter>(cmd =>
            Persist(new CounterIncremented(cmd.Amount), ev =>
            {
                _value += ev.Amount;
                _eventCount++;
                if (LastSequenceNr % 5 == 0) SaveSnapshot(new CounterSnapshot(_value, _eventCount));
            }));

        Recover<CounterIncremented>(ev => { _value += ev.Amount; _eventCount++; });
        Recover<SnapshotOffer>(offer =>
        {
            if (offer.Snapshot is CounterSnapshot snap) { _value = snap.Value; _eventCount = snap.EventCount; }
        });
    }
}
```

> 재기동 검증 팁: 동일 `PersistenceId`로 ActorSystem을 두 번 순차 실행하면, 2차 실행 시작 시 `RecoveryCompleted` 로그의 `LastSequenceNr`가 증가해 있어야 합니다.

### 10. SSE (Server-Sent Events) + PipeTo 패턴
- SSEUserActor: 알림 큐 관리
- `PipeTo` 패턴: `Task.Delay().ContinueWith().PipeTo(Self, Sender)` 비동기 대기
- SSEController REST API + SSEService 액터 관리

```csharp
public class SSEUserActor : ReceiveActor
{
    private Queue<Notification> notifications = new Queue<Notification>();

    public SSEUserActor(string identyValue)
    {
        // 알림 메시지 수신 -> 큐에 저장
        Receive<Notification>(msg =>
        {
            notifications.Enqueue(msg);
        });

        // 알림 확인 요청 -> 큐에서 꺼내 반환
        ReceiveAsync<CheckNotification>(async msg =>
        {
            if (notifications.Count > 0)
            {
                Sender.Tell(notifications.Dequeue());
            }
            else
            {
                // 알림이 없으면 1초 후 하트비트 반환 (PipeTo 패턴)
                HeartBeatNotification heartBeat = new HeartBeatNotification();
                Task.Delay(1000)
                    .ContinueWith(tr => heartBeat)
                    .PipeTo(Self, Sender);  // 완료 시 Self에게 전달, 원래 Sender 유지
            }
        });
    }
}
```

> **PipeTo 패턴**: `Task.ContinueWith().PipeTo(target, sender)`는 비동기 작업이 완료되면 결과를 지정된 액터에게 자동으로 `Tell`합니다. 액터 내부에서 `await`로 블로킹하는 것보다 안전합니다.

### 11. MCP Server 통합
- MCP(Model Context Protocol) 도구에서 액터 연동
- `ActorCommand` 메시지 계층: AddNote, SearchByText, SearchByRadius, SearchByVector
- `HistoryActor`: 슬라이딩 윈도우 큐
- Tell (Fire-and-Forget) + Ask (동기 응답) 혼용

### 12. Remote 액터
- HOCON `provider = remote` + `dot-netty.tcp` 설정
- `ActorSelection(path).ResolveOne(timeout)` 원격 액터 조회
- 위치 투명성: `akka.tcp://SystemName@hostname:port/user/actorName`

### 13. 테스트 (TestKit)
- `TestKit` 상속 (xUnit 기반)
- `ExpectMsg<T>()`, `ExpectNoMsg()`, `Within(timeout, action)`
- `CreateTestProbe()` + `TestActor` 내장 프로브
- `Sys.ActorOf()` 테스트 시스템 액터 생성
- NBench 성능 테스트 (`@PerfBenchmark`, Counter, GC 메트릭)

### 14. ASP.NET Core 통합
- **수동 Singleton**: AkkaService + Program.cs 직접 초기화
- **IHostedService**: 라이프사이클 관리
- **Akka.Hosting (권장)**: `AddAkka()` + `WithRemoting()` + `WithActors()` fluent API
- `IRequiredActor<T>` DI로 Controller에서 액터 참조

```csharp
// Akka.Hosting 방식 (권장)
builder.Services.AddAkka("MySystem", config =>
{
    config
        .WithRemoting("localhost", 9000)
        .WithClustering()
        .WithActors((system, registry) =>
        {
            var hello = system.ActorOf(Props.Create<HelloActor>(), "hello");
            registry.Register<HelloActor>(hello);
        });
});

// Controller
public class HelloController : ControllerBase
{
    private readonly IActorRef _hello;
    public HelloController(IRequiredActor<HelloActor> hello)
    {
        _hello = hello.ActorRef;
    }

    [HttpPost]
    public async Task<IActionResult> Hello()
    {
        var response = await _hello.Ask<string>("Hello", TimeSpan.FromSeconds(3));
        return Ok(response);
    }
}
```

### 14-1. AkkaService 다중 ActorSystem 관리

```csharp
public class AkkaService
{
    private Dictionary<string, ActorSystem> actorSystems = new();
    private Dictionary<string, IActorRef> actors = new();

    public ActorSystem CreateActorSystem(string name, int port = 0)
    {
        if (actorSystems.ContainsKey(name))
            throw new Exception($"{name} actorsystem has already been created.");

        if (port == 0)
        {
            actorSystems[name] = ActorSystem.Create(name);
        }
        else
        {
            // port > 0이면 Remote 모드 자동 활성화
            string akkaConfig = @"akka {
                actor { provider = remote }
                remote { dot-netty.tcp { port = $port; hostname = ""127.0.0.1"" } }
            }".Replace("$port", port.ToString());
            actorSystems[name] = ActorSystem.Create(name,
                ConfigurationFactory.ParseString(akkaConfig));
        }
        return actorSystems[name];
    }

    public ActorSystem GetActorSystem(string name = "default")
        => actorSystems.ContainsKey(name) ? actorSystems[name] : CreateActorSystem(name);

    // 액터 레지스트리
    public void AddActor(string name, IActorRef actor) => actors[name] = actor;
    public IActorRef GetActor(string name) => actors.GetValueOrDefault(name);
}
```

- **다중 ActorSystem**: `Dictionary<string, ActorSystem>`으로 여러 시스템을 이름 기반 관리
- **액터 레지스트리**: 주요 액터를 이름으로 등록/조회하여 DI 컨테이너에서 접근 용이
- **원격 모드 자동 전환**: `port` 파라미터가 0이 아니면 자동으로 `Akka.Remote` 활성화

## 코드 생성 규칙

1. **액터는 `ReceiveActor`를 상속**하고 생성자에서 `Receive<T>(handler)`를 등록합니다.
2. **비동기 처리**는 `ReceiveAsync<T>(async handler)`를 사용합니다.
3. **메시지 클래스는 record 또는 불변 class**로 설계합니다.
4. **FSM이 필요하면 `FSM<TState, TData>`**를 사용합니다. 단순 상태 전환은 `Become()`/`Unbecome()`.
5. **HOCON 설정**은 `akka { }` 블록으로 작성합니다.
6. **영속화**: `ReceivePersistentActor` + `PersistenceId` + `Persist()`/`Recover<T>()`.
7. **스트림 흐름 제어**: `Source.ActorRef -> Throttle -> Sink.ActorRef` 파이프라인.
8. **DI 통합**: `Akka.Hosting`의 `AddAkka()` + `IRequiredActor<T>` 패턴을 권장합니다.
9. **로깅**: `Context.GetLogger()`로 `ILoggingAdapter`를 사용합니다.
10. 라우터는 요구사항에 맞는 전략을 선택합니다:
    - 균등 분배: `RoundRobinPool`
    - 최초 완료: `ScatterGatherFirstCompletedPool`
    - 전체 전달: `BroadcastPool`
    - 일관된 해싱: `ConsistentHashingPool`

$ARGUMENTS
