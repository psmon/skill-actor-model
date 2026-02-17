# TEST RESULT WebApplication

작성일: 2026-02-17

## 범위
- `skill-test/projects/sample-cluster-dotnet`
- `skill-test/projects/sample-cluster-java`
- `skill-test/projects/sample-cluster-kotlin`

## 공통 전환 결과
- 콘솔 엔트리포인트 -> 웹 애플리케이션 API 전환
- Swagger 지원 추가
- 공통 API 추가
  - `GET /api/heath`
  - `GET /api/actor/hello`
  - `GET /api/cluster/info`
  - `POST /api/kafka/fire-event`
- Kafka 스케줄 트리거 제거, API 트리거로 이동
- 파일 로깅 구성 적용

## 진영별 통합 방식

### 1) C# Akka.NET + ASP.NET Core (.NET 10)
- 통합 방식:
  - `ActorRuntime : IHostedService`에서 ActorSystem 생성/종료 관리
  - `Akka.DependencyInjection`으로 `HelloActor`에 DI 적용
  - Minimal API에서 `Ask`로 액터 호출
- DI 전략:
  - `IWelcomeMessageProvider` -> `HelloActor`
  - ASP.NET DI + Akka DI 브리지
- 파일:
  - `skill-test/projects/sample-cluster-dotnet/ClusterActors/Program.cs`
  - `skill-test/projects/sample-cluster-dotnet/ClusterActors/ActorRuntime.cs`

### 2) Java Akka Classic + Spring Boot MVC
- 통합 방식:
  - Spring Boot 시작 시 `AkkaActorRuntime` 컴포넌트에서 ActorSystem 초기화
  - Spring-Akka Extension(`SpringActorProducer`)로 액터 빈 생성
  - MVC Controller에서 `PatternsCS.ask`로 액터 호출
- DI 전략:
  - Spring Bean(`helloActorBean`)을 Akka actor로 생성
  - `WelcomeMessageProvider` 주입
- 파일:
  - `skill-test/projects/sample-cluster-java/src/main/java/cluster/java/config/AkkaActorRuntime.java`
  - `skill-test/projects/sample-cluster-java/src/main/java/cluster/java/api/ApiControllers.java`

### 3) Kotlin Pekko Typed + Spring WebFlux + Coroutine
- 통합 방식:
  - Spring Boot 앱 내부 컴포넌트(`AkkaActorRuntime`)에서 Typed ActorSystem 관리
  - WebFlux `suspend` API에서 `AskPattern` + coroutine `await` 사용
  - `ClusterSingleton`을 통해 Kafka singleton proxy 제공
- DI 전략:
  - Spring Component(`WelcomeMessageProvider`)를 Typed actor 생성 시 주입
- 파일:
  - `skill-test/projects/sample-cluster-kotlin/src/main/kotlin/cluster/kotlin/Main.kt`

## 테스트 결과

### Kotlin
- `./gradlew test`: 성공
- `./gradlew bootJar`: 성공

### Java
- `./gradlew test`: 성공
- `./gradlew bootJar`: 성공

### Dotnet
- 로컬 `dotnet test ClusterActors.sln`: 환경 SDK에 따라 제약 가능
- SDK10 컨테이너 재검증: `dotnet test ClusterActors.Tests/ClusterActors.Tests.csproj -c Release`
- 결과: **성공 (11/11)**

## 인프라 반영
- Java/Kotlin/Dotnet Dockerfile을 웹 앱 구동 기준으로 갱신
- Java/Kotlin/Dotnet K8s 매니페스트에 HTTP 포트/헬스 프로브 반영

## Dotnet 후속 검증 업데이트 (2026-02-17)
- Swashbuckle `.NET 10` 호환 버전으로 정렬 (`Swashbuckle.AspNetCore 10.0.0`)
- `Ask` 호출 패턴을 Akka.NET classic `Sender` 응답 방식으로 정리
- Kubernetes 환경변수 우선순위/Pod DNS 계산 로직 보정 (`ActorRuntime`)

### Dotnet 테스트 재검증
- 실행: `docker run --rm -v ... mcr.microsoft.com/dotnet/sdk:10.0 dotnet test ClusterActors.Tests/ClusterActors.Tests.csproj -c Release`
- 결과: **성공 (11/11)**

### Dotnet Kubernetes API 검증
- `/api/heath`: 성공
- `/api/actor/hello`: 성공 (`wellcome actor world!`)
- `/api/cluster/info`: 성공 (memberCount=2, members=Up)
- `/api/kafka/fire-event`: 성공 (produced/observed 일치)
- `/swagger/index.html`: HTTP `200`

### Dotnet 로그 검증
- `Member is Up` 2노드 확인
- `Kafka stream round-trip succeeded` 확인
