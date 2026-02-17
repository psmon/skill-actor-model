---
name: actor-ai-agent
description: Akka.NET 액터 기반 AI 에이전트 파이프라인 코드를 생성합니다. C# + Akka.NET으로 LLM 통합 AI 에이전트를 구현하거나, 액터 파이프라인 단계 설계, Context.Become 워크플로우 상태 머신, 비동기 LLM 호출 패턴, SSE 스트리밍, Fire-and-Forget 사이드 태스크 등 AI 에이전트 아키텍처를 작성할 때 사용합니다.
argument-hint: "[파이프라인 요구사항]"
---

# Akka.NET 기반 AI 에이전트 파이프라인 스킬

Akka.NET 액터를 활용하여 AI 에이전트(LLM 통합) 파이프라인을 설계하고 구현하는 스킬입니다.
Memorizer v1 아키텍처를 참조 모델로 합니다.

## 참고 문서

- AI Agent 가이드: [skill-maker/docs/actor/04-memorizer-ai-agent/README.md](../../../../skill-maker/docs/actor/04-memorizer-ai-agent/README.md)
- Akka.NET 기본 패턴: [skill-maker/docs/actor/03-dotnet-akka-net/README.md](../../../../skill-maker/docs/actor/03-dotnet-akka-net/README.md)
- 액터모델 개요: [skill-maker/docs/actor/00-actor-model-overview.md](../../../../skill-maker/docs/actor/00-actor-model-overview.md)

## 환경

- **프레임워크**: Akka.NET 1.5.x
- **언어**: C# (.NET 10 권장)
- **외부 통합**: OpenAI LLM API, PostgreSQL (pgvector), Neo4j Graph DB
- **라이선스**: Apache License 2.0

## 코어 스킬 조합 방식 (최신)

AI-agent 스킬은 단독으로 쓰기보다 코어 스킬과 조합해 사용합니다.

1. `dotnet-akka-net`으로 메시지 계약/액터 책임/기본 수명주기 규칙을 먼저 고정합니다.
2. 클러스터가 필요하면 `dotnet-akka-net-cluster`로 singleton/sharding/pubsub 경계를 분리합니다.
3. 배포 대상이 분산 환경이면 `dotnet-akka-net-infra` 규칙(주소/seed/health/readiness)을 선반영합니다.
4. 마지막에 본 스킬(`actor-ai-agent`)로 오케스트레이터 단계(Analyze/Search/Decision/Final + SideTask)를 얹습니다.

조합 시 고정 원칙:
- 오케스트레이터는 상태 전환(`Become`)만 담당하고, 단계 액터는 단일 책임을 유지합니다.
- 외부 I/O(LLM/DB)는 결과를 self-message로 환원해 액터 순차 처리를 보장합니다.
- 사용자 응답 경로와 Fire-and-Forget 사이드태스크 경로를 분리합니다.
- Ask/Reply 계약은 코어 스킬의 언어 표준을 그대로 따릅니다.

## 핵심 아키텍처: Actor as AI Pipeline Stage

각 액터가 AI 파이프라인의 한 단계를 담당합니다. AI 처리를 이산적이고 테스트 가능한 단위로 분리합니다.

```
User Request
    |
ChatBotActor (Orchestrator, 세션별 1개)
    |
    +---> SearchMemoryActor (질의 분석 + 메모리 검색)
    |         |
    |         v
    +---> DecisionActor (검색 결과 관련성 평가)
    |         |
    |         v
    +---> LLM Final Response 생성
    |
    +---> [Fire-and-Forget 사이드 태스크]
          +---> GraphRelationshipActor (그래프 관계 추출)
          +---> MetadataEmbeddingActor (임베딩 벡터 생성)
          +---> TitleGenerationActor (자동 제목 생성)
```

## 핵심 설계 패턴

### 1. Context.Become() 워크플로우 상태 머신

ChatBotActor가 AI 처리 단계마다 상태를 전환합니다. 클로저로 원래 요청/응답 컨텍스트를 캡처합니다.

```
Base State
  --> WaitingForQueryTypeAnalysis (질의 유형 분석 대기)
  --> WaitingForSearchResponse (메모리 검색 대기)
  --> WaitingForEvaluationResponse (관련성 평가 대기)
  --> Back to Base (응답 전송 후 복귀)
```

