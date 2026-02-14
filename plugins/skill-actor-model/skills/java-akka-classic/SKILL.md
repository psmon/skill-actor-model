---
name: java-akka-classic
description: Java + Akka Classic 액터 모델 코드를 생성합니다. Java로 Akka Classic 기반 액터를 구현하거나, AbstractActor 패턴, 라우팅, 타이머, 배치처리, 스트림, 영속화, 클러스터 등 Akka Classic 패턴을 작성할 때 사용합니다.
argument-hint: "[패턴명] [요구사항]"
---

# Java + Akka Classic 액터 모델 스킬

Java + Akka Classic(2.7.x) 기반의 액터 모델 코드를 생성하는 스킬입니다.

## 참고 문서

- 상세 패턴 가이드: [skill-maker/docs/actor/01-java-akka-classic/README.md](../../../../skill-maker/docs/actor/01-java-akka-classic/README.md)
- 액터모델 개요: [skill-maker/docs/actor/00-actor-model-overview.md](../../../../skill-maker/docs/actor/00-actor-model-overview.md)
- 크로스 플랫폼 비교: [skill-maker/docs/actor/05-cross-platform-comparison.md](../../../../skill-maker/docs/actor/05-cross-platform-comparison.md)

## 환경

- **프레임워크**: Akka Classic 2.7.x
- **언어**: Java 11+
- **빌드**: Gradle / Maven
- **웹 프레임워크**: Spring Boot 2.7.x
- **라이선스**: BSL (Business Source License)
- **주요 패키지**: akka-actor, akka-persistence, akka-persistence-jdbc, sqlite-jdbc

## 지원 패턴

### 1. 기본 액터 (Basic Actor)
- `AbstractActor` 상속, `createReceive()` 구현
- `receiveBuilder().match(Class, handler).matchAny(handler).build()` 패턴
- `Props.create(Class)` 팩토리
- `tell()` (Fire-and-Forget), `ask()` (Request-Response), `forward()`

```java
public class HelloActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props Props() {
        return Props.create(HelloActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, msg -> {
                log.info("Received: {}", msg);
                getSender().tell("world", getSelf());
            })
            .matchAny(o -> log.info("received unknown message"))
            .build();
    }
}
```

#### Guard 조건 (같은 타입 내 분기)

```java
.match(String.class, s -> s.equals("CMD_CREATE"), s -> {
    // "CMD_CREATE" 문자열일 때만 실행
})
.match(String.class, s -> s.equals("CMD_WORK"), s -> {
    // "CMD_WORK" 문자열일 때만 실행
})
```

#### Forward (원래 발신자 유지)

```java
// tell: 현재 액터가 발신자로 설정됨
target.tell(msg, getSelf());

// forward: 원래 발신자 정보를 유지하며 전달 (프록시, 라우터에 유용)
target.forward(msg, getContext());
```

| 패턴 | 코드 | 발신자 정보 |
|------|------|------------|
| `tell` | `target.tell(msg, getSelf())` | 현재 액터가 발신자 |
| `forward` | `target.forward(msg, getContext())` | **원래 발신자** 유지 |

### 1-1. Ask 패턴 (Request-Response)
- `Patterns.ask()`로 메시지를 보내고 `Future`로 응답 수신
- `pipe` 패턴으로 Future 결과를 액터에게 자동 전달 (블로킹 회피)

```java
// ask 패턴 기본 사용법
Timeout timeout = Timeout.create(Duration.ofSeconds(1));
Future<Object> future = Patterns.ask(targetActor, "request", timeout);
String result = (String) Await.result(future, timeout.duration());

// pipe 패턴 (논블로킹 권장)
pipe(future, getContext().dispatcher()).to(getSender());
```

> **주의**: `Await.result()`는 현재 스레드를 블로킹합니다. 가능하면 `pipe` 패턴을 사용하세요.

