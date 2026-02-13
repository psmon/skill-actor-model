# Actor Model Skills 베스트 프랙티스

> Claude Code에서 액터 모델 스킬을 효과적으로 활용하는 가이드입니다.

---

## 사용 가능한 스킬 목록

| 스킬 이름 | 슬래시 명령어 | 대상 플랫폼 | 용도 |
|-----------|-------------|------------|------|
| `java-akka-classic` | `/java-akka-classic` | Java + Akka Classic 2.7.x | Akka Classic 기반 액터 코드 생성 |
| `kotlin-pekko-typed` | `/kotlin-pekko-typed` | Kotlin + Pekko Typed 1.1.x | Pekko Typed 기반 타입 안전한 액터 코드 생성 |
| `dotnet-akka-net` | `/dotnet-akka-net` | C# + Akka.NET 1.5.x | Akka.NET 기반 액터 코드 생성 |
| `actor-ai-agent` | `/actor-ai-agent` | C# + Akka.NET + LLM | AI 에이전트 파이프라인 설계 및 코드 생성 |

---

## 스킬 호출 방법

### 직접 호출 (슬래시 명령어)

```
/java-akka-classic Router 패턴으로 5개 워커에게 라운드로빈 분배하는 코드 작성
```

```
/kotlin-pekko-typed 클러스터 싱글턴 카운터 액터 구현
```

```
/dotnet-akka-net FSM 기반 배치 처리 액터 작성, 1초 타임아웃에 자동 플러시
```

```
/actor-ai-agent 사용자 질문을 분류하고 RAG 검색 후 LLM 응답을 생성하는 파이프라인 설계
```

### 자연어 호출 (자동 감지)

Claude가 대화 맥락에서 관련 스킬을 자동으로 로드합니다:

- "Java로 Akka 액터 만들어줘" → `java-akka-classic` 자동 활성화
- "Kotlin Pekko로 타입 안전한 액터 구현" → `kotlin-pekko-typed` 자동 활성화
- "C#으로 Akka.NET 라우터 설정해줘" → `dotnet-akka-net` 자동 활성화
- "액터로 AI 에이전트 파이프라인 만들어줘" → `actor-ai-agent` 자동 활성화

---

## 패턴별 스킬 선택 가이드

### 기본 메시징 (Tell/Ask/Forward)

모든 플랫폼에서 지원합니다. 플랫폼 선택 기준:

| 상황 | 추천 스킬 | 이유 |
|------|----------|------|
| 타입 안전성 최우선 | `kotlin-pekko-typed` | sealed class + ActorRef<T> |
| .NET 프로젝트 | `dotnet-akka-net` | async/await 네이티브 지원 |
| 기존 Java 프로젝트 | `java-akka-classic` | Spring Boot 통합 |

### 라우팅

```
/java-akka-classic RoundRobinPool 5개 워커로 메시지 분배
/kotlin-pekko-typed ServiceKey 기반 GroupRouter로 클러스터 내 워커 발견
/dotnet-akka-net ScatterGatherFirstCompletedPool로 가장 빠른 응답 수집
```

### 타이머/배치 처리

```
/java-akka-classic AbstractActorWithTimers로 주기적 배치 플러시 구현
/kotlin-pekko-typed Behaviors.withTimers로 상태 기반 배치 프로세서 구현
/dotnet-akka-net FSM<State, Data>로 Idle/Active 상태 전환 배치 액터 구현
```

**추천**: FSM 기반 배치가 필요하면 `dotnet-akka-net`이 가장 완성도 높은 `FSM<TState, TData>` 프레임워크를 제공합니다.

### 영속화

```
/kotlin-pekko-typed DurableStateBehavior로 현재 상태를 PostgreSQL에 저장
/dotnet-akka-net ReceivePersistentActor로 이벤트 소싱 + RavenDB 스냅샷
```

| 방식 | 스킬 | 특징 |
|------|------|------|
| DurableState | `kotlin-pekko-typed` | 현재 상태 직접 저장, 빠른 복구 |
| Event Sourcing | `dotnet-akka-net` | 모든 이벤트 저장, 이력 추적 가능 |

### 클러스터

```
/kotlin-pekko-typed ClusterSingleton + ClusterSharding 설정
/java-akka-classic 멀티 노드 클러스터 라우팅 구성
```

**추천**: 클러스터 기능이 가장 풍부한 것은 `kotlin-pekko-typed`입니다 (Singleton, Sharding, PubSub, Receptionist).

### 스트림/흐름 제어

```
/java-akka-classic Source.actorRef -> throttle -> Sink.actorRef 파이프라인
/kotlin-pekko-typed Source.queue 기반 배압 처리 + QueueOfferResult 모니터링
/dotnet-akka-net 런타임 TPS 동적 변경 가능한 Throttle 액터
```

### AI 에이전트 파이프라인

```
/actor-ai-agent RAG 기반 챗봇 파이프라인 설계
/actor-ai-agent Context.Become 워크플로우 상태 머신으로 질의분석->검색->평가->응답 구현
/actor-ai-agent SSE 스트리밍으로 AI 추론 과정 실시간 전송
```