```csharp
public class ChatBotActor : ReceiveActor, IWithTimers
{
    public ITimerScheduler Timers { get; set; }
    private readonly List<ChatMessage> _conversationHistory = new();

    public ChatBotActor(IActorRef searchActor, IActorRef decisionActor)
    {
        // Base 상태: 사용자 요청 수신
        Receive<UserChatRequest>(request =>
        {
            _conversationHistory.Add(new ChatMessage("user", request.Message));

            // 질의 유형 분석 요청
            searchActor.Tell(new AnalyzeQueryTypeRequest(request.Message));

            // 상태 전환 (클로저로 request, Sender 캡처)
            Context.Become(WaitingForQueryTypeAnalysis(request, Sender));
        });
    }

    private UntypedReceive WaitingForQueryTypeAnalysis(
        UserChatRequest originalRequest, IActorRef originalSender)
    {
        return message =>
        {
            switch (message)
            {
                case AnalyzeQueryTypeResponse response when response.NeedsSearch:
                    // 검색 필요 -> 검색 요청 후 다음 상태로 전환
                    _searchActor.Tell(new SearchMemoryRequest(response.TransformedQuery));
                    Context.Become(WaitingForSearchResponse(originalRequest, originalSender));
                    break;

                case AnalyzeQueryTypeResponse response:
                    // 검색 불필요 -> 직접 LLM 응답 생성
                    GenerateFinalResponse(originalRequest, null, originalSender);
                    Context.UnbecomeStacked(); // Base로 복귀
                    break;
            }
        };
    }

    private UntypedReceive WaitingForSearchResponse(
        UserChatRequest originalRequest, IActorRef originalSender)
    {
        return message =>
        {
            if (message is SearchMemoryResponse searchResult)
            {
                if (searchResult.Results.Any())
                {
                    // 관련성 평가 요청
                    _decisionActor.Tell(new EvaluateRelevanceRequest(
                        originalRequest.Message, searchResult.Results));
                    Context.Become(WaitingForEvaluation(originalRequest, originalSender));
                }
                else
                {
                    GenerateFinalResponse(originalRequest, null, originalSender);
                    Context.UnbecomeStacked();
                }
            }
        };
    }
}
```

### 2. Task.Run() + self.Tell() 비동기 LLM 호출

액터의 단일 스레드 보장을 유지하면서 비동기 LLM API를 호출합니다.

```csharp
// LLM 호출을 별도 스레드에서 실행, 결과를 메시지로 수신
private void HandleSearchRequest(SearchMemoryRequest request)
{
    var self = Self;

    Task.Run(async () =>
    {
        try
        {
            var result = await _llmService.AnalyzeQueryAsync(request.Query);
            self.Tell(new QueryAnalysisCompleted(result));
        }
        catch (Exception ex)
        {
            self.Tell(new QueryAnalysisFailed(ex.Message));
        }
    });
}
```

**주의**: 액터 내부에서 직접 `await`하지 않습니다. `Task.Run()` + `self.Tell()`로 결과를 메시지로 변환하여 액터의 순차 처리 보장을 유지합니다.

### 3. Fire-and-Forget 사이드 태스크

사용자 응답 경로(Tell + 상태 전환)와 독립적으로 실행되는 부수 작업입니다.

```csharp
// 응답 전송 후 사이드 태스크 병렬 실행
private void GenerateFinalResponse(UserChatRequest request,
    List<MemoryResult> relevantResults, IActorRef originalSender)
{
    // 1. 사용자에게 응답 (메인 경로)
    var response = BuildResponse(request, relevantResults);
    originalSender.Tell(new ChatResponse(response));

    // 2. Fire-and-Forget 사이드 태스크
    _graphActor.Tell(new SuggestRelationships(relevantResults));
    _embeddingActor.Tell(new ProcessEmbeddings(request.Message));
    _titleActor.Tell(new GenerateTitle(request.Message));

    // Base 상태로 복귀
    Context.UnbecomeStacked();
}
```

### 4. EventStream 느슨한 결합

배치 처리 완료 이벤트를 EventStream으로 발행합니다. 관심 있는 액터만 구독합니다.

