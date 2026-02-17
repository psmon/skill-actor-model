---
name: kotlin-pekko-typed-test
description: Kotlin + Apache Pekko Typed(1.4.x) 액터 유닛테스트를 생성합니다. ActorTestKit과 TestProbe를 사용해 replyTo 기반 상호작용, 타입 안전 메시지 검증, actor stop 검증을 작성할 때 사용합니다.
argument-hint: "[테스트대상] [시나리오]"
---

# Kotlin + Pekko Typed 테스트 스킬

Pekko Typed 1.4.x 기반 Kotlin 프로젝트의 테스트 코드를 작성할 때 사용합니다.

## 호환 버전

- Apache Pekko Typed: `1.4.x`
- 테스트 모듈: `pekko-actor-testkit-typed_2.13` (동일 버전 권장)
- 테스트 프레임워크: `JUnit 5` + Kotlin 테스트
- Kotlin: `1.9.x`

## 의존성 예시

```kotlin
val pekkoVersion = "1.4.0"

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

## 마이그레이션 회귀검증 (1.1.x -> 1.4.x)

- 기존 테스트를 유지한 상태에서 신규 회귀 테스트를 추가하는 전략을 권장합니다.
- 예: Singleton 중복 실행 방지 테스트 + `Stop` 메시지 기반 graceful stop 테스트를 함께 유지.
- 클러스터형 프로젝트는 단일 노드 테스트 외에 `TwoNodeClusterTest` 같은 2노드 시나리오를 별도로 실행해 조인 회귀를 점검합니다.

## 참고 문서

- [skill-maker/docs/actor/testkit/pekko-kotlin-typed-test.md](../../../../skill-maker/docs/actor/testkit/pekko-kotlin-typed-test.md)
- [plugins/skill-actor-model/skills/kotlin-pekko-typed/SKILL.md](../kotlin-pekko-typed/SKILL.md)

## WebApplication 통합 업데이트 (2026-02-17)

- 콘솔 엔트리 중심 샘플을 웹 API 중심으로 확장할 때 아래 기본 API를 우선 제공합니다.
  - `GET /api/heath`
  - `GET /api/actor/hello`
  - `GET /api/cluster/info`
  - `POST /api/kafka/fire-event`
- Kafka 실행은 스케줄러 자동 실행보다 API 트리거 방식을 우선합니다.
- Swagger/OpenAPI와 파일 기반 로깅 구성을 기본 포함합니다.
- 플랫폼별 권장 웹 모드:
  - .NET: ASP.NET Core (.NET 10)
  - Java: Spring Boot MVC (Java 21, Spring Boot 3.5.x)
  - Kotlin: Spring WebFlux + Coroutine (Spring Boot 3.5.x)

## Web 전환 테스트 주의사항 (2026-02)

1. 기존 actor 단위 테스트를 유지한 상태에서 웹 전환으로 추가된 메시지 계약 테스트를 반드시 보강합니다.
2. `/api/actor/hello`, `/api/cluster/info`, `/api/kafka/fire-event`에 대응하는 액터 응답 테스트를 각각 둡니다.
3. Kafka 트리거는 스케줄 기반이 아닌 API 단발 실행 기준으로 테스트 시나리오를 갱신합니다.
4. 클러스터 테스트는 최소 2노드 Up 상태를 확인한 뒤 기능 테스트를 수행합니다.
5. 종료 테스트는 actor stop/coordinated shutdown 확인에 집중하고 Kafka 종료는 제외합니다.
6. 런타임 버전 제약이 있을 경우(예: net10) SDK 컨테이너 기반 테스트 경로를 같이 제공합니다.