---

## 베스트 프랙티스

### 1. 메시지 설계

**Java (Akka Classic)**
```java
// 불변 메시지 클래스 사용
public record HelloMessage(String text) {}
public record HelloResponse(String reply) {}
```

**Kotlin (Pekko Typed)**
```kotlin
// sealed class 계층으로 exhaustive 검사
sealed class Command
data class Hello(val msg: String, val replyTo: ActorRef<Command>) : Command()
data class HelloResponse(val reply: String) : Command()
```

**C# (Akka.NET)**
```csharp
// record로 불변 메시지
public record HelloMessage(string Text);
public record HelloResponse(string Reply);
```

### 2. 에러 처리 전략

| 플랫폼 | 패턴 | 적용 |
|--------|------|------|
| Java | `supervisorStrategy()` 오버라이드 | 부모 액터에서 전체 자식 감독 |
| Kotlin | `Behaviors.supervise().onFailure()` | 자식별 개별 감독 전략 |
| C# | `SupervisorStrategy()` 오버라이드 | 부모 액터에서 전체 자식 감독 |
| AI Agent | OneForOneStrategy + 3회 재시작 | LLM API 실패에 보수적 대응 |

### 3. 테스트 작성

```
/java-akka-classic TestKit으로 HelloActor 단위 테스트 작성
/kotlin-pekko-typed ActorTestKit + TestProbe로 타입 안전한 테스트 작성
/dotnet-akka-net TestKit + xUnit으로 FSM 배치 액터 테스트 작성
```

### 4. 웹 프레임워크 통합

| 플랫폼 | 프레임워크 | 통합 패턴 |
|--------|-----------|----------|
| Java/Kotlin | Spring Boot | `@Configuration` + `@Bean` + Coroutine `await()` |
| C# | ASP.NET Core | `Akka.Hosting` + `IRequiredActor<T>` DI |
| AI Agent | ASP.NET Core | `Akka.Hosting` + `Channel<T>` SSE 브릿지 |

### 5. 플랫폼 간 마이그레이션

한 플랫폼의 패턴을 다른 플랫폼으로 변환할 때:

```
# Java -> Kotlin 변환
/kotlin-pekko-typed 다음 Java Akka Classic 코드를 Pekko Typed로 변환해줘: [코드]

# Kotlin -> C# 변환
/dotnet-akka-net 다음 Kotlin Pekko Typed 코드를 Akka.NET으로 변환해줘: [코드]

# 기본 패턴 -> AI Agent
/actor-ai-agent 기존 Akka.NET ReceiveActor를 AI 파이프라인 단계로 확장해줘
```

---

## 스킬 조합 활용

복잡한 시스템에서는 여러 스킬을 조합할 수 있습니다:

### 시나리오: 멀티 플랫폼 마이크로서비스

1. `/kotlin-pekko-typed` - 클러스터 기반 메인 서비스 (API Gateway + 비즈니스 로직)
2. `/dotnet-akka-net` - AI 처리 서비스 (LLM 통합 + 영속화)
3. `/actor-ai-agent` - AI 에이전트 파이프라인 설계

### 시나리오: 레거시 마이그레이션

1. `/java-akka-classic` - 기존 시스템 분석 및 패턴 이해
2. `/kotlin-pekko-typed` - 타입 안전한 새 구현으로 변환

---

## 참고 프로젝트

| 프로젝트 | 플랫폼 | 경로 |
|---------|-------|------|
| java-labs/springweb | Java + Akka Classic | `https://github.com/psmon/java-labs/tree/master/springweb` |
| KotlinBootLabs | Kotlin + Akka Typed | `https://github.com/psmon/java-labs/tree/master/KotlinBootLabs` |
| KotlinBootReactiveLabs | Kotlin + Pekko Typed | `https://github.com/psmon/kopring-reactive-labs/tree/main/KotlinBootReactiveLabs` |
| NetCoreLabs | C# + Akka.NET | `https://github.com/psmon/NetCoreLabs` |
| memorizer-v1 | C# + Akka.NET + AI | `https://github.com/psmon/memorizer-v1` |

---

## 주의사항

1. **Akka 라이선스**: Java Akka Classic 2.7+는 BSL 라이선스입니다. 오픈소스가 필요하면 Pekko(Kotlin) 또는 Akka.NET(C#)을 선택하세요.
2. **Pekko 패키지명**: Pekko는 `org.apache.pekko.*`입니다. `akka.*`와 혼동하지 마세요. HOCON도 `pekko { }` 블록입니다.
3. **AI Agent**: `actor-ai-agent` 스킬은 Akka.NET 전용입니다. 다른 플랫폼의 AI 통합이 필요하면 해당 플랫폼 스킬과 조합하세요.
4. **직렬화**: 클러스터/영속화 사용 시 메시지 직렬화 설정을 반드시 확인하세요:
   - Java: Protobuf / Java Serialization
   - Kotlin: Jackson JSON/CBOR + `PersitenceSerializable` 마커
   - C#: Hyperion / Newtonsoft.Json
