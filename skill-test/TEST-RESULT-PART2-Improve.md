# TEST RESULT PART2 Improve

## 개선 상세

### A. 스킬 참조 기반 적용

#### 1) C# Akka.NET (`dotnet-akka-net*`, `dotnet-akka-net-cluster`, `dotnet-akka-net-test`, `dotnet-akka-net-infra`)
- `ClusterSingletonManager/Proxy` 패턴으로 Kafka 작업 단일 실행 보장
- `ClusterListenerActor`에서 `MemberUp` + 멤버 수 확인 후 지연 트리거
- TestKit 기반 1회 실행 보장 테스트 추가

#### 2) Java Akka Classic (`java-akka-classic*`, `java-akka-classic-cluster`, `java-akka-classic-test`, `java-akka-classic-infra`)
- `ClusterSingletonManager + Proxy` 구성 및 1회 실행 플래그 적용
- 클러스터 이벤트 기반 자동 실행 + JUnit/akka-testkit 테스트 추가

#### 3) Kotlin Pekko Typed (`kotlin-pekko-typed*`, `kotlin-pekko-typed-cluster`, `kotlin-pekko-typed-test`, `kotlin-pekko-typed-infra`)
- `ClusterSingleton + SingletonActor` 구성
- `started` 플래그로 중복 실행 차단
- `pipeToSelf`로 Kafka 완료/실패를 액터 메시지로 수렴해 로그 신뢰성 강화

### B. 스킬 외 추가 적용 (Petabridge 아이디어 채택)
- Streams 기반 Kafka round-trip 파이프라인 직접 추가
  - Producer: `plainSink`
  - Consumer: `plainSource`
- Producer 안정 옵션 반영
  - `enable.idempotence=true`
  - `acks=all`
- Consumer group을 실행별 unique id로 생성하여 재시작 간 간섭 완화
- 프로젝트별 토픽 분리로 완전 독립 실행 보장

## 인프라 개선
- Kafka standalone 매니페스트 신규 추가
  - `skill-test/infra/k8s-kafka-standalone.yaml`
- 각 프로젝트 StatefulSet에 Kafka 관련 env 추가
  - `KAFKA_BOOTSTRAP_SERVERS`
  - `KAFKA_TOPIC`
  - `KAFKA_GROUP_ID_PREFIX`
  - `KAFKA_START_DELAY_SECONDS`

## 통합 검증 중 발견/수정 이슈
1. Kafka 이미지 태그
- 문제: `bitnami/kafka:3.7` pull 실패 (manifest not found)
- 조치: `apache/kafka:3.7.1` + 해당 이미지용 `KAFKA_*`, `CLUSTER_ID` env로 전환

2. Dotnet Kafka 설정
- 문제: `akka.kafka` 섹션/dispatcher 누락으로 런타임 실패
- 조치: `Program.cs` HOCON에 `akka.kafka.producer`, `akka.kafka.consumer`, `akka.kafka.default-dispatcher` 추가

3. Kotlin 완료 로그 가시성
- 문제: 완료 상태 로그가 불명확
- 조치: `pipeToSelf` + `KafkaRunCompleted/KafkaRunFailed` 메시지로 명시 로그 처리

## 결과 요약
- 유닛테스트: dotnet/java/kotlin 모두 성공
- 쿠버 통합검증: Kafka 선기동 → 프로젝트별 2노드 조인 + Kafka round-trip 성공 로그 확인 → 전체 graceful shutdown 완료

---

## 추가 개선: Kotlin Pekko 1.4 마이그레이션 (2026-02-17)

### A. 스킬 참조 기반 적용

#### 1) `kotlin-pekko-typed`
- 코어 Typed/Cluster/Stream/TestKit 모듈을 Pekko `1.4.0`으로 상향.
- `_2.13` 바이너리 기준에서 `scala-library:2.13.18` 해석 확인.

#### 2) `kotlin-pekko-typed-cluster`
- `ClusterSingleton`/`ClusterListener` 흐름을 유지하며 1.4 환경에서 동작 검증.
- `MemberUp` 이벤트 기반 클러스터 준비 판정 로직 회귀 없음 확인.

