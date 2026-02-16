# sample-cluster-kotlin — Kotlin Pekko Typed Cluster

Kotlin + Apache Pekko Typed 1.1.3 기반 클러스터 프로젝트.

## 구조

```
src/main/kotlin/cluster/kotlin/
├── Main.kt                    # 엔트리포인트 (ActorSystem + whenTerminated 블로킹)
├── ClusterListenerActor.kt    # 클러스터 MemberUp 이벤트 리스너
├── CounterSingletonActor.kt   # Singleton 카운터 액터
└── PubSubManagerActor.kt      # Topic 기반 PubSub 매니저

src/main/resources/
└── application.conf           # HOCON 설정 (환경변수 fallback 지원)

src/test/kotlin/cluster/kotlin/
├── ClusterActorTest.kt        # 1-Node 클러스터 테스트
└── TwoNodeClusterTest.kt      # 2-Node 클러스터 테스트

infra/
├── Dockerfile                 # 멀티스테이지 빌드 (gradle → temurin JRE)
└── k8s-cluster.yaml           # Headless Service + StatefulSet (2 replicas)
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
# 배포
kubectl apply -f infra/k8s-cluster.yaml

# 상태 확인 (pod-0 → pod-1 순서로 기동)
kubectl get pods -w

# 로그 확인 ("Member is Up" 로그가 각 노드에서 2개)
kubectl logs pekko-cluster-0
kubectl logs pekko-cluster-1

# 정리 (coordinated-shutdown으로 graceful leave)
kubectl delete -f infra/k8s-cluster.yaml
```

## 환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `CLUSTER_HOSTNAME` | `127.0.0.1` | 노드 바인드 호스트 |
| `CLUSTER_PORT` | `0` (자동) | 리모팅 포트 |
| `CLUSTER_SEED_NODES` | `[]` (빈 목록) | seed-nodes 목록 (HOCON 배열 형식) |
| `CLUSTER_MIN_NR` | `1` | 클러스터 최소 멤버 수 |

## K8s 아키텍처

- **StatefulSet** (`podManagementPolicy: OrderedReady`): pod-0(Seed) 먼저 기동
- **Headless Service**: `pekko-cluster-{ordinal}.pekko-cluster.default.svc.cluster.local` DNS
- **seed-nodes**: pod-0만 seed로 지정, pod-1은 joining
- **프로토콜**: `pekko://ClusterSystem@<podIP>:25520`
