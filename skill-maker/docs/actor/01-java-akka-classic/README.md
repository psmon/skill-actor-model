# Java + Akka Classic 완전 가이드

> **프로젝트 환경**: Akka Classic 2.7.0, Scala 2.13, Spring Boot 2.7.11, Java 11
> **소스 프로젝트**: `java-labs/springweb` 모듈
> **주요 의존성**: `akka-actor`, `akka-stream`, `akka-cluster`, `akka-testkit`, `akka-cluster-metrics`

---

## 목차

1. [Akka Classic 개요](#1-akka-classic-개요)
2. [기본 액터 (Basic Actor)](#2-기본-액터-basic-actor)
3. [액터 계층과 라우팅 (Actor Hierarchy & Routing)](#3-액터-계층과-라우팅-actor-hierarchy--routing)
4. [타이머 액터 (Timer Actor)](#4-타이머-액터-timer-actor)
5. [배치 처리 (Batch Processing)](#5-배치-처리-batch-processing)
6. [Ask 패턴과 Self-Throttle](#6-ask-패턴과-self-throttle)
7. [HOCON 기반 라우팅 전략 (Routing Strategies)](#7-hocon-기반-라우팅-전략-routing-strategies)
8. [Akka Streams 기반 흐름 제어 (Throttle & Backpressure)](#8-akka-streams-기반-흐름-제어-throttle--backpressure)
9. [클러스터 (Cluster)](#9-클러스터-cluster)
10. [생명주기와 종료 (Lifecycle & Shutdown)](#10-생명주기와-종료-lifecycle--shutdown)
11. [테스트 (Testing with TestKit)](#11-테스트-testing-with-testkit)
12. [Spring Boot 통합](#12-spring-boot-통합)
13. [Dispatcher 설정](#13-dispatcher-설정)
14. [프로젝트 구성 (Build Configuration)](#14-프로젝트-구성-build-configuration)

---

## 1. Akka Classic 개요

### Actor Model이란?

Actor Model은 동시성 프로그래밍을 위한 수학적 모델로, 모든 것을 **액터(Actor)**라는 독립적인 계산 단위로 추상화합니다. 각 액터는 다음과 같은 특성을 가집니다:

- **메시지 기반 통신**: 액터 간 직접 메서드 호출 없이 비동기 메시지로만 소통합니다.
- **상태 캡슐화**: 각 액터는 자신만의 상태를 가지며, 외부에서 직접 접근할 수 없습니다.
- **단일 스레드 보장**: 한 액터는 한 번에 하나의 메시지만 처리하므로, 내부 상태에 대한 동기화가 필요 없습니다.
- **감독 계층 (Supervision Hierarchy)**: 부모 액터가 자식 액터의 실패를 관리합니다.

### Akka Classic vs Akka Typed

Akka Classic은 `AbstractActor`를 기반으로 하며, `receiveBuilder()`로 메시지 핸들러를 구성합니다. Akka Typed는 컴파일 타임 타입 안전성을 제공하지만, Classic은 더 유연하고 학습 곡선이 낮습니다. 이 문서에서는 **Classic API**를 다룹니다.

### 핵심 개념 요약

| 개념 | 설명 |
|------|------|
| `ActorSystem` | 액터를 생성하고 관리하는 최상위 컨테이너 |
| `ActorRef` | 액터에 대한 불변 참조. 메시지 전송에 사용 |
| `Props` | 액터 생성을 위한 설정 객체 (팩토리 패턴) |
| `tell()` | Fire-and-forget 방식의 비동기 메시지 전송 |
| `ask()` | 응답을 기다리는 요청-응답 패턴 |
| `forward()` | 원래 발신자 정보를 유지하며 메시지 전달 |
| `Dispatcher` | 액터의 스레드 실행을 관리하는 스케줄러 |

---

## 2. 기본 액터 (Basic Actor)

### 2.1 HelloWorld Actor

가장 기본적인 액터 구현입니다. `AbstractActor`를 상속하고 `createReceive()`에서 메시지 핸들러를 정의합니다.

```java
package com.webnori.springweb.example.akka.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.webnori.springweb.example.akka.models.FakeSlowMode;

public class HelloWorld extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private ActorRef probe;
    private boolean isBlockForTest = false;
    private Long blockTime;
    private int receivedCount = 0;

    // Props 팩토리 메서드 - 액터 생성 시 반드시 사용
    public static Props Props() {
        return Props.create(HelloWorld.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ActorRef.class, actorRef -> {
                // 테스트용 probe 액터 참조 설정
                this.probe = actorRef;
                getSender().tell("done", getSelf());
            })
            .match(FakeSlowMode.class, message -> {
                isBlockForTest = true;
                blockTime = message.bockTime;
                log.info("Switch SlowMode:{}", self().path());
            })
            .match(String.class, s -> {
                receivedCount++;
                if (isBlockForTest) Thread.sleep(blockTime);
                if (probe != null) {
                    probe.tell("world", this.context().self());
                }
            })
            .matchAny(o -> log.info("received unknown message"))
            .build();
    }
}
```

### 2.2 핵심 API 설명

#### `AbstractActor`
Akka Classic에서 액터를 구현하기 위한 기본 추상 클래스입니다. 반드시 `createReceive()` 메서드를 오버라이드해야 합니다.

#### `Props.create()`
액터 인스턴스를 생성하기 위한 설정 객체입니다. 직접 `new`로 액터를 생성하면 안 되고, 반드시 `Props`를 통해 생성해야 합니다. 팩토리 메서드 패턴으로 `Props()` 정적 메서드를 제공하는 것이 관례입니다.

```java
// 액터 생성
ActorRef greetActor = actorSystem.actorOf(HelloWorld.Props(), "HelloWorld");
```

#### `receiveBuilder().match()`
메시지 타입별 핸들러를 등록합니다. 메시지가 도착하면 위에서 아래 순서로 매칭을 시도합니다.

```java
receiveBuilder()
    .match(String.class, s -> { /* String 타입 처리 */ })
    .match(Integer.class, n -> { /* Integer 타입 처리 */ })
    .matchAny(o -> { /* 매칭되지 않는 모든 메시지 처리 */ })
    .build();
```

#### `match()`에 가드 조건 추가
`match()`의 두 번째 인자로 조건(predicate)을 전달하여 같은 타입 내에서 분기할 수 있습니다:

```java
.match(String.class, s -> s.equals("CMD_CREATE"), s -> {
    // "CMD_CREATE" 문자열일 때만 실행
})
.match(String.class, s -> s.equals("CMD_WORK"), s -> {
    // "CMD_WORK" 문자열일 때만 실행
})
```

#### `tell()` - Fire-and-Forget 패턴
비동기적으로 메시지를 보내고 응답을 기다리지 않습니다:

```java
// 발신자 정보 포함
greetActor.tell("hello", getRef());

// 발신자 없이 전송 (응답 불필요 시)
greetActor.tell("hello", ActorRef.noSender());
```

#### `getSender().tell()` - 응답 전송
메시지를 보낸 액터에게 응답을 보냅니다:

```java
getSender().tell("world", getSelf());
```

> **주의사항**: `getSender()`는 현재 처리 중인 메시지의 발신자를 반환합니다. 비동기 콜백(예: `CompletableFuture.thenApply()`) 내에서는 `getSender()`의 값이 변경될 수 있으므로, 사용 전에 반드시 로컬 변수에 저장해 두어야 합니다.

### 2.3 GreetingActor - Dispatcher 연동

```java
public class GreetingActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    // 기본 Props
    public static Props Props() {
        return Props.create(GreetingActor.class);
    }

    // 커스텀 Dispatcher를 지정한 Props
    public static Props Props(String dispatcher) {
        return Props.create(GreetingActor.class).withDispatcher(dispatcher);
    }

    private boolean isSlowMode = false;
    private Long blockTime;
    private ActorRef probe;
    private Random rand = new Random();

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ActorRef.class, actorRef -> {
                this.probe = actorRef;
                getSender().tell("done", getSelf());
            })
            .match(FakeSlowMode.class, message -> {
                isSlowMode = true;
                blockTime = message.bockTime;
                log.info("Switch SlowMode:{}", self().path());
            })
            .match(String.class, s -> {
                int sleepSec = 0;
                if (isSlowMode) {
                    sleepSec = rand.nextInt(500);
                    Thread.sleep(blockTime + sleepSec);
                }
            })
            .matchAny(o -> log.info("received unknown message"))
            .build();
    }
}
```

**`withDispatcher()`**: 액터가 사용할 Dispatcher를 지정합니다. Blocking I/O가 필요한 액터는 전용 Dispatcher를 분리하여 다른 액터에 영향을 주지 않도록 해야 합니다.

---

## 3. 액터 계층과 라우팅 (Actor Hierarchy & Routing)

### 3.1 개념

Akka에서 모든 액터는 트리 구조의 계층을 형성합니다. 부모 액터가 자식 액터를 생성하고, 자식 액터의 생명주기를 관리합니다. 이 구조는 **Supervision Strategy**(감독 전략)의 기반이 됩니다.

```
            ActorSystem (root guardian)
                  |
            user guardian (/user)
                  |
           ParentActor (/user/parent)
           /      |      \
        w1       w2       w3     (ChildActor)
```

### 3.2 ParentActor - 자식 생성과 라우터

```java
package com.webnori.springweb.example.akka.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.RoundRobinGroup;
import java.util.Arrays;
import java.util.List;

public class ParentActor extends AbstractActor {

    // 이벤트 정의 - 실무에서는 Class(Object)로 관리하는 것이 권장됩니다
    public static String CMD_CREATE_CHILDS = "CMD_CREATE_CHILDS";
    public static String CMD_SOME_WORK = "CMD_SOME_WORK";
    public static String CMD_MESSAGE_REPLY = "CMD_MESSAGE_REPLY";
    public static String CMD_SOME_WORK_COMPLETED = "CMD_SOME_WORK_COMPLETED";

    private boolean firstInit;
    private ActorRef routerActor;
    private ActorRef testProbeActor;

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props Props() {
        return Props.create(ParentActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, s -> s.equals(CMD_CREATE_CHILDS), s -> {
                if (!firstInit) {
                    // 자식 액터 생성 - context().actorOf()로 부모-자식 관계 형성
                    var w1 = context().actorOf(ChildActor.Props(), "w1");
                    var w2 = context().actorOf(ChildActor.Props(), "w2");
                    var w3 = context().actorOf(ChildActor.Props(), "w3");

                    // 자식 액터들의 경로를 기반으로 RoundRobinGroup 라우터 생성
                    List<String> paths = Arrays.asList(
                        "/user/" + self().path().name() + "/" + w1.path().name(),
                        "/user/" + self().path().name() + "/" + w2.path().name(),
                        "/user/" + self().path().name() + "/" + w3.path().name());

                    routerActor = getContext().actorOf(
                        new RoundRobinGroup(paths).props(), "router16");
                }
                firstInit = true;
            })
            .match(String.class, s -> s.equals(CMD_SOME_WORK), s -> {
                // 라우터를 통해 자식에게 작업 분배
                routerActor.tell(CMD_SOME_WORK, ActorRef.noSender());
            })
            .match(String.class, s -> s.equals(CMD_SOME_WORK_COMPLETED), s -> {
                // 자식으로부터 작업 완료 통지 수신
                log.info("CMD_SOME_WORK_COMPLETED");
                if (testProbeActor != null) {
                    testProbeActor.tell(CMD_SOME_WORK_COMPLETED, ActorRef.noSender());
                }
            })
            .match(String.class, s -> s.equals(CMD_MESSAGE_REPLY), s -> {
                // forward: 원래 발신자 정보를 유지하며 메시지 전달
                sender().forward(CMD_MESSAGE_REPLY, getContext());
            })
            .match(ActorRef.class, actorRef -> {
                testProbeActor = actorRef;
            })
            .matchAny(o -> log.warning("received unknown message"))
            .build();
    }
}
```

### 3.3 ChildActor - 부모에게 완료 통보

```java
package com.webnori.springweb.example.akka.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class ChildActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props Props() {
        return Props.create(ChildActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, s -> {
                log.info("[{}] ChildActor InMessage : {}", self().path().name(), s);
                // context().parent()로 부모 액터에게 완료 통지
                context().parent().tell(
                    ParentActor.CMD_SOME_WORK_COMPLETED, ActorRef.noSender());
            })
            .matchAny(o -> log.warning("received unknown message"))
            .build();
    }
}
```

### 3.4 핵심 패턴 비교: tell vs forward

| 패턴 | 코드 | 발신자 정보 |
|------|------|------------|
| `tell` | `target.tell(msg, getSelf())` | 현재 액터가 발신자 |
| `forward` | `target.forward(msg, getContext())` | **원래 발신자** 유지 |

`forward`는 중간 액터(프록시, 라우터)를 거치더라도 최초 발신자가 응답을 받을 수 있게 합니다.

### 3.5 계층 액터 테스트

```java
public class HierarchyActorTest {
    @Test
    @DisplayName("Round Robbin Test")
    public void EventFlowParentTOChildTest() {
        new TestKit(actorSystem) {{
            probe = new TestKit(actorSystem);

            // 부모 액터 생성
            parentActor = actorSystem.actorOf(ParentActor.Props());

            // 자식 생성 커맨드
            parentActor.tell(ParentActor.CMD_CREATE_CHILDS, ActorRef.noSender());

            // 테스트 관찰자 설정
            parentActor.tell(probe.getRef(), getRef());

            // RoundRobin으로 자식에게 자동 분배
            parentActor.tell(ParentActor.CMD_SOME_WORK, ActorRef.noSender());
            parentActor.tell(ParentActor.CMD_SOME_WORK, ActorRef.noSender());
            parentActor.tell(ParentActor.CMD_SOME_WORK, ActorRef.noSender());

            // 각 자식의 작업 완료 검증
            probe.expectMsg(ParentActor.CMD_SOME_WORK_COMPLETED);
            probe.expectMsg(ParentActor.CMD_SOME_WORK_COMPLETED);
            probe.expectMsg(ParentActor.CMD_SOME_WORK_COMPLETED);
        }};
    }

    @Test
    @DisplayName("EventForwardTest")
    public void EventForwardTest() {
        new TestKit(actorSystem) {{
            TestKit forwardActor = new TestKit(actorSystem);
            // forwardActor.getRef()를 sender로 지정하여 전송
            parentActor.tell(ParentActor.CMD_MESSAGE_REPLY, forwardActor.getRef());
            // forward를 통해 메시지를 돌려받는지 확인
            forwardActor.expectMsg(ParentActor.CMD_MESSAGE_REPLY);
        }};
    }
}
```

> **실행 로그 예시** - 작업이 w1, w2, w3에 균등 분배됩니다:
> ```
> [w2] ChildActor InMessage : CMD_SOME_WORK
> [w1] ChildActor InMessage : CMD_SOME_WORK
> [w3] ChildActor InMessage : CMD_SOME_WORK
> ```

---

## 4. 타이머 액터 (Timer Actor)

### 4.1 개념

`AbstractActorWithTimers`를 상속하면 액터 내부에서 안전하게 타이머(스케줄러)를 사용할 수 있습니다. 외부의 `system.scheduler()`를 사용하는 것보다 액터 생명주기와 연동되어 안전합니다.

### 4.2 타이머 종류

| 메서드 | 설명 |
|--------|------|
| `startSingleTimer(key, msg, delay)` | 한 번만 실행되는 타이머 |
| `startPeriodicTimer(key, msg, interval)` | `interval` 간격으로 반복 실행 (이전 완료 무관) |
| `startTimerAtFixedRate(key, msg, interval)` | 고정 비율로 반복 실행 |

**타이머 키(Key)**: 같은 키로 새 타이머를 시작하면 기존 타이머가 자동 취소됩니다. 중복 실행을 방지하는 핵심 메커니즘입니다.

### 4.3 TestTimerActor - Single + Periodic 전환

```java
package com.webnori.springweb.example.akka.actors;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.time.Duration;

public class TestTimerActor extends AbstractActorWithTimers {

    private static final Object TICK_KEY = "TickKey";
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public TestTimerActor() {
        // 500ms 후 1회 실행되는 SingleTimer 시작
        getTimers().startSingleTimer(TICK_KEY, new FirstTick(), Duration.ofMillis(500));
    }

    public static Props Props() {
        return Props.create(TestTimerActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, message -> {
                log.info(message);
            })
            .match(FirstTick.class, message -> {
                log.info("First Tick");
                // SingleTimer 완료 후 -> PeriodicTimer로 전환
                // 같은 TICK_KEY를 사용하므로 SingleTimer는 자동 교체됨
                getTimers().startPeriodicTimer(TICK_KEY, new Tick(), Duration.ofSeconds(1));
            })
            .match(Tick.class, message -> {
                log.info("Tick");
            })
            .build();
    }

    // 타이머 메시지 클래스 - private static final로 정의
    private static final class FirstTick {}
    private static final class Tick {}
}
```

### 4.4 TimerActor - 자식 액터와 결합

```java
public class TimerActor extends AbstractActorWithTimers {

    private static final Object TICK_KEY = "TickKey";
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final ActorRef helloActor;

    public TimerActor() {
        // 생성자에서 자식 액터 생성
        helloActor = context().actorOf(HelloWorld.Props(), "helloActor");
    }

    public static Props Props() {
        return Props.create(TimerActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(FirstTick.class, message -> {
                log.info("First Tick");
                // 주기적 타이머 시작
                getTimers().startPeriodicTimer(TICK_KEY, new Tick(), Duration.ofSeconds(1));
            })
            .match(Tick.class, message -> {
                log.info("Tick");
                // 매 Tick마다 자식 액터에게 메시지 전송
                helloActor.tell("Hello~", self());
            })
            .build();
    }

    private static final class FirstTick {}
    private static final class Tick {}
}
```

### 4.5 TpsMeasurementActor - 감쇠(Decay) 타이머를 활용한 TPS 측정

실시간 TPS(Transactions Per Second)를 측정하는 고급 타이머 활용 예시입니다. 여러 개의 타이머 키를 사용하여 측정-감쇠-초기화 단계를 구현합니다.

```java
package com.webnori.springweb.akka.stream.actor;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.webnori.springweb.akka.stream.actor.model.TPSInfo;
import java.time.Duration;

public class TpsMeasurementActor extends AbstractActorWithTimers {

    // 서로 다른 타이머 키로 독립적인 타이머 관리
    private static final Object TICK_TPS_KEY = "TickKey";
    private static final Object TICK_TPSRESET_KEY = "TickKey2";
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    protected long transactionCount = 0;
    private ActorRef probe;
    protected double tps;
    protected double lastTps;

    public TpsMeasurementActor() {
        getTimers().startSingleTimer(TICK_TPS_KEY, new FirstTick(), Duration.ofMillis(500));
    }

    public static Props Props() {
        return Props.create(TpsMeasurementActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ActorRef.class, actorRef -> {
                this.probe = actorRef;
                getSender().tell("done", getSelf());
            })
            .match(FirstTick.class, message -> {
                log.info("First Tick");
                // 1초마다 TPS 체크
                getTimers().startPeriodicTimer(
                    TICK_TPS_KEY, new TPSCheckTick(), Duration.ofMillis(1000));
            })
            .match(String.class, message -> {
                if (message.equals("tps")) {
                    // Ask 패턴으로 현재 TPS 값 반환
                    getSender().tell(new TPSInfo(lastTps), ActorRef.noSender());
                } else {
                    transactionCount++;
                }
            })
            .match(TPSCheckTick.class, message -> {
                long startTime = System.currentTimeMillis() - 1000;
                long endTime = System.currentTimeMillis();
                tps = transactionCount / ((endTime - startTime) / 1000);
                if (tps > 0) {
                    lastTps = tps;
                    // 500ms 후 절반으로 감쇠
                    getTimers().startPeriodicTimer(
                        TICK_TPSRESET_KEY, new TPSResetHalfTick(), Duration.ofMillis(500));
                }
                transactionCount = 0;
                log.info("TPS:" + lastTps);
            })
            .match(TPSResetHalfTick.class, message -> {
                lastTps = lastTps / 2;
                // 추가 500ms 후 완전 초기화
                getTimers().startPeriodicTimer(
                    TICK_TPSRESET_KEY, new TPSResetTick(), Duration.ofMillis(500));
            })
            .match(TPSResetTick.class, message -> {
                lastTps = 0;
            })
            .build();
    }

    private static final class FirstTick {}
    private static final class TPSCheckTick {}
    private static final class TPSResetTick {}
    private static final class TPSResetHalfTick {}
}
```

**감쇠 로직 흐름**:
```
이벤트 수신 -> transactionCount++
  |
  v
[매 1초] TPSCheckTick -> tps 계산 -> lastTps 갱신 -> transactionCount 리셋
  |
  v
[500ms 후] TPSResetHalfTick -> lastTps = lastTps / 2
  |
  v
[추가 500ms 후] TPSResetTick -> lastTps = 0
```

> **설계 포인트**: 여러 타이머 키(`TICK_TPS_KEY`, `TICK_TPSRESET_KEY`)를 사용하면 서로 독립적으로 동작하는 타이머를 관리할 수 있습니다. 같은 키를 재사용하면 이전 타이머가 교체됩니다.

---

## 5. 배치 처리 (Batch Processing)

### 5.1 개념

`SafeBatchActor`는 타이머와 버퍼를 결합하여 **마이크로 배치(Micro-Batching)** 패턴을 구현합니다. 메시지를 개별 처리하지 않고 일정 시간 동안 모아서 한꺼번에 처리합니다.

이 패턴은 다음과 같은 상황에 유용합니다:
- 데이터베이스 벌크 INSERT
- 외부 API 배치 호출
- 로그 집계 및 플러시

### 5.2 SafeBatchActor

```java
package com.webnori.springweb.example.akka.actors;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.time.Duration;
import java.util.ArrayList;

public class SafeBatchActor extends AbstractActorWithTimers {

    private static final Object TICK_KEY = "TickKey";
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final ArrayList<String> batchList;

    public SafeBatchActor() {
        batchList = new ArrayList();
        // 즉시 시작하는 SingleTimer (0ms 딜레이)
        getTimers().startSingleTimer(TICK_KEY, new FirstTick(), Duration.ofMillis(0));
    }

    public static Props Props() {
        return Props.create(SafeBatchActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, message -> {
                // 메시지를 버퍼에 축적
                batchList.add(message);
            })
            .match(FirstTick.class, message -> {
                log.info("First Tick");
                // 1초마다 Flush하는 PeriodicTimer 시작
                getTimers().startPeriodicTimer(
                    TICK_KEY, new Tick(), Duration.ofSeconds(1));
            })
            .match(Tick.class, message -> {
                log.info("Tick");
                // 배치 플러시 - 모인 데이터를 한꺼번에 처리
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

### 5.3 SafeBatchActor 테스트 - Throttle과 결합

```java
@Test
@DisplayName("Actor - SafeBatchActor Test")
public void SafeBatchActor() {
    new TestKit(actorSystem) {{
        final Materializer materializer = ActorMaterializer.create(actorSystem);

        ActorRef batchActor = actorSystem.actorOf(SafeBatchActor.Props(), "batchActor");

        int processCountPerSec = 100;
        // Throttle을 앞에 배치하여 유입량 제어
        final ActorRef throttler =
            Source.actorRef(1000, OverflowStrategy.dropNew())
                .throttle(processCountPerSec,
                    FiniteDuration.create(1, TimeUnit.SECONDS),
                    processCountPerSec, ThrottleMode.shaping())
                .to(Sink.actorRef(batchActor, akka.NotUsed.getInstance()))
                .run(materializer);

        for (int i = 0; i < 10000; i++) {
            throttler.tell("#### Hello World!", ActorRef.noSender());
        }

        expectNoMessage(Duration.ofSeconds(10));
    }};
}
```

**동작 흐름**:
```
Producer (10000건) --> [Throttle: 100/sec] --> SafeBatchActor
                                                  |
                                          매 1초마다 Flush
                                          (약 100건씩 배치 처리)
```

> **실무 팁**: 타이머 기반 Flush 외에도 `batchList.size() >= threshold` 조건으로 즉시 Flush하는 로직을 추가하면 더 효율적인 배치 처리가 가능합니다.

---

## 6. Ask 패턴과 Self-Throttle

### 6.1 Ask 패턴 개념

`tell()`이 Fire-and-Forget인 반면, `ask()`는 액터에게 메시지를 보내고 **Future로 응답을 받는** 요청-응답 패턴입니다. 응답을 기다려야 하는 경우에 사용합니다.

```java
// ask 패턴 기본 사용법
Timeout timeout = Timeout.create(Duration.ofSeconds(1));
Future<Object> future = Patterns.ask(targetActor, "tps", timeout);
TPSInfo result = (TPSInfo) Await.result(future, timeout.duration());
```

> **주의**: `Await.result()`는 현재 스레드를 블로킹합니다. 액터 내부에서 사용할 때는 성능 영향을 고려해야 합니다. 가능하면 `pipe` 패턴을 사용하세요.

### 6.2 SlowConsumerActor - Ask + Self-Throttle

TPS를 자체 측정하고, 임계값을 초과하면 스스로 속도를 늦추는 자기 조절 액터입니다.

```java
package com.webnori.springweb.akka.stream.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.webnori.springweb.akka.stream.actor.model.TPSInfo;
import scala.concurrent.Await;
import scala.concurrent.Future;
import java.time.Duration;
import static java.lang.Thread.sleep;

public class SlowConsumerActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private ActorRef probe;
    private ActorRef tpsActor;
    private long totalProcessCount = 0;
    private int tpsLimit = 400;  // 이 값 이상이면 자체 감속

    public static Props Props() {
        return Props.create(SlowConsumerActor.class);
    }

    @Override
    public Receive createReceive() {
        // 자식으로 TPS 측정 액터 생성
        tpsActor = context().actorOf(TpsMeasurementActor.Props(), "tpsActor");
        tpsActor.tell(self(), ActorRef.noSender());

        return receiveBuilder()
            .match(ActorRef.class, actorRef -> {
                this.probe = actorRef;
                getSender().tell("done", getSelf());
            })
            .match(String.class, s -> {
                // TPS 측정 이벤트 기록
                tpsActor.tell("SomeEvent", ActorRef.noSender());
                long sleepValue = 0;

                // Ask 패턴으로 현재 TPS 조회
                Timeout timeout = Timeout.create(Duration.ofSeconds(1));
                Future<Object> future = Patterns.ask(tpsActor, "tps", timeout);
                TPSInfo result = (TPSInfo) Await.result(future, timeout.duration());

                // Self-Throttle: TPS 초과 시 자체 감속
                if (result.tps > tpsLimit) {
                    sleepValue = (long) result.tps;
                    sleep(sleepValue);
                    log.info("World Slow - Total:{} Sleep:{}",
                        totalProcessCount, sleepValue);
                }

                totalProcessCount++;
                probe.tell("world", ActorRef.noSender());
            })
            .matchAny(o -> log.info("received unknown message"))
            .build();
    }
}
```

### 6.3 TPSInfo 메시지 모델

```java
package com.webnori.springweb.akka.stream.actor.model;

import java.util.Objects;

public class TPSInfo {
    public double tps;

    public TPSInfo(double tps) {
        this.tps = tps;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TPSInfo tpsInfo = (TPSInfo) obj;
        return tps == tpsInfo.tps;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tps);
    }
}
```

### 6.4 SlowConsumerActor 테스트

```java
@Test
@DisplayName("SlowConsumerActorTest")
public void SlowConsumerActorTest() {
    new TestKit(actorSystem) {{
        final Materializer materializer = ActorMaterializer.create(actorSystem);
        final TestKit probe = new TestKit(actorSystem);
        final ActorRef slowConsumerActor =
            actorSystem.actorOf(SlowConsumerActor.Props(), "SlowConsumerActor");
        slowConsumerActor.tell(probe.getRef(), getRef());
        expectMsg(Duration.ofSeconds(1), "done");

        int testCount = 50000;
        int bufferSize = 100000;
        int processCountPerSec = 200;

        // 외부 Throttle로 유입량 제한
        final ActorRef throttler =
            Source.actorRef(bufferSize, OverflowStrategy.dropNew())
                .throttle(processCountPerSec,
                    FiniteDuration.create(1, TimeUnit.SECONDS),
                    processCountPerSec, ThrottleMode.shaping())
                .to(Sink.actorRef(slowConsumerActor, akka.NotUsed.getInstance()))
                .run(materializer);

        within(Duration.ofSeconds(10), () -> {
            for (int i = 0; i < testCount; i++) {
                throttler.tell("hello", probe.getRef());
            }
            for (int i = 0; i < testCount; i++) {
                probe.expectMsg(Duration.ofSeconds(3), "world");
            }
            expectNoMessage();
            return null;
        });
    }};
}
```

> **설계 포인트**: 외부 Throttle(Akka Streams)과 내부 Self-Throttle(Ask 패턴)을 이중으로 사용하여 안정적인 처리량 제어를 구현합니다.

---

## 7. HOCON 기반 라우팅 전략 (Routing Strategies)

### 7.1 개념

Akka는 HOCON 설정 파일을 통해 코드 변경 없이 라우팅 전략을 변경할 수 있습니다. `FromConfig.getInstance()`를 사용하면 설정 파일에 정의된 라우터를 자동으로 생성합니다.

### 7.2 라우터 설정 (router-test.conf)

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
    pool-dispatcher {
      attempt-teamwork = off
    }
  }

  /router4 {
    router = balancing-pool
    nr-of-instances = 5
    pool-dispatcher {
      executor = "thread-pool-executor"
      thread-pool-executor {
        core-pool-size-min = 5
        core-pool-size-max = 5
      }
    }
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

### 7.3 라우팅 전략 비교

| 전략 | 설명 | 사용 시나리오 |
|------|------|-------------|
| `round-robin-pool` | 순서대로 균등 분배 | 일반적인 작업 분배 |
| `random-pool` | 무작위 분배 | 부하가 균일한 작업 |
| `balancing-pool` | 공유 메일박스에서 가져감 | 처리 시간이 불균일한 작업 |
| `smallest-mailbox-pool` | 메일박스가 가장 적은 routee에 분배 | 작업량 편중 방지 |
| `broadcast-pool` | 모든 routee에 동시 전달 | 전체 통지, 캐시 갱신 |
| `tail-chopping-pool` | 순차 전송 후 첫 응답 사용 | 지연 시간 최소화 |

### 7.4 WorkerActor - 라우터 테스트용 액터

```java
package com.webnori.springweb.akka.router.routing.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class WorkerActor extends AbstractActor {

    private ActorRef _probe;
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private int messageCount = 0;

    // Props에 생성자 파라미터 전달
    public static Props Props(ActorRef _probe) {
        return Props.create(WorkerActor.class, _probe);
    }

    public WorkerActor(ActorRef probe) {
        _probe = probe;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(WorkMessage.class, message -> {
                String pathName = self().path().name();
                messageCount++;
                log.info("[{}] ChildActor InMessage : {} - {}",
                    pathName, message, messageCount);
                // 특정 routee에 인위적 지연 (부하 불균형 시뮬레이션)
                if (pathName.equals("$a")) {
                    log.info("SomeBlocking - 300ms");
                    Thread.sleep(300);
                }
                _probe.tell("completed", ActorRef.noSender());
            })
            .build();
    }
}
```

### 7.5 WorkMessage - 직렬화 가능한 메시지

```java
package com.webnori.springweb.akka.router.routing.actor;

import com.webnori.springweb.example.akka.actors.cluster.MySerializable;

public final class WorkMessage implements MySerializable {
    private static final long serialVersionUID = 1L;
    public final String payload;

    public WorkMessage(String payload) {
        this.payload = payload;
    }
}
```

### 7.6 BasicRoutingTest - FromConfig 기반 테스트

```java
package com.webnori.springweb.akka.router.routing;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.routing.FromConfig;
import akka.testkit.javadsl.TestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class BasicRoutingTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setup() {
        // router-test.conf 설정 로드
        actorSystem = serverStart("ClusterSystem", "router-test", "seed");
    }

    // 라우터 이름으로 테스트 - HOCON 설정과 매핑
    public void RouterByNameTest(String routerName) {
        new TestKit(actorSystem) {{
            int testCount = 50;
            final TestKit probe = new TestKit(actorSystem);

            // FromConfig: HOCON 설정에서 라우터 전략을 자동 로드
            ActorRef router = actorSystem.actorOf(
                FromConfig.getInstance().props(WorkerActor.Props(probe.getRef())),
                routerName);

            within(Duration.ofSeconds(30), () -> {
                for (int i = 0; i < testCount; i++) {
                    router.tell(new WorkMessage("message-" + i), ActorRef.noSender());
                }

                awaitCond(probe::msgAvailable);

                for (int i = 0; i < testCount; i++) {
                    probe.expectMsg("completed");
                }
                probe.expectNoMessage();
                return null;
            });
        }};
    }

    @Test
    public void RoundRobinRoutingTest()    { RouterByNameTest("router1"); }

    @Test
    public void RandomRoutingTest()        { RouterByNameTest("router2"); }

    @Test
    public void BalancingRoutingTest()     { RouterByNameTest("router3"); }

    @Test
    public void BalancingRoutingWithThreadPoolTest() { RouterByNameTest("router4"); }

    @Test
    public void SmallestMailBoxRoutingTest() { RouterByNameTest("router5"); }
}
```

> **핵심**: `FromConfig.getInstance().props(workerProps)`만으로 HOCON에 정의된 모든 라우팅 설정(전략, 인스턴스 수, Dispatcher 등)이 자동 적용됩니다. 코드 변경 없이 운영 환경에서 설정만 바꿔 라우팅 전략을 튜닝할 수 있습니다.

### 7.7 Pool vs Group 라우터

| 유형 | 생성 방식 | 사용 시나리오 |
|------|-----------|-------------|
| **Pool** | 라우터가 routee를 직접 생성 | 단일 JVM 내 라우팅 |
| **Group** | 이미 존재하는 액터 경로를 지정 | 클러스터 환경, 기존 액터 활용 |

```java
// Pool - 라우터가 5개의 routee를 직접 생성
ActorRef poolRouter = actorSystem.actorOf(
    new RoundRobinPool(5).props(HelloWorld.Props()), "poolRouter");

// Group - 이미 존재하는 액터들의 경로를 지정
List<String> paths = Arrays.asList("/user/w1", "/user/w2", "/user/w3");
ActorRef groupRouter = actorSystem.actorOf(
    new RoundRobinGroup(paths).props(), "groupRouter");
```

---

## 8. Akka Streams 기반 흐름 제어 (Throttle & Backpressure)

### 8.1 개념

Akka Streams는 Reactive Streams 표준을 구현한 스트림 처리 프레임워크입니다. **Backpressure**(배압)를 통해 Producer가 Consumer의 처리 속도를 초과하지 않도록 자동으로 제어합니다.

핵심 구성 요소:
- **Source**: 데이터 생산자 (입력)
- **Flow**: 데이터 변환 단계 (처리)
- **Sink**: 데이터 소비자 (출력)

```
Source --> Flow(변환) --> Flow(throttle) --> Sink
```

### 8.2 Throttle 패턴 - 속도 제한

Akka Streams의 `throttle()` 연산자를 사용하여 액터 앞단에 속도 제한기를 배치합니다.

```java
@Test
@DisplayName("Actor - HelloWorld Test")
public void TestItManyThrottle() {
    new TestKit(actorSystem) {{
        final Materializer materializer = ActorMaterializer.create(actorSystem);
        final TestKit probe = new TestKit(actorSystem);

        int poolCount = 100;
        final ActorRef greetActor = actorSystem.actorOf(
            new RoundRobinPool(poolCount).props(
                HelloWorld.Props().withDispatcher("my-dispatcher-test1")),
            "router2");

        // probe 설정
        for (int i = 0; i < poolCount; i++) {
            greetActor.tell(probe.getRef(), getRef());
            expectMsg(Duration.ofSeconds(1), "done");
        }

        int processCountPerSec = 3;

        // Throttle 스트림 생성 - 초당 3개만 통과
        final ActorRef throttler1 =
            Source.actorRef(1000, OverflowStrategy.dropNew())
                .throttle(processCountPerSec,
                    FiniteDuration.create(1, TimeUnit.SECONDS),
                    processCountPerSec, ThrottleMode.shaping())
                .to(Sink.actorRef(greetActor, akka.NotUsed.getInstance()))
                .run(materializer);

        // 동일한 구조의 추가 throttler (다중 채널)
        final ActorRef throttler2 =
            Source.actorRef(1000, OverflowStrategy.dropNew())
                .throttle(processCountPerSec,
                    FiniteDuration.create(1, TimeUnit.SECONDS),
                    processCountPerSec, ThrottleMode.shaping())
                .to(Sink.actorRef(greetActor, akka.NotUsed.getInstance()))
                .run(materializer);

        within(Duration.ofSeconds(100), () -> {
            int testCount = 50;
            for (int i = 0; i < testCount; i++) {
                throttler1.tell("hello1", getRef());
                throttler2.tell("hello2", getRef());
            }
            for (int i = 0; i < testCount * 2; i++) {
                probe.expectMsg(Duration.ofSeconds(100), "world");
            }
            expectNoMessage();
            return null;
        });
    }};
}
```

**Throttle 스트림 구조**:
```
Source.actorRef(bufferSize, OverflowStrategy)
    .throttle(elements, per, maximumBurst, ThrottleMode)
    .to(Sink.actorRef(targetActor, onCompleteMessage))
    .run(materializer)
```

| 파라미터 | 설명 |
|----------|------|
| `bufferSize` | 내부 버퍼 크기 |
| `OverflowStrategy.dropNew()` | 버퍼 초과 시 새 메시지 드롭 |
| `throttle(3, 1.second)` | 1초당 최대 3개 통과 |
| `ThrottleMode.shaping()` | 메시지를 지연시켜 균일하게 전달 |

### 8.3 RoundRobin + Throttle 결합 패턴

```java
@Test
@DisplayName("Actor - RoundRobinThrottleTest Test")
public void RoundRobinThrottleTest() {
    new TestKit(actorSystem) {{
        int concurrencyCount = 10;      // 동시 처리 능력
        int processCountPerSec = 3;     // 초당 처리 밸브
        int maxBufferSize = 3000;       // 최대 버퍼 수 (넘을 시 Drop)
        int testCallCount = 100;

        final Materializer materializer = ActorMaterializer.create(actorSystem);
        List<String> paths = new ArrayList<>();

        // 병렬 워커 액터 생성
        for (int i = 0; i < concurrencyCount; i++) {
            String pathName = "w" + (i + 1);
            ActorRef work = actorSystem.actorOf(
                GreetingActor.Props("my-dispatcher-test1"), pathName);
            work.tell(new FakeSlowMode(), ActorRef.noSender());
            paths.add("/user/" + pathName);
        }

        // RoundRobinGroup 라우터
        ActorRef router = actorSystem.actorOf(
            new RoundRobinGroup(paths).props(), "router");

        // Throttle -> Router -> Workers
        final ActorRef throttler =
            Source.actorRef(maxBufferSize, OverflowStrategy.dropNew())
                .throttle(processCountPerSec,
                    FiniteDuration.create(1, TimeUnit.SECONDS),
                    processCountPerSec, ThrottleMode.shaping())
                .to(Sink.actorRef(router, akka.NotUsed.getInstance()))
                .run(materializer);

        for (int i = 0; i < testCallCount; i++) {
            throttler.tell("Hello World!" + i, ActorRef.noSender());
        }

        int expectedCompleteTime = testCallCount / 3;
        expectNoMessage(Duration.ofSeconds(expectedCompleteTime));
    }};
}
```

**아키텍처 다이어그램**:
```
Producer (100건)
    |
    v
[Throttle: 3건/sec]
    |
    v
[RoundRobinGroup Router]
   /    |    \    ...    \
  w1   w2   w3  ...    w10  (각각 전용 Dispatcher)
```

### 8.4 Backpressure 파이프라인

완전한 Source -> Flow -> Sink 파이프라인으로, 비동기 API 호출에 backpressure를 적용합니다.

```java
@Test
@DisplayName("BackPressureTest")
public void BackPressureTest() {
    new TestKit(actorSystem) {{
        final ActorMaterializerSettings settings =
            ActorMaterializerSettings.create(actorSystem)
                .withDispatcher("my-dispatcher-streamtest");
        final Materializer materializer =
            ActorMaterializer.create(settings, actorSystem);

        // Source 생성 - 1부터 4000까지의 정수
        Source<Integer, NotUsed> source = Source.range(1, 4000);

        // 병렬 비동기 처리 Flow (mapAsync)
        final int parallelism = 450;
        Flow<Integer, String, NotUsed> parallelFlow =
            Flow.<Integer>create()
                .mapAsync(parallelism, BackPressureTest::callApiAsync);

        // Buffer + Backpressure 전략
        int bufferSize = 1000;
        Flow<Integer, Integer, NotUsed> backpressureFlow =
            Flow.<Integer>create()
                .buffer(bufferSize, OverflowStrategy.backpressure());

        AtomicInteger processedCount = new AtomicInteger();

        // Sink 정의
        Sink<String, CompletionStage<Done>> sink = Sink.foreach(s -> {
            processedCount.getAndIncrement();
        });

        // 파이프라인 연결 및 실행
        source.via(backpressureFlow)
              .via(parallelFlow)
              .to(sink)
              .run(materializer);

        within(Duration.ofSeconds(15), () -> {
            expectNoMessage(Duration.ofSeconds(10));
            return null;
        });
    }};
}
```

**비동기 API 호출 시뮬레이션**:
```java
private static CompletionStage<String> callApiAsync(Integer param) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            double dValue = Math.random();
            int iValue = (int) (dValue * 1000);
            Thread.sleep(iValue); // API 응답 시간 시뮬레이션
            tpsActor.tell("CompletedEvent", ActorRef.noSender());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Response for " + param;
    }, executor);
}
```

### 8.5 Throttle + Backpressure 결합

```java
public void ThrottleTest(int tps, int testCount) {
    new TestKit(actorSystem) {{
        final Materializer materializer = ActorMaterializer.create(
            ActorMaterializerSettings.create(actorSystem)
                .withDispatcher("my-dispatcher-streamtest"),
            actorSystem);

        Source<Integer, NotUsed> source = Source.range(1, testCount);

        final int parallelism = 450;
        Flow<Integer, String, NotUsed> parallelFlow =
            Flow.<Integer>create()
                .mapAsync(parallelism, BackPressureTest::callApiAsync);

        int bufferSize = 100000;
        Flow<Integer, Integer, NotUsed> backpressureFlow =
            Flow.<Integer>create()
                .buffer(bufferSize, OverflowStrategy.backpressure());

        Sink<String, CompletionStage<Done>> sink = Sink.foreach(s -> {
            // 처리 완료
        });

        // backpressure + throttle + parallel 결합
        source.via(backpressureFlow)
              .throttle(tps,
                  FiniteDuration.create(1, TimeUnit.SECONDS),
                  tps, ThrottleMode.shaping())
              .via(parallelFlow)
              .to(sink)
              .run(materializer);

        within(Duration.ofSeconds(15), () -> {
            expectNoMessage(Duration.ofSeconds(10));
            return null;
        });
    }};
}
```

### 8.6 OverflowStrategy 비교

| 전략 | 설명 |
|------|------|
| `backpressure()` | 버퍼가 가득 차면 upstream에 신호를 보내 생산 중단 |
| `dropNew()` | 버퍼가 가득 차면 새로운 요소를 버림 |
| `dropHead()` | 버퍼가 가득 차면 가장 오래된 요소를 버림 |
| `dropTail()` | 버퍼가 가득 차면 가장 최근 요소를 버림 |
| `dropBuffer()` | 버퍼를 비우고 새 요소만 유지 |
| `fail()` | 버퍼가 가득 차면 스트림 실패 |

---

## 9. 클러스터 (Cluster)

### 9.1 개념

Akka Cluster는 여러 JVM(노드)에 걸쳐 액터를 분산 배치하고 통신할 수 있게 합니다. Gossip 프로토콜을 사용하여 멤버십을 관리하고, Split Brain Resolver로 네트워크 파티션을 처리합니다.

### 9.2 클러스터 설정 (cluster.conf)

```hocon
akka {
  actor {
    provider = cluster
    serialization-bindings {
      "com.webnori.springweb.example.akka.actors.cluster.MySerializable" = jackson-json
    }
  }

  remote.artery {
    canonical {
      hostname = "127.0.0.1"
      port = 12551
    }
  }

  cluster {
    seed-nodes = [
      "akka://ClusterSystem@127.0.0.1:12551"
    ]
    role {
      seed.min-nr-of-members = 1
    }
    auto-down-unreachable-after = 10s
    downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
  }

  extensions = ["akka.cluster.metrics.ClusterMetricsExtension"]
}
```

### 9.3 MySerializable 마커 인터페이스

클러스터 환경에서 노드 간 메시지를 전송하려면 직렬화가 필요합니다. 마커 인터페이스를 사용하여 Jackson JSON 직렬화를 적용합니다.

```java
package com.webnori.springweb.example.akka.actors.cluster;

// 클러스터 간 전송되는 모든 메시지가 구현해야 하는 마커 인터페이스
public interface MySerializable {}
```

HOCON 설정에서 이 인터페이스를 구현한 모든 클래스에 `jackson-json` 직렬화를 적용합니다:
```hocon
akka.actor.serialization-bindings {
  "com.webnori.springweb.example.akka.actors.cluster.MySerializable" = jackson-json
}
```

### 9.4 ClusterListener - 멤버십 이벤트 감시

```java
package com.webnori.springweb.example.akka.actors.cluster;

import akka.actor.AbstractActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class ClusterListener extends AbstractActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    Cluster cluster = Cluster.get(getContext().system());

    @Override
    public void preStart() {
        // 클러스터 이벤트 구독
        cluster.subscribe(self(),
            (ClusterEvent.SubscriptionInitialStateMode)
                ClusterEvent.initialStateAsEvents(),
            MemberEvent.class,
            UnreachableMember.class);
    }

    @Override
    public void postStop() {
        cluster.unsubscribe(self());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(MemberUp.class, mUp -> {
                log.info("Member is Up: {}", mUp.member());
            })
            .match(UnreachableMember.class, mUnreachable -> {
                log.info("Member detected as unreachable: {}",
                    mUnreachable.member());
            })
            .match(MemberRemoved.class, mRemoved -> {
                log.info("Member is Removed: {}", mRemoved.member());
            })
            .match(MemberEvent.class, message -> {
                // 기타 멤버 이벤트 무시
            })
            .build();
    }
}
```

### 9.5 ClusterHelloWorld - 분산 액터

```java
package com.webnori.springweb.example.akka.actors.cluster;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class ClusterHelloWorld extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    Cluster cluster = Cluster.get(getContext().system());

    public static Props Props() {
        return Props.create(ClusterHelloWorld.class);
    }

    @Override
    public void preStart() {
        cluster.subscribe(self(),
            (ClusterEvent.SubscriptionInitialStateMode)
                ClusterEvent.initialStateAsEvents(),
            ClusterEvent.MemberEvent.class,
            ClusterEvent.UnreachableMember.class);
    }

    @Override
    public void postStop() {
        cluster.unsubscribe(self());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ClusterEvent.MemberUp.class, mUp -> {
                log.info("Member is Up: {}", mUp.member());
            })
            .match(ClusterEvent.UnreachableMember.class, mUnreachable -> {
                log.info("Member detected as unreachable: {}",
                    mUnreachable.member());
            })
            .match(ClusterEvent.MemberRemoved.class, mRemoved -> {
                log.info("Member is Removed: {}", mRemoved.member());
            })
            .match(ClusterEvent.MemberEvent.class, message -> {
                // 사용자 정의 분산 처리 메시지 구현 가능
            })
            .match(String.class, s -> {
                log.info("Received String message: {} {}",
                    s, context().self().path());
            })
            .match(TestClusterMessages.Ping.class, s -> {
                log.info("Received Ping message: {}",
                    context().self().path());
            })
            .build();
    }
}
```

### 9.6 ClusterRouterPool - 분산 라우팅

`AkkaManager`에서 `ClusterRouterPool`을 사용하여 여러 노드에 걸친 분산 라우터를 생성합니다:

```java
// 클러스터 라우터 설정
int totalInstances = 100;        // 전체 인스턴스 수
int maxInstancesPerNode = 3;     // 노드당 최대 인스턴스
boolean allowLocalRoutees = true; // 로컬 routee 허용

Set<String> useRoles = new HashSet<>(Arrays.asList("work"));

ActorRef clusterActor = actorSystem.actorOf(
    new ClusterRouterPool(
        new RoundRobinPool(0),
        new ClusterRouterPoolSettings(
            totalInstances,
            maxInstancesPerNode,
            allowLocalRoutees,
            useRoles))
        .props(Props.create(ClusterHelloWorld.class)),
    "workerRouter1");
```

### 9.7 Factorial 분산 계산 예제

클러스터를 활용한 분산 팩토리얼 계산 예제입니다.

#### FactorialRequest / FactorialResult 메시지

```java
public class FactorialRequest implements MySerializable {
    private static final long serialVersionUID = 1L;
    public final Integer upToN;

    public FactorialRequest(int upToN) {
        this.upToN = upToN;
    }
}

public class FactorialResult implements MySerializable {
    private static final long serialVersionUID = 1L;
    public final int n;
    public final BigInteger factorial;

    FactorialResult(int n, BigInteger factorial) {
        this.n = n;
        this.factorial = factorial;
    }
}
```

#### FactorialBackend - pipe 패턴

```java
package com.webnori.springweb.akka.cluster.factorial;

import akka.actor.AbstractActor;
import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;
import static akka.pattern.PatternsCS.pipe;

public class FactorialBackend extends AbstractActor {

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Integer.class, n -> {
                // CompletableFuture로 비동기 계산
                CompletableFuture<FactorialResult> result =
                    CompletableFuture.supplyAsync(() -> factorial(n))
                        .thenApply((factorial) -> new FactorialResult(n, factorial));

                // pipe 패턴: Future 결과를 sender에게 자동 전달
                pipe(result, getContext().dispatcher()).to(sender());
            })
            .build();
    }

    BigInteger factorial(int n) {
        BigInteger acc = BigInteger.ONE;
        for (int i = 1; i <= n; ++i) {
            acc = acc.multiply(BigInteger.valueOf(i));
        }
        return acc;
    }
}
```

> **pipe 패턴**: `pipe(future, dispatcher).to(sender())`는 Future가 완료되면 그 결과를 지정된 액터에게 자동으로 `tell`합니다. 액터 내부에서 `Await.result()`로 블로킹하는 것보다 훨씬 안전합니다.

#### FactorialClient - 분산 요청 클라이언트

```java
package com.webnori.springweb.akka.cluster.factorial;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ReceiveTimeout;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.FromConfig;
import scala.concurrent.duration.Duration;
import java.util.concurrent.TimeUnit;

public class FactorialClient extends AbstractActor {
    final int upToN;
    final boolean repeat;
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    // FromConfig로 클러스터 라우터 생성
    ActorRef backend = getContext().actorOf(
        FromConfig.getInstance().props(), "factorialBackendRouter");

    ActorRef probe;

    public FactorialClient(int upToN, boolean repeat) {
        this.upToN = upToN;
        this.repeat = repeat;
    }

    @Override
    public void preStart() {
        // 10초간 메시지가 없으면 ReceiveTimeout 발생
        getContext().setReceiveTimeout(Duration.create(10, TimeUnit.SECONDS));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(FactorialRequest.class, request -> {
                int upToN = request.upToN;
                log.info("Starting batch of factorials up to [{}]", upToN);
                for (Integer n = 1; n <= upToN; n++) {
                    backend.tell(n, self());
                }
                probe = getSender();
            })
            .match(FactorialResult.class, result -> {
                if (result.n == upToN) {
                    log.info("FactorialResult {}! = {}",
                        result.n, result.factorial);
                    if (repeat) sendJobs();
                    else getContext().stop(self());

                    // 결과를 probe에 전달
                    probe.tell(result, ActorRef.noSender());
                }
            })
            .match(ReceiveTimeout.class, message -> {
                log.info("Timeout");
                sendJobs();
            })
            .build();
    }

    void sendJobs() {
        log.info("Starting batch of factorials up to [{}]", upToN);
        for (int n = 1; n <= upToN; n++) {
            backend.tell(n, self());
        }
    }
}
```

#### Factorial 클러스터 라우터 설정 (factorial.conf)

```hocon
akka {
  actor {
    provider = cluster
    serialization-bindings {
      "com.webnori.springweb.example.akka.actors.cluster.MySerializable" = jackson-json
    }
  }

  remote.artery {
    canonical {
      hostname = "127.0.0.1"
      port = 0    # 자동 포트 할당
    }
  }

  cluster {
    seed-nodes = [
      "akka://ClusterSystem@127.0.0.1:12551"
    ]
    auto-down-unreachable-after = 10s
    min-nr-of-members = 3
    role {
      client.min-nr-of-members = 1
      backend.min-nr-of-members = 2
    }
  }

  extensions = ["akka.cluster.metrics.ClusterMetricsExtension"]
}

akka.actor.deployment {
  /factorialClient/factorialBackendRouter = {
    router = cluster-metrics-adaptive-group
    metrics-selector = mix
    routees.paths = ["/user/factorialBackend"]
    cluster {
      enabled = on
      use-role = backend
      allow-local-routees = off
    }
  }
}
```

#### FactorialTest - 멀티 노드 테스트

```java
@SpringBootTest
public class FactorialTest {

    @BeforeClass
    public static void setup() {
        // Seed 노드
        clusterSystem1 = serverStart("ClusterSystem", "server", "seed");

        // Backend 워커 노드 2개
        clusterSystem2 = serverStart("ClusterSystem", "factorial", "backend");
        clusterSystem2.actorOf(
            Props.create(FactorialBackend.class), "factorialBackend");

        clusterSystem3 = serverStart("ClusterSystem", "factorial", "backend");
        clusterSystem3.actorOf(
            Props.create(FactorialBackend.class), "factorialBackend");
    }

    @Test
    public void clusterTest() {
        final int upToN = 200;
        final Config config = ConfigFactory.parseString(
            "akka.cluster.roles = [client]")
            .withFallback(ConfigFactory.load("factorial"));

        final ActorSystem system = ActorSystem.create("ClusterSystem", config);

        new TestKit(system) {{
            ActorRef probe = getRef();
            // 클러스터 준비 완료 시 실행
            Cluster.get(system).registerOnMemberUp(() -> {
                ActorRef frontActor = system.actorOf(
                    Props.create(FactorialClient.class, upToN, false),
                    "factorialClient");
                frontActor.tell(new FactorialRequest(upToN), probe);
            });
            expectMsgClass(Duration.ofSeconds(20), FactorialResult.class);
        }};
    }
}
```

**클러스터 아키텍처**:
```
[Seed Node :12551]
       |
   ----+----
   |        |
[Backend1]  [Backend2]       <-- FactorialBackend 액터
   \        /
    \      /
  [Client Node]              <-- FactorialClient 액터
  (cluster-metrics-adaptive-group 라우터)
```

---

## 10. 생명주기와 종료 (Lifecycle & Shutdown)

### 10.1 액터 생명주기

```
          actorOf()
              |
              v
        [Constructor]
              |
              v
          preStart()
              |
              v
      [메시지 처리 루프]  <--+
              |              |
              v              |
        [메시지 수신] -------+
              |
         [종료 요청]
              |
              v
          postStop()
              |
              v
          [종료됨]
```

### 10.2 LifeCycleTestActor

```java
package com.webnori.springweb.akka.utils.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class LifeCycleTestActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    static public int workCountForTest;
    private ActorRef probe;

    public static Props Props() {
        return Props.create(LifeCycleTestActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, message -> {
                switch (message) {
                    case "someWork": {
                        workCountForTest++;
                        Thread.sleep(500);
                        log.info("WorkCount:{}", workCountForTest);
                        if (workCountForTest == 10) {
                            probe.tell("done", ActorRef.noSender());
                        }
                    }
                }
            })
            .match(ActorRef.class, actorRef -> {
                this.probe = actorRef;
            })
            .build();
    }

    @Override
    public void postStop() {
        log.info("LifeCycleTestActor Stop - workCountForTest:{}", workCountForTest);
        probe.tell("cancel", ActorRef.noSender());
    }
}
```

### 10.3 PoisonPill vs stop() 비교

#### PoisonPill - 대기 메시지 처리 후 종료

```java
@Test
public void ActorPoisonPillTest() {
    var testActor = actorSystem.actorOf(LifeCycleTestActor.Props(), "testActor");

    new TestKit(actorSystem) {{
        final TestKit probe = new TestKit(actorSystem);
        testActor.tell(probe.getRef(), getRef());

        // 10개의 작업 메시지 전송
        for (int i = 0; i < 10; i++) {
            testActor.tell("someWork", ActorRef.noSender());
        }

        // PoisonPill: 이 메시지까지 받고 중지 (대기 중인 10개 모두 처리)
        testActor.tell(PoisonPill.getInstance(), ActorRef.noSender());

        within(Duration.ofSeconds(10), () -> {
            // PoisonPill 이후 전송 - 처리되지 않음 (DeadLetters로)
            testActor.tell("someWork", ActorRef.noSender());

            probe.expectMsg(Duration.ofSeconds(10), "done");
            probe.expectNoMessage();

            // 10개 모두 처리됨
            assertEquals(10, LifeCycleTestActor.workCountForTest);
            return null;
        });
    }};
}
```

#### stop() - 즉시 종료

```java
@Test
public void ActorStopTest() {
    var testActor = actorSystem.actorOf(LifeCycleTestActor.Props(), "testActor");

    new TestKit(actorSystem) {{
        final TestKit probe = new TestKit(actorSystem);
        testActor.tell(probe.getRef(), getRef());

        for (int i = 0; i < 10; i++) {
            testActor.tell("someWork", ActorRef.noSender());
        }

        // stop: 대기 중인 메시지를 고려하지 않고 즉시 중단
        actorSystem.stop(testActor);

        within(Duration.ofSeconds(10), () -> {
            testActor.tell("someWork", ActorRef.noSender());

            // postStop에서 "cancel" 전송됨
            probe.expectMsg(Duration.ofSeconds(10), "cancel");
            probe.expectNoMessage();

            // 10개보다 적게 처리됨
            assertEquals(true, LifeCycleTestActor.workCountForTest < 10);
            return null;
        });
    }};
}
```

| 종료 방법 | 대기 메시지 | 동작 |
|-----------|------------|------|
| `PoisonPill` | 모두 처리 후 종료 | 메일박스에 PoisonPill이 큐잉됨 |
| `actorSystem.stop()` | 무시하고 즉시 종료 | 현재 메시지 처리 후 바로 종료 |
| `Kill` | 즉시 종료 + 예외 발생 | `ActorKilledException` 발생 |

### 10.4 CoordinatedShutdown - 단계적 종료

프로덕션 환경에서 진행 중인 작업을 안전하게 완료한 후 종료하는 메커니즘입니다.

#### WorkStatusActor - 작업 상태 추적

```java
package com.webnori.springweb.akka.utils.actor;

import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class WorkStatusActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private boolean isGraceFulShutDown;
    private int remainWork;
    private int errorCount;

    public static Props Props() {
        return Props.create(WorkStatusActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, message -> {
                switch (message) {
                    case "stop": {
                        if (remainWork == 0) {
                            sender().tell(Done.getInstance(), ActorRef.noSender());
                            if (!isGraceFulShutDown) {
                                log.info(
                                    "=== GraceFul ShutDown === RemainWorks:{},Errors:{}",
                                    remainWork, errorCount);
                            }
                            isGraceFulShutDown = true;
                        }
                    } break;
                    case "increse": { remainWork++; } break;
                    case "decrese": {
                        if (remainWork > 0) remainWork--;
                    } break;
                    case "decrese-exception": {
                        if (remainWork > 0) remainWork--;
                        errorCount++;
                    } break;
                }
            })
            .build();
    }

    @Override
    public void postStop() {
        log.info("WorkStatusActor Stop - remainWork:{}", remainWork);
    }
}
```

#### CoordinatedShutdown 테스트

```java
@SpringBootTest
public class CoordinatedShutdownTest {

    private static ActorRef appActor;

    @BeforeClass
    public static void bootUp() {
        actorSystem = serverStart("ClusterSystem", "router-test", "seed");
        appActor = actorSystem.actorOf(WorkStatusActor.Props(), "APPActor");
    }

    @AfterClass
    public static void bootDown() {
        int retryCount = 5;
        for (int i = 0; i < retryCount; i++) {
            // CoordinatedShutdown에 작업 완료 확인 태스크 등록
            CoordinatedShutdown.get(actorSystem).addTask(
                CoordinatedShutdown.PhaseBeforeServiceUnbind(),
                "WorkCheckTask",
                () -> {
                    return akka.pattern.Patterns
                        .ask(appActor, "stop", Duration.ofSeconds(1))
                        .thenApply(reply -> Done.getInstance());
                });
        }
    }

    @Test
    public void GraceArOKTest() throws InterruptedException {
        appActor.tell("increse", ActorRef.noSender());
        appActor.tell("increse", ActorRef.noSender());
        appActor.tell("decrese", ActorRef.noSender());
        appActor.tell("decrese-exception", ActorRef.noSender());
        // remainWork = 0이 되므로 Graceful Shutdown 성공
    }

    @Test
    public void GraceNotOKTest() throws InterruptedException {
        appActor.tell("increse", ActorRef.noSender());
        // remainWork = 1이므로 Graceful Shutdown 대기
    }
}
```

**CoordinatedShutdown 주요 Phase**:
```
PhaseBeforeServiceUnbind   --> 서비스 언바인드 전 (작업 완료 대기)
PhaseServiceUnbind         --> HTTP/gRPC 서비스 언바인드
PhaseServiceRequestsDone   --> 진행 중인 요청 완료
PhaseServiceStop           --> 서비스 중지
PhaseBeforeClusterShutdown --> 클러스터 떠나기 전
PhaseClusterShutdown       --> 클러스터 종료
PhaseBeforeActorSystemTerminate --> ActorSystem 종료 전
PhaseActorSystemTerminate  --> ActorSystem 종료
```

---

## 11. 테스트 (Testing with TestKit)

### 11.1 TestKit 개요

`akka-testkit`은 액터의 비동기 메시지 전달을 동기적으로 검증할 수 있는 테스트 도구입니다.

### 11.2 기본 테스트 구조

```java
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

public class BasicTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setup() {
        // 테스트용 ActorSystem 생성
        final Config newConfig = ConfigFactory.parseString(
            String.format("akka.cluster.roles = [%s]", "seed"))
            .withFallback(ConfigFactory.load("test"));

        actorSystem = ActorSystem.create("ClusterSystem", newConfig);
    }

    @Test
    @DisplayName("Actor - HelloWorld Test")
    public void TestIt() {
        new TestKit(actorSystem) {{
            // probe: 메시지 수신을 관찰하는 테스트 액터
            final TestKit probe = new TestKit(actorSystem);

            // 테스트 대상 액터 생성
            final ActorRef greetActor =
                actorSystem.actorOf(HelloWorld.Props(), "HelloWorld");

            // probe를 주입
            greetActor.tell(probe.getRef(), getRef());
            expectMsg(Duration.ofSeconds(1), "done");

            within(Duration.ofSeconds(3), () -> {
                // 메시지 전송
                greetActor.tell("hello", getRef());

                // probe에 메시지 도착 대기
                awaitCond(probe::msgAvailable);

                // 응답 검증
                probe.expectMsg(Duration.ZERO, "world");

                // 추가 메시지 없음 확인
                expectNoMessage();
                return null;
            });
        }};
    }
}
```

### 11.3 대량 메시지 테스트

```java
@Test
@DisplayName("Actor - HelloWorld Tests")
public void TestItMany() {
    new TestKit(actorSystem) {{
        final TestKit probe = new TestKit(actorSystem);
        final ActorRef greetActor =
            actorSystem.actorOf(HelloWorld.Props(), "HelloWorld2");

        greetActor.tell(probe.getRef(), getRef());
        expectMsg(Duration.ofSeconds(1), "done");

        within(Duration.ofSeconds(3), () -> {
            int testCount = 1000;

            // 1000개 메시지 일괄 전송
            for (int i = 0; i < testCount; i++) {
                greetActor.tell("hello", getRef());
            }

            // 1000개 응답 모두 수신 확인
            for (int i = 0; i < testCount; i++) {
                probe.expectMsg(Duration.ofSeconds(1), "world");
            }

            expectNoMessage();
            return null;
        });
    }};
}
```

### 11.4 주요 TestKit API

| 메서드 | 설명 |
|--------|------|
| `expectMsg(duration, msg)` | 지정된 시간 내에 특정 메시지 수신 확인 |
| `expectMsgClass(duration, class)` | 지정된 클래스 타입의 메시지 수신 확인 |
| `expectNoMessage()` | 추가 메시지가 없음을 확인 |
| `expectNoMessage(duration)` | 지정된 시간 동안 메시지가 없음을 확인 |
| `within(duration, supplier)` | 지정된 시간 내에 블록 실행 |
| `awaitCond(supplier)` | 조건이 true가 될 때까지 대기 |
| `probe.getRef()` | probe 액터의 ActorRef 반환 |
| `probe.msgAvailable` | probe에 수신 가능한 메시지가 있는지 확인 |
| `getRef()` | TestKit 자체의 ActorRef (발신자로 사용) |

### 11.5 AbstractJavaTest - 테스트 기본 클래스

```java
package com.webnori.springweb.akka;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.webnori.springweb.example.akka.AkkaManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.scalatestplus.junit.JUnitSuite;

