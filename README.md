# skill-actor-model

Claude Code 스킬 기반으로 **액터 모델(Actor Model)을 다양한 언어에서 학습**할 수 있는 스킬셋입니다.

동일한 동시성 패턴(Tell, Ask, Router, Supervision, Timer, Batch, Stream, Cluster 등)이 Java, Kotlin, C# 세 플랫폼에서 어떻게 구현되는지 비교하며 학습할 수 있도록 설계되었습니다.

## 스킬 목록

| 스킬 | 명령어 | 플랫폼 |
|------|--------|--------|
| Java Akka Classic | `/java-akka-classic` | Java + Akka Classic 2.7.x |
| Kotlin Pekko Typed | `/kotlin-pekko-typed` | Kotlin + Pekko Typed 1.1.x |
| C# Akka.NET | `/dotnet-akka-net` | C# + Akka.NET 1.5.x |
| AI Agent Pipeline | `/actor-ai-agent` | C# + Akka.NET + LLM |

## 설치 방법

### 방법 1: 플러그인 마켓플레이스로 설치 (권장)

Claude Code에서 아래 명령어를 순서대로 실행합니다.

```shell
# 1. 마켓플레이스 등록
/plugin marketplace add psmon/skill-actor-model

# 2. 플러그인 설치
/plugin install skill-actor-model@actor-model-skills
```

설치 후 `/java-akka-classic`, `/kotlin-pekko-typed`, `/dotnet-akka-net`, `/actor-ai-agent` 명령어를 사용할 수 있습니다.

> 플러그인을 최신 버전으로 업데이트하려면: `/plugin marketplace update`

### 방법 2: Git 저장소 클론 (로컬 개발용)

저장소를 클론하면 `.claude/skills/` 디렉토리의 스킬이 자동으로 인식됩니다.

```bash
git clone https://github.com/psmon/skill-actor-model.git
cd skill-actor-model
claude   # 스킬이 자동 로드됩니다
```

## 사용 방법

Claude Code에서 슬래시 명령어로 원하는 플랫폼의 액터 패턴 코드를 생성합니다.

```
/java-akka-classic RoundRobinPool 라우터로 5개 워커에 메시지 분배
/kotlin-pekko-typed sealed class 기반 상태 전환 액터 구현
/dotnet-akka-net FSM 배치 처리 액터, 1초 타임아웃 자동 플러시
/actor-ai-agent RAG 기반 질의분석 -> 검색 -> 평가 -> 응답 파이프라인 설계
```

플러그인으로 설치한 경우 네임스페이스가 붙습니다.

```
/skill-actor-model:java-akka-classic RoundRobinPool 라우터
/skill-actor-model:dotnet-akka-net ReceiveActor 기본 패턴
```

자연어로 질문해도 관련 스킬이 자동 활성화됩니다.

## 프로젝트 구조

```
skill-actor-model/
├── .claude-plugin/
│   └── marketplace.json               # 플러그인 마켓플레이스 카탈로그
├── plugins/skill-actor-model/         # 플러그인 배포용
│   ├── .claude-plugin/plugin.json
│   └── skills/                        # 배포되는 스킬
│       ├── java-akka-classic/SKILL.md
│       ├── kotlin-pekko-typed/SKILL.md
│       ├── dotnet-akka-net/SKILL.md
│       └── actor-ai-agent/SKILL.md
├── .claude/skills/                    # 로컬 개발용 스킬 (동일 내용)
│   ├── java-akka-classic/SKILL.md
│   ├── kotlin-pekko-typed/SKILL.md
│   ├── dotnet-akka-net/SKILL.md
│   └── actor-ai-agent/SKILL.md
├── skil-maker/
│   ├── docs/actor/                    # 플랫폼별 액터 패턴 참조 문서
│   │   ├── 00-actor-model-overview.md
│   │   ├── 01-java-akka-classic/
│   │   ├── 02-kotlin-pekko-typed/
│   │   ├── 03-dotnet-akka-net/
│   │   ├── 04-memorizer-ai-agent/
│   │   └── 05-cross-platform-comparison.md
│   └── guides/
│       └── actor-skills.md            # 스킬 활용 베스트 프랙티스
└── README.md
```

## 다루는 패턴

| 패턴 | Java | Kotlin | C# | AI Agent |
|------|:----:|:------:|:--:|:--------:|
| Tell / Ask / Forward | O | O | O | - |
| Router | O | O | O | - |
| Supervision | O | O | O | O |
| Timer | O | O | O | - |
| FSM / Batch | O | O | O | - |
| Persistence | - | O | O | - |
| Streams / Throttle | O | O | O | - |
| Cluster | O | O | - | - |
| PubSub | - | O | - | - |
| SSE / WebSocket | - | O | O | O |
| AI Pipeline | - | - | - | O |

## 참고 프로젝트

이 스킬셋의 참조 문서는 아래 실제 프로젝트의 소스코드에서 추출되었습니다.

| 프로젝트 | 플랫폼 | 링크 |
|---------|--------|------|
| springweb | Java + Akka Classic | [java-labs/springweb](https://github.com/psmon/java-labs/tree/master/springweb) |
| KotlinBootLabs | Kotlin + Akka Typed | [java-labs/KotlinBootLabs](https://github.com/psmon/java-labs/tree/master/KotlinBootLabs) |
| KotlinBootReactiveLabs | Kotlin + Pekko Typed | [kopring-reactive-labs](https://github.com/psmon/kopring-reactive-labs/tree/main/KotlinBootReactiveLabs) |
| NetCoreLabs | C# + Akka.NET | [NetCoreLabs](https://github.com/psmon/NetCoreLabs) |
| memorizer-v1 | C# + Akka.NET + AI | [memorizer-v1](https://github.com/psmon/memorizer-v1) |