### 2. 라우팅 (Router)
- **Pool Router**: `RoundRobinPool`, `RandomPool`, `BalancingPool`, `SmallestMailboxPool`, `BroadcastPool`, `TailChoppingPool`, `ConsistentHashingPool`
- **Group Router**: `RoundRobinGroup` (기존 액터 경로 기반)
- HOCON 설정 기반 `FromConfig.getInstance()` 선언적 라우터

```java
// 코드 기반 Pool Router
ActorRef router = system.actorOf(
    new RoundRobinPool(5).props(Props.create(WorkerActor.class)),
    "roundRobinRouter"
);

// Group Router
List<String> paths = Arrays.asList("/user/parent/w1", "/user/parent/w2");
ActorRef groupRouter = context().actorOf(new RoundRobinGroup(paths).props(), "router");

// ConsistentHashing Pool Router
// 메시지가 ConsistentHashable을 구현하면 동일 해시키 → 동일 routee 보장
ActorRef hashRouter = system.actorOf(
    new ConsistentHashingPool(5).props(Props.create(WorkerActor.class)),
    "consistentHashRouter"
);
```

#### ConsistentHashingPool 메시지 정의

ConsistentHashing 라우터를 사용하려면 메시지가 `ConsistentHashingRouter.ConsistentHashable`과 `Serializable`을 구현해야 합니다.

```java
import akka.routing.ConsistentHashingRouter;
import java.io.Serializable;

public final class HelloMessage
        implements ConsistentHashingRouter.ConsistentHashable, Serializable {
    private final String name;
    private final String hashKey;

    public HelloMessage(String name) { this(name, name); }
    public HelloMessage(String name, String hashKey) {
        this.name = name;
        this.hashKey = hashKey;
    }

    public String getName() { return name; }

    @Override
    public Object consistentHashKey() { return hashKey; }
}
```

### 2-1. HOCON 선언적 라우터 설정
- `FromConfig.getInstance()`로 코드 변경 없이 설정 파일에서 라우팅 전략 적용

```hocon
akka.actor.deployment {
  /router1 {
    router = round-robin-pool
    nr-of-instances = 5
  }
  /router2 {
    router = random-pool
    nr-of-instances = 5
  }
  /router3 {
    router = balancing-pool
    nr-of-instances = 5
    pool-dispatcher { attempt-teamwork = off }
  }
  /router4 {
    router = consistent-hashing-pool
    nr-of-instances = 5
  }
  /router5 {
    router = smallest-mailbox-pool
    nr-of-instances = 5
  }
  /router6 {
    router = broadcast-pool
    nr-of-instances = 5
  }
  /router7 {
    router = tail-chopping-pool
    nr-of-instances = 5
    within = 2 milliseconds
    tail-chopping-router.interval = 300 milliseconds
  }
}
```

```java
// FromConfig: HOCON 설정에서 라우터 전략을 자동 로드
ActorRef router = actorSystem.actorOf(
    FromConfig.getInstance().props(WorkerActor.Props(probe.getRef())),
    "router1");  // HOCON의 /router1 설정이 자동 적용
```

| 전략 | 설명 | 사용 시나리오 |
|------|------|-------------|
| `round-robin-pool` | 순서대로 균등 분배 | 일반적인 작업 분배 |
| `random-pool` | 무작위 분배 | 부하가 균일한 작업 |
| `balancing-pool` | 공유 메일박스에서 가져감 | 처리 시간이 불균일한 작업 |
| `smallest-mailbox-pool` | 메일박스가 가장 적은 routee에 분배 | 작업량 편중 방지 |
| `broadcast-pool` | 모든 routee에 동시 전달 | 전체 통지, 캐시 갱신 |
| `tail-chopping-pool` | 순차 전송 후 첫 응답 사용 | 지연 시간 최소화 |
| `consistent-hashing-pool` | 동일 해시키 → 동일 routee 보장 | 세션 친화성, 사용자별 처리 |

### 3. 타이머 (Timer)
- `AbstractActorWithTimers` 상속
- `startSingleTimer()`, `startPeriodicTimer()`, `startTimerAtFixedRate()`
- 타이머 키(Key) 기반 관리 (동일 키로 새 타이머 시작 시 기존 자동 취소)

