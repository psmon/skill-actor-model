---
name: dotnet-akka-net-test
description: C# + Akka.NET(1.5.x) 액터 코드의 유닛테스트를 생성합니다. Akka.TestKit.Xunit2를 사용해 메시지 송수신, sender 검증, probe 주입, 타이밍(ExpectNoMsg/Within) 테스트를 작성할 때 사용합니다. 닷넷은 운영 프로젝트와 테스트 프로젝트를 분리하는 구조를 기본으로 사용합니다.
argument-hint: "[테스트대상] [시나리오]"
---

# C# + Akka.NET 테스트 스킬

Akka.NET 1.5.x 기반 프로젝트의 테스트 코드를 작성할 때 사용합니다.

## 호환 버전

- Akka.NET: `1.5.x`
- 테스트 프레임워크: `xUnit`
- TestKit 패키지: `Akka.TestKit.Xunit2` (Akka 버전과 동일 계열 권장)
- .NET: `net9.0` (기본 스킬과 동일)

## 기본 구조 (권장)

닷넷은 테스트 분리 구성이 일반적이므로 아래 구조를 기본으로 사용합니다.

- `src/MyActorApp` (운영/라이브러리 프로젝트)
- `tests/MyActorApp.Tests` (테스트 프로젝트)
- 테스트 프로젝트는 운영 프로젝트를 `ProjectReference`로 참조

## 필수 패키지

테스트 프로젝트 예시:

```xml
<ItemGroup>
  <PackageReference Include="Akka.TestKit.Xunit2" Version="1.5.60" />
  <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.12.0" />
  <PackageReference Include="xunit" Version="2.9.2" />
  <PackageReference Include="xunit.runner.visualstudio" Version="2.8.2" />
</ItemGroup>
```

운영 프로젝트와 테스트 프로젝트의 Akka 버전은 동일 계열을 유지합니다.

## 테스트 작성 패턴

1. 테스트 클래스가 `TestKit` 상속
2. 액터 생성: `Sys.ActorOf(...)`
3. 자극 주입: `Tell(...)`
4. 검증: `ExpectMsg`, `ExpectNoMsg`, `CreateTestProbe`, `probe.ExpectMsg`
5. 필요 시 sender 검증: `Assert.Equal(TestActor, probe.Sender)`

## 권장 대기 전략 (중요)

- `Thread.Sleep` / `Task.Delay` 기반 고정 대기를 테스트 본문에서 사용하지 않습니다.
- 우선순위는 `AwaitAssert` + `ExpectMsg*` + `ExpectNoMsg` 입니다.
- 다중 `ActorSystem` 테스트처럼 TestKit 인스턴스 경계가 분리된 경우:
  - 관찰자 액터(`collector`) + `TaskCompletionSource`로 \"조건 충족 시 완료\" 구조를 사용합니다.
  - 전파 지연이 있는 PubSub는 scheduler 반복 publish 후 수신 즉시 cancel 패턴을 사용합니다.

```csharp
using Xunit;

public class HelloTests : TestKit
{
    [Fact]
    public void A_should_receive_world_from_B()
    {
        var b = Sys.ActorOf(Props.Create(() => new BActor()));
        var a = Sys.ActorOf(Props.Create(() => new AActor()));

        a.Tell(new Start(b, TestActor));

        ExpectMsg("World", TimeSpan.FromSeconds(3));
        ExpectNoMsg(TimeSpan.FromMilliseconds(200));
    }
}
```

## sample21 기반 강화 패턴

- 운영 프로젝트(`HelloActors`)와 테스트 프로젝트(`HelloActors.Tests`)를 분리합니다.
- 메시지는 `record`로 모델링하고 `Start(TargetB, ReportTo)`로 시작 의존성을 주입합니다.
- `AActor` 내부에 `_reportTo`를 캐시해 `World`를 최종 검증 지점(TestActor)으로 전달합니다.
- 테스트는 `ExpectMsg` 뒤에 `ExpectNoMsg`를 배치해 추가 메시지 누출을 검증합니다.

```csharp
public sealed record Start(IActorRef TargetB, IActorRef ReportTo);
public sealed record Hello(IActorRef ReplyTo);
public sealed record World(string Message);

a.Tell(new Start(b, TestActor));
ExpectMsg("World", TimeSpan.FromSeconds(3));
ExpectNoMsg(TimeSpan.FromMilliseconds(200));
```

## 프롬프트 작성 규칙

- 요구사항에 타임아웃을 명시합니다. (예: 3초)
- Request-Response는 sender 또는 `replyTo` 기준으로 검증합니다.
- 부작용 없는 단위 테스트를 우선하고, 통합 테스트는 별도 프로젝트로 분리합니다.

## 참고 문서

- [skill-maker/docs/actor/testkit/dotnet-akka-net-test.md](../../../../skill-maker/docs/actor/testkit/dotnet-akka-net-test.md)
- [plugins/skill-actor-model/skills/dotnet-akka-net/SKILL.md](../dotnet-akka-net/SKILL.md)

## WebApplication 통합 업데이트 (2026-02-17)

- 콘솔 엔트리 중심 샘플을 웹 API 중심으로 확장할 때 아래 기본 API를 우선 제공합니다.
  - `GET /api/heath`
  - `GET /api/actor/hello`
  - `GET /api/cluster/info`
  - `POST /api/kafka/fire-event`
- Kafka 실행은 스케줄러 자동 실행보다 API 트리거 방식을 우선합니다.
- Swagger/OpenAPI와 파일 기반 로깅 구성을 기본 포함합니다.
- 플랫폼별 권장 웹 모드:
  - .NET: ASP.NET Core (.NET 10)
  - Java: Spring Boot MVC (Java 21, Spring Boot 3.5.x)
  - Kotlin: Spring WebFlux + Coroutine (Spring Boot 3.5.x)

## Web 전환 테스트 주의사항 (2026-02)

1. 기존 actor 단위 테스트를 유지한 상태에서 웹 전환으로 추가된 메시지 계약 테스트를 반드시 보강합니다.
2. `/api/actor/hello`, `/api/cluster/info`, `/api/kafka/fire-event`에 대응하는 액터 응답 테스트를 각각 둡니다.
3. Kafka 트리거는 스케줄 기반이 아닌 API 단발 실행 기준으로 테스트 시나리오를 갱신합니다.
4. 클러스터 테스트는 최소 2노드 Up 상태를 확인한 뒤 기능 테스트를 수행합니다.
5. 종료 테스트는 actor stop/coordinated shutdown 확인에 집중하고 Kafka 종료는 제외합니다.
6. 런타임 버전 제약이 있을 경우(예: net10) SDK 컨테이너 기반 테스트 경로를 같이 제공합니다.
