# sample-cluster-kotlin — Kotlin Pekko Typed Cluster

Kotlin + Apache Pekko Typed 1.4.0 기반 클러스터 프로젝트.

## 1.1.x -> 1.4.x 변경 요약
- Pekko Core/Cluster/Stream/TestKit `1.4.0` 적용
- Pekko Management + Cluster Bootstrap + Kubernetes API Discovery 적용
- K8s RBAC(ServiceAccount/Role/RoleBinding) 추가
- Bootstrap contact-point 포트(`management:8558`) 및 Pod IP 기반 Management hostname 적용

## 구조

```
src/main/kotlin/cluster/kotlin/
├── Main.kt                    # 엔트리포인트 (Pekko Management + Cluster Bootstrap 시작)
├── ClusterListenerActor.kt    # 클러스터 MemberUp 이벤트 리스너
├── CounterSingletonActor.kt   # Singleton 카운터 액터
├── KafkaStreamSingletonActor.kt # Pekko Kafka Streams 1회 실행 싱글톤
└── PubSubManagerActor.kt      # Topic 기반 PubSub 매니저

src/main/resources/
└── application.conf           # HOCON 설정 (환경변수 fallback 지원)

src/test/kotlin/cluster/kotlin/
├── ClusterActorTest.kt        # 1-Node 클러스터 테스트
└── TwoNodeClusterTest.kt      # 2-Node 클러스터 테스트

infra/
├── Dockerfile                 # 멀티스테이지 빌드 (gradle → temurin JRE)
└── k8s-cluster.yaml           # Headless Service + StatefulSet + RBAC (2 replicas)
```

## 로컬 빌드 & 테스트

```bash
# WSL CRLF 이슈 해결
sed -i 's/\r$//' gradlew

# 유닛테스트 실행
./gradlew test

# 애플리케이션 실행 (Ctrl+C로 종료)
./gradlew run
```

## Docker 이미지 빌드

```bash
docker build -f infra/Dockerfile -t sample-cluster-kotlin:latest .
```

## Kubernetes 배포 (Docker Desktop)

```bash
# Kafka standalone 1개 먼저 구동
kubectl apply -f ../../infra/k8s-kafka-standalone.yaml
kubectl rollout status statefulset/kafka

# 배포
kubectl apply -f infra/k8s-cluster.yaml

# 상태 확인 (pod-0 → pod-1 순서로 기동)
kubectl get pods -w

# 로그 확인 ("Member is Up" 로그가 각 노드에서 2개)
kubectl logs pekko-cluster-0
kubectl logs pekko-cluster-1
# Bootstrap/Management 확인
kubectl logs pekko-cluster-0 | grep "Bootstrap"
kubectl logs pekko-cluster-1 | grep "Bootstrap"
# Kafka 1회 실행 확인
kubectl logs pekko-cluster-0 | grep "Kafka stream round-trip"
kubectl logs pekko-cluster-1 | grep "Kafka stream round-trip"

# 정리 (coordinated-shutdown으로 graceful leave)
kubectl delete -f infra/k8s-cluster.yaml
kubectl delete -f ../../infra/k8s-kafka-standalone.yaml
```

## 환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `CLUSTER_HOSTNAME` | `127.0.0.1` | 노드 바인드 호스트 |
| `CLUSTER_PORT` | `0` (자동) | 리모팅 포트 |
| `CLUSTER_SEED_NODES` | 미설정 | 로컬 fallback seed-nodes (Bootstrap과 혼용 금지) |
| `CLUSTER_MIN_NR` | `1` | 클러스터 최소 멤버 수 |
| `CLUSTER_BOOTSTRAP_SERVICE_NAME` | `pekko-cluster` | Bootstrap 대상 서비스명 |
| `CLUSTER_BOOTSTRAP_REQUIRED_CONTACT_POINTS` | `2` | Bootstrap 최소 contact-point |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka.default.svc.cluster.local:9092` | Kafka bootstrap 서버 |
| `KAFKA_TOPIC` | `cluster-kotlin-events` | Kotlin 프로젝트 전용 토픽 |
| `KAFKA_GROUP_ID_PREFIX` | `cluster-kotlin-group` | Consumer group prefix |
| `KAFKA_START_DELAY_SECONDS` | `15` | 클러스터 안정화 후 Kafka 실행 지연 |

## K8s 아키텍처

- **StatefulSet** (`podManagementPolicy: OrderedReady`): pod-0(Seed) 먼저 기동
- **Headless Service**: `pekko-cluster-{ordinal}.pekko-cluster.default.svc.cluster.local` DNS
- **Discovery**: Kubernetes API + Pekko Cluster Bootstrap
- **RBAC**: ServiceAccount/Role/RoleBinding으로 pod list/watch/get 허용
- **프로토콜**: `pekko://ClusterSystem@<podIP>:25520`
