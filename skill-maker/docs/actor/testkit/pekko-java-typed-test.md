# Pekko Typed (Java) TestKit 활용 가이드

이 문서는 **Apache Pekko Typed Java DSL**의 비동기 테스트 방법을 한글로 정리한 가이드입니다.

## 목차

1. 비동기 테스트 개요
2. 기본 예제 (Echo)
3. `ActorTestKit` 사용법
4. 액터 중지 테스트
5. Mocked Behavior 관찰
6. JUnit 통합
7. 테스트 설정(config)
8. 실무 체크리스트

## 1. 비동기 테스트 개요

Pekko Typed 테스트는 실제 `ActorSystem` 위에서 수행됩니다.  
즉, 단순 단위 테스트보다 실제 런타임과 가까운 환경에서 다음을 검증합니다.

- 메시지 송수신
- 프로토콜 타입 안정성
- 타이밍/종료 동작

## 2. 기본 예제 (Echo)

### 2.1 액터 프로토콜

```java
public static class Echo {
  public static class Ping {
    public final String message;
    public final ActorRef<Pong> replyTo;

    public Ping(String message, ActorRef<Pong> replyTo) {
      this.message = message;
      this.replyTo = replyTo;
    }
  }

  public static class Pong {
    public final String message;

    public Pong(String message) {
      this.message = message;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Pong)) return false;
      Pong that = (Pong) o;
      return message.equals(that.message);
    }

    @Override
    public int hashCode() {
      return message.hashCode();
    }
  }
}
```

### 2.2 액터 동작

```java
public static Behavior<Echo.Ping> create() {
  return Behaviors.receive(Echo.Ping.class)
      .onMessage(Echo.Ping.class, ping -> {
        ping.replyTo.tell(new Echo.Pong(ping.message));
        return Behaviors.same();
      })
      .build();
}
```

## 3. `ActorTestKit` 사용법

### 3.1 기본 생성/종료

```java
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;

class AsyncTestingExampleTest {
  static final ActorTestKit testKit = ActorTestKit.create();

  @AfterAll
  static void cleanup() {
    testKit.shutdownTestKit();
  }
}
```

### 3.2 spawn + probe 검증

```java
ActorRef<Echo.Ping> pinger = testKit.spawn(create(), "ping");
TestProbe<Echo.Pong> probe = testKit.createTestProbe();

pinger.tell(new Echo.Ping("hello", probe.ref()));
probe.expectMessage(new Echo.Pong("hello"));
```

- 익명 스폰도 가능: `testKit.spawn(create())`
- `TestProbe`는 `ActorRef`처럼 reply 대상에 직접 넣을 수 있음

## 4. 액터 중지 테스트

```java
ActorRef<Echo.Ping> pinger = testKit.spawn(create(), "pinger");
testKit.stop(pinger);

ActorRef<Echo.Ping> recreated = testKit.spawn(create(), "pinger");
testKit.stop(recreated, Duration.ofSeconds(10));
```

주의:

- `testKit.stop()`은 **같은 testKit으로 spawn된 액터**만 중지할 수 있습니다.

## 5. Mocked Behavior 관찰

외부 의존 액터를 실제로 띄우지 않고, 모의 Behavior로 상호작용만 검증할 수 있습니다.

```java
Behavior<Message> mocked = Behaviors.receiveMessage(msg -> {
  msg.replyTo.tell(msg.i);
  return Behaviors.same();
});

TestProbe<Message> probe = testKit.createTestProbe();
ActorRef<Message> mockedPublisher =
    testKit.spawn(Behaviors.monitor(Message.class, probe.ref(), mocked));
```

핵심 포인트:

- `Behaviors.monitor`로 메시지 관찰
- 실제 비즈니스 로직 없이 메시지 계약(프로토콜) 검증 가능

## 6. JUnit 통합

JUnit 환경에서는 `TestKitJunitResource`를 사용하면 수명주기 관리가 단순해집니다.

```java
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.junit.ClassRule;

public class JunitIntegrationExampleTest {
  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();
}
```

## 7. 테스트 설정(config)

기본 로딩 규칙:

- `application-test.conf`가 있으면 우선 사용
- 없으면 Pekko 기본 `reference.conf` 사용

필요 시 코드에서 직접 config 주입:

```java
Config custom = ConfigFactory.parseString(
    "pekko.loglevel = DEBUG\n" +
    "pekko.log-config-on-start = on\n");

ActorTestKit testKit = ActorTestKit.create(custom.withFallback(ConfigFactory.load()));
```

## 8. 실무 체크리스트

- `replyTo: ActorRef<T>`를 프로토콜에 명시해 테스트 용이성 확보
- `Thread.sleep` 대신 `expectMessage`, `expectNoMessage` 계열 사용
- 테스트마다 독립적인 testKit/시스템 수명주기 관리
- 액터 상태 검증보다 "입력 메시지 -> 출력 메시지" 계약 검증 중심으로 작성
- 비즈니스 의존 액터는 `monitor + mocked behavior`로 대체해 테스트 속도/안정성 확보
