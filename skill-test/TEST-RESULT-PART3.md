# TEST-RESULT-PART3

## 개요
- 작업 목표: Cafe24 API 호출 제한 대응(Leaky Bucket) 컨셉을 3개 클러스터 샘플(Kotlin Pekko Typed / Java Akka Classic / C# Akka.NET)에 공통 적용
- 범위: MallId별 백프레셔 안전호출, 더미 API 기반 검증, 클러스터 싱글턴 메트릭 집계, 유닛테스트/쿠버 통합검증/그레이스풀 종료
- 수행 일시: 2026-03-01

## 구현 요약
- 공통 기능
  - MallId별 호출 분리(요청 경로 분리)
  - Streams throttle + adaptive delay + 429 재시도
  - Dummy Cafe24 API(실호출 없음, hello->world, 나머지 echo)
  - 클러스터 싱글턴 메트릭 집계(totalCalls, throttled429, avgQueueDelayMs)
  - API 추가
    - `GET /api/cafe24/call?mallId=...&word=...`
    - `GET /api/cafe24/metrics?mallId=...`
- Kubernetes env 추가
  - `CAFE24_BUCKET_CAPACITY`
  - `CAFE24_LEAK_RATE_PER_SECOND`
  - `CAFE24_PER_MALL_MAX_RPS`

## 유닛테스트 결과
- Kotlin (`sample-cluster-kotlin`)
  - 실행: `./gradlew test`
  - 결과: 성공
  - 신규 테스트: `Cafe24ApiActorTest`
- Java (`sample-cluster-java`)
  - 실행: `./gradlew test`
  - 결과: 성공
  - 신규 테스트: `Cafe24ApiActorTest`
- Dotnet (`sample-cluster-dotnet`)
  - 실행: `dotnet test`
  - 결과: 성공 (`Passed: 13, Failed: 0`)
  - 신규 테스트: `Cafe24ApiActorTests`

## Kubernetes 통합 검증 결과

### 1) Kotlin Pekko Typed
- 배포
  - `kubectl apply -f skill-test/infra/k8s-kafka-standalone.yaml`
  - `kubectl apply -f skill-test/projects/sample-cluster-kotlin/infra/k8s-cluster.yaml`
  - `kubectl rollout status statefulset/pekko-cluster`
- 조인 확인
  - `memberCount=2` 확인 (`/api/cluster/info`)
  - 로그: `Member is Up` 2건
- 기능 확인
  - `/api/cafe24/call?mallId=mall-a&word=hello` -> `world`, `200`
  - `/api/cafe24/call?mallId=mall-b&word=alpha` -> `alpha`, `200`
  - mall-a burst 호출 후 `/api/cafe24/metrics?mallId=mall-a` -> `totalCalls=7`, `throttled429=0`
- 로그 확인
  - `Cafe24 safe call mall=... status=200 bucket=.../...`

### 2) Java Akka Classic
- 배포
  - `kubectl apply -f skill-test/projects/sample-cluster-java/infra/k8s-cluster.yaml`
  - `kubectl rollout status statefulset/akka-cluster`
- 조인 확인
  - `memberCount=2` 확인 (`/api/cluster/info`)
  - 로그: `Member is Up` 2건
- 기능 확인
  - `/api/cafe24/call?mallId=mall-a&word=hello` -> `world`, `200`
  - `/api/cafe24/call?mallId=mall-b&word=beta` -> `beta`, `200`
  - mall-a burst 호출 후 `/api/cafe24/metrics?mallId=mall-a` -> `totalCalls=7`, `throttled429=0`
- 로그 확인
  - `Cafe24 safe call mall=... status=200 bucket=.../...`

### 3) C# Akka.NET
- 배포
  - `kubectl apply -f skill-test/projects/sample-cluster-dotnet/infra/k8s-cluster.yaml`
  - `kubectl rollout status statefulset/akkanet-cluster`
- 조인 확인
  - `memberCount=2` 확인 (`/api/cluster/info`)
  - 로그: `Member is Up` 2건
- 기능 확인
  - `/api/cafe24/call?mallId=mall-a&word=hello` -> `world`, `200`
  - `/api/cafe24/call?mallId=mall-b&word=gamma` -> `gamma`, `200`
  - mall-a burst 호출 후 `/api/cafe24/metrics?mallId=mall-a` -> `totalCalls=7`, `throttled429=0`
- 로그 확인
  - `Cafe24 safe call mall=... status=200 bucket=.../...`

## 그레이스풀 셧다운
- 프로젝트별 StatefulSet 삭제 후 Pod 종료 확인
  - `pekko-cluster`, `akka-cluster`, `akkanet-cluster`
- 마지막으로 Kafka 삭제
  - `kubectl delete -f skill-test/infra/k8s-kafka-standalone.yaml`
- 최종 상태
  - `kubectl get pods` -> `No resources found in default namespace.`

## 결론
- 로컬 단일 노드 컨셉을 클러스터 환경으로 확장하면서 MallId별 제약 관리와 중앙 메트릭 집계를 동시에 만족
- 3개 플랫폼 모두 유닛/통합 검증 및 종료 시나리오를 통과
