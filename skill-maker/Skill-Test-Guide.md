# Actor Test Skill Guide

본 문서는 `plugins/skill-actor-model/skills` 하위에 추가된 테스트 스킬 3종의 사용법과 베스트 프랙티스를 정리합니다.

## 1. 추가된 테스트 스킬

- `kotlin-pekko-typed-test`
  - 대상: Kotlin + Apache Pekko Typed 1.1.x
  - 핵심: `ActorTestKit`, `TestProbe`, `replyTo` 기반 검증
  - 테스트 모듈: `pekko-actor-testkit-typed_2.13`

- `java-akka-classic-test`
  - 대상: Java + Akka Classic 2.7.x
  - 핵심: `akka-testkit`, `TestKit`, `expectMsg/expectNoMessage/within`
  - 테스트 모듈: `akka-testkit_2.13`

- `dotnet-akka-net-test`
  - 대상: C# + Akka.NET 1.5.x
  - 핵심: `Akka.TestKit.Xunit2`, `TestKit` 상속, probe/sender 검증
  - 테스트 모듈: `Akka.TestKit.Xunit2`
  - 구조 원칙: 운영 프로젝트와 테스트 프로젝트 분리

## 2. 호환 버전 원칙

- 기본 스킬의 런타임 버전과 테스트 모듈 버전을 같은 메이저/마이너 계열로 유지합니다.
- 권장 기준:
  - Pekko: `1.1.x` + `pekko-actor-testkit-typed_2.13:1.1.x`
  - Akka Classic: `2.7.x` + `akka-testkit_2.13:2.7.x`
  - Akka.NET: `1.5.x` + `Akka.TestKit.Xunit2:1.5.x`

## 3. 샘플 프로젝트 검증 대상

- `skill-test/projects/sample19`: Kotlin + Pekko Typed 테스트 샘플
- `skill-test/projects/sample20`: Java + Akka Classic 테스트 샘플
- `skill-test/projects/sample21`: C# + Akka.NET 테스트 샘플 (운영/테스트 프로젝트 분리)

공통 시나리오:
- A 액터가 B 액터에게 `Hello` 전송
- B 액터가 A 액터에게 `World` 응답
- 테스트에서 최종 응답 `"World"` 수신을 검증

## 4. 베스트 프랙티스

1. 프로토콜 중심 테스트
- 테스트는 구현 내부가 아니라 메시지 프로토콜(입력/출력) 기준으로 작성합니다.
- `replyTo` 또는 sender를 명시적으로 사용해 관찰 가능성을 확보합니다.

2. 타이밍 안정성 확보
- `Thread.sleep` 대신 TestKit의 `expectMessage`, `expectNoMessage`, `within`을 사용합니다.
- 모든 타임아웃을 테스트 코드에 명시해 CI 환경 편차를 줄입니다.

3. 리소스 수명주기 관리
- Java/Pekko는 테스트 종료 시 ActorSystem/TestKit 종료를 명시합니다.
- .NET은 테스트 클래스(`TestKit`) 수명주기를 이용해 시스템 정리를 보장합니다.

4. 닷넷 분리 구성
- 운영 코드와 테스트 코드를 다른 프로젝트로 분리합니다.
- 테스트 프로젝트는 `ProjectReference`로 운영 프로젝트를 참조합니다.
- 이 구조를 기본 템플릿으로 유지하면 재사용성과 유지보수성이 높아집니다.

5. 실패를 스킬 개선으로 환류
- 빌드/테스트 실패 시 코드만 고치지 말고 스킬 지침의 누락 항목(패키지, using/import, 버전 호환 규칙)을 즉시 보강합니다.

## 5. 권장 프롬프트 예시

- Kotlin:
  - `kotlin-pekko-typed-test sample19 구조로 Hello/World 액터 테스트를 작성하고 ActorTestKit 기반으로 검증`
- Java:
  - `java-akka-classic-test akka-testkit으로 A->B->A World 응답 시나리오를 JUnit5 테스트로 작성`
- .NET:
  - `dotnet-akka-net-test 운영 프로젝트와 테스트 프로젝트를 분리해 Akka.TestKit.Xunit2 테스트 작성`