```java
public class TimerActor extends AbstractActorWithTimers {
    private static final Object TICK_KEY = "TickKey";

    public TimerActor() {
        getTimers().startSingleTimer(TICK_KEY, new FirstTick(), Duration.ofMillis(500));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(FirstTick.class, msg -> {
                getTimers().startPeriodicTimer(TICK_KEY, new Tick(), Duration.ofSeconds(1));
            })
            .match(Tick.class, msg -> log.info("Tick"))
            .build();
    }
}
```

### 4. 배치 처리 (Batch)
- 타이머 기반 메시지 축적 후 주기적 일괄 처리
- `SafeBatchActor` 패턴: 메시지를 List에 축적, 타이머 Tick마다 flush

```java
public class SafeBatchActor extends AbstractActorWithTimers {
    private static final Object TICK_KEY = "TickKey";
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final ArrayList<String> batchList = new ArrayList<>();

    public SafeBatchActor() {
        getTimers().startSingleTimer(TICK_KEY, new FirstTick(), Duration.ofMillis(0));
    }

    public static Props Props() {
        return Props.create(SafeBatchActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, message -> {
                batchList.add(message);
            })
            .match(FirstTick.class, message -> {
                getTimers().startPeriodicTimer(
                    TICK_KEY, new Tick(), Duration.ofSeconds(1));
            })
            .match(Tick.class, message -> {
                log.info("Batch Flush {}", batchList.size());
                // 여기서 bulk INSERT, API 호출 등 수행
                batchList.clear();
            })
            .build();
    }

    private static final class FirstTick {}
    private static final class Tick {}
}
```

> **실무 팁**: `batchList.size() >= threshold` 조건으로 즉시 Flush하는 로직을 추가하면 더 효율적입니다.

### 5. 감독 전략 (Supervision)
- `OneForOneStrategy` / `AllForOneStrategy`
- `supervisorStrategy()` 오버라이드
- `DeciderBuilder.match()` 체인으로 예외별 Resume/Restart/Stop/Escalate 분기

```java
@Override
public SupervisorStrategy supervisorStrategy() {
    return new OneForOneStrategy(10, Duration.ofMinutes(1),
        DeciderBuilder
            .match(ArithmeticException.class, e -> SupervisorStrategy.resume())
            .match(NullPointerException.class, e -> SupervisorStrategy.restart())
            .match(IllegalArgumentException.class, e -> SupervisorStrategy.stop())
            .matchAny(o -> SupervisorStrategy.escalate())
            .build()
    );
}
```

### 6. Akka Streams 흐름 제어
- `Source.actorRef()` -> `.throttle()` -> `Sink.actorRef()` 파이프라인
- `OverflowStrategy`: backpressure, dropNew, dropHead, dropTail, dropBuffer, fail
- `mapAsync(parallelism, fn)` 병렬 비동기 처리

```java
ActorRef throttler = Source.actorRef(1000, OverflowStrategy.dropNew())
    .throttle(3, FiniteDuration.create(1, TimeUnit.SECONDS), 3, ThrottleMode.shaping())
    .to(Sink.actorRef(targetActor, NotUsed.getInstance()))
    .run(materializer);
```

### 6-1. Backpressure 파이프라인
- `Source.range()` → `buffer(backpressure)` → `mapAsync()` → `Sink.foreach()` 완전한 파이프라인
- 비동기 API 호출에 배압 적용

```java
Source<Integer, NotUsed> source = Source.range(1, 4000);

// 병렬 비동기 처리 Flow
int parallelism = 450;
Flow<Integer, String, NotUsed> parallelFlow =
    Flow.<Integer>create()
        .mapAsync(parallelism, param -> CompletableFuture.supplyAsync(() -> {
            // 비동기 API 호출 시뮬레이션
            return "Response for " + param;
        }, executor));

// Buffer + Backpressure 전략
Flow<Integer, Integer, NotUsed> backpressureFlow =
    Flow.<Integer>create()
        .buffer(1000, OverflowStrategy.backpressure());

// 파이프라인 연결 및 실행
source.via(backpressureFlow)
      .via(parallelFlow)
      .to(Sink.foreach(s -> { /* 처리 완료 */ }))
      .run(materializer);
```

