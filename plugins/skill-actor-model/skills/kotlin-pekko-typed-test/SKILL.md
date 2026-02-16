---
name: kotlin-pekko-typed-test
description: Kotlin + Apache Pekko Typed(1.1.x) 액터 유닛테스트를 생성합니다. ActorTestKit과 TestProbe를 사용해 replyTo 기반 상호작용, 타입 안전 메시지 검증, actor stop 검증을 작성할 때 사용합니다.
argument-hint: "[테스트대상] [시나리오]"
---

# Kotlin + Pekko Typed 테스트 스킬

Pekko Typed 1.1.x 기반 Kotlin 프로젝트의 테스트 코드를 작성할 때 사용합니다.

## 호환 버전

- Apache Pekko Typed: `1.1.x`
- 테스트 모듈: `pekko-actor-testkit-typed_2.13` (동일 버전 권장)
- 테스트 프레임워크: `JUnit 5` + Kotlin 테스트
- Kotlin: `1.9.x`

## 의존성 예시

```kotlin
val pekkoVersion = "1.1.3"

dependencies {
    implementation("org.apache.pekko:pekko-actor-typed_2.13:$pekkoVersion")

    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_2.13:$pekkoVersion")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
```

## 테스트 작성 패턴

1. `ActorTestKit.create()` 생성
2. `testKit.spawn(...)`으로 액터 생성
3. `testKit.createTestProbe<T>()`로 관찰 포인트 생성
4. `probe.expectMessage(...)`로 결과 검증
5. `@AfterEach`에서 `testKit.shutdownTestKit()`

## 권장 대기 전략 (중요)

- `Thread.sleep`를 테스트 본문에 두지 않습니다.
- 전파/형성 대기는 `TestProbe.awaitAssert(max, interval) { ... }`로 감쌉니다.
- Receptionist/Topic 전파처럼 eventually-consistent 경로는:
  - `awaitAssert` 내부에서 조회/발행을 수행하고
  - `expectMessage*`로 관찰자 기준 완료를 검증합니다.

```kotlin
@Test
fun `A sends Hello and receives World from B`() {
    val a = testKit.spawn(ActorA.create(), "a")
    val b = testKit.spawn(ActorB.create(), "b")
    val probe = testKit.createTestProbe<String>()

    a.tell(Start(b, probe.ref()))

probe.expectMessage("World")
}
```

## sample19 기반 강화 패턴

- `ACommand`/`BCommand`를 분리해 테스트 대상의 역할 경계를 명확히 합니다.
- `Start(target, reportTo)` 메시지로 의존 액터와 보고 채널을 주입합니다.
- `ActorA`는 `reportTo`를 내부 상태로 보관하고, `World` 수신 시 probe로 전달합니다.
- 이 패턴은 테스트에서 `TestProbe<String>` 하나로 최종 결과를 단정하기 쉽습니다.

```kotlin
data class Start(val target: ActorRef<BCommand>, val reportTo: ActorRef<String>) : ACommand
data class Hello(val replyTo: ActorRef<ACommand>) : BCommand

// A -> B
msg.target.tell(Hello(context.self))
// B -> A
msg.replyTo.tell(World("World"))
// A -> probe
reportTo?.tell(msg.message)
```

## 주의사항

- Typed에서는 `replyTo: ActorRef<T>`를 메시지에 명시해 검증 가능성을 높입니다.
- `Thread.sleep` 대신 `expectMessage`/`expectNoMessage`를 사용합니다.
- 테스트마다 독립적인 ActorSystem을 사용해 상태 누수를 차단합니다.

## 참고 문서

- [skill-maker/docs/actor/testkit/pekko-kotlin-typed-test.md](../../../../skill-maker/docs/actor/testkit/pekko-kotlin-typed-test.md)
- [plugins/skill-actor-model/skills/kotlin-pekko-typed/SKILL.md](../kotlin-pekko-typed/SKILL.md)
