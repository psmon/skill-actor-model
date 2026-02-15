# Java Akka Classic TestKit 활용 가이드

이 문서는 **Java + Akka Classic** 환경에서 `akka-testkit`으로 액터를 테스트하는 방법을 한글로 정리한 가이드입니다.

## 목차

1. 개요
2. 의존성 설정
3. 기본 테스트 예제
4. 주요 assertion API
5. 타이밍 검증 (`within`)
6. 로그/예외 검증
7. 내부 동작 후킹(override)
8. 모범 사례

## 1. 개요

Akka Classic 테스트의 핵심은 다음입니다.

- 액터 간 메시지 흐름 검증
- 비동기 응답 타이밍 검증
- 예외/로그 이벤트 검증

`akka-testkit`은 `TestKit`을 통해 메시지 큐, probe, 타이밍 assertion을 제공합니다.

## 2. 의존성 설정

> 이 저장소의 기본 스킬 기준 권장 버전은 `Akka 2.7.x`입니다.

```java
dependencies {
    implementation("com.typesafe.akka:akka-actor_2.13:2.7.0")

    testImplementation("com.typesafe.akka:akka-testkit_2.13:2.7.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
```

## 3. 기본 테스트 예제

### 3.1 테스트 대상 액터

```java
import akka.actor.AbstractActor;
import akka.actor.ActorRef;

public class SomeActor extends AbstractActor {
    private ActorRef target;

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .matchEquals("hello", msg -> {
                getSender().tell("world", getSelf());
                if (target != null) target.forward("hello", getContext());
            })
            .match(ActorRef.class, actorRef -> {
                target = actorRef;
                getSender().tell("done", getSelf());
            })
            .build();
    }
}
```

### 3.2 TestKit 테스트

```java
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SomeActorTest {
    static ActorSystem system;

    @BeforeAll
    static void setup() {
        system = ActorSystem.create("test-system");
    }

    @AfterAll
    static void teardown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    void should_reply_world_and_forward_hello() {
        new TestKit(system) {{
            ActorRef subject = system.actorOf(Props.create(SomeActor.class));
            TestKit probe = new TestKit(system);

            subject.tell(probe.getRef(), getRef());
            expectMsg(Duration.ofSeconds(1), "done");

            within(Duration.ofSeconds(3), () -> {
                subject.tell("hello", getRef());
                awaitCond(probe::msgAvailable);

                expectMsg(Duration.ZERO, "world");
                probe.expectMsg(Duration.ZERO, "hello");
                expectNoMessage(Duration.ofMillis(200));
                return null;
            });
        }};
    }
}
```

## 4. 주요 assertion API

| API | 설명 |
|---|---|
| `expectMsgEquals(max, msg)` | 지정 시간 내 동일 메시지 수신 |
| `expectMsgClass(max, clazz)` | 타입 기반 수신 검증 |
| `expectMsgAnyOf(max, ...msgs)` | 후보 값 중 하나 수신 |
| `expectMsgAllOf(max, ...msgs)` | 다중 메시지 수신 |
| `expectNoMessage(max)` | 메시지 없음 검증 |
| `receiveN(n, max)` | n개 메시지 수신 |
| `awaitCond(max, interval, predicate)` | 조건이 true 될 때까지 폴링 |
| `awaitAssert(max, interval, assertion)` | assertion 재시도 |
| `ignoreMsg(filter)` | 특정 메시지 무시 |

## 5. 타이밍 검증 (`within`)

```java
within(Duration.ZERO, Duration.ofSeconds(1), () -> {
    getRef().tell(42, ActorRef.noSender());
    expectMsgEquals(42);
    return null;
});
```

- `within` 블록 전체 시간 상한을 명시할 수 있습니다.
- `expectNoMessage`를 마지막에 둘 경우, 블록 전체 시간이 길어질 수 있어 테스트 의도를 분명히 적어두는 것이 좋습니다.

## 6. 로그/예외 검증

예외를 직접 잡기 어려운 통합 테스트에서는 `EventFilter` 기반 로그 검증을 사용합니다.

```java
new EventFilter(Exception.class, system)
    .occurrences(1)
    .intercept(() -> {
        // 예외를 유발할 코드
        return null;
    });
```

필요 시 `application.conf`에 테스트용 로거를 설정합니다.

```hocon
akka.loggers = [akka.testkit.TestEventListener]
```

## 7. 내부 동작 후킹(override)

타이머/스케줄링 같은 내부 동작은 테스트 더블로 대체해 검증할 수 있습니다.

- 운영 액터의 스케줄링 메서드를 protected 수준으로 분리
- 테스트에서 해당 메서드를 override하여 probe에 직접 메시지 전송

이 방식은 타이밍 민감 테스트의 안정성을 높입니다.

## 8. 모범 사례

- 테스트 종료 시 `shutdownActorSystem`을 항상 호출
- `Thread.sleep`보다 `within`, `awaitCond` 우선
- 단일 액터 테스트와 다중 액터 상호작용 테스트를 분리
- "응답 있음"뿐 아니라 "불필요한 추가 메시지 없음"도 검증
- 프로덕션 dispatcher 설정과 테스트 dispatcher 차이를 인지하고 타임아웃을 조정