public abstract class AbstractJavaTest extends JUnitSuite {
    public static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = AkkaManager.getInstance().getActorSystem();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }
}
```

> **테스트 팁**: `TestKit.shutdownActorSystem()`은 모든 액터를 정리하고 ActorSystem을 안전하게 종료합니다. `@AfterClass`에서 반드시 호출하여 테스트 간 간섭을 방지하세요.

---

## 12. Spring Boot 통합

### 12.1 AkkaManager - Singleton 패턴

Spring Boot 애플리케이션에서 ActorSystem을 관리하는 싱글턴 클래스입니다.

```java
package com.webnori.springweb.example.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.routing.ClusterRouterPool;
import akka.cluster.routing.ClusterRouterPoolSettings;
import akka.routing.RoundRobinPool;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.webnori.springweb.example.akka.actors.HelloWorld;
import com.webnori.springweb.example.akka.actors.TimerActor;
import com.webnori.springweb.example.akka.actors.cluster.ClusterHelloWorld;
import com.webnori.springweb.example.akka.actors.cluster.ClusterListener;
import lombok.Getter;
import java.util.*;

public final class AkkaManager {
    private static AkkaManager INSTANCE;

    @Getter
    private final ActorSystem actorSystem;
    private String akkaConfig;
    private String role;
    private String hostname;
    private String hostport;
    private String seed;

