# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Claude Code 스킬 플러그인 저장소. 액터 모델(Actor Model) 코드를 Java / Kotlin / C# 세 플랫폼에서 생성하는 15종의 스킬을 제공한다. 버전: 1.14.0, 라이선스: MIT.

## Skills (슬래시 명령어)

| 명령어 | 플랫폼 | 액터 베이스 |
|--------|--------|------------|
| `/java-akka-classic` | Java + Akka Classic 2.7.x | `AbstractActor` |
| `/kotlin-pekko-typed` | Kotlin + Pekko Typed 1.1.x | `AbstractBehavior<T>` |
| `/dotnet-akka-net` | C# + Akka.NET 1.5.x | `ReceiveActor` |
| `/java-akka-classic-test` | Java + Akka Classic TestKit | `TestKit` |
| `/kotlin-pekko-typed-test` | Kotlin + Pekko Typed ActorTestKit | `ActorTestKit` |
| `/dotnet-akka-net-test` | C# + Akka.TestKit.Xunit2 | `TestKit` |
| `/java-akka-classic-cluster` | Java + Akka Classic Cluster 2.7.x | `AbstractActor` + Cluster |
| `/kotlin-pekko-typed-cluster` | Kotlin + Pekko Typed Cluster 1.1.x | `AbstractBehavior<T>` + Cluster |
| `/dotnet-akka-net-cluster` | C# + Akka.NET Cluster 1.5.x | `ReceiveActor` + Cluster |
| `/java-akka-classic-infra` | Java + Akka Classic + Docker/K8s | Infra/Discovery |
| `/kotlin-pekko-typed-infra` | Kotlin + Pekko Typed + Docker/K8s | Infra/Discovery |
| `/dotnet-akka-net-infra` | C# + Akka.NET + Docker/K8s | Infra/Discovery |
| `/actor-ai-agent` | C# + Akka.NET + LLM | `ReceiveActor` + AI Pipeline |
| `/actor-ai-agent-java` | Java + Akka Classic + LLM | `AbstractActor` + AI Pipeline |
| `/actor-ai-agent-kotlin` | Kotlin + Pekko Typed + LLM | `AbstractBehavior<T>` + AI Pipeline |

플러그인 설치 시 네임스페이스 접두사: `/skill-actor-model:kotlin-pekko-typed` 형태.

## Architecture

### 이중 배포 구조

스킬 정의(SKILL.md)가 두 곳에 동일하게 존재한다:

- **`plugins/skill-actor-model/skills/`** — 플러그인 마켓플레이스 배포용 (사용자가 `/plugin install`로 설치)
- **`.claude/skills/`** — 로컬 개발용 (git clone 시 자동 인식). `.gitignore`에 의해 커밋 제외.

스킬 수정 시 `plugins/` 하위를 편집한다. 로컬 `.claude/skills/`는 개발 편의를 위한 복사본.

### 핵심 디렉토리

```
plugins/skill-actor-model/
├── .claude-plugin/plugin.json        # 플러그인 매니페스트 (name, version)
└── skills/{skill-name}/SKILL.md      # 스킬 정의 (15종)

.claude-plugin/marketplace.json       # 마켓플레이스 카탈로그 (루트)

skill-maker/
├── docs/actor/                       # 스킬 생성 시 참조한 원본 문서
│   ├── 00-actor-model-overview.md    # 액터모델 개요
│   ├── 01-java-akka-classic/         # Java Akka 패턴 가이드
│   ├── 02-kotlin-pekko-typed/        # Kotlin Pekko 패턴 가이드
│   ├── 03-dotnet-akka-net/           # C# Akka.NET 패턴 가이드
│   ├── 04-memorizer-ai-agent/        # AI Agent 아키텍처 가이드
│   └── 05-cross-platform-comparison.md
├── guides/actor-skills.md            # 스킬 활용 베스트 프랙티스
├── Skill-Guide.md                    # Claude Code 스킬 생성 가이드 (공식 문서 기반)
└── Skill-MargetPlace.md              # 플러그인 마켓플레이스 배포 가이드

skill-test/
├── TEST.md                           # 스킬 셀프 테스트 프롬프트 모음
└── projects/                         # 테스트로 생성된 코드 (.gitignore 제외)

prompt/                               # 프로젝트 작업 지침 프롬프트
```

## Critical Rules

### skill-test/projects는 코드작성 지침으로 참고 금지

`skill-test/projects/` 하위의 샘플코드는 **스킬 능력 검증용**이다. 스킬이 코드를 올바르게 생성하는지 테스트하는 목적이므로, 새 코드를 작성할 때 이 샘플을 참고하면 스킬의 독립적인 코드 생성 능력을 검증할 수 없다. **스킬 업데이트 작업 시에만** 이 규칙을 해제한다 (TEST.md의 "스킬업데이트 지침" 참조).

### 스킬 업데이트 워크플로우

1. `skill-maker/Skill-MargetPlace.md`에서 플러그인 관리 방법을 먼저 참고
2. `plugins/skill-actor-model/skills/{skill-name}/SKILL.md`를 편집
3. 기존 스킬 내용과 중복이면 업데이트하지 않음
4. `.claude-plugin/marketplace.json`의 버전도 함께 업데이트
5. `plugins/skill-actor-model/.claude-plugin/plugin.json`의 버전도 동기화

### 스킬 신규 생성 워크플로우

1. `skill-maker/Skill-Guide.md` — Claude Code 스킬 작성 규약 참조
2. `skill-maker/docs/actor/` — 플랫폼별 패턴 참조 문서로 스킬 내용 구성
3. `skill-maker/guides/actor-skills.md` — 완성 후 베스트 프랙티스 업데이트

## Development Environment

- WSL2 Linux (Ubuntu) on Windows
- Java 21 (Temurin), .NET 9.0, Kotlin + Gradle (Kotlin DSL)
- Gradle wrapper 사용 시 WSL CRLF 이슈: `sed -i 's/\r$//' gradlew` 필요
- MCP: browsermcp 설정됨 (`.mcp.json`)

## Platform Key Differences

| 항목 | Java Akka Classic | Kotlin Pekko Typed | C# Akka.NET |
|------|-------------------|-------------------|-------------|
| 타입 안전성 | 런타임 (Object) | 컴파일타임 (제네릭) | 런타임 (Object) |
| 메시지 정의 | 내부 불변 클래스 / record | sealed class 계층 | record |
| 응답 패턴 | `getSender()` 암묵적 | `replyTo: ActorRef<T>` 명시적 | `Sender` 암묵적 |
| 상태 전환 | become/unbecome | Behavior 반환 | Become/Unbecome 또는 FSM |
| 패키지 | `akka.*` | `org.apache.pekko.*` (akka와 혼동 주의) | Akka namespace |
| HOCON | `akka { }` | `pekko { }` | `akka { }` |
| 라이선스 | BSL | Apache 2.0 | Apache 2.0 |
