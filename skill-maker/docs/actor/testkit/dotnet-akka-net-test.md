# Akka.NET TestKit 활용 가이드

Akka.NET 액터 테스트는 **비동기 메시지 흐름**과 **타이밍 제약**을 검증하는 것이 핵심입니다.  
이 문서는 `Akka.TestKit`을 이용해 C# 액터를 테스트하는 실전 패턴을 정리한 한글 가이드입니다.

## 목차

1. 테스트가 중요한 이유
2. 패키지/기본 설정
3. 기본 테스트 흐름
4. 주요 검증 API
5. 타이밍 검증 (`Within`)
6. Probe 활용 패턴
7. 로그/예외 검증
8. 실무 주의사항

## 1. 테스트가 중요한 이유

액터 모델은 다음 특성 때문에 일반 단위 테스트와 접근이 다릅니다.

- 메시지 전달이 비동기적으로 일어남
- 액터 간 상호작용이 상태 전이와 타이밍에 의존함
- 호출 결과보다 "메시지가 언제/어디로 전달됐는지" 검증이 중요함

`Akka.TestKit`은 이런 특성에 맞춘 검증 도구(`ExpectMsg`, `Within`, `TestProbe`)를 제공합니다.

## 2. 패키지/기본 설정

주로 `Akka.TestKit.Xunit2`를 사용합니다.

```xml
<ItemGroup>
  <PackageReference Include="Akka" Version="1.5.60" />
  <PackageReference Include="Akka.TestKit.Xunit2" Version="1.5.60" />
  <PackageReference Include="xunit" Version="2.9.2" />
  <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.12.0" />
</ItemGroup>
```

권장 사항:

- `Akka`와 `Akka.TestKit.Xunit2`는 같은 버전 계열 유지
- 테스트 프로젝트를 별도로 분리해 운영 코드와 독립 실행

## 3. 기본 테스트 흐름

### 3.1 테스트 대상 액터

```csharp
using Akka.Actor;

public sealed class SomeActor : ReceiveActor
{
    private IActorRef? _target;

    public SomeActor()
    {
        Receive<string>(s => s == "hello", _ =>
        {
            Sender.Tell("world");
            _target?.Forward("hello");
        });

        Receive<IActorRef>(actorRef =>
        {
            _target = actorRef;
            Sender.Tell("done");
        });
    }
}
```

### 3.2 TestKit 테스트

```csharp
using Akka.TestKit.Xunit2;
using Xunit;

public class SomeActorTests : TestKit
{
    [Fact]
    public void Should_reply_world_and_forward_hello()
    {
        var subject = Sys.ActorOf(Props.Create(() => new SomeActor()));
        var probe = CreateTestProbe();

        subject.Tell(probe.Ref, TestActor);
        ExpectMsg("done", TimeSpan.FromSeconds(1));

        Within(TimeSpan.FromSeconds(3), () =>
        {
            subject.Tell("hello", TestActor);

            ExpectMsg("world", TimeSpan.FromMilliseconds(100));
            probe.ExpectMsg("hello", TimeSpan.FromMilliseconds(100));
            Assert.Equal(TestActor, probe.Sender);
            ExpectNoMsg(TimeSpan.FromMilliseconds(200));
        });
    }
}
```

## 4. 주요 검증 API

| API | 설명 |
|---|---|
| `ExpectMsg<T>()` | 지정 시간 내 메시지 1건 수신 검증 |
| `ExpectMsgAnyOf()` | 후보 값 중 하나 수신 검증 |
| `ExpectMsgAllOf()` | 여러 메시지 수신 검증 |
| `ExpectNoMsg()` | 지정 시간 동안 메시지 없음 검증 |
| `ExpectMsgFrom<T>()` | 특정 sender로부터 온 메시지 검증 |
| `ReceiveN()` | n개 메시지 일괄 수신 |
| `FishForMessage()` | 조건을 만족하는 메시지 탐색 |
| `AwaitCondition()` | 조건식이 true가 될 때까지 폴링 |
| `AwaitAssert()` | assertion이 통과할 때까지 재시도 |
| `IgnoreMessages()` | 잡음 메시지 필터링 |

## 5. 타이밍 검증 (`Within`)

타이밍이 중요한 테스트는 `Within`으로 감싸서 전체 실행 상한을 명시합니다.

```csharp
Within(TimeSpan.FromMilliseconds(0), TimeSpan.FromSeconds(1), () =>
{
    TestActor.Tell(42);
    ExpectMsg<int>(42);
});
```

팁:

- 빌드 서버가 느리다면 시간 스케일 팩터(`akka.test.timefactor`)를 고려
- 타이트한 시간 제한은 CI에서 간헐 실패를 유발할 수 있음

## 6. Probe 활용 패턴

`TestProbe`는 다수 액터 상호작용 검증에 매우 유용합니다.

### 6.1 전달(Forward) 검증

```csharp
var probe = CreateTestProbe();
var forwarder = Sys.ActorOf(Props.Create(() => new Forwarder(probe.Ref)));

forwarder.Tell("msg", TestActor);
probe.ExpectMsg("msg");
Assert.Equal(TestActor, probe.LastSender);
```

### 6.2 DeathWatch 검증

```csharp
var probe = CreateTestProbe();
var target = Sys.ActorOf(Props.Create(() => new SomeActor()));

probe.Watch(target);
target.Tell(PoisonPill.Instance);
probe.ExpectMsg<Terminated>();
```

### 6.3 Probe 이름 부여

```csharp
var worker = CreateTestProbe("worker");
var aggregator = CreateTestProbe("aggregator");
```

로그/어설션에서 액터 경로 가독성이 좋아집니다.

## 7. 로그/예외 검증

통합 테스트에서는 내부 예외를 직접 보기 어렵기 때문에 로그 검증이 필요할 수 있습니다.

- `TestEventListener`를 활성화
- `EventFilter`로 예외/로그 패턴 검증

```hocon
akka.loggers = ["Akka.TestKit.TestEventListener, Akka.TestKit"]
```

## 8. 실무 주의사항

- `Thread.Sleep` 기반 대기보다 `ExpectMsg`/`AwaitAssert` 사용
- sender 검증이 필요한 테스트는 `ExpectMsgFrom` 또는 `probe.Sender` 활용
- `CallingThreadDispatcher` 환경에서 probe 간 양방향 통신은 데드락 위험 주의
- 테스트는 "성공 케이스 + 메시지 없음 + 타임아웃"을 함께 검증
