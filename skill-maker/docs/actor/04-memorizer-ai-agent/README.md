# 04. Memorizer AI-Agent: Akka.NET 액터로 구축한 AI 처리 파이프라인

> **프로젝트**: Memorizer v1 - AI 메모리 관리 챗봇
> **기술 스택**: Akka.NET + ASP.NET Core + LLM (OpenAI) + PostgreSQL + Neo4j
> **핵심 개념**: 액터 = AI 파이프라인 스테이지, 메시지 = AI 작업 결과

---

## 목차

1. [개요: 왜 액터 모델로 AI 에이전트를 만드는가](#1-개요-왜-액터-모델로-ai-에이전트를-만드는가)
2. [전체 아키텍처](#2-전체-아키텍처)
3. [액터 파이프라인 상세](#3-액터-파이프라인-상세)
   - [3.1 ChatBotActor - 대화 관리 액터](#31-chatbotactor---대화-관리-액터-core-orchestrator)
   - [3.2 SearchMemoryActor - 메모리 검색 액터](#32-searchmemoryactor---메모리-검색-액터)
   - [3.3 DecisionActor - 결정 액터](#33-decisionactor---결정-액터-relevance-evaluation)
   - [3.4 GraphRelationshipActor - 그래프 관계 액터](#34-graphrelationshipactor---그래프-관계-액터)
   - [3.5 MetadataEmbeddingActor - 메타데이터 임베딩 액터](#35-metadataembeddingactor---메타데이터-임베딩-액터)
   - [3.6 TitleGenerationActor - 제목 생성 액터](#36-titlegenerationactor---제목-생성-액터)
   - [3.7 GraphSyncActor - 그래프 동기화 액터](#37-graphsyncactor---그래프-동기화-액터)
4. [핵심 설계 패턴](#4-핵심-설계-패턴)
5. [ASP.NET Core 통합](#5-aspnet-core-통합)
6. [실전 고려사항](#6-실전-고려사항)
7. [기존 액터 패턴과의 비교](#7-기존-액터-패턴과의-비교)

---

## 1. 개요: 왜 액터 모델로 AI 에이전트를 만드는가

AI 챗봇 시스템을 구현할 때 가장 흔한 접근 방식은 하나의 컨트롤러에서 순차적으로 LLM API를 호출하는 것이다.

```csharp
// 전형적인 (그리고 문제가 있는) 접근 방식
public async Task<string> HandleChat(string userMessage)
{
    var searchResults = await _searchService.Search(userMessage);        // 1. 검색
    var evaluation = await _llm.Evaluate(searchResults);                  // 2. 평가
    var response = await _llm.GenerateResponse(userMessage, evaluation);  // 3. 응답 생성
    await _embeddingService.UpdateEmbeddings(userMessage);                // 4. 임베딩 갱신
    await _graphService.UpdateRelationships(userMessage);                 // 5. 그래프 갱신
    return response;
}
```

이 접근 방식의 문제점:

| 문제 | 설명 |
|------|------|
| **직렬 병목** | 4번, 5번은 응답과 무관한데도 사용자가 기다려야 함 |
| **상태 관리 혼재** | 사용자 세션, 대화 이력, 검색 상태가 뒤섞임 |
| **장애 전파** | 임베딩 생성 실패가 전체 응답을 실패시킴 |
| **확장성 제한** | 동시 사용자 수만큼 스레드가 블로킹됨 |
| **복잡한 워크플로우** | 조건부 분기, 재시도, 타임아웃 처리가 코드를 복잡하게 만듦 |

**액터 모델은 이 모든 문제를 자연스럽게 해결한다.**

각 AI 작업(검색, 평가, 응답 생성, 임베딩, 그래프)을 독립된 액터로 분리하면:

- **병렬 처리**: 응답 생성과 무관한 작업(임베딩, 그래프)은 Fire-and-Forget으로 병렬 수행
- **격리된 상태**: 각 사용자 세션이 자신만의 ChatBotActor 인스턴스를 가짐
- **장애 격리**: 임베딩 액터가 실패해도 챗봇 응답에 영향 없음
- **비동기 파이프라인**: LLM 호출 결과가 메시지로 다음 액터에 전달
- **상태 기계**: `Context.Become()`으로 복잡한 AI 워크플로우를 명확하게 표현

---

## 2. 전체 아키텍처

### 시스템 구성도

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ASP.NET Core Web API                         │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  AskBotController                                            │   │
│  │  ConcurrentDictionary<string, IActorRef> sessionActors       │   │
│  │  Channel<StreamingUpdate> → SSE Stream                       │   │
│  └────────────────────────────┬─────────────────────────────────┘   │
│                               │ Ask(UserChatRequest)                │
│  ┌────────────────────────────▼─────────────────────────────────┐   │
│  │                     Akka.NET ActorSystem                      │   │
│  │                                                               │   │
│  │  ┌─────────────────────────────────────────────────────────┐  │   │
│  │  │              ChatBotActor (per session)                  │  │   │
│  │  │  State: Base → WaitingForQueryType → WaitingForSearch   │  │   │
│  │  │         → WaitingForEvaluation → Back to Base           │  │   │
│  │  │  Manages: Conversation history, Short-term memory       │  │   │
│  │  └───┬──────────┬──────────┬──────────┬──────────┬─────────┘  │   │
│  │      │          │          │          │          │             │   │
│  │      ▼          ▼          ▼          ▼          ▼             │   │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐      │   │
│  │  │Search  │ │Decision│ │Graph   │ │Metadata│ │Title   │      │   │
│  │  │Memory  │ │Actor   │ │Relation│ │Embedding│ │Genera- │      │   │
│  │  │Actor   │ │        │ │Actor   │ │Actor   │ │tion    │      │   │
│  │  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘      │   │
│  │      │          │          │          │          │             │   │
│  │      ▼          ▼          ▼          ▼          ▼             │   │
│  │  Vector DB   LLM API    Neo4j    Embedding   LLM API         │   │
│  │  (pgvector)  (OpenAI)   Graph    API         (OpenAI)         │   │
│  │                         DB       (OpenAI)                     │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  GraphSyncActor (singleton)                                   │   │
│  │  PostgreSQL → Neo4j 전체 동기화                                │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 액터 파이프라인 흐름 (대화 요청)

```
사용자 요청
    │
    ▼
ChatBotActor ──────────────────────────────────────────────────────────┐
    │                                                                  │
    │ 1. AnalyzeQueryTypeRequest                                       │
    ▼                                                                  │
SearchMemoryActor                                                      │
    │                                                                  │
    │ 2. AnalyzeQueryTypeResponse                                      │
    ▼                                                                  │
ChatBotActor (WaitingForQueryTypeAnalysis)                             │
    │                                                                  │
    │ 검색 필요한 경우:                                                  │
    │ 3. SearchMemoryRequest                                           │
    ▼                                                                  │
SearchMemoryActor                                                      │
    │  ┌─────────────────────────────┐                                 │
    │  │ 쿼리 변환 → 벡터 검색       │                                 │
    │  │ 실패 시 → 키워드 추출       │                                 │
    │  │ 재시도 (최대 3회)           │                                 │
    │  └─────────────────────────────┘                                 │
    │ 4. SearchMemoryResponse                                          │
    ▼                                                                  │
ChatBotActor (WaitingForSearchResponse)                                │
    │                                                                  │
    │ 검색 결과가 있는 경우:                                             │
    │ 5. EvaluateRelevanceRequest                                      │
    ▼                                                                  │
DecisionActor                                                          │
    │                                                                  │
    │ 6. EvaluateRelevanceResponse (관련 결과 필터링)                    │
    ▼                                                                  │
ChatBotActor (WaitingForEvaluationResponse)                            │
    │                                                                  │
    │ 7. LLM으로 최종 응답 생성 (Task.Run + self.Tell)                  │
    ▼                                                                  │
ChatBotActor (Base state 복귀) ─── 응답 반환 ──────────────────────────┘
    │
    │ Fire-and-Forget (응답과 독립적):
    ├──→ GraphRelationshipActor (관계 제안)
    ├──→ MetadataEmbeddingActor (임베딩 생성)
    └──→ TitleGenerationActor (제목 생성)
```

이 흐름에서 핵심은 **응답 경로**(1~7)와 **부수 작업**(그래프, 임베딩, 제목)이 완전히 분리된다는 점이다. 사용자는 부수 작업의 완료를 기다리지 않는다.

---

## 3. 액터 파이프라인 상세

### 3.1 ChatBotActor - 대화 관리 액터 (Core Orchestrator)

ChatBotActor는 전체 시스템의 핵심이다. 사용자 세션 하나당 하나의 ChatBotActor 인스턴스가 생성되며, 대화의 전체 생명주기를 관리한다.

#### 클래스 구조

```csharp
public class ChatBotActor : ReceiveActor, IWithTimers
{
    // 의존성
    private readonly IActorRef _searchMemoryActor;
    private readonly IActorRef _decisionActor;
    private readonly IActorRef _graphRelationshipActor;
    private readonly IActorRef _metadataEmbeddingActor;
    private readonly IActorRef _titleGenerationActor;

    // 세션 상태
    private readonly string _userId;
    private readonly List<ConversationEntry> _conversationHistory;  // 최대 10개
    private string _shortTermMemory;  // LLM이 추출한 대화 맥락 요약
    private readonly List<StreamingUpdate> _reasoningSteps;  // 추론 과정 추적

    // IWithTimers - 세션 타임아웃 관리
    public ITimerScheduler Timers { get; set; }
    private static readonly TimeSpan SessionTimeout = TimeSpan.FromDays(3);
}
```

#### Context.Become()으로 구현한 상태 기계

ChatBotActor의 가장 중요한 설계는 **`Context.Become()`을 활용한 상태 기계**이다. LLM 기반 AI 파이프라인은 본질적으로 상태 기계이며, 각 상태는 "어떤 AI 작업의 응답을 기다리는 중"을 나타낸다.

```
                    ┌──────────────────┐
                    │    Base State    │◄──────────────────────────────┐
                    │  (메시지 수신)    │                               │
                    └────────┬─────────┘                               │
                             │ UserChatRequest 수신                     │
                             ▼                                         │
                ┌────────────────────────────┐                         │
                │ WaitingForQueryTypeAnalysis │                         │
                │ (쿼리 유형 분석 대기)        │                         │
                └────────────┬───────────────┘                         │
                             │ AnalyzeQueryTypeResponse 수신            │
                             ▼                                         │
            ┌───────────────────────────────────┐                      │
            │    검색이 필요한가?                  │                      │
            ├─── Yes ───┐       ├─── No ────────┼──→ LLM 직접 응답 ────┤
            │           ▼       │               │                      │
            │  ┌───────────────────────┐        │                      │
            │  │ WaitingForSearchResp  │        │                      │
            │  │ (메모리 검색 대기)      │        │                      │
            │  └───────────┬───────────┘        │                      │
            │              │                    │                      │
            │              ▼                    │                      │
            │  ┌───────────────────────────┐    │                      │
            │  │ 검색 결과가 있는가?         │    │                      │
            │  ├── Yes ──┐  ├── No ────────┼────┼──→ LLM 직접 응답 ────┤
            │  │         ▼  │              │    │                      │
            │  │  ┌──────────────────────┐ │    │                      │
            │  │  │WaitingForEvaluation  │ │    │                      │
            │  │  │(관련성 평가 대기)      │ │    │                      │
            │  │  └──────────┬───────────┘ │    │                      │
            │  │             │             │    │                      │
            │  │             ▼             │    │                      │
            │  │  LLM 최종 응답 생성       │    │                      │
            │  │  (Task.Run + self.Tell)   │    │                      │
            │  │             │             │    │                      │
            └──┴─────────────┴─────────────┴────┴──→ Base State 복귀 ──┘
```

```csharp
public class ChatBotActor : ReceiveActor, IWithTimers
{
    public ChatBotActor(/* dependencies */)
    {
        // Base State: 일반 메시지 처리
        Receive<UserChatRequest>(HandleUserChatRequest);
        Receive<SessionTimeout>(_ => HandleSessionTimeout());
    }

    private void HandleUserChatRequest(UserChatRequest request)
    {
        var originalSender = Sender;

        // 추론 단계 기록 (SSE 스트리밍으로 클라이언트에 전송)
        _reasoningSteps.Add(new StreamingUpdate
        {
            Type = "reasoning",
            Content = "쿼리 유형을 분석하고 있습니다..."
        });

        // 검색 액터에 쿼리 유형 분석 요청
        _searchMemoryActor.Tell(new AnalyzeQueryTypeRequest
        {
            Query = request.Message,
            ConversationContext = _shortTermMemory,
            UserId = _userId
        });

        // 상태 전환: 쿼리 유형 분석 응답 대기
        Context.Become(WaitingForQueryTypeAnalysis(request, originalSender));
    }

    /// <summary>
    /// 상태: 쿼리 유형 분석 결과를 기다리는 중
    /// Context.Become()에 클로저를 전달하여 원래 요청과 발신자를 캡처한다.
    /// </summary>
    private Receive WaitingForQueryTypeAnalysis(
        UserChatRequest originalRequest,
        IActorRef originalSender)
    {
        return message =>
        {
            switch (message)
            {
                case AnalyzeQueryTypeResponse analysisResponse:
                    HandleAnalyzeQueryTypeResponseContinuation(
                        analysisResponse, originalRequest, originalSender);
                    return true;

                case UserChatRequest newRequest:
                    // 다른 요청이 들어오면 Stash하거나 "처리 중" 응답
                    Sender.Tell(new ChatBotResponse
                    {
                        Message = "이전 요청을 처리 중입니다. 잠시 후 다시 시도해주세요.",
                        IsProcessing = true
                    });
                    return true;

                case SessionTimeout:
                    HandleSessionTimeout();
                    return true;

                default:
                    return false; // 처리하지 않은 메시지
            }
        };
    }

    /// <summary>
    /// 상태: 메모리 검색 결과를 기다리는 중
    /// </summary>
    private Receive WaitingForSearchResponse(
        UserChatRequest originalRequest,
        IActorRef originalSender,
        AnalyzeQueryTypeResponse queryAnalysis)
    {
        return message =>
        {
            switch (message)
            {
                case SearchMemoryResponse searchResponse:
                    _reasoningSteps.Add(new StreamingUpdate
                    {
                        Type = "reasoning",
                        Content = $"검색 완료: {searchResponse.Results.Count}개 결과 발견"
                    });

                    if (searchResponse.Results.Any())
                    {
                        // 검색 결과가 있으면 관련성 평가 요청
                        _decisionActor.Tell(new EvaluateRelevanceRequest
                        {
                            Query = originalRequest.Message,
                            SearchResults = searchResponse.Results,
                            IsMultiTopic = queryAnalysis.IsMultiTopic
                        });

                        Context.Become(WaitingForEvaluationResponse(
                            originalRequest, originalSender, searchResponse));
                    }
                    else
                    {
                        // 검색 결과 없음 → LLM 직접 응답
                        GenerateResponseWithoutMemory(originalRequest, originalSender);
                    }
                    return true;

                default:
                    return false;
            }
        };
    }

    /// <summary>
    /// 상태: 관련성 평가 결과를 기다리는 중
    /// </summary>
    private Receive WaitingForEvaluationResponse(
        UserChatRequest originalRequest,
        IActorRef originalSender,
        SearchMemoryResponse searchResponse)
    {
        return message =>
        {
            switch (message)
            {
                case EvaluateRelevanceResponse evaluationResponse:
                    _reasoningSteps.Add(new StreamingUpdate
                    {
                        Type = "reasoning",
                        Content = $"관련 메모리 {evaluationResponse.RelevantResults.Count}개 선별 완료"
                    });

                    // 최종 응답 생성 (비동기 + self.Tell 패턴)
                    GenerateResponseWithMemory(
                        originalRequest, originalSender,
                        evaluationResponse.RelevantResults,
                        evaluationResponse.Reasoning);
                    return true;

                default:
                    return false;
            }
        };
    }
}
```

**Context.Become()이 AI 파이프라인에 적합한 이유:**

1. **클로저로 컨텍스트 전달**: 각 상태 전환 시 `originalRequest`, `originalSender`를 클로저로 캡처하여 콜백 지옥 없이 데이터를 전달한다.
2. **메시지 필터링**: 각 상태에서 해당 상태에 맞는 메시지만 처리하고, 다른 메시지는 무시하거나 "처리 중" 응답을 보낼 수 있다.
3. **명시적 워크플로우**: AI 파이프라인의 흐름이 코드 구조 자체에 드러난다.

#### 비동기 LLM 호출 패턴 (Task.Run + self.Tell)

액터 내부에서 `async/await`를 직접 사용하면 액터의 단일 스레드 보장이 깨질 수 있다. Memorizer는 **Task.Run() + self.Tell()** 패턴으로 이를 해결한다.

```csharp
private void GenerateResponseWithMemory(
    UserChatRequest request,
    IActorRef originalSender,
    List<MemorySearchResult> relevantResults,
    string reasoning)
{
    var self = Self;  // 클로저에서 사용하기 위해 캡처

    // Task.Run()으로 별도 스레드에서 LLM 호출
    Task.Run(async () =>
    {
        try
        {
            // 대화 이력 + 관련 메모리 + 단기 기억으로 프롬프트 구성
            var prompt = BuildPromptWithMemory(
                request.Message,
                _conversationHistory,
                _shortTermMemory,
                relevantResults);

            var response = await _llmService.GenerateResponseAsync(prompt);

            // 결과를 자기 자신에게 메시지로 전송
            // → 액터의 메일박스를 통해 순차적으로 처리됨
            self.Tell(new LlmResponseGenerated
            {
                Response = response,
                OriginalRequest = request,
                OriginalSender = originalSender,
                RelevantResults = relevantResults,
                Reasoning = reasoning
            });
        }
        catch (Exception ex)
        {
            // 실패도 메시지로 전달
            self.Tell(new Status.Failure(ex));
        }
    });

    // 즉시 반환 - 액터는 블로킹되지 않음
    // 다른 메시지(타임아웃 등)를 계속 처리할 수 있음
}
```

**이 패턴이 중요한 이유:**

```
[잘못된 방식] async/await 직접 사용
──────────────────────────────────
HandleMessage()
    │
    await llm.GenerateAsync()   ← 이 사이에 다른 메시지가 처리될 수 있음!
    │                              (액터의 상태 일관성이 깨짐)
    continue processing...

[올바른 방식] Task.Run + self.Tell
──────────────────────────────────
HandleMessage()
    │
    Task.Run(async => {
        var result = await llm.GenerateAsync();
        self.Tell(result);        ← 결과가 메일박스에 들어감
    });
    │
    return; (즉시 반환)
    │
    ... 다른 메시지 처리 가능 ...
    │
HandleLlmResult()                ← 메일박스에서 순서대로 처리
    │
    상태 업데이트 (안전!)
```

> **참고**: Akka.NET의 `ReceiveAsync`를 사용하면 `async/await`가 지원되지만, `Context.Become()`과 조합할 때는 `Task.Run() + self.Tell()` 패턴이 더 명확하고 안전하다. ChatBotActor에서는 상태 기계가 핵심이므로 이 패턴을 선택한 것이다.

#### 대화 컨텍스트 관리

ChatBotActor는 두 가지 수준의 메모리를 유지한다:

```csharp
// 1. 명시적 대화 이력 (최근 10개 턴)
private readonly List<ConversationEntry> _conversationHistory = new();
private const int MaxConversationEntries = 10;

// 2. LLM이 추출한 단기 기억 (대화 맥락 요약)
private string _shortTermMemory = "";

private async Task UpdateConversationContext(string userMessage, string botResponse)
{
    // 대화 이력 추가 (원형 버퍼처럼 동작)
    _conversationHistory.Add(new ConversationEntry
    {
        UserMessage = userMessage,
        BotResponse = botResponse,
        Timestamp = DateTime.UtcNow
    });

    // 최대 10개 유지
    while (_conversationHistory.Count > MaxConversationEntries)
    {
        _conversationHistory.RemoveAt(0);
    }

    // LLM으로 대화 맥락 요약 갱신
    // "이 대화에서 지금까지 어떤 주제가 논의되었는가?"
    _shortTermMemory = await _llmService.ExtractConversationContext(
        _conversationHistory, _shortTermMemory);
}
```

이 설계에서 `_shortTermMemory`는 단순한 이력이 아니라, LLM이 능동적으로 추출한 **대화 맥락 요약**이다. 예를 들어 사용자가 "아까 말한 그 책"이라고 하면, `_shortTermMemory`에 "사용자가 '사피엔스' 책에 대해 논의함"이 포함되어 있어 맥락을 유지할 수 있다.

#### 멀티모달 지원

```csharp
// 이미지 + 텍스트 동시 분석
private void HandleUserChatRequest(UserChatRequest request)
{
    if (request.HasImage)
    {
        // 이미지가 포함된 경우: 이미지 분석 → 텍스트와 결합
        var imageDescription = await _llmService.AnalyzeImage(
            request.ImageData,
            request.Message  // "이 이미지에서 무엇이 보이나요?"
        );

        // 이미지 설명을 쿼리에 포함하여 파이프라인 진행
        request = request with
        {
            EnrichedMessage = $"{request.Message}\n[이미지 분석 결과: {imageDescription}]"
        };
    }

    // 이후 일반 파이프라인과 동일하게 진행
    _searchMemoryActor.Tell(new AnalyzeQueryTypeRequest { ... });
    Context.Become(WaitingForQueryTypeAnalysis(request, Sender));
}
```

#### 세션 타임아웃 (IWithTimers)

```csharp
public class ChatBotActor : ReceiveActor, IWithTimers
{
    public ITimerScheduler Timers { get; set; }

    private static readonly TimeSpan SessionTimeout = TimeSpan.FromDays(3);

    protected override void PreStart()
    {
        base.PreStart();
        ResetSessionTimer();
    }

    private void ResetSessionTimer()
    {
        // 타이머를 리셋 - 매 메시지 수신 시 호출
        Timers.StartSingleTimer(
            "session-timeout",          // 타이머 키 (동일 키로 재등록하면 이전 것 취소)
            new SessionTimeout(),       // 타임아웃 시 전송할 메시지
            SessionTimeout              // 3일
        );
    }

    private void HandleSessionTimeout()
    {
        _log.Info($"세션 타임아웃: {_userId}");

        // 대화 이력을 영구 저장소에 기록
        PersistConversationHistory();

        // 자기 자신을 종료
        Context.Stop(Self);
    }
}
```

#### 추론 과정 추적 (Reasoning Steps)

사용자에게 AI의 "사고 과정"을 투명하게 보여주기 위해, 파이프라인의 각 단계를 기록한다.

```csharp
private readonly List<StreamingUpdate> _reasoningSteps = new();

// 파이프라인 각 단계에서 추론 과정을 기록
_reasoningSteps.Add(new StreamingUpdate
{
    Type = "reasoning",
    Content = "쿼리 유형을 분석하고 있습니다...",
    Timestamp = DateTime.UtcNow
});

_reasoningSteps.Add(new StreamingUpdate
{
    Type = "reasoning",
    Content = "3개의 관련 메모리를 찾았습니다. 관련성을 평가하고 있습니다...",
    Timestamp = DateTime.UtcNow
});

// SSE를 통해 실시간으로 클라이언트에 전송
// → 사용자는 "AI가 어떤 단계를 거치고 있는지" 실시간으로 확인
```

---

### 3.2 SearchMemoryActor - 메모리 검색 액터

SearchMemoryActor는 사용자 쿼리를 분석하고, 저장된 메모리에서 관련 정보를 검색하는 역할을 한다. 단순한 검색 서비스가 아니라, **LLM을 활용한 지능형 검색 최적화**를 수행한다.

#### 핵심 기능

```csharp
public class SearchMemoryActor : ReceiveActor
{
    private readonly ILlmService _llmService;
    private readonly IMemoryRepository _memoryRepository;
    private readonly IEmbeddingService _embeddingService;

    public SearchMemoryActor(/* dependencies */)
    {
        // ReceiveAsync: 내부에서 async/await 직접 사용
        // (상태 기계가 불필요한 경우 ReceiveAsync가 더 간결)
        ReceiveAsync<SearchMemoryRequest>(HandleSearchMemoryRequest);
        ReceiveAsync<AnalyzeQueryTypeRequest>(HandleAnalyzeQueryTypeRequest);
        ReceiveAsync<MultiTopicSearchRequest>(HandleMultiTopicSearchRequest);
    }
}
```

> **설계 선택**: SearchMemoryActor는 `Context.Become()`을 사용하지 않고 `ReceiveAsync`를 사용한다. 이유는 (1) 검색 요청 간에 상태 전환이 필요 없고, (2) 각 요청이 독립적이며, (3) 검색 로직 자체가 순차적 `async` 흐름이기 때문이다.

#### 쿼리 유형 분석

```csharp
private async Task HandleAnalyzeQueryTypeRequest(AnalyzeQueryTypeRequest request)
{
    // LLM으로 쿼리 유형 분석
    // - 검색이 필요한 질문인가? (예: "어제 저장한 레시피 알려줘")
    // - 일반 대화인가? (예: "안녕하세요")
    // - 여러 주제를 다루는 복합 쿼리인가?
    var analysisPrompt = $"""
        사용자 쿼리를 분석하세요:
        쿼리: {request.Query}
        대화 맥락: {request.ConversationContext}

        다음 형식으로 응답하세요:
        NEEDS_SEARCH: true/false
        IS_MULTI_TOPIC: true/false
        TOPIC_COUNT: 1-3
        TOPICS: [주제1, 주제2, ...]
        REASONING: 판단 근거
        """;

    var analysis = await _llmService.AnalyzeAsync(analysisPrompt);
    var response = ParseAnalysisResponse(analysis);

    // 결과를 발신자(ChatBotActor)에게 전송
    Sender.Tell(response);
}
```

#### 다중 주제 검색

하나의 질문이 여러 주제를 포함할 수 있다. 예: "지난주 회의 내용이랑 오늘 할일 목록 보여줘"

```csharp
private async Task HandleMultiTopicSearchRequest(MultiTopicSearchRequest request)
{
    var allResults = new List<MemorySearchResult>();

    // 각 주제별로 독립적으로 검색
    foreach (var topic in request.Topics)  // 최대 3개 주제
    {
        var topicResults = await SearchForTopic(topic, request.UserId);
        allResults.AddRange(topicResults);
    }

    // 중복 제거 후 결과 반환
    var deduplicated = allResults
        .GroupBy(r => r.MemoryId)
        .Select(g => g.OrderByDescending(r => r.Score).First())
        .ToList();

    Sender.Tell(new SearchMemoryResponse { Results = deduplicated });
}
```

#### 검색 재시도 전략

```
검색 파이프라인:
┌──────────────────────────────┐
│ 1. LLM 쿼리 변환             │  "어제 저장한 그 레시피"
│    (한국어 → 검색 최적화)     │  → "레시피 cooking recipe 2024-01"
└──────────────┬───────────────┘
               ▼
┌──────────────────────────────┐
│ 2. 벡터 임베딩 검색           │  pgvector cosine similarity
│    (Embedding Search)         │
└──────────────┬───────────────┘
               │
               ├── 결과 있음 → 반환
               │
               ▼ 결과 없음
┌──────────────────────────────┐
│ 3. LLM 키워드 추출           │  핵심 키워드 추출
│    (Keyword Extraction)       │
└──────────────┬───────────────┘
               ▼
┌──────────────────────────────┐
│ 4. 키워드 기반 검색           │  full-text search fallback
│    (Keyword Search)           │
└──────────────┬───────────────┘
               │
               ├── 결과 있음 → 반환
               │
               ▼ 결과 없음 (재시도 < 3)
┌──────────────────────────────┐
│ 5. 쿼리 재구성               │  LLM으로 다른 검색어 생성
│    (Query Reformulation)      │
└──────────────┬───────────────┘
               │
               └── 2번으로 돌아감 (최대 3회 재시도)
```

```csharp
private async Task<List<MemorySearchResult>> SearchWithRetry(
    string query, string userId, int maxRetries = 3)
{
    var currentQuery = query;

    for (int attempt = 0; attempt < maxRetries; attempt++)
    {
        // 1단계: 쿼리 변환 (한국어 + 영어 이중 변환)
        var transformedQuery = await TransformQuery(currentQuery);

        // 2단계: 벡터 임베딩 검색
        var embedding = await _embeddingService.GenerateEmbeddingAsync(transformedQuery);
        var results = await _memoryRepository.SearchByVectorAsync(
            userId, embedding, topK: 10, minScore: 0.7);

        if (results.Any())
            return results;

        // 3단계: 키워드 추출 후 텍스트 검색 (폴백)
        var keywords = await ExtractKeywords(currentQuery);
        results = await _memoryRepository.SearchByKeywordsAsync(userId, keywords);

        if (results.Any())
            return results;

        // 4단계: 쿼리 재구성 후 재시도
        currentQuery = await ReformulateQuery(query, attempt);
    }

    return new List<MemorySearchResult>();  // 모든 재시도 실패
}
```

#### 이중 언어 지원

```csharp
private async Task<string> TransformQuery(string originalQuery)
{
    // 한국어 쿼리를 검색에 최적화된 형태로 변환
    // + 영어 키워드도 함께 생성 (영어로 저장된 메모리 검색을 위해)
    var prompt = $"""
        다음 쿼리를 벡터 검색에 최적화하세요:
        원본: {originalQuery}

        규칙:
        1. 핵심 의미를 유지하면서 검색에 적합한 형태로 변환
        2. 한국어와 영어 키워드를 모두 포함
        3. 대명사("그것", "아까 그")를 구체적 명사로 교체

        출력: 최적화된 검색 쿼리 (한 줄)
        """;

    return await _llmService.GenerateAsync(prompt);
}
```

---

### 3.3 DecisionActor - 결정 액터 (Relevance Evaluation)

DecisionActor는 검색 결과의 **관련성을 LLM으로 평가**하는 역할을 한다. 벡터 검색의 유사도 점수만으로는 실제 관련성을 정확히 판단할 수 없기 때문에, LLM이 의미적 관련성을 평가한다.

#### 구조화된 LLM 출력 파싱

```csharp
public class DecisionActor : ReceiveActor
{
    public DecisionActor(ILlmService llmService)
    {
        ReceiveAsync<EvaluateRelevanceRequest>(async request =>
        {
            try
            {
                var evaluation = await EvaluateRelevance(request);
                Sender.Tell(evaluation);
            }
            catch (Exception ex)
            {
                // 오류 시 보수적 접근: 모든 결과를 관련 있다고 판단
                // → 사용자에게 더 많은 정보를 제공하는 것이 누락보다 나음
                _log.Warning(ex, "관련성 평가 실패. 모든 결과를 포함합니다.");
                Sender.Tell(new EvaluateRelevanceResponse
                {
                    RelevantResults = request.SearchResults,  // 전부 포함
                    Reasoning = "평가 중 오류 발생. 모든 검색 결과를 포함합니다."
                });
            }
        });
    }

    private async Task<EvaluateRelevanceResponse> EvaluateRelevance(
        EvaluateRelevanceRequest request)
    {
        // 단일 주제 vs 다중 주제에 따라 다른 프롬프트
        var prompt = request.IsMultiTopic
            ? BuildMultiTopicEvaluationPrompt(request)
            : BuildSingleTopicEvaluationPrompt(request);

        var llmResponse = await _llmService.GenerateAsync(prompt);

        // 구조화된 출력 파싱
        // LLM 응답 형식:
        // RELEVANT: true
        // REASONING: 검색 결과 #1, #3이 사용자의 레시피 질문과 직접 관련됩니다.
        // RELEVANT_IDS: 1, 3
        return ParseEvaluationResponse(llmResponse, request.SearchResults);
    }

    private EvaluateRelevanceResponse ParseEvaluationResponse(
        string llmResponse, List<MemorySearchResult> allResults)
    {
        var lines = llmResponse.Split('\n', StringSplitOptions.RemoveEmptyEntries);
        var response = new EvaluateRelevanceResponse();

        foreach (var line in lines)
        {
            if (line.StartsWith("RELEVANT:"))
            {
                response.IsRelevant = line.Contains("true", StringComparison.OrdinalIgnoreCase);
            }
            else if (line.StartsWith("REASONING:"))
            {
                response.Reasoning = line.Substring("REASONING:".Length).Trim();
            }
            else if (line.StartsWith("RELEVANT_IDS:"))
            {
                var ids = line.Substring("RELEVANT_IDS:".Length)
                    .Split(',', StringSplitOptions.RemoveEmptyEntries)
                    .Select(id => int.TryParse(id.Trim(), out var n) ? n : -1)
                    .Where(n => n >= 0)
                    .ToList();

                response.RelevantResults = allResults
                    .Where((_, index) => ids.Contains(index))
                    .ToList();
            }
        }

        // 파싱 실패 시 안전 장치
        if (!response.RelevantResults.Any() && response.IsRelevant)
        {
            response.RelevantResults = allResults;  // 전부 포함
        }

        return response;
    }
}
```

**오류-안전 설계(Error-Safe Fallback)의 원칙:**

AI 파이프라인에서 "평가 실패 = 결과 누락"이 되면 사용자 경험이 크게 나빠진다. DecisionActor는 실패 시 **보수적으로 모든 결과를 포함**하는 전략을 취한다. 약간의 노이즈가 있더라도 관련 정보를 놓치는 것보다 낫기 때문이다.

---

### 3.4 GraphRelationshipActor - 그래프 관계 액터

GraphRelationshipActor는 메모리 간의 **지식 그래프 관계**를 관리한다. 새 메모리가 저장될 때 기존 메모리와의 관계를 LLM이 제안하고, Neo4j 그래프 데이터베이스에 저장한다.

```csharp
public class GraphRelationshipActor : ReceiveActor
{
    private readonly ILlmService _llmService;
    private readonly IGraphSyncService _graphSyncService;
    private readonly double MinConfidence = 0.7;  // 최소 신뢰도 임계값

    public GraphRelationshipActor(/* dependencies */)
    {
        ReceiveAsync<SuggestRelationshipsRequest>(HandleSuggestRelationships);
        ReceiveAsync<BatchProcessMemories>(HandleBatchProcess);
    }

    private async Task HandleSuggestRelationships(SuggestRelationshipsRequest request)
    {
        // LLM으로 관계 제안
        var prompt = $"""
            새 메모리와 기존 메모리 사이의 관계를 분석하세요:

            새 메모리:
            제목: {request.NewMemory.Title}
            내용: {request.NewMemory.Content}

            기존 메모리 목록:
            {FormatExistingMemories(request.ExistingMemories)}

            다음 형식으로 관계를 제안하세요:
            RELATION: [새메모리ID] -> [기존메모리ID] | [관계유형] | [신뢰도 0.0-1.0]
            관계유형: RELATED_TO, DERIVED_FROM, CONTRADICTS, SUPPORTS, PART_OF
            """;

        var llmResponse = await _llmService.GenerateAsync(prompt);
        var suggestions = ParseRelationshipSuggestions(llmResponse);

        // 신뢰도 임계값 필터링
        var highConfidence = suggestions
            .Where(s => s.Confidence >= MinConfidence)
            .ToList();

        // Neo4j에 관계 생성
        foreach (var suggestion in highConfidence)
        {
            await _graphSyncService.CreateRelationshipAsync(
                suggestion.SourceId,
                suggestion.TargetId,
                suggestion.RelationType,
                suggestion.Confidence);
        }

        Sender.Tell(new SuggestRelationshipsResponse
        {
            CreatedRelationships = highConfidence.Count,
            Suggestions = highConfidence
        });
    }

    /// <summary>
    /// 배치 처리: 여러 메모리의 관계를 한 번에 분석
    /// MetadataEmbeddingActor 완료 후 또는 GraphSyncActor에서 호출
    /// </summary>
    private async Task HandleBatchProcess(BatchProcessMemories request)
    {
        foreach (var memory in request.Memories)
        {
            var relatedMemories = await _graphSyncService.FindNearbyNodesAsync(
                memory.Id, maxDistance: 2);

            if (relatedMemories.Any())
            {
                Self.Tell(new SuggestRelationshipsRequest
                {
                    NewMemory = memory,
                    ExistingMemories = relatedMemories
                });
            }
        }
    }
}
```

---

### 3.5 MetadataEmbeddingActor - 메타데이터 임베딩 액터

MetadataEmbeddingActor는 **대량의 메모리에 대해 임베딩 벡터를 생성**하는 배치 작업을 담당한다.

#### 배치 처리 상태 관리

```csharp
public class MetadataEmbeddingActor : ReceiveActor
{
    // 배치 작업 상태 추적
    private class BatchState
    {
        public int TotalPages { get; set; }
        public int CurrentPage { get; set; }
        public int Outstanding { get; set; }  // 처리 중인 항목 수
        public int SuccessCount { get; set; }
        public int FailureCount { get; set; }
        public DateTime StartTime { get; set; }
    }

    private BatchState _currentBatch;

    public MetadataEmbeddingActor(
        IEmbeddingService embeddingService,
        IMemoryRepository memoryRepository)
    {
        ReceiveAsync<StartBatchEmbedding>(HandleStartBatch);
        ReceiveAsync<ProcessNextPage>(HandleProcessNextPage);
        Receive<EmbeddingPageCompleted>(HandlePageCompleted);
    }

    private async Task HandleStartBatch(StartBatchEmbedding request)
    {
        var totalMemories = await _memoryRepository.CountMemoriesNeedingEmbedding(
            request.UserId);

        _currentBatch = new BatchState
        {
            TotalPages = (int)Math.Ceiling(totalMemories / (double)PageSize),
            CurrentPage = 0,
            Outstanding = 0,
            SuccessCount = 0,
            FailureCount = 0,
            StartTime = DateTime.UtcNow
        };

        // 첫 페이지 처리 시작
        Self.Tell(new ProcessNextPage());
    }

    private async Task HandleProcessNextPage(ProcessNextPage _)
    {
        if (_currentBatch.CurrentPage >= _currentBatch.TotalPages)
        {
            // 모든 페이지 처리 완료
            PublishBatchCompleted();
            return;
        }

        var memories = await _memoryRepository.GetMemoriesPage(
            _currentBatch.CurrentPage, PageSize);

        foreach (var memory in memories)
        {
            _currentBatch.Outstanding++;

            try
            {
                // 이중 임베딩 생성
                // 1. 메타데이터 임베딩: 제목 + 태그 (빠른 검색용)
                var metadataText = $"{memory.Title} {string.Join(" ", memory.Tags)}";
                var metadataEmbedding = await _embeddingService.GenerateEmbeddingAsync(
                    metadataText);

                // 2. 전체 콘텐츠 임베딩: 제목 + 본문 (정밀 검색용)
                var contentText = $"{memory.Title}\n{memory.Content}";
                var contentEmbedding = await _embeddingService.GenerateEmbeddingAsync(
                    contentText);

                await _memoryRepository.UpdateEmbeddingsAsync(
                    memory.Id, metadataEmbedding, contentEmbedding);

                _currentBatch.SuccessCount++;
            }
            catch (Exception ex)
            {
                _log.Warning(ex, $"메모리 {memory.Id} 임베딩 생성 실패");
                _currentBatch.FailureCount++;
            }
            finally
            {
                _currentBatch.Outstanding--;
            }
        }

        _currentBatch.CurrentPage++;

        // 다음 페이지 처리 (자기 자신에게 메시지)
        Self.Tell(new ProcessNextPage());
    }

    private void PublishBatchCompleted()
    {
        var elapsed = DateTime.UtcNow - _currentBatch.StartTime;

        // EventStream으로 배치 완료 이벤트 게시
        // → 관심 있는 다른 액터들이 구독하여 수신
        Context.System.EventStream.Publish(new BatchMetadataEmbeddingCompleted
        {
            TotalProcessed = _currentBatch.SuccessCount + _currentBatch.FailureCount,
            SuccessCount = _currentBatch.SuccessCount,
            FailureCount = _currentBatch.FailureCount,
            ElapsedTime = elapsed
        });

        _log.Info($"배치 임베딩 완료: 성공={_currentBatch.SuccessCount}, " +
                  $"실패={_currentBatch.FailureCount}, 소요={elapsed.TotalSeconds:F1}초");
    }
}
```

**이중 임베딩 전략:**

```
메타데이터 임베딩 (metadata_embedding)
├── 입력: "제목: 파스타 레시피 | 태그: 요리, 이탈리안, 저녁식사"
├── 용도: 빠른 주제 매칭
└── 장점: 짧은 텍스트 → 빠른 생성 + 명확한 의미

전체 콘텐츠 임베딩 (content_embedding)
├── 입력: "파스타 레시피\n재료: 파스타 200g, 올리브오일 2스푼..."
├── 용도: 상세 내용 매칭
└── 장점: 세부 정보 포함 → 정밀한 유사도 검색
```

검색 시 두 임베딩을 가중치로 결합하여 사용한다:

```sql
-- PostgreSQL pgvector 쿼리 예시
SELECT id, title,
    (0.3 * (metadata_embedding <=> query_embedding) +
     0.7 * (content_embedding <=> query_embedding)) AS combined_score
FROM memories
WHERE user_id = @userId
ORDER BY combined_score ASC
LIMIT 10;
```

---

### 3.6 TitleGenerationActor - 제목 생성 액터

TitleGenerationActor는 제목이 없는 메모리에 LLM을 사용하여 자동으로 제목을 생성한다.

#### Self-Message 패턴을 이용한 순차 처리

```csharp
public class TitleGenerationActor : ReceiveActor
{
    private int _processedCount;
    private int _totalToProcess;

    public TitleGenerationActor(ILlmService llmService, IMemoryRepository repository)
    {
        ReceiveAsync<StartTitleGeneration>(HandleStartTitleGeneration);
        ReceiveAsync<GenerateNextTitle>(HandleGenerateNextTitle);
    }

    private async Task HandleStartTitleGeneration(StartTitleGeneration request)
    {
        var untitledMemories = await _repository.GetUntitledMemoriesAsync(request.UserId);

        _totalToProcess = untitledMemories.Count;
        _processedCount = 0;

        if (_totalToProcess == 0)
        {
            Context.System.EventStream.Publish(new TitleGenerationCompleted
            {
                TotalProcessed = 0,
                Message = "제목 생성이 필요한 메모리가 없습니다."
            });
            return;
        }

        // 첫 번째 항목 처리 시작
        Self.Tell(new GenerateNextTitle
        {
            Memories = untitledMemories,
            CurrentIndex = 0
        });
    }

    private async Task HandleGenerateNextTitle(GenerateNextTitle request)
    {
        if (request.CurrentIndex >= request.Memories.Count)
        {
            // 모든 항목 처리 완료
            Context.System.EventStream.Publish(new TitleGenerationCompleted
            {
                TotalProcessed = _processedCount,
                Message = $"{_processedCount}개의 제목이 생성되었습니다."
            });
            return;
        }

        var memory = request.Memories[request.CurrentIndex];

        try
        {
            var title = await _llmService.GenerateAsync($"""
                다음 메모 내용을 읽고 간결한 제목을 생성하세요 (20자 이내):
                {memory.Content.Substring(0, Math.Min(500, memory.Content.Length))}

                제목:
                """);

            await _repository.UpdateTitleAsync(memory.Id, title.Trim());
            _processedCount++;
        }
        catch (Exception ex)
        {
            _log.Warning(ex, $"메모리 {memory.Id} 제목 생성 실패");
            Context.System.EventStream.Publish(new TitleGenerationFailed
            {
                MemoryId = memory.Id,
                Error = ex.Message
            });
        }

        // 다음 항목 처리 (Self-Message 패턴)
        // → 메일박스를 통해 순차적으로 처리되므로
        //   다른 메시지(취소 등)를 중간에 처리할 수 있음
        Self.Tell(new GenerateNextTitle
        {
            Memories = request.Memories,
            CurrentIndex = request.CurrentIndex + 1
        });
    }
}
```

**Self-Message 패턴의 장점:**

단순한 `for` 루프 대신 Self-Message 패턴을 사용하는 이유:

```
[for 루프 방식]                    [Self-Message 방식]
HandleBatch()                      HandleNext()
  for (i = 0; i < N; i++)           Process(item[i])
    await Process(item[i])           Self.Tell(HandleNext(i+1))
  // 루프 중간에                     // Self.Tell이 메일박스에 들어감
  // 다른 메시지 처리 불가!           // → 사이에 다른 메시지 처리 가능!
                                     // 예: CancelBatch, StatusQuery 등
```

---

### 3.7 GraphSyncActor - 그래프 동기화 액터

GraphSyncActor는 PostgreSQL에 저장된 메모리 데이터를 Neo4j 그래프 데이터베이스로 **전체 동기화**하는 역할을 한다.

```csharp
public class GraphSyncActor : ReceiveActor
{
    private readonly IMemoryRepository _memoryRepository;
    private readonly IGraphSyncService _graphSyncService;
    private readonly IActorRef _graphRelationshipActor;

    public GraphSyncActor(/* dependencies */)
    {
        ReceiveAsync<InitializeGraphSchema>(HandleInitializeSchema);
        ReceiveAsync<StartFullSync>(HandleStartFullSync);
        ReceiveAsync<SyncNextPage>(HandleSyncNextPage);
    }

    /// <summary>
    /// Neo4j 스키마 초기화: 인덱스 및 제약 조건 생성
    /// </summary>
    private async Task HandleInitializeSchema(InitializeGraphSchema _)
    {
        await _graphSyncService.EnsureSchemaAsync();
        // 인덱스: Memory(id), Memory(userId)
        // 제약: Memory.id UNIQUE
        _log.Info("그래프 스키마 초기화 완료");
    }

    /// <summary>
    /// PostgreSQL → Neo4j 전체 동기화 시작
    /// </summary>
    private async Task HandleStartFullSync(StartFullSync request)
    {
        var totalCount = await _memoryRepository.CountAllMemoriesAsync(request.UserId);
        var totalPages = (int)Math.Ceiling(totalCount / (double)PageSize);

        _log.Info($"전체 동기화 시작: {totalCount}개 메모리, {totalPages}페이지");

        Self.Tell(new SyncNextPage
        {
            UserId = request.UserId,
            CurrentPage = 0,
            TotalPages = totalPages
        });
    }

    private async Task HandleSyncNextPage(SyncNextPage request)
    {
        if (request.CurrentPage >= request.TotalPages)
        {
            _log.Info("전체 동기화 완료");
            return;
        }

        var memories = await _memoryRepository.GetMemoriesPageAsync(
            request.UserId, request.CurrentPage, PageSize);

        foreach (var memory in memories)
        {
            // 1. Neo4j에 노드 생성/업데이트
            await _graphSyncService.UpsertMemoryNodeAsync(memory);

            // 2. 관계 제안 요청 (GraphRelationshipActor에 위임)
            _graphRelationshipActor.Tell(new SuggestRelationshipsRequest
            {
                NewMemory = memory,
                ExistingMemories = await _graphSyncService.FindNearbyNodesAsync(
                    memory.Id, maxDistance: 2)
            });
        }

        // 다음 페이지 처리
        Self.Tell(new SyncNextPage
        {
            UserId = request.UserId,
            CurrentPage = request.CurrentPage + 1,
            TotalPages = request.TotalPages
        });
    }
}
```

---

## 4. 핵심 설계 패턴

### 4.1 AI-Agent에서 액터 모델이 유용한 이유

| 액터 모델 특성 | AI-Agent에서의 활용 |
|---------------|-------------------|
| **격리된 상태** | 각 사용자 세션이 독립된 ChatBotActor 인스턴스로 관리됨. 대화 이력, 단기 기억이 스레드 안전하게 격리됨 |
| **메시지 기반 비동기** | LLM API 호출 결과가 메시지로 자연스럽게 다음 단계에 전달. `await`의 콜백 지옥 없이 파이프라인 구성 |
| **Supervision** | LLM API 실패, 네트워크 오류 시 자동 복구. 개별 액터 실패가 전체 시스템에 전파되지 않음 |
| **Context.Become()** | 복잡한 AI 워크플로우(쿼리분석 → 검색 → 평가 → 응답)를 명시적 상태 전환으로 표현 |
| **Fire-and-Forget** | 응답과 무관한 부수 작업(임베딩, 그래프, 제목)을 비블로킹으로 병렬 수행 |
| **EventStream** | 배치 작업 완료 등의 이벤트를 관심 있는 구독자에게 비동기 전달 |
| **Timer (IWithTimers)** | 세션 타임아웃, LLM 호출 타임아웃 등 시간 기반 로직을 선언적으로 관리 |

### 4.2 메시지 설계: AI 작업의 입력과 출력

각 메시지는 AI 파이프라인의 **입력(Request)과 출력(Response)** 쌍으로 설계된다.

```csharp
// 요청 메시지: 다음 단계에 필요한 모든 정보를 포함
public record AnalyzeQueryTypeRequest
{
    public string Query { get; init; }
    public string ConversationContext { get; init; }
    public string UserId { get; init; }
}

// 응답 메시지: 판단 결과 + 근거 + 다음 단계 결정에 필요한 정보
public record AnalyzeQueryTypeResponse
{
    public bool NeedsSearch { get; init; }
    public bool IsMultiTopic { get; init; }
    public int TopicCount { get; init; }
    public List<string> Topics { get; init; }
    public string Reasoning { get; init; }  // LLM의 판단 근거
}

// 검색 요청/응답
public record SearchMemoryRequest
{
    public string Query { get; init; }
    public string TransformedQuery { get; init; }
    public string UserId { get; init; }
    public bool IsMultiTopic { get; init; }
    public List<string> Topics { get; init; }
}

public record SearchMemoryResponse
{
    public List<MemorySearchResult> Results { get; init; }
    public string SearchStrategy { get; init; }  // "vector" | "keyword" | "hybrid"
    public int RetryCount { get; init; }
}

// 평가 요청/응답
public record EvaluateRelevanceRequest
{
    public string Query { get; init; }
    public List<MemorySearchResult> SearchResults { get; init; }
    public bool IsMultiTopic { get; init; }
}

public record EvaluateRelevanceResponse
{
    public bool IsRelevant { get; init; }
    public List<MemorySearchResult> RelevantResults { get; init; }
    public string Reasoning { get; init; }
}
```

**메시지 설계 원칙:**

1. **자기 완결적**: 메시지만으로 처리에 필요한 모든 정보를 얻을 수 있어야 한다
2. **불변(Immutable)**: `record` 타입 사용으로 불변성 보장
3. **추론 근거 포함**: `Reasoning` 필드로 AI의 판단 과정을 추적 가능

### 4.3 Supervision 전략: AI 파이프라인의 장애 복구

```csharp
// ChatBotActor에서 자식 액터의 Supervision 전략
protected override SupervisorStrategy SupervisorStrategy()
{
    return new OneForOneStrategy(
        maxNrOfRetries: 3,
        withinTimeRange: TimeSpan.FromMinutes(1),
        localOnlyDecider: ex => ex switch
        {
            // LLM API 일시 오류 → 재시작
            HttpRequestException => Directive.Restart,

            // LLM 응답 파싱 실패 → 재시작 (다른 응답이 올 수 있음)
            FormatException => Directive.Restart,

            // 타임아웃 → 재시작
            TaskCanceledException => Directive.Restart,

            // 그 외 → 에스컬레이션
            _ => Directive.Escalate
        });
}
```

```
Supervision 트리:

ActorSystem
    │
    ├── /user/chatbot-session-user123 (ChatBotActor)
    │       자체 상태 기계로 오류 처리
    │       LLM 응답 실패 → Status.Failure 메시지로 처리
    │
    ├── /user/search-memory (SearchMemoryActor)
    │       OneForOneStrategy: 3회 재시도 후 중지
    │       검색 실패 → 빈 결과 반환
    │
    ├── /user/decision (DecisionActor)
    │       OneForOneStrategy: 3회 재시도 후 중지
    │       평가 실패 → 모든 결과 포함 (보수적 접근)
    │
    ├── /user/graph-relationship (GraphRelationshipActor)
    │       Fire-and-Forget → 실패해도 사용자 응답에 영향 없음
    │
    ├── /user/metadata-embedding (MetadataEmbeddingActor)
    │       배치 실패 → 개별 항목 스킵, 진행 계속
    │
    ├── /user/title-generation (TitleGenerationActor)
    │       개별 실패 → EventStream으로 실패 이벤트 게시, 다음 항목 진행
    │
    └── /user/graph-sync (GraphSyncActor)
            개별 실패 → 로그 기록, 다음 페이지 진행
```

### 4.4 Fire-and-Forget vs Ask 패턴

Memorizer는 **응답 경로**와 **부수 작업**을 명확히 구분하여 각각 다른 통신 패턴을 사용한다.

```csharp
// === 응답 경로: Tell + Context.Become() ===
// 사용자에게 응답을 돌려줘야 하므로 결과를 기다림
// 그러나 Ask 대신 Tell + 상태 전환을 사용 (논블로킹)

_searchMemoryActor.Tell(new SearchMemoryRequest { ... });
Context.Become(WaitingForSearchResponse(request, sender));
// → SearchMemoryActor가 Sender.Tell()로 결과를 보내면
//   WaitingForSearchResponse 상태에서 처리

// === 부수 작업: Tell (Fire-and-Forget) ===
// 사용자 응답과 무관 → 결과를 기다리지 않음

_graphRelationshipActor.Tell(new SuggestRelationshipsRequest { ... });
_metadataEmbeddingActor.Tell(new StartBatchEmbedding { ... });
_titleGenerationActor.Tell(new StartTitleGeneration { ... });
// → 세 작업이 동시에 병렬 실행됨
// → 실패해도 사용자 응답에 영향 없음
```

```
시간축 →

[Ask 패턴 (사용하지 않는 이유)]
ChatBot: ──Ask(Search)──────────대기──────────결과───Ask(Eval)───대기───결과──응답──
         블로킹!                                    블로킹!

[Tell + Become 패턴 (실제 사용)]
ChatBot: ──Tell(Search)──Become(Waiting)─────────Handle(Result)──Tell(Eval)──Become──
         즉시 반환     다른 메시지 처리 가능     결과 수신시 처리  즉시 반환

[Fire-and-Forget (부수 작업)]
ChatBot: ──응답 전송──Tell(Graph)──Tell(Embed)──Tell(Title)──끝──
                     ↓            ↓             ↓
                     병렬 실행     병렬 실행      병렬 실행
                     (실패 무관)   (실패 무관)    (실패 무관)
```

### 4.5 EventStream 활용: 느슨한 결합의 이벤트 시스템

```csharp
// 이벤트 게시자 (MetadataEmbeddingActor)
Context.System.EventStream.Publish(new BatchMetadataEmbeddingCompleted
{
    TotalProcessed = 100,
    SuccessCount = 98,
    FailureCount = 2,
    ElapsedTime = TimeSpan.FromMinutes(5)
});

// 이벤트 구독자 (필요한 액터가 구독)
// 예: 모니터링 액터, GraphSyncActor (임베딩 완료 후 동기화 시작)
Context.System.EventStream.Subscribe<BatchMetadataEmbeddingCompleted>(Self);
```

EventStream은 액터 간의 **느슨한 결합**을 가능하게 한다. 게시자는 구독자가 누구인지 알 필요가 없고, 구독자는 관심 있는 이벤트만 선택적으로 수신한다.

---

## 5. ASP.NET Core 통합

### 5.1 Akka.Hosting을 이용한 DI 등록

```csharp
// Program.cs
builder.Services.AddAkka("memorizer-system", configurationBuilder =>
{
    configurationBuilder
        .WithActors((system, registry, resolver) =>
        {
            // Singleton 액터 등록
            var searchMemoryActor = system.ActorOf(
                resolver.Props<SearchMemoryActor>(), "search-memory");
            registry.Register<SearchMemoryActorKey>(searchMemoryActor);

            var decisionActor = system.ActorOf(
                resolver.Props<DecisionActor>(), "decision");
            registry.Register<DecisionActorKey>(decisionActor);

            var graphRelationshipActor = system.ActorOf(
                resolver.Props<GraphRelationshipActor>(), "graph-relationship");
            registry.Register<GraphRelationshipActorKey>(graphRelationshipActor);

            var metadataEmbeddingActor = system.ActorOf(
                resolver.Props<MetadataEmbeddingActor>(), "metadata-embedding");
            registry.Register<MetadataEmbeddingActorKey>(metadataEmbeddingActor);

            var titleGenerationActor = system.ActorOf(
                resolver.Props<TitleGenerationActor>(), "title-generation");
            registry.Register<TitleGenerationActorKey>(titleGenerationActor);

            var graphSyncActor = system.ActorOf(
                resolver.Props<GraphSyncActor>(), "graph-sync");
            registry.Register<GraphSyncActorKey>(graphSyncActor);
        });
});

// Actor Key 정의
public sealed class SearchMemoryActorKey { }
public sealed class DecisionActorKey { }
public sealed class GraphRelationshipActorKey { }
public sealed class MetadataEmbeddingActorKey { }
public sealed class TitleGenerationActorKey { }
public sealed class GraphSyncActorKey { }
```

### 5.2 세션 액터 관리 (Controller)

```csharp
[ApiController]
[Route("api/[controller]")]
public class AskBotController : ControllerBase
{
    // 사용자 세션별 ChatBotActor 관리
    private static readonly ConcurrentDictionary<string, IActorRef> _sessionActors = new();

    private readonly ActorSystem _actorSystem;
    private readonly IRequiredActor<SearchMemoryActorKey> _searchMemoryActor;
    private readonly IRequiredActor<DecisionActorKey> _decisionActor;
    private readonly IRequiredActor<GraphRelationshipActorKey> _graphRelationshipActor;

    public AskBotController(
        ActorSystem actorSystem,
        IRequiredActor<SearchMemoryActorKey> searchMemoryActor,
        IRequiredActor<DecisionActorKey> decisionActor,
        IRequiredActor<GraphRelationshipActorKey> graphRelationshipActor)
    {
        _actorSystem = actorSystem;
        _searchMemoryActor = searchMemoryActor;
        _decisionActor = decisionActor;
        _graphRelationshipActor = graphRelationshipActor;
    }

    /// <summary>
    /// 사용자 세션에 대한 ChatBotActor를 가져오거나 생성
    /// </summary>
    private IActorRef GetOrCreateSessionActor(string userId)
    {
        return _sessionActors.GetOrAdd(userId, uid =>
        {
            var props = Props.Create(() => new ChatBotActor(
                uid,
                _searchMemoryActor.ActorRef,
                _decisionActor.ActorRef,
                _graphRelationshipActor.ActorRef
                // ... 기타 의존성
            ));

            return _actorSystem.ActorOf(props, $"chatbot-session-{uid}");
        });
    }
}
```

### 5.3 SSE 스트리밍: 액터 → 클라이언트 실시간 전달

```csharp
[HttpPost("chat/stream")]
public async Task ChatStream(
    [FromBody] ChatRequest request,
    CancellationToken cancellationToken)
{
    Response.ContentType = "text/event-stream";
    Response.Headers["Cache-Control"] = "no-cache";
    Response.Headers["Connection"] = "keep-alive";

    var sessionActor = GetOrCreateSessionActor(request.UserId);

    // Channel을 사용하여 액터 메시지를 SSE 스트림으로 변환
    var channel = Channel.CreateUnbounded<StreamingUpdate>();

    // 액터에게 채널 전달
    var chatRequest = new UserChatRequest
    {
        UserId = request.UserId,
        Message = request.Message,
        StreamChannel = channel.Writer,  // 액터가 여기에 업데이트를 씀
        HasImage = request.ImageData != null,
        ImageData = request.ImageData
    };

    // Ask 패턴으로 최종 응답 대기 (또는 Tell + Channel 조합)
    var responseTask = sessionActor.Ask<ChatBotResponse>(chatRequest, TimeSpan.FromMinutes(2));

    // 채널에서 스트리밍 업데이트를 읽어 SSE로 전송
    await foreach (var update in channel.Reader.ReadAllAsync(cancellationToken))
    {
        var sseData = JsonSerializer.Serialize(update);
        await Response.WriteAsync($"data: {sseData}\n\n", cancellationToken);
        await Response.Body.FlushAsync(cancellationToken);

        // 최종 응답이면 스트림 종료
        if (update.Type == "complete")
            break;
    }
}
```

**Actor → SSE Bridge 패턴:**

```
ChatBotActor
    │
    │ StreamChannel.Writer.TryWrite(new StreamingUpdate
    │ {
    │     Type = "reasoning",
    │     Content = "쿼리 유형을 분석하고 있습니다..."
    │ });
    │
    ▼
Channel<StreamingUpdate>
    │
    │ ReadAllAsync()
    ▼
SSE Response
    │
    │ data: {"type":"reasoning","content":"쿼리 유형을 분석하고 있습니다..."}
    │ data: {"type":"reasoning","content":"3개의 관련 메모리를 찾았습니다..."}
    │ data: {"type":"reasoning","content":"관련성을 평가하고 있습니다..."}
    │ data: {"type":"complete","content":"파스타 레시피는 다음과 같습니다..."}
    ▼
클라이언트 (실시간 표시)
```

---

## 6. 실전 고려사항

### 6.1 LLM API 호출 시 주의사항

#### Rate Limiting 처리

```csharp
// LLM 서비스에서 재시도 정책 적용
public class LlmService : ILlmService
{
    private static readonly SemaphoreSlim _rateLimiter = new(maxCount: 10);

    public async Task<string> GenerateAsync(string prompt)
    {
        await _rateLimiter.WaitAsync();
        try
        {
            return await RetryPolicy.ExecuteAsync(async () =>
            {
                var response = await _httpClient.PostAsJsonAsync("/v1/chat/completions", new
                {
                    model = "gpt-4",
                    messages = new[] { new { role = "user", content = prompt } }
                });
                response.EnsureSuccessStatusCode();
                return await ParseResponse(response);
            });
        }
        finally
        {
            _rateLimiter.Release();
        }
    }
}
```

#### 타임아웃 처리

```csharp
// ChatBotActor에서 LLM 응답 타임아웃 처리
private void GenerateResponseWithMemory(/* ... */)
{
    var self = Self;

    // 타임아웃 타이머 설정
    Timers.StartSingleTimer(
        "llm-timeout",
        new LlmTimeout(),
        TimeSpan.FromSeconds(30));

    Task.Run(async () =>
    {
        try
        {
            var response = await _llmService.GenerateResponseAsync(prompt);
            self.Tell(new LlmResponseGenerated { Response = response });
        }
        catch (Exception ex)
        {
            self.Tell(new Status.Failure(ex));
        }
    });
}

// 타임아웃 메시지 처리 (WaitingForEvaluation 등의 상태에서)
case LlmTimeout:
    _log.Warning("LLM 응답 타임아웃");
    originalSender.Tell(new ChatBotResponse
    {
        Message = "응답 생성에 시간이 오래 걸리고 있습니다. 다시 시도해주세요.",
        IsTimeout = true
    });
    Context.UnbecomeStacked();  // 이전 상태로 복귀
    return true;
```

### 6.2 메모리 관리

#### 대화 이력 크기 제한

```csharp
// 최대 10개 턴으로 제한
// → LLM 프롬프트의 토큰 수를 예측 가능하게 유지
// → 비용 제어 + 응답 속도 유지
private const int MaxConversationEntries = 10;
```

#### 액터 인스턴스 관리

```csharp
// 세션 액터 정리 (타임아웃 또는 명시적 종료)
private void HandleSessionTimeout()
{
    // ConcurrentDictionary에서 제거
    _sessionActors.TryRemove(_userId, out _);

    // 액터 종료
    Context.Stop(Self);
}
```

### 6.3 테스트 가능성

액터 모델은 메시지 기반이므로 **통합 테스트**가 자연스럽다.

```csharp
[Fact]
public async Task ChatBotActor_ShouldReturnResponse_WithRelevantMemories()
{
    // Arrange
    using var system = ActorSystem.Create("test");

    // TestProbe로 의존 액터를 목킹
    var searchProbe = system.CreateTestProbe();
    var decisionProbe = system.CreateTestProbe();

    var chatBot = system.ActorOf(Props.Create(() =>
        new ChatBotActor("test-user", searchProbe, decisionProbe, /* ... */)));

    // Act
    chatBot.Tell(new UserChatRequest
    {
        UserId = "test-user",
        Message = "어제 저장한 레시피 보여줘"
    });

    // Assert: 쿼리 유형 분석 요청이 SearchMemoryActor로 전송되는지 확인
    var analyzeRequest = searchProbe.ExpectMsg<AnalyzeQueryTypeRequest>();
    Assert.Equal("어제 저장한 레시피 보여줘", analyzeRequest.Query);

    // 쿼리 분석 응답을 시뮬레이션
    searchProbe.Reply(new AnalyzeQueryTypeResponse
    {
        NeedsSearch = true,
        IsMultiTopic = false,
        Topics = new List<string> { "레시피" }
    });

    // 검색 요청이 전송되는지 확인
    var searchRequest = searchProbe.ExpectMsg<SearchMemoryRequest>();
    Assert.True(searchRequest.Query.Contains("레시피"));

    // ... 파이프라인의 각 단계를 순서대로 검증
}
```

### 6.4 모니터링 및 디버깅

```csharp
// Akka.NET 로깅을 통한 파이프라인 추적
private readonly ILoggingAdapter _log = Context.GetLogger();

// 각 액터에서 메시지 수신/발신 로깅
_log.Info("[ChatBot-{0}] 쿼리 유형 분석 요청: {1}", _userId, request.Message);
_log.Info("[SearchMemory] 검색 전략: {0}, 결과: {1}건", strategy, results.Count);
_log.Info("[Decision] 관련 결과: {0}/{1}건", relevant.Count, total.Count);

// Akka.NET의 기본 DeadLetter 모니터링
// → 메시지가 목적지에 도달하지 못한 경우 자동 로깅
```

---

## 7. 기존 액터 패턴과의 비교

### 기존 문서의 액터 패턴 vs AI-Agent 패턴

| 특성 | 기존 패턴 (01-03) | AI-Agent 패턴 (04) |
|------|-------------------|-------------------|
| **액터의 역할** | 범용 작업 처리 (카운터, 라우터 등) | 특정 AI 작업 전담 (검색, 평가, 생성) |
| **메시지 복잡도** | 단순 (숫자, 문자열) | 복합 (쿼리 + 컨텍스트 + 메타데이터) |
| **상태 기계** | 간단한 On/Off | 다단계 AI 워크플로우 (4~5단계) |
| **비동기 처리** | 주로 동기 | LLM 호출이 핵심 (수초 소요) |
| **실패 처리** | 단순 재시작 | 보수적 폴백 (결과 누락 방지) |
| **배치 처리** | 해당 없음 | Self-Message로 페이지네이션 |
| **외부 통합** | 제한적 | LLM API, Vector DB, Graph DB |
| **실시간 스트리밍** | 해당 없음 | SSE + Channel 브리지 |

### 핵심 차이점: 액터 = AI 파이프라인 스테이지

전통적인 액터 시스템에서 액터는 "상태를 가진 동시성 단위"이다. AI-Agent 패턴에서 액터는 **"AI 처리 파이프라인의 한 단계"**가 된다.

```
전통적 액터:
  Actor = 상태 + 행위 + 메일박스

AI-Agent 액터:
  Actor = AI 작업(LLM 호출) + 결과 판단 + 다음 단계 라우팅

  SearchMemoryActor = "검색 최적화" AI 작업
  DecisionActor     = "관련성 평가" AI 작업
  ChatBotActor      = "오케스트레이션" + "응답 생성" AI 작업
```

이 패턴의 장점은 각 AI 작업이 **독립적으로 확장, 교체, 테스트**될 수 있다는 것이다. 예를 들어:
- SearchMemoryActor의 검색 전략을 바꾸고 싶으면 해당 액터만 수정
- DecisionActor의 LLM 모델을 교체하고 싶으면 해당 액터만 수정
- 새로운 AI 작업(예: 감정 분석)을 추가하고 싶으면 새 액터를 파이프라인에 삽입

---

## 요약

Memorizer 프로젝트는 Akka.NET 액터 모델이 AI-Agent 시스템에 얼마나 자연스럽게 적용될 수 있는지를 보여주는 실전 사례이다.

**핵심 교훈:**

1. **액터 = AI 파이프라인 스테이지**: 각 액터가 하나의 AI 작업을 전담하면 시스템이 명확해진다
2. **Context.Become() = AI 워크플로우**: 복잡한 다단계 AI 처리를 상태 기계로 표현하면 코드가 선언적이 된다
3. **Task.Run() + self.Tell() = 안전한 비동기 LLM 호출**: 액터의 단일 스레드 보장을 유지하면서 비블로킹 호출을 구현한다
4. **Fire-and-Forget = 부수 작업 분리**: 사용자 응답과 무관한 작업을 비블로킹으로 병렬 수행한다
5. **보수적 폴백 = AI 시스템의 안정성**: 실패 시 "결과 없음"보다 "약간의 노이즈"가 나은 선택이다
6. **EventStream = 느슨한 결합**: 배치 작업 완료 알림 등을 구독/게시 패턴으로 처리한다
7. **SSE + Channel = 실시간 투명성**: AI의 추론 과정을 실시간으로 사용자에게 전달한다

이 패턴들은 Memorizer뿐 아니라, LLM 기반 AI-Agent를 구축하는 모든 프로젝트에 적용할 수 있다. 액터 모델은 AI 시스템의 복잡성을 관리하는 데 탁월한 도구이다.