    @Getter private ActorRef greetActor;
    @Getter private ActorRef routerActor;
    @Getter private ActorRef clusterActor;
    @Getter private ActorRef clusterManagerActor;

    private AkkaManager() {
        // 환경변수에서 클러스터 설정 로드
        akkaConfig = System.getenv("akka.cluster-config");
        role = System.getenv("akka.role");
        hostname = System.getenv("akka.hostname");
        hostport = System.getenv("akka.hostport");
        seed = System.getenv("akka.seed");

        actorSystem = serverStart("ClusterSystem", akkaConfig, role);
        InitActor();
    }

    public static AkkaManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AkkaManager();
        }
        return INSTANCE;
    }

    private ActorSystem serverStart(String sysName, String clusterConfig, String role) {
        Config regularConfig = ConfigFactory.load();
        Config combined;

        Boolean isCluster = !isEmptyString(clusterConfig) ||
                            !isEmptyString(role) ||
                            !isEmptyString(hostname) ||
                            !isEmptyString(hostport) ||
                            !isEmptyString(seed);

        if (isCluster) {
            // 클러스터 모드: 환경변수 기반 동적 설정
            Config newConfig = ConfigFactory.parseString(
                String.format("akka.cluster.roles = [%s]", role))
                .withFallback(ConfigFactory.load(clusterConfig));

            newConfig = ConfigFactory.parseString(
                String.format("akka.cluster.seed-nodes = [\"%s\"]", seed))
                .withFallback(ConfigFactory.load(newConfig));

            newConfig = ConfigFactory.parseString(
                String.format(
                    "akka.remote.artery.canonical.hostname = \"%s\"", hostname))
                .withFallback(ConfigFactory.load(newConfig));

            newConfig = ConfigFactory.parseString(
                String.format(
                    "akka.remote.artery.canonical.port = %s", hostport))
                .withFallback(ConfigFactory.load(newConfig));

            combined = newConfig.withFallback(regularConfig);
        } else {
            // 로컬 모드: 기본 클러스터 설정
            final Config newConfig = ConfigFactory.parseString(
                String.format("akka.cluster.roles = [%s]", "seed"))
                .withFallback(ConfigFactory.load("cluster"));
            combined = newConfig.withFallback(regularConfig);
        }

        ActorSystem serverSystem = ActorSystem.create(sysName, combined);
        serverSystem.actorOf(
            Props.create(ClusterListener.class), "clusterListener");
        return serverSystem;
    }

    private void InitActor() {
        // 단일 액터 + 커스텀 Dispatcher
        greetActor = actorSystem.actorOf(
            HelloWorld.Props().withDispatcher("my-dispatcher"), "HelloWorld");

        // RoundRobinPool 라우터 (5 인스턴스)
        routerActor = actorSystem.actorOf(
            new RoundRobinPool(5).props(HelloWorld.Props()), "roundRobinPool");

        // 타이머 액터 + 블로킹 전용 Dispatcher
        actorSystem.actorOf(
            TimerActor.Props().withDispatcher("my-blocking-dispatcher"),
            "TimerActor");

        // 클러스터 라우터 (work 역할 노드에 분산)
        int totalInstances = 100;
        int maxInstancesPerNode = 3;
        boolean allowLocalRoutees = true;

        Set<String> useRoles = new HashSet<>(Arrays.asList("work"));
        clusterActor = actorSystem.actorOf(
            new ClusterRouterPool(
                new RoundRobinPool(0),
                new ClusterRouterPoolSettings(
                    totalInstances, maxInstancesPerNode,
                    allowLocalRoutees, useRoles))
                .props(Props.create(ClusterHelloWorld.class)),
            "workerRouter1");

        // 매니저 역할 클러스터 라우터
        Set<String> useManagerRoles = new HashSet<>(Arrays.asList("manager"));
        clusterManagerActor = actorSystem.actorOf(
            new ClusterRouterPool(
                new RoundRobinPool(0),
                new ClusterRouterPoolSettings(
                    totalInstances, maxInstancesPerNode,
                    allowLocalRoutees, useManagerRoles))
                .props(Props.create(ClusterHelloWorld.class)),
            "workerRouter2");
    }

    boolean isEmptyString(String string) {
        return string == null || string.isEmpty();
    }
}
```

### 12.2 Spring Bean으로의 전환 가이드

위의 `AkkaManager`를 Spring `@Configuration`으로 전환하는 패턴입니다:

```java
@Configuration
public class AkkaConfig {