```csharp
// 발행
Context.System.EventStream.Publish(new EmbeddingBatchCompleted(processedCount));

// 구독
Context.System.EventStream.Subscribe<EmbeddingBatchCompleted>(Self);
```

### 5. Self-Message 배치 처리

대량 데이터를 페이지 단위로 처리합니다. Self.Tell()로 다음 페이지를 예약하여 메시지 인터리빙(중단 가능)을 허용합니다.

```csharp
public class MetadataEmbeddingActor : ReceiveActor
{
    public MetadataEmbeddingActor()
    {
        Receive<ProcessNextPage>(msg =>
        {
            var batch = GetNextBatch(msg.CurrentIndex, pageSize: 50);

            if (batch.Any())
            {
                ProcessBatch(batch);
                // 다음 페이지 예약 (중단 가능한 배치 처리)
                Self.Tell(new ProcessNextPage(msg.CurrentIndex + 1));
            }
            else
            {
                Context.System.EventStream.Publish(
                    new EmbeddingBatchCompleted(_totalProcessed));
            }
        });

        // 중단 명령 처리 가능 (Self-Message 패턴의 장점)
        Receive<StopProcessing>(_ =>
        {
            _logger.Info("Batch processing interrupted");
        });
    }
}
```

### 6. 에러 안전 폴백 전략

AI 처리 실패 시 보수적으로 모든 결과를 포함합니다. 정보 누락보다 과잉 포함이 낫습니다.

```csharp
// DecisionActor: 평가 실패 시 모든 결과 포함
private List<MemoryResult> EvaluateRelevance(string query, List<MemoryResult> results)
{
    try
    {
        var evaluation = _llmService.EvaluateAsync(query, results).Result;
        return evaluation.RelevantResults;
    }
    catch (Exception ex)
    {
        _logger.Warning($"Evaluation failed, including all results: {ex.Message}");
        return results; // 폴백: 전체 포함
    }
}
```

### 7. 감독 전략 (AI 특화)

LLM API 호출 실패에 대한 보수적 재시작 전략입니다.

```csharp
protected override SupervisorStrategy SupervisorStrategy()
{
    return new OneForOneStrategy(
        maxNrOfRetries: 3,
        withinTimeRange: TimeSpan.FromMinutes(1),
        localOnlyDecider: ex => ex switch
        {
            HttpRequestException => Directive.Restart,    // 네트워크 오류
            FormatException => Directive.Restart,          // LLM 응답 파싱 실패
            TaskCanceledException => Directive.Restart,    // 타임아웃
            _ => Directive.Escalate
        }
    );
}
```

### 8. GraphSyncActor - DB 동기화 (PostgreSQL → Neo4j)

PostgreSQL에 저장된 메모리 데이터를 Neo4j 그래프 데이터베이스로 전체 동기화합니다. `Self.Tell(SyncNextPage)` 페이지네이션으로 중단 가능한 배치 동기화를 구현합니다.

```csharp
public class GraphSyncActor : ReceiveActor
{
    private const int PageSize = 50;
    private readonly IMemoryRepository _memoryRepository;
    private readonly IGraphSyncService _graphSyncService;
    private readonly IActorRef _graphRelationshipActor;

    public GraphSyncActor(IMemoryRepository memoryRepository,
        IGraphSyncService graphSyncService, IActorRef graphRelationshipActor)
    {
        _memoryRepository = memoryRepository;
        _graphSyncService = graphSyncService;
        _graphRelationshipActor = graphRelationshipActor;

        ReceiveAsync<InitializeGraphSchema>(async _ =>
        {
            await _graphSyncService.EnsureSchemaAsync();
            // 인덱스: Memory(id), Memory(userId)
            // 제약: Memory.id UNIQUE
        });

        ReceiveAsync<StartFullSync>(async request =>
        {
            var totalCount = await _memoryRepository.CountAllMemoriesAsync(request.UserId);
            var totalPages = (int)Math.Ceiling(totalCount / (double)PageSize);

            Self.Tell(new SyncNextPage
            {
                UserId = request.UserId,
                CurrentPage = 0,
                TotalPages = totalPages
            });
        });

        ReceiveAsync<SyncNextPage>(async request =>
        {
            if (request.CurrentPage >= request.TotalPages) return;

            var memories = await _memoryRepository.GetMemoriesPageAsync(
                request.UserId, request.CurrentPage, PageSize);

            foreach (var memory in memories)
            {
                await _graphSyncService.UpsertMemoryNodeAsync(memory);
                _graphRelationshipActor.Tell(new SuggestRelationshipsRequest
                {
                    NewMemory = memory,
                    ExistingMemories = await _graphSyncService
                        .FindNearbyNodesAsync(memory.Id, maxDistance: 2)
                });
            }

            // 다음 페이지 (Self-Message 페이지네이션)
            Self.Tell(new SyncNextPage
            {
                UserId = request.UserId,
                CurrentPage = request.CurrentPage + 1,
                TotalPages = request.TotalPages
            });
        });
    }
}

// 메시지 정의
public record InitializeGraphSchema;
public record StartFullSync(string UserId);
public record SyncNextPage
{
    public string UserId { get; init; }
    public int CurrentPage { get; init; }
    public int TotalPages { get; init; }
}
```

