# skill-actor-model 프로젝트 메모리 실습

## 프로젝트 개요

Claude Code 스킬 플러그인으로, 액터 모델(Actor Model)을 Java / Kotlin / C# 세 플랫폼에서 코드 생성할 수 있는 스킬셋이다. 액터 모델 경험이 없어도 슬래시 명령어 하나로 고급 동시성 패턴을 구현할 수 있다.

## 설치 - 클로드 코드내

```
# 1. 마켓플레이스로 부터 등록
/plugin marketplace add psmon/skill-actor-model

# 2. 플러그인 설치
/plugin install skill-actor-model@actor-model-skills
```

## 코틀린 Typed 액터 코드 작성 테스트

```
/skill-actor-model:kotlin-pekko-typed skill-test/projects/sample1 하위폴터에 hello world 액터코드 작성, 코틀린 콘솔프로젝트로 수행
```

## 자바 Akka Classic 액터 코드 작성 테스트

```
/skill-actor-model:java-akka-classic skill-test/projects/sample2 하위폴터에 hello world 액터코드 작성, 자바 콘솔프로젝트로 수행
```

## 닷넷 Akka.NET 액터 코드 작성 테스트

```
/skill-actor-model:dotnet-akka-net skill-test/projects/sample3 하위폴터에 hello world 액터코드 작성, 닷넷 콘솔프로젝트로 수행
```


## 스킬 6종

| 스킬 | 명령어 | 플랫폼 | 액터 베이스 |
|------|--------|--------|------------|
| Java Akka Classic | `/java-akka-classic` | Java + Akka 2.7.x | `AbstractActor` |
| Kotlin Pekko Typed | `/kotlin-pekko-typed` | Kotlin + Pekko 1.1.x | `AbstractBehavior<T>` |
| C# Akka.NET | `/dotnet-akka-net` | C# + Akka.NET 1.5.x | `ReceiveActor` |
| AI Agent Pipeline (.NET) | `/actor-ai-agent` | C# + Akka.NET + LLM | `ReceiveActor` + AI |
| AI Agent Pipeline (Java) | `/actor-ai-agent-java` | Java + Akka Classic + LLM | `AbstractActor` + AI |
| AI Agent Pipeline (Kotlin) | `/actor-ai-agent-kotlin` | Kotlin + Pekko Typed + LLM | `AbstractBehavior<T>` + AI |

플러그인 설치 시 네임스페이스 접두사: `/skill-actor-model:kotlin-pekko-typed` 형태.

## 프로젝트 구조 핵심

```
skill-actor-model/
├── .claude-plugin/marketplace.json    # 마켓플레이스 카탈로그
├── plugins/skill-actor-model/         # 플러그인 배포본
│   ├── .claude-plugin/plugin.json
│   └── skills/                        # SKILL.md 6종 (실제 스킬 정의)
├── skill-maker/docs/actor/            # 스킬 생성 참조 문서 (패턴 가이드)
│   ├── 00-actor-model-overview.md
│   ├── 01-java-akka-classic/
│   ├── 02-kotlin-pekko-typed/
│   ├── 03-dotnet-akka-net/
│   ├── 04-memorizer-ai-agent/
│   └── 05-cross-platform-comparison.md
├── skill-test/                        # 스킬 셀프 테스트
│   ├── TEST.md                        # 테스트 프롬프트 모음
│   └── projects/sample1~3/            # 생성된 콘솔 프로젝트 (커밋 안 함)
└── README.md
```

## 스킬 테스트 결과 (이번 세션)

skill-test/TEST.md의 프롬프트를 3개 플랫폼 모두 실행하여 검증 완료:

### sample1: Kotlin + Pekko Typed 1.1.3
- 빌드: Gradle 8.7 (Kotlin DSL), Java 21
- 구조: `sealed class HelloCommand` → `AbstractBehavior<HelloCommand>` → `companion object { fun create() }`
- 응답 방식: 명시적 `replyTo: ActorRef<T>` 메시지 포함
- 패키지: `org.apache.pekko.*`, HOCON `pekko { }` 블록
- 실행 성공 확인

### sample2: Java + Akka Classic 2.7.0
- 빌드: Gradle 8.7 (Kotlin DSL), Java 17
- 구조: `HelloActor extends AbstractActor` → `receiveBuilder().match()` 패턴
- 응답 방식: 암묵적 `getSender().tell()`
- 메시지: 내부 불변 클래스 (Hello, HelloResponse)
- 실행 성공 확인

### sample3: C# + Akka.NET 1.5.60
- 빌드: dotnet CLI, .NET 9.0
- 구조: `HelloActor : ReceiveActor` → 생성자에서 `Receive<T>(handler)` 등록
- 응답 방식: `Sender.Tell()`
- 메시지: C# `record` 타입
- Top-level statements 사용 (Program.cs), 액터는 Actors/ 폴더 분리
- 실행 성공 확인

## 플랫폼별 핵심 차이

| 항목 | Java Akka Classic | Kotlin Pekko Typed | C# Akka.NET |
|------|-------------------|-------------------|-------------|
| 타입 안전성 | 런타임 (Object) | 컴파일타임 (제네릭) | 런타임 (Object) |
| 메시지 정의 | 내부 불변 클래스 | sealed class 계층 | record |
| 응답 패턴 | getSender() 암묵적 | replyTo 명시적 | Sender 암묵적 |
| 상태 전환 | become/unbecome | Behavior 반환 | Become/Unbecome 또는 FSM |
| FSM | 없음 (수동 구현) | Behavior 교체 | FSM<TState, TData> 내장 |
| 영속화 | - | DurableState (R2DBC) | ReceivePersistentActor (RavenDB) |

## 개발 환경

- WSL2 Linux (Ubuntu 계열)
- Java 21 (Temurin), .NET 9.0.303
- Gradle wrapper로 빌드 (시스템에 gradle 미설치, wrapper JAR 직접 배포)
- gradlew 실행 시 `sed -i 's/\r$//'` 줄바꿈 변환 필요 (WSL CRLF 이슈)

## 참고 프로젝트 (실제 소스 출처)

- Java: [java-labs/springweb](https://github.com/psmon/java-labs/tree/master/springweb)
- Kotlin: [kopring-reactive-labs](https://github.com/psmon/kopring-reactive-labs/tree/main/KotlinBootReactiveLabs)
- C#: [NetCoreLabs](https://github.com/psmon/NetCoreLabs)
- AI Agent: [memorizer-v1](https://github.com/psmon/memorizer-v1)

### DOC
- https://doc.akka.io/libraries/akka-core/current/index-classic.html
- https://getakka.net/articles/intro/what-is-akka.html
- https://pekko.apache.org/