    @Bean
    public ActorSystem actorSystem() {
        Config config = ConfigFactory.load();
        return ActorSystem.create("MySystem", config);
    }

    @Bean
    public ActorRef greetActor(ActorSystem system) {
        return system.actorOf(HelloWorld.Props()
            .withDispatcher("my-dispatcher"), "HelloWorld");
    }

    @Bean
    public ActorRef routerActor(ActorSystem system) {
        return system.actorOf(
            new RoundRobinPool(5).props(HelloWorld.Props()),
            "roundRobinPool");
    }

    @PreDestroy
    public void shutdown(ActorSystem system) {
        system.terminate();
    }
}
```

> **참고**: Spring Bean 기반 Akka 통합에 대한 상세 내용은 [Baeldung - Akka with Spring](https://www.baeldung.com/akka-with-spring) 문서를 참고하세요.

---

## 13. Dispatcher 설정

### 13.1 Dispatcher 개요

Dispatcher는 액터에 스레드를 할당하는 핵심 구성 요소입니다. 적절한 Dispatcher 설정은 성능에 직접적인 영향을 미칩니다.

### 13.2 application.conf 기본 설정

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
  # 한 액터가 연속 처리할 최대 메시지 수
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

### 13.3 테스트용 Dispatcher (test.conf)

```hocon
my-dispatcher-test1 {
  type = Dispatcher
  executor = "fork-join-executor"
  fork-join-executor {
    parallelism-min = 2
    parallelism-factor = 2.0
    parallelism-max = 50
  }
  throughput = 10
}

