# TEST RESULT - Pekko Migration (1.1.x -> 1.4.x)

## 대상
- 프로젝트: `skill-test/projects/sample-cluster-kotlin`
- 마이그레이션 범위: Apache Pekko `1.1.x` -> `1.4.0`
- Scala Binary: `_2.13` (해결 결과 `scala-library:2.13.18`)
- 수행일: 2026-02-17

## 핵심 패치 요약 (사소한 버그 수정 제외)
1. **코어 의존성 업그레이드**
- `pekko-actor-typed`, `pekko-cluster-typed`, `pekko-cluster-sharding-typed`, `pekko-stream`, `pekko-slf4j`, `pekko-actor-testkit-typed`를 `1.4.0`으로 상향.

2. **Pekko Management + Cluster Bootstrap 도입**
- `pekko-management`, `pekko-management-cluster-bootstrap`, `pekko-discovery-kubernetes-api` 추가.
- `Main.kt`에서 `PekkoManagement.get(system).start()` 및 `ClusterBootstrap.get(system).start()` 실행.

3. **Kubernetes API Discovery 기반 클러스터 조인 전환**
- `application.conf`에 `discovery.method = kubernetes-api` 및 `management.cluster.bootstrap.contact-point-discovery` 설정 반영.
- `port-name = "management"`를 명시해 bootstrap contact-point 포트 해석 안정화.

4. **Kubernetes 인프라 강화**
- `infra/k8s-cluster.yaml`에 `ServiceAccount`/`Role`/`RoleBinding` 추가(RBAC).
- `management` 포트(`8558`) 노출 및 bootstrap 관련 env(`CLUSTER_BOOTSTRAP_*`) 반영.
- `MANAGEMENT_HOSTNAME`을 Pod IP로 주입해 Bootstrap self contact-point 비교 문제 해소.

5. **테스트 보강**
- `ClusterActorTest`에 신규 테스트 추가:
  - `kafka singleton should stop gracefully on stop message`
- 기존 테스트 유지 + 신규 테스트 통과 확인.

## 결과
- 빌드/테스트: `./gradlew clean test` 성공.
- Kubernetes 검증: 2노드 조인, `Member is Up` 확인, Kafka round-trip 성공 로그 확인.