#### 3) `kotlin-pekko-typed-test`
- 기존 테스트 유지 + 신규 테스트(`StopKafkaStream` graceful stop) 추가.
- `./gradlew clean test` 기준 전체 통과.

#### 4) `kotlin-pekko-typed-infra`
- Kubernetes API Discovery + Cluster Bootstrap 방식으로 전환.
- `ServiceAccount/Role/RoleBinding` 반영.
- `management` 포트 및 `contact-point-discovery.port-name` 명시.

### B. 스킬 외 추가 적용
- Bootstrap self contact-point/lowest-address 비교 실패 이슈 대응:
  - `MANAGEMENT_HOSTNAME`을 Pod DNS가 아닌 Pod IP(`status.podIP`)로 주입해 self-join 판정 안정화.
- 배포 과정에서 발생한 일시적 DNS/프로빙 실패를 재시도 롤아웃으로 수습하고 최종 조인/기능 성공 로그 확보.

### C. 검증 결과
- 클러스터:
  - `Member is Up` 2건 확인
  - `Cluster is ready (2/2 members Up)` 확인
- 기능:
  - `Kafka stream round-trip succeeded` 확인
- 종료:
  - Kubernetes 리소스 graceful 삭제 후 잔여 리소스 없음 확인

---

## WebApplication 업그레이드 개선 (2026-02-17)

### A. 스킬 참조 기반 적용
- `dotnet-akka-net`, `dotnet-akka-net-cluster`, `dotnet-akka-net-infra`
  - ASP.NET Core Web API + Akka DI 브리지(`IHostedService` + `Akka.DependencyInjection`) 적용
- `java-akka-classic`, `java-akka-classic-cluster`, `java-akka-classic-infra`
  - Spring Boot MVC + Akka Classic 통합, Spring-Akka Extension 방식 반영
- `kotlin-pekko-typed`, `kotlin-pekko-typed-cluster`, `kotlin-pekko-typed-infra`
  - Spring WebFlux + Coroutine + Pekko Typed `AskPattern` 통합 적용

### B. 스킬 외 추가 적용
- 공통 REST 계약 통일:
  - `/api/heath`, `/api/actor/hello`, `/api/cluster/info`, `/api/kafka/fire-event`
- Kafka 트리거 전환:
  - 클러스터 준비 후 지연 스케줄 방식 제거
  - API 호출 시 단발 실행 방식으로 변경
- 인프라 헬스체크 정렬:
  - K8s readiness를 remoting TCP에서 HTTP(`/api/heath`) 기준으로 전환

### C. 검증 결과 요약
- Java/Kotlin: 테스트 및 bootJar 성공
- Dotnet: SDK10 컨테이너 기반 테스트 11/11 성공, Kubernetes API/클러스터/Kafka 검증 완료

### D. WebApplication 후속 안정화 (Dotnet, 2026-02-17)
- Swashbuckle 런타임 충돌 해결:
  - 문제: `Swashbuckle.AspNetCore 7.0.0` + `.NET 10`에서 `TypeLoadException`
  - 조치: `Swashbuckle.AspNetCore 10.0.0`으로 상향
- Akka Ask 통신 방식 정렬:
  - `replyTo` 메시지 계약에서 `Sender` 기반 응답으로 단순화
  - Minimal API `Ask(object, timeout)` 패턴으로 일관화
- Kubernetes 클러스터 주소 안정화:
  - 문제: appsettings 기본값 우선으로 ENV가 무시되어 `127.0.0.1` 바인딩
  - 조치: `ENV > POD_NAME 기반 DNS > appsettings` 우선순위로 수정
  - 결과: 2노드 정상 조인 및 `cluster info` API 정상 응답
- 검증 결과:
  - .NET10 SDK 컨테이너 기준 테스트 11/11 성공
  - `/api/*` + Swagger + Kafka round-trip + Member Up 로그까지 모두 확인