my-dispatcher-streamtest {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 50
  }
  throughput = 1
}

akka {
  actor {
    default-dispatcher {
      fork-join-executor.parallelism-min = 10
      fork-join-executor.parallelism-max = 50
      fork-join-executor.parallelism-factor = 2.0
    }
  }
}
```

### 13.4 Dispatcher 사용 예시

```java
// Props 생성 시 Dispatcher 지정
ActorRef actor1 = actorSystem.actorOf(
    HelloWorld.Props().withDispatcher("my-dispatcher"), "actor1");

// 블로킹 작업용 Dispatcher
ActorRef timerActor = actorSystem.actorOf(
    TimerActor.Props().withDispatcher("my-blocking-dispatcher"), "timer");

// Props 팩토리에서 Dispatcher 지정
public static Props Props(String dispatcher) {
    return Props.create(GreetingActor.class).withDispatcher(dispatcher);
}
ActorRef actor2 = actorSystem.actorOf(
    GreetingActor.Props("my-dispatcher-test1"), "actor2");

// Materializer에 Dispatcher 적용 (Streams)
ActorMaterializerSettings settings =
    ActorMaterializerSettings.create(actorSystem)
        .withDispatcher("my-dispatcher-streamtest");
Materializer materializer = ActorMaterializer.create(settings, actorSystem);
```

### 13.5 Dispatcher 선택 가이드

| 유형 | Executor | 용도 |
|------|----------|------|
| CPU 집약적 | `fork-join-executor` | 계산 위주, 짧은 작업 |
| I/O 블로킹 | `thread-pool-executor` (fixed-pool) | DB 조회, 외부 API 호출 |
| 스트림 처리 | `thread-pool-executor` (large pool) | Akka Streams 파이프라인 |

> **중요**: 블로킹 작업(Thread.sleep, DB 호출 등)을 하는 액터는 반드시 전용 Dispatcher를 사용하세요. `default-dispatcher`에서 블로킹이 발생하면 시스템 전체의 응답성이 저하됩니다.

---

## 14. 프로젝트 구성 (Build Configuration)

### 14.1 build.gradle 의존성

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '2.7.11'
    id 'io.spring.dependency-management' version '1.0.15.RELEASE'
    id 'me.champeau.jmh' version '0.6.8'
}

group = 'com.webnori'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '11'

repositories {
    mavenCentral()
    maven { url "https://repo.akka.io/maven" }
}

dependencies {
    def scalaVersion = '2.13'
    def akkaVersion = '2.7.0'
    def akkaHttpVersion = '10.4.0'

    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // Akka Core
    implementation platform("com.typesafe.akka:akka-bom_$scalaVersion:$akkaVersion")
    implementation "com.typesafe.akka:akka-actor_$scalaVersion:$akkaVersion"

    // Akka Jackson 직렬화 (클러스터 메시지)
    implementation "com.typesafe.akka:akka-serialization-jackson_$scalaVersion:$akkaVersion"

    // Akka Stream
    implementation "com.typesafe.akka:akka-stream_$scalaVersion:$akkaVersion"

    // Akka Cluster
    implementation "com.typesafe.akka:akka-cluster_$scalaVersion:$akkaVersion"
    implementation "com.typesafe.akka:akka-cluster-metrics_$scalaVersion:$akkaVersion"

    // Akka Logging
    implementation "com.typesafe.akka:akka-slf4j_$scalaVersion:$akkaVersion"

    // Test
    testImplementation "com.typesafe.akka:akka-testkit_$scalaVersion:$akkaVersion"
    testImplementation "com.typesafe.akka:akka-stream-testkit_$scalaVersion:$akkaVersion"
}
```

