---
name: java-akka-classic
description: Java + Akka Classic 액터 모델 코드를 생성합니다. Java로 Akka Classic 기반 액터를 구현하거나, AbstractActor 패턴, 라우팅, 타이머, 배치처리, 스트림, 클러스터 등 Akka Classic 패턴을 작성할 때 사용합니다.
argument-hint: "[패턴명] [요구사항]"
---

# Java + Akka Classic 액터 모델 스킬

Java + Akka Classic(2.7.x) 기반의 액터 모델 코드를 생성하는 스킬입니다.

## 참고 문서

- 상세 패턴 가이드: [skil-maker/docs/actor/01-java-akka-classic/README.md](../../../../skil-maker/docs/actor/01-java-akka-classic/README.md)
- 액터모델 개요: [skil-maker/docs/actor/00-actor-model-overview.md](../../../../skil-maker/docs/actor/00-actor-model-overview.md)
- 크로스 플랫폼 비교: [skil-maker/docs/actor/05-cross-platform-comparison.md](../../../../skil-maker/docs/actor/05-cross-platform-comparison.md)

## 환경

- **프레임워크**: Akka Classic 2.7.x
- **언어**: Java 11+
- **빌드**: Gradle / Maven
- **웹 프레임워크**: Spring Boot 2.7.x
- **라이선스**: BSL (Business Source License)

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

### 2. 라우팅 (Router)
- **Pool Router**: `RoundRobinPool`, `RandomPool`, `BalancingPool`, `SmallestMailboxPool`, `BroadcastPool`, `TailChoppingPool`
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
```

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

### 7. 클러스터 (Cluster)
- `ClusterListener`: MemberUp, UnreachableMember, MemberRemoved 이벤트 감시
- `ClusterRouterPool`: 멀티 노드 라우팅
- 역할(role) 기반 분산 처리
- HOCON `seed-nodes`, `auto-down` 설정

### 8. 생명주기 & 종료
- `preStart()`, `postStop()`, `preRestart()`, `postRestart()`
- `PoisonPill` vs `context().stop()` vs `Kill`
- `CoordinatedShutdown`: 단계별 graceful shutdown

### 9. 테스트 (TestKit)
- `TestKit` 익명 클래스 패턴
- `expectMsg()`, `expectMsgClass()`, `expectNoMessage()`, `within()`
- `getRef()` 로 TestKit 자체를 sender로 사용

### 10. Spring Boot 통합
- `@Configuration` + `@Bean`으로 ActorSystem 관리
- `@PostConstruct` / `@PreDestroy` 라이프사이클
- `AkkaManager` 싱글턴 패턴

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

$ARGUMENTS
