# TEST RESULT PART2 (Kafka + Cluster)

## 범위
- `skill-test/projects/sample-cluster-dotnet`
- `skill-test/projects/sample-cluster-java`
- `skill-test/projects/sample-cluster-kotlin`

## 구현 요약
- 3개 프로젝트 공통 Kafka Streams 연동 완료
- 클러스터 멤버 조인 완료 후 `Cluster Singleton` 경유로 자동 1회 실행
- 프로젝트별 토픽 분리
  - dotnet: `cluster-dotnet-events`
  - java: `cluster-java-events`
  - kotlin: `cluster-kotlin-events`
- Kafka standalone 1개(`kafka.default.svc.cluster.local:9092`) 선기동 구성 완료

## 유닛테스트 결과
### 1) C# Akka.NET
- 실행: `dotnet test` (`sample-cluster-dotnet`)
- 결과: **성공**
- 통과: `9/9`
- 신규 검증:
  - `KafkaStreamSingletonActorTests.Kafka_singleton_should_execute_stream_runner_only_once`
  - `KafkaStreamSingletonActorTests.ClusterListener_should_trigger_kafka_singleton_once_when_cluster_ready`

### 2) Java Akka Classic
- 실행: `./gradlew test` (`sample-cluster-java`)
- 결과: **성공**
- 신규 검증:
  - `KafkaStreamSingletonActorTest.kafkaSingletonShouldExecuteRunnerOnlyOnce`

### 3) Kotlin Pekko Typed
- 실행: `./gradlew test` (`sample-cluster-kotlin`)
- 결과: **성공**
- 신규 검증:
  - `ClusterActorTest.kafka singleton should execute runner only once`

## Kubernetes 통합 검증 결과 (실행 완료)
실행일시: **2026-02-17 (KST)**

### 1) Kafka standalone 선기동
- 적용: `kubectl apply -f skill-test/infra/k8s-kafka-standalone.yaml`
- 결과: `statefulset/kafka` Ready 1/1
- 로그 확인: `Kafka Server started` 확인

### 2) Dotnet 2노드 검증
- 배포: `skill-test/projects/sample-cluster-dotnet/infra/k8s-cluster.yaml`
- 결과:
  - `Member is Up` 2개 노드 확인
  - `Cluster is ready (2/2 members Up)` 확인
  - `Kafka stream round-trip succeeded` 확인

### 3) Java 2노드 검증
- 배포: `skill-test/projects/sample-cluster-java/infra/k8s-cluster.yaml`
- 결과:
  - `Member is Up` 2개 노드 확인
  - `Cluster is ready (2/2 members Up)` 확인
  - `Kafka stream round-trip succeeded` 확인

### 4) Kotlin 2노드 검증
- 배포: `skill-test/projects/sample-cluster-kotlin/infra/k8s-cluster.yaml`
- 결과:
  - `Member is Up` 2개 노드 확인
  - `Cluster is ready (2/2 members Up)` 확인
  - `Kafka stream round-trip succeeded` 확인

### 5) Graceful shutdown
- 프로젝트별 삭제:
  - dotnet/java/kotlin `kubectl delete -f .../infra/k8s-cluster.yaml`
- Kafka 삭제:
  - `kubectl delete -f skill-test/infra/k8s-kafka-standalone.yaml`
- 결과: `default` 네임스페이스 내 관련 Pod/Service/StatefulSet 모두 삭제 확인

## 비고
- Kafka 이미지는 `bitnami/kafka:3.7` 태그 미존재로 pull 실패하여 `apache/kafka:3.7.1`로 전환
- Dotnet 실행 중 발견된 Kafka 설정 이슈(`akka.kafka` config/dispatcher) 보완 후 round-trip 성공 확인
- Kotlin은 완료 로그 가시성을 위해 `pipeToSelf` 완료/실패 메시지 처리 추가

---

## 추가 검증: Kotlin Pekko 1.4 마이그레이션
실행일시: **2026-02-17 (KST)**

### 1) 유닛테스트
- 대상: `skill-test/projects/sample-cluster-kotlin`
- 실행: `./gradlew clean test`
- 결과: **성공**
- 포함 검증:
  - 기존 테스트 유지 통과
  - 신규 테스트 `kafka singleton should stop gracefully on stop message` 통과
- 추가 실행: `./gradlew test --tests "cluster.kotlin.TwoNodeClusterTest"` → **성공** (2-Node 시나리오 회귀 확인)

### 2) Kubernetes 클러스터 조인/기능 검증
- Kafka 상태 확인 후 미가동 상태에서 재기동:
  - `kubectl apply -f skill-test/infra/k8s-kafka-standalone.yaml`
  - `kubectl rollout status statefulset/kafka`
- Kotlin 클러스터 배포:
  - `kubectl apply -f skill-test/projects/sample-cluster-kotlin/infra/k8s-cluster.yaml`
  - `kubectl rollout status statefulset/pekko-cluster`
- 로그 검증 결과:
  - `ClusterBootstrap` 시작 및 `kubernetes-api` discovery 확인
  - `Member is Up` 2건 확인
  - `Cluster is ready (2/2 members Up)` 확인
  - `Starting Kafka stream round-trip once...` 확인
  - `Kafka stream round-trip succeeded...` 확인

### 3) 그레이스풀 셧다운
- `kubectl delete -f skill-test/projects/sample-cluster-kotlin/infra/k8s-cluster.yaml`
- `kubectl delete -f skill-test/infra/k8s-kafka-standalone.yaml`
- 결과: default 네임스페이스 잔여 Pod/StatefulSet 없음 확인

---

## WebApplication 전환 추가 결과 (2026-02-17)

### 적용 요약
- dotnet/java/kotlin 3종 모두 웹 API 엔드포인트 추가
- `kafka fire-event`를 스케줄러 방식에서 API 트리거 방식으로 전환
- Swagger + 파일 기반 로깅 적용

### 추가 테스트
- Kotlin: `./gradlew test`, `./gradlew bootJar` 성공
- Java: `./gradlew test`, `./gradlew bootJar` 성공
- Dotnet: SDK10 컨테이너 기반 `dotnet test ClusterActors.Tests/ClusterActors.Tests.csproj -c Release` 성공 (11/11)

### 비고
- Dotnet은 Swashbuckle 버전 정렬 및 Ask/Sender 패턴 정리 후 Kubernetes 통합 검증까지 완료

## WebApplication 후속 재검증 (2026-02-17)

### Dotnet 재검증
- 유닛테스트:
  - SDK10 컨테이너 기반 실행으로 전환
  - 결과: **11/11 통과**
- Kubernetes 통합:
  - `akkanet-cluster` 2노드 `Member is Up` 확인
  - `/api/heath`, `/api/actor/hello`, `/api/cluster/info`, `/api/kafka/fire-event`, `/swagger/index.html` 모두 성공
  - Kafka 발행/수신 round-trip 로그 확인

### Graceful shutdown 재확인 (Kafka 유지)
- 실행: `kubectl scale statefulset akka-cluster pekko-cluster akkanet-cluster --replicas=0`
- 결과:
  - Java/Kotlin/Dotnet 애플리케이션 Pod 모두 종료
  - Kafka(`kafka-0`)는 계속 Running 유지