> **주의**: Akka 2.7.0부터 `akka-bom`(Bill of Materials)을 사용하여 모든 Akka 모듈의 버전을 일관되게 관리할 수 있습니다. Scala 버전(`2.13`)은 모든 Akka 의존성에서 동일해야 합니다.

### 14.2 프로젝트 디렉토리 구조

```
springweb/
  src/
    main/
      java/com/webnori/springweb/
        example/akka/
          actors/
            HelloWorld.java          # 기본 액터
            GreetingActor.java       # Dispatcher 연동 액터
            ParentActor.java         # 부모 액터 (계층)
            ChildActor.java          # 자식 액터
            TimerActor.java          # 타이머 + 자식 액터
            TestTimerActor.java      # 타이머 전환 예제
            SafeBatchActor.java      # 배치 처리 액터
            cluster/
              ClusterListener.java   # 클러스터 이벤트 감시
              ClusterHelloWorld.java # 분산 액터
              MySerializable.java    # 직렬화 마커 인터페이스
              TestClusterMessages.java
          AkkaManager.java           # Singleton ActorSystem 관리
          models/
            FakeSlowMode.java        # 테스트용 슬로우 모드 모델
      resources/
        application.conf             # 기본 Dispatcher 설정
        cluster.conf                 # 클러스터 설정
    test/
      java/com/webnori/springweb/akka/
        intro/
          BasicTest.java             # 기본 테스트
          SimpleActorTest.java       # 다양한 액터 샘플 테스트
          HierarchyActorTest.java    # 계층 액터 테스트
          ThrottleTest.java          # Throttle 테스트
          DisPatcherTest.java        # Dispatcher 테스트
        router/routing/
          BasicRoutingTest.java      # HOCON 라우팅 테스트
          actor/
            WorkerActor.java
            WorkMessage.java
        stream/
          BackPressureTest.java      # Backpressure 테스트
          actor/
            SlowConsumerActor.java   # Self-Throttle 액터
            TpsMeasurementActor.java # TPS 측정 액터
            model/TPSInfo.java
        utils/
          ActorLifeCycleTest.java    # 생명주기 테스트
          CoordinatedShutdownTest.java # 단계적 종료 테스트
          actor/
            LifeCycleTestActor.java
            WorkStatusActor.java
        cluster/factorial/
          FactorialBackend.java      # 분산 계산 백엔드
          FactorialClient.java       # 분산 계산 클라이언트
          FactorialTest.java         # 클러스터 테스트
          FactorialRequest.java
          FactorialResult.java
      resources/
        test.conf                    # 테스트 Dispatcher 설정
        router-test.conf             # 라우터 전략 설정
        factorial.conf               # 클러스터 팩토리얼 설정
        server.conf                  # 서버 설정
```

