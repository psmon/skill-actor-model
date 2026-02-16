---
name: java-akka-classic-test
description: Java + Akka Classic(2.7.x) 액터 유닛테스트를 생성합니다. akka-testkit과 JUnit으로 메시지 수신, probe 검증, within/expectNoMessage 타이밍 검증을 작성할 때 사용합니다.
argument-hint: "[테스트대상] [시나리오]"
---

# Java + Akka Classic 테스트 스킬

Akka Classic 2.7.x 기반 프로젝트의 테스트 코드를 작성할 때 사용합니다.

## 호환 버전

- Akka Classic: `2.7.x`
- 테스트 모듈: `akka-testkit_2.13` (Akka 버전과 동일)
- 테스트 프레임워크: `JUnit 5`
- Java: `17` (기본 스킬과 동일)

## 의존성 예시

```kotlin
dependencies {
    implementation("com.typesafe.akka:akka-actor_2.13:2.7.0")

    testImplementation("com.typesafe.akka:akka-testkit_2.13:2.7.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
```

## 테스트 작성 패턴

1. 테스트 수명주기에서 `ActorSystem` 생성/종료
2. `TestKit` probe 준비
3. 대상 액터에 메시지 주입
4. `expectMsg`, `expectNoMessage`, `within`으로 검증

## 권장 대기 전략 (중요)

- `Thread.sleep`로 클러스터 형성/전파를 기다리지 않습니다.
- `awaitAssert(max, interval, assertion)`을 기본 대기 전략으로 사용합니다.
- 크로스노드 전파 검증은 `awaitAssert` 내부에서 publish/query + `expectMsg*`를 재시도합니다.
- `expectNoMessage`는 \"추가 메시지 누출 검증\" 용도로만 짧게 사용합니다.

```java
@Test
void hello_world_roundtrip() {
    new TestKit(system) {{
        ActorRef b = system.actorOf(BActor.props());
        ActorRef a = system.actorOf(AActor.props(b, getRef()));

        a.tell(new Start(), getRef());

        expectMsgEquals(Duration.ofSeconds(2), "World");
        expectNoMessage(Duration.ofMillis(200));
    }};
}
```

## sample20 기반 강화 패턴

- 테스트 가능성을 높이기 위해 `ActorA`에 의존성(`targetB`, `reportTo`)을 생성자 주입합니다.
- 시작 트리거를 `Start` 메시지로 분리해 테스트에서 시나리오를 명시적으로 시작합니다.
- 응답 검증은 `expectMsgEquals` + `expectNoMessage`를 함께 사용해 과잉 메시지도 차단합니다.

```java
public static Props props(ActorRef targetB, ActorRef reportTo) {
    return Props.create(ActorA.class, () -> new ActorA(targetB, reportTo));
}

a.tell(new Messages.Start(), getRef());
expectMsgEquals(Duration.ofSeconds(2), "World");
expectNoMessage(Duration.ofMillis(200));
```

## 주의사항

- 테스트 종료 시 `TestKit.shutdownActorSystem(system)` 호출
- `Await.result` 같은 블로킹 방식 대신 TestKit assertion 우선
- 타이밍 민감 테스트는 `within` 사용으로 상한 시간을 고정

## 참고 문서

- [skill-maker/docs/actor/testkit/java-akka-classic-test.md](../../../../skill-maker/docs/actor/testkit/java-akka-classic-test.md)
- [plugins/skill-actor-model/skills/java-akka-classic/SKILL.md](../java-akka-classic/SKILL.md)