### 6-2. WorkingWithGraph (Broadcast + Merge)
- `GraphDSL`로 fan-out/fan-in 그래프 구성
- `Source(Random 1~100)` → `Broadcast(2)` → fan1(`+2`), fan2(`+10`) → `Merge(2)` → `Sink.foreach`

```java
Source<Integer, NotUsed> source = Source.from(randomValues);
Flow<Integer, Integer, NotUsed> fan1 = Flow.of(Integer.class).map(n -> n + 2);
Flow<Integer, Integer, NotUsed> fan2 = Flow.of(Integer.class).map(n -> n + 10);
Sink<Integer, CompletionStage<Done>> sink = Sink.foreach(out -> System.out.println("[Out] " + out));

RunnableGraph<CompletionStage<Done>> graph = RunnableGraph.fromGraph(
    GraphDSL.create(sink, (builder, out) -> {
        var src = builder.add(source);
        var bcast = builder.add(Broadcast.<Integer>create(2));
        var merge = builder.add(Merge.<Integer>create(2));
        var f1 = builder.add(fan1);
        var f2 = builder.add(fan2);

        builder.from(src).viaFanOut(bcast);
        builder.from(bcast.out(0)).via(f1).toFanIn(merge);
        builder.from(bcast.out(1)).via(f2).toFanIn(merge);
        builder.from(merge).to(out);
        return ClosedShape.getInstance();
    })
);
graph.run(materializer);
```

**OverflowStrategy 비교**:

| 전략 | 설명 |
|------|------|
| `backpressure()` | 버퍼가 가득 차면 upstream에 신호를 보내 생산 중단 |
| `dropNew()` | 버퍼가 가득 차면 새로운 요소를 버림 |
| `dropHead()` | 버퍼가 가득 차면 가장 오래된 요소를 버림 |
| `dropTail()` | 버퍼가 가득 차면 가장 최근 요소를 버림 |
| `dropBuffer()` | 버퍼를 비우고 새 요소만 유지 |
| `fail()` | 버퍼가 가득 차면 스트림 실패 |

### 7. Persistence (SQLite + akka-persistence-jdbc)
- `AbstractPersistentActor` 상속 + `persistenceId()` 필수
- `persist(event, handler)` / `createReceiveRecover()` 이벤트 소싱
- `SnapshotOffer` + `saveSnapshot()` 스냅샷 복구/저장
- SQLite를 사용할 경우 앱 시작 시 `event_journal`, `event_tag`, `snapshot` 스키마를 선생성
- HOCON에서 `jdbc-journal` / `jdbc-snapshot-store`를 같은 shared-db(`slick`)로 연결

```java
public class PersistentCounterActor extends AbstractPersistentActor {
    private int counter = 0;
    private long eventCount = 0;
    private final String persistenceId;

    @Override
    public String persistenceId() { return persistenceId; }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
            .match(CounterIncremented.class, this::applyEvent)
            .match(SnapshotOffer.class, offer -> {
                CounterState snap = (CounterState) offer.snapshot();
                counter = snap.counter(); eventCount = snap.eventCount();
            })
            .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Increment.class, cmd -> {
                persist(new CounterIncremented(cmd.amount()), e -> {
                    applyEvent(e);
                    if (lastSequenceNr() % 5 == 0) saveSnapshot(new CounterState(counter, eventCount));
                });
            })
            .build();
    }
}
```

```hocon
akka.persistence.journal.plugin = "jdbc-journal"
akka.persistence.snapshot-store.plugin = "jdbc-snapshot-store"

jdbc-journal { use-shared-db = "slick" }
jdbc-snapshot-store { use-shared-db = "slick" }

akka-persistence-jdbc.shared-databases.slick {
  profile = "slick.jdbc.SQLiteProfile$"
  db {
    url = "jdbc:sqlite:sample11-data/akka-persistence.db"
    driver = "org.sqlite.JDBC"
    connectionPool = disabled
    keepAliveConnection = on
  }
}
```