**핵심 포인트**: `Self.Tell(SyncNextPage)` 페이지네이션은 Section 5의 Self-Message 배치 처리와 동일한 패턴입니다. 페이지 간 다른 메시지(중단 명령 등) 처리가 가능합니다.

### 9. LLM 검색 재시도 전략 (4단계 파이프라인)

벡터 검색 실패 시 키워드 검색, 쿼리 재구성까지 자동 폴백하는 4단계 검색 파이프라인입니다.

```
검색 파이프라인:
1. LLM 쿼리 변환 (한국어 → 검색 최적화)
   → "어제 저장한 그 레시피" → "레시피 cooking recipe 2024-01"
2. 벡터 임베딩 검색 (pgvector cosine similarity)
   → 결과 있음 → 반환
3. LLM 키워드 추출 → 키워드 기반 검색 (full-text search)
   → 결과 있음 → 반환
4. 쿼리 재구성 → 2번으로 재시도 (최대 3회)
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

**검색 전략 비교표**:

| 단계 | 방식 | 강점 | 약점 |
|------|------|------|------|
| 벡터 검색 | pgvector cosine | 의미 유사도 | 최신 데이터 임베딩 지연 |
| 키워드 검색 | full-text search | 정확한 용어 매칭 | 동의어/오타 미처리 |
| 쿼리 재구성 | LLM reformulation | 다양한 관점 시도 | LLM 호출 비용 |

### 10. 대화 컨텍스트 관리 (이중 메모리 구조)

명시적 대화 이력(최근 10턴)과 LLM이 추출한 단기 기억(대화 맥락 요약) 이중 구조로 컨텍스트를 관리합니다.

```csharp
// ChatBotActor 내부
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

    // 최대 10개 유지 (FIFO 트리밍)
    while (_conversationHistory.Count > MaxConversationEntries)
    {
        _conversationHistory.RemoveAt(0);
    }

    // LLM으로 대화 맥락 요약 갱신
    _shortTermMemory = await _llmService.ExtractConversationContext(
        _conversationHistory, _shortTermMemory);
}
```

**이중 메모리 구조**:
- `_conversationHistory`: 최근 10개 턴의 원본 대화 (사용자 질문 + 봇 응답)
- `_shortTermMemory`: LLM이 능동적으로 추출한 대화 맥락 요약. "아까 말한 그 책"처럼 애매한 참조를 해석할 때 사용

### 11. Rate Limiting (LLM API 동시 호출 제한)

`SemaphoreSlim`으로 LLM API 동시 호출 수를 제한하고, `RetryPolicy`로 일시적 실패를 재시도합니다.

```csharp
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
                var response = await _httpClient.PostAsJsonAsync(
                    "/v1/chat/completions", new
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

**Rate Limiting 설계 포인트**:
- `SemaphoreSlim(10)`: 최대 10개 동시 LLM 호출 허용
- `WaitAsync()`/`Release()`: `try-finally`로 반드시 해제 보장
- `RetryPolicy`: 429 Too Many Requests, 503 Service Unavailable 등 일시적 오류 자동 재시도
- 액터 레벨이 아닌 **서비스 레벨**에서 제한하여, 여러 액터가 공유하는 전역 동시성 제어

## SSE 스트리밍 (실시간 추론 과정 표시)

`Channel<StreamingUpdate>`를 브릿지로 사용하여 액터의 처리 단계를 실시간으로 클라이언트에 전송합니다.

```csharp
// Controller
[HttpGet("stream/{sessionId}")]
public async IAsyncEnumerable<StreamingUpdate> StreamResponse(string sessionId)
{
    var channel = Channel.CreateUnbounded<StreamingUpdate>();

    _chatBotActor.Tell(new UserChatRequest(query, channel.Writer));

    await foreach (var update in channel.Reader.ReadAllAsync())
    {
        yield return update;
    }
}

// ChatBotActor 내부: 각 단계에서 채널에 진행 상황 기록
private void NotifyProgress(ChannelWriter<StreamingUpdate> writer, string step)
{
    writer.TryWrite(new StreamingUpdate(step, DateTime.UtcNow));
}
```

## 세션 관리

`ConcurrentDictionary`로 사용자별 ChatBotActor 인스턴스를 관리합니다.

```csharp
public class SessionManager
{
    private readonly ConcurrentDictionary<string, IActorRef> _sessions = new();

    public IActorRef GetOrCreateSession(string userId, ActorSystem system)
    {
        return _sessions.GetOrAdd(userId, id =>
        {
            return system.ActorOf(
                Props.Create(() => new ChatBotActor(_searchActor, _decisionActor)),
                $"chat-{id}"
            );
        });
    }
}
```

## ASP.NET Core 통합 (Akka.Hosting)

```csharp
builder.Services.AddAkka("AgentSystem", config =>
{
    config.WithActors((system, registry) =>
    {
        var searchActor = system.ActorOf(
            Props.Create<SearchMemoryActor>(), "search");
        registry.Register<SearchMemoryActor>(searchActor);

        var decisionActor = system.ActorOf(
            Props.Create<DecisionActor>(), "decision");
        registry.Register<DecisionActor>(decisionActor);

        var graphActor = system.ActorOf(
            Props.Create<GraphRelationshipActor>(), "graph");
        registry.Register<GraphRelationshipActor>(graphActor);
    });
});
```

## 코드 생성 규칙

1. **파이프라인 설계**: 각 AI 처리 단계를 독립 액터로 분리합니다. 하나의 액터 = 하나의 AI 태스크.
2. **상태 머신**: `Context.Become()` + 클로저로 워크플로우 상태를 관리합니다. 원래 요청/응답자를 클로저로 캡처합니다.
3. **비동기 LLM 호출**: `Task.Run()` + `self.Tell()` 패턴을 사용합니다. 액터 내부에서 직접 `await`하지 않습니다.
4. **사이드 태스크**: 사용자 응답과 무관한 작업은 `Tell()` Fire-and-Forget으로 병렬 실행합니다.
5. **에러 처리**: LLM 실패 시 보수적 폴백(전체 결과 포함)을 적용합니다.
6. **배치 처리**: `Self.Tell()` 페이지네이션으로 중단 가능한 대량 처리를 구현합니다.
7. **느슨한 결합**: 배치 완료 등 이벤트는 `EventStream`으로 발행합니다.
8. **실시간 피드백**: `Channel<T>`를 브릿지로 SSE 스트리밍합니다.
9. **세션 관리**: `ConcurrentDictionary<string, IActorRef>`로 사용자별 액터를 관리합니다.
10. **대화 이력**: 이중 메모리 구조(`_conversationHistory` + `_shortTermMemory`)로 10개 이내 이력 + LLM 맥락 요약을 유지합니다 (Section 10 참조).
11. **타임아웃**: LLM 응답 30초, 세션 3일 타임아웃을 설정합니다.
12. **Rate Limiting**: `SemaphoreSlim`으로 LLM API 동시 호출 수를 제한합니다. 서비스 레벨에서 전역 제어합니다 (Section 11 참조).
13. **검색 재시도**: 벡터 검색 → 키워드 검색 → 쿼리 재구성의 4단계 폴백 파이프라인을 적용합니다 (Section 9 참조).
14. **DB 동기화**: `Self.Tell()` 페이지네이션으로 중단 가능한 대량 동기화를 구현합니다 (Section 8 참조).

$ARGUMENTS