---

## 부록: 패턴 빠른 참조

### A. 메시지 전달 패턴

```java
// 1. Tell (Fire-and-Forget)
actor.tell(message, getSelf());
actor.tell(message, ActorRef.noSender());

// 2. Ask (Request-Reply)
Future<Object> future = Patterns.ask(actor, message, timeout);
Object result = Await.result(future, timeout.duration());

// 3. Forward (원래 발신자 유지)
target.forward(message, getContext());

// 4. Pipe (Future 결과를 액터에게 전달)
pipe(completableFuture, getContext().dispatcher()).to(sender());
```

### B. 액터 생성 패턴

```java
// 1. 기본 생성
ActorRef actor = system.actorOf(MyActor.Props(), "name");

// 2. 자식 액터 생성
ActorRef child = context().actorOf(ChildActor.Props(), "child");

// 3. Dispatcher 지정
ActorRef actor = system.actorOf(
    MyActor.Props().withDispatcher("my-dispatcher"), "name");

// 4. Pool 라우터
ActorRef router = system.actorOf(
    new RoundRobinPool(5).props(MyActor.Props()), "router");

// 5. Group 라우터
ActorRef router = system.actorOf(
    new RoundRobinGroup(paths).props(), "router");

// 6. FromConfig 라우터
ActorRef router = system.actorOf(
    FromConfig.getInstance().props(MyActor.Props()), "router1");

// 7. 클러스터 라우터
ActorRef clusterRouter = system.actorOf(
    new ClusterRouterPool(
        new RoundRobinPool(0),
        new ClusterRouterPoolSettings(100, 3, true, roles))
        .props(Props.create(MyActor.class)),
    "clusterRouter");
```

