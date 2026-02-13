---
name: actor-ai-agent
description: Akka.NET 액터 기반 AI 에이전트 파이프라인 코드를 생성합니다. C# + Akka.NET으로 LLM 통합 AI 에이전트를 구현하거나, 액터 파이프라인 단계 설계, Context.Become 워크플로우 상태 머신, 비동기 LLM 호출 패턴, SSE 스트리밍, Fire-and-Forget 사이드 태스크 등 AI 에이전트 아키텍처를 작성할 때 사용합니다.
argument-hint: "[파이프라인 요구사항]"
---

# Akka.NET 기반 AI 에이전트 파이프라인 스킬

Akka.NET 액터를 활용하여 AI 에이전트(LLM 통합) 파이프라인을 설계하고 구현하는 스킬입니다.
Memorizer v1 아키텍처를 참조 모델로 합니다.

## 참고 문서

- AI Agent 가이드: [skil-maker/docs/actor/04-memorizer-ai-agent/README.md](../../../../skil-maker/docs/actor/04-memorizer-ai-agent/README.md)
- Akka.NET 기본 패턴: [skil-maker/docs/actor/03-dotnet-akka-net/README.md](../../../../skil-maker/docs/actor/03-dotnet-akka-net/README.md)
- 액터모델 개요: [skil-maker/docs/actor/00-actor-model-overview.md](../../../../skil-maker/docs/actor/00-actor-model-overview.md)

## 환경

- **프레임워크**: Akka.NET 1.5.x + ASP.NET Core
- **언어**: C# (.NET 9.0)
- **외부 통합**: OpenAI LLM API, PostgreSQL (pgvector), Neo4j Graph DB
- **라이선스**: Apache License 2.0

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
10. **대화 이력**: 10개 이내로 제한하여 LLM 토큰 사용량을 예측 가능하게 합니다.
11. **타임아웃**: LLM 응답 30초, 세션 3일 타임아웃을 설정합니다.
12. **Rate Limiting**: `SemaphoreSlim`으로 LLM API 동시 호출 수를 제한합니다 (최대 10개).

$ARGUMENTS
