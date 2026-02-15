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
