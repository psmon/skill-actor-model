# sample-cluster-dotnet — C# Akka.NET Cluster

C# + Akka.NET 1.5.60 기반 클러스터 프로젝트.

## 구조

```
ClusterActors/
├── Program.cs                 # 엔트리포인트 (ActorSystem + WhenTerminated 블로킹)
├── ClusterListenerActor.cs    # 클러스터 이벤트 리스너 (MemberUp/Unreachable/Removed)
├── CounterSingletonActor.cs   # Singleton 카운터 액터
├── KafkaStreamSingletonActor.cs # Akka.Streams.Kafka Producer/Consumer 1회 실행 싱글톤
├── PubSubPublisherActor.cs    # DistributedPubSub 발행자
└── PubSubSubscriberActor.cs   # DistributedPubSub 구독자

ClusterActors.Tests/
├── ClusterActorTests.cs
├── TwoNodeClusterTests.cs
└── KafkaStreamSingletonActorTests.cs # Kafka 싱글톤 1회 실행 보장 테스트

infra/
├── Dockerfile                 # 멀티스테이지 빌드 (dotnet sdk → runtime)
└── k8s-cluster.yaml           # Headless Service + StatefulSet (2 replicas)
```

## 로컬 빌드 & 테스트

```bash
# 유닛테스트 실행
dotnet test

# 애플리케이션 실행 (Ctrl+C로 종료)
dotnet run --project ClusterActors
```

## Docker 이미지 빌드

```bash
docker build -f infra/Dockerfile -t sample-cluster-dotnet:latest .
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
kubectl logs akkanet-cluster-0
kubectl logs akkanet-cluster-1
# Kafka 1회 실행 확인
kubectl logs akkanet-cluster-0 | grep "Kafka stream round-trip"
kubectl logs akkanet-cluster-1 | grep "Kafka stream round-trip"

# 정리 (coordinated-shutdown으로 graceful leave)
kubectl delete -f infra/k8s-cluster.yaml
kubectl delete -f ../../infra/k8s-kafka-standalone.yaml
```

## 환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `CLUSTER_HOSTNAME` | `127.0.0.1` | 노드 바인드 호스트 |
| `CLUSTER_PORT` | `0` (자동) | 리모팅 포트 |
| `CLUSTER_SEED_NODES` | (없음) | seed-nodes (HOCON 문자열) |
| `CLUSTER_MIN_NR` | `1` | 클러스터 최소 멤버 수 |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka.default.svc.cluster.local:9092` | Kafka bootstrap 서버 |
| `KAFKA_TOPIC` | `cluster-dotnet-events` | .NET 프로젝트 전용 토픽 |
| `KAFKA_GROUP_ID_PREFIX` | `cluster-dotnet-group` | Consumer group prefix |
| `KAFKA_START_DELAY_SECONDS` | `15` | 클러스터 안정화 후 Kafka 실행 지연 |

## K8s 아키텍처

- **StatefulSet** (`podManagementPolicy: OrderedReady`): pod-0(Seed) 먼저 기동
- **Headless Service**: `akkanet-cluster-{ordinal}.akkanet-cluster.default.svc.cluster.local` DNS
- **seed-nodes**: pod-0만 seed로 지정, pod-1은 joining
- **프로토콜**: `akka.tcp://ClusterSystem@<podIP>:4053`