### 8. 클러스터 (Cluster)
- `ClusterListener`: MemberUp, UnreachableMember, MemberRemoved 이벤트 감시
- `ClusterRouterPool`: 멀티 노드 라우팅
- 역할(role) 기반 분산 처리
- HOCON `seed-nodes`, `auto-down` 설정

### 9. 생명주기 & 종료
- `preStart()`, `postStop()`, `preRestart()`, `postRestart()`
- `PoisonPill` vs `context().stop()` vs `Kill`
- `CoordinatedShutdown`: 단계별 graceful shutdown

### 10. 테스트 (TestKit)
- `TestKit` 익명 클래스 패턴
- `expectMsg()`, `expectMsgClass()`, `expectNoMessage()`, `within()`
- `getRef()` 로 TestKit 자체를 sender로 사용

### 11. Spring Boot 통합
- `@Configuration` + `@Bean`으로 ActorSystem 관리
- `@PostConstruct` / `@PreDestroy` 라이프사이클
- `AkkaManager` 싱글턴 패턴

### 12. Dispatcher 설정
- Dispatcher는 액터에 스레드를 할당하는 핵심 구성 요소
- CPU-bound는 `fork-join-executor`, I/O-bound는 `thread-pool-executor` 사용

```hocon
# 기본 Dispatcher - 비블로킹 액터용
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

# 블로킹 Dispatcher - I/O 작업용
my-blocking-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 16
  }
  throughput = 5
}
```

```java
// Props 생성 시 Dispatcher 지정
ActorRef actor = actorSystem.actorOf(
    HelloWorld.Props().withDispatcher("my-dispatcher"), "actor1");

// 블로킹 작업용 Dispatcher
ActorRef timerActor = actorSystem.actorOf(
    TimerActor.Props().withDispatcher("my-blocking-dispatcher"), "timer");

// Materializer에 Dispatcher 적용 (Streams)
ActorMaterializerSettings settings =
    ActorMaterializerSettings.create(actorSystem)
        .withDispatcher("my-dispatcher-streamtest");
Materializer materializer = ActorMaterializer.create(settings, actorSystem);
```

| 유형 | Executor | 용도 |
|------|----------|------|
| CPU 집약적 | `fork-join-executor` | 계산 위주, 짧은 작업 |
| I/O 블로킹 | `thread-pool-executor` (fixed-pool) | DB 조회, 외부 API 호출 |
| 스트림 처리 | `thread-pool-executor` (large pool) | Akka Streams 파이프라인 |

## 코드 생성 규칙

1. **액터 클래스는 반드시 `AbstractActor`를 상속**하고 `createReceive()`를 구현합니다.
2. **Props 팩토리 메서드**를 `public static Props Props()` 형태로 제공합니다.
3. **메시지 클래스는 불변(Immutable)**으로 설계합니다. `final` 필드 + 생성자 패턴 또는 record 사용을 권장합니다.
4. **로깅**은 `Logging.getLogger(getContext().getSystem(), this)`를 사용합니다.
5. **HOCON 설정**은 `application.conf`에 `akka { }` 블록으로 작성합니다.
6. **Dispatcher 설정**: CPU-bound는 `fork-join-executor`, I/O-bound는 `thread-pool-executor`를 사용합니다.
7. 타이머가 필요하면 `AbstractActorWithTimers`를 상속합니다.
8. 라우터는 요구사항에 맞는 전략을 선택합니다:
   - 균등 분배: `RoundRobinPool`
   - 부하 인식: `SmallestMailboxPool`
   - 브로드캐스트: `BroadcastPool`
   - 최저 지연: `TailChoppingPool`
   - 해시 기반: `ConsistentHashingPool` (메시지에 `ConsistentHashable` 구현 필요)

$ARGUMENTS
