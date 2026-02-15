---
name: actor-ai-agent-java
description: Java + Akka Classic 액터 기반 AI 에이전트 파이프라인 코드를 생성합니다. Java로 오케스트레이터 액터, 단계별 파이프라인(분석/검색/평가), become 상태 전환, 비동기 LLM 호출, Fire-and-Forget 사이드 태스크 구조를 구현할 때 사용합니다.
argument-hint: "[파이프라인 요구사항]"
---

# Java Akka Classic 기반 AI 에이전트 파이프라인 스킬

Java + Akka Classic으로 AI 에이전트 파이프라인(LLM 연동)을 액터 워크플로우로 구성하는 스킬입니다.

## 참고 문서

- AI Agent 가이드: [skill-maker/docs/actor/04-memorizer-ai-agent/README.md](../../../../skill-maker/docs/actor/04-memorizer-ai-agent/README.md)
- Java Akka Classic 패턴: [skill-maker/docs/actor/01-java-akka-classic/README.md](../../../../skill-maker/docs/actor/01-java-akka-classic/README.md)

## 환경

- **프레임워크**: Akka Classic 2.7.x
- **언어**: Java 17+
- **형태**: 콘솔/서버 모두 가능

## 핵심 패턴

### 1. 오케스트레이터 + 단계 액터 분리

- `ChatBotActor`가 전체 흐름을 조정
- 단계별 액터: `QueryAnalyzerActor`, `MemorySearchActor`, `DecisionActor`
- 사용자 응답 경로와 사이드 태스크 경로를 분리

### 2. become/unbecome 상태 전환

```java
public class ChatBotActor extends AbstractActor {
    private final ActorRef analyzer;

    @Override
    public Receive createReceive() {
        return idle();
    }

    private Receive idle() {
        return receiveBuilder()
            .match(UserChatRequest.class, req -> {
                analyzer.tell(new AnalyzeQuery(req.message()), self());
                getContext().become(waitingForAnalysis(req, sender()));
            })
            .build();
    }

    private Receive waitingForAnalysis(UserChatRequest req, ActorRef replyTo) {
        return receiveBuilder()
            .match(QueryTypeAnalyzed.class, analyzed -> {
                // 다음 단계로 전환
                getContext().unbecome();
            })
            .build();
    }
}
```

### 3. 비동기 LLM 호출 (pipe 패턴)

- `CompletableFuture`로 LLM API 호출
- 완료 결과를 `pipe` 또는 self-message로 액터 메시지화
- 액터 스레드에서 블로킹 호출(`get()/join()`) 지양

### 4. Fire-and-Forget 사이드 태스크

- 메인 응답 후 `TitleActor`, `EmbeddingActor`, `GraphActor`로 비동기 이벤트 발행
- 실패해도 사용자 응답 경로에 영향 없도록 설계

### 5. 폴백 전략

- Decision 실패 시 검색 결과 전체 포함
- 검색 실패 시 메모리 없이 기본 LLM 응답
- 타임아웃 시 보수적 응답 + 에러 로깅

### 6. 콘솔 파이프라인 표준 로그 포맷 (sample16)

- 콘솔 데모 시작 시 파이프라인 헤더를 출력
- 단계 로그를 `[Analyze] -> [Search] -> [Decision] -> [Final] + [SideTask]` 형태로 고정
- 오케스트레이터는 단계별 `become()` 체인을 분리해 상태 전환 가독성을 유지

```java
System.out.println("Java Akka Classic AI Agent Pipeline (Console)");
System.out.println("[Analyze] -> [Search] -> [Decision] -> [Final] + [SideTask]");
```

## 생성 규칙

1. 오케스트레이터 액터는 반드시 상태 전환으로 단계를 분리합니다.
2. 단계 액터는 단일 책임을 유지합니다(분석/검색/평가).
3. 비동기 외부 호출은 Future 결과를 메시지로 변환해 처리합니다.
4. 사이드 태스크는 Fire-and-Forget 경로로 분리합니다.
5. 콘솔 데모에서는 단계별 로그(`[Analyze]`, `[Search]`, `[Decision]`, `[Final]`)를 명확히 출력합니다.

$ARGUMENTS
