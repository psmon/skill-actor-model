---
name: dotnet-akka-net
description: C# + Akka.NET 액터 모델 코드를 생성합니다. C#으로 Akka.NET 기반 액터를 구현하거나, ReceiveActor, FSM 배치처리, 라우팅, Dispatcher, Mailbox, Persistence(RavenDB), Streams Throttle, SSE, Remote, MCP Server 등 Akka.NET 패턴을 작성할 때 사용합니다.
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
- **주요 패키지**: Akka, Akka.Remote, Akka.Streams, Akka.DependencyInjection, Akka.Persistence.RavenDB

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
```

### 6. Dispatcher 설정
- HOCON 기반 5가지 Dispatcher 타입: Dispatcher, TaskDispatcher, PinnedDispatcher, ForkJoinDispatcher, SynchronizedDispatcher
- `Props.Create<T>().WithDispatcher("my-dispatcher")`
- `throughput` 파라미터로 액터당 메시지 배치 크기 제어

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

### 9. Persistence (RavenDB)
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

### 10. SSE (Server-Sent Events)
- SSEUserActor: 알림 큐 관리
- `PipeTo` 패턴: `Task.Delay().PipeTo(Self)` 하트비트
- SSEController REST API + SSEService 액터 관리

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