### C. Streams Throttle 패턴

```java
// Source -> Throttle -> Sink(Actor)
ActorRef throttler =
    Source.actorRef(bufferSize, OverflowStrategy.dropNew())
        .throttle(elementsPerSec, Duration.ofSeconds(1),
            maxBurst, ThrottleMode.shaping())
        .to(Sink.actorRef(targetActor, onComplete))
        .run(materializer);

// Source -> Buffer(Backpressure) -> Throttle -> mapAsync -> Sink
source
    .via(Flow.<T>create().buffer(size, OverflowStrategy.backpressure()))
    .throttle(tps, Duration.ofSeconds(1))
    .via(Flow.<T>create().mapAsync(parallelism, this::asyncCall))
    .to(Sink.foreach(result -> { /* 처리 */ }))
    .run(materializer);
```

### D. 테스트 패턴

```java
new TestKit(actorSystem) {{
    TestKit probe = new TestKit(actorSystem);
    ActorRef actor = actorSystem.actorOf(MyActor.Props(), "test");

    // 메시지 전송
    actor.tell(message, getRef());

    // 응답 확인
    expectMsg(Duration.ofSeconds(1), expectedResponse);
    probe.expectMsg(expectedResponse);

    // 시간 범위 내 실행
    within(Duration.ofSeconds(5), () -> {
        // 테스트 로직
        return null;
    });

    // 메시지 없음 확인
    expectNoMessage(Duration.ofSeconds(3));
}};
```

---

## 참고 링크

- [Akka Classic Actors 공식 문서](https://doc.akka.io/docs/akka/current/index-actors.html)
- [Akka Testing 공식 문서](https://doc.akka.io/docs/akka/current/testing.html)
- [Akka Routing 공식 문서](https://doc.akka.io/docs/akka/current/routing.html)
- [Akka Streams 공식 문서](https://doc.akka.io/docs/akka/current/stream/stream-flows-and-basics.html)
- [Akka Cluster 공식 문서](https://doc.akka.io/docs/akka/current/typed/cluster.html)
- [Akka Coordinated Shutdown](https://doc.akka.io/docs/akka/current/coordinated-shutdown.html)
- [Akka Timers / Scheduled Messages](https://doc.akka.io/docs/akka/current/actors.html#timers-scheduled-messages)
- [Baeldung - Akka with Spring](https://www.baeldung.com/akka-with-spring)
