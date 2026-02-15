---
name: actor-ai-agent-kotlin
description: Kotlin + Pekko Typed 액터 기반 AI 에이전트 파이프라인 코드를 생성합니다. Kotlin으로 오케스트레이터 액터, 단계별 파이프라인(분석/검색/평가), Behavior 상태 전환, 비동기 LLM 호출, Fire-and-Forget 사이드 태스크 구조를 구현할 때 사용합니다.
argument-hint: "[파이프라인 요구사항]"
---

# Kotlin Pekko Typed 기반 AI 에이전트 파이프라인 스킬

Kotlin + Pekko Typed로 AI 에이전트 파이프라인(LLM 연동)을 타입 안전하게 구성하는 스킬입니다.

## 참고 문서

- AI Agent 가이드: [skill-maker/docs/actor/04-memorizer-ai-agent/README.md](../../../../skill-maker/docs/actor/04-memorizer-ai-agent/README.md)
- Kotlin Pekko Typed 패턴: [skill-maker/docs/actor/02-kotlin-pekko-typed/README.md](../../../../skill-maker/docs/actor/02-kotlin-pekko-typed/README.md)

## 환경

- **프레임워크**: Apache Pekko Typed 1.1.x
- **언어**: Kotlin
- **형태**: 콘솔/서버 모두 가능

## 핵심 패턴

### 1. 오케스트레이터 + 단계 액터

- `ChatBotActor`(orchestrator) + `QueryAnalyzerActor`, `MemorySearchActor`, `DecisionActor`
- 각 단계는 `sealed class` 명령 체계로 타입 안전 메시지 처리

### 2. Behavior 상태 전환

```kotlin
private fun idle(): Behavior<ChatCommand> = Behaviors.receiveMessage { msg ->
    when (msg) {
        is UserChatRequest -> {
            analyzer.tell(AnalyzeQuery(msg.message, context.self))
            waitingForAnalysis(msg, msg.replyTo)
        }
        else -> Behaviors.same()
    }
}

private fun waitingForAnalysis(
    request: UserChatRequest,
    replyTo: ActorRef<ChatResponse>
): Behavior<ChatCommand> = Behaviors.receiveMessage { msg ->
    when (msg) {
        is QueryTypeAnalyzed -> idle()
        else -> Behaviors.same()
    }
}
```

### 3. 비동기 LLM 호출 + pipeToSelf

- 외부 비동기 결과를 `context.pipeToSelf()`로 자기 메시지로 변환
- 액터 내부 상태는 메시지 처리 시점에만 변경

### 4. Fire-and-Forget 사이드 태스크

- 응답 후 제목 생성, 임베딩 생성, 그래프 추출 작업을 별도 액터로 전달
- 메인 응답 latency와 분리

### 5. 폴백 전략

- 평가 실패 시 검색 결과 전체 사용
- 검색 실패 시 기본 응답
- 단계별 타임아웃/실패 로그를 명확히 남김

### 6. messageAdapter 브리지 패턴 (sample16)

- 하위 단계 액터 응답(`Command`)을 오케스트레이터 내부 명령(`ChatCommand`)으로 안전하게 변환
- `ctx.messageAdapter(Command::class.java) { InternalResult(it) }` 패턴으로 타입 경계를 분리
- 오케스트레이터는 `InternalResult`만 처리하여 상태 전환 로직 단순화

```kotlin
val adapter = ctx.messageAdapter(Command::class.java) { InternalResult(it) }
analyzer.tell(AnalyzeQuery(req.message, adapter))
search.tell(SearchMemory(query, adapter))
decision.tell(EvaluateRelevance(req.message, docs, adapter))
```

## 생성 규칙

1. `sealed class` 명령/응답 계층을 사용합니다.
2. 오케스트레이터는 단계별 `Behavior` 전환으로 구현합니다.
3. 비동기 호출은 `pipeToSelf` 또는 self-message 패턴으로 처리합니다.
4. 사이드 태스크는 사용자 응답 이후 Fire-and-Forget으로 분리합니다.
5. 콘솔 데모에서는 단계별 로그(`[Analyze]`, `[Search]`, `[Decision]`, `[Final]`)를 출력합니다.

$ARGUMENTS
