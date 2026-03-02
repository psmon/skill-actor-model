# sample-cluster-dotnet — ASP.NET Core + Akka.NET

ASP.NET Core Web API + Akka.NET 1.5.x 클러스터 웹 애플리케이션입니다.

## 핵심 스택
- ASP.NET Core (`net10.0` target)
- Akka.NET Cluster + Cluster Singleton
- Akka.DependencyInjection + Microsoft DI
- Swagger UI (`/swagger`)
- Serilog 파일 로깅(`logs/clusteractors-*.log`)

## API
- `GET /api/heath`
- `GET /api/actor/hello` -> `wellcome actor world!`
- `GET /api/cluster/info` -> ActorSystem 클러스터 정보
- `POST /api/kafka/fire-event` -> Kafka 1회 발행/수신 round-trip
- `GET /api/cafe24/call?mallId={mallId}&word={word}` -> MallId별 안전 호출(더미 Cafe24)
- `GET /api/cafe24/metrics?mallId={mallId}` -> MallId별 호출 메트릭(클러스터 싱글턴 집계)

## 실행
```bash
dotnet run --project ClusterActors
```

Swagger:
- `http://localhost:8080/swagger`

## 테스트
로컬 SDK가 `net10.0`을 지원하면:
```bash
dotnet test ClusterActors.sln
```

SDK 10 컨테이너로 검증하는 방법:
```bash
docker run --rm -v "$(pwd)":/work -w /work mcr.microsoft.com/dotnet/sdk:10.0 \
  dotnet test ClusterActors.Tests/ClusterActors.Tests.csproj -c Release
```

## Docker
```bash
docker build -f infra/Dockerfile -t sample-cluster-dotnet:latest .
```

## Kubernetes
```bash
kubectl apply -f ../../infra/k8s-kafka-standalone.yaml
kubectl apply -f infra/k8s-cluster.yaml
kubectl get pods -w
kubectl logs akkanet-cluster-0
kubectl logs akkanet-cluster-1
```

## 주요 환경변수
- `ASPNETCORE_URLS` (default k8s: `http://0.0.0.0:8080`)
- `POD_NAME`, `CLUSTER_SERVICE_NAME`, `CLUSTER_HOSTNAME`, `CLUSTER_PORT`, `CLUSTER_MIN_NR`, `CLUSTER_SEED_NODES`
- `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC`, `KAFKA_GROUP_ID_PREFIX`
- `CAFE24_BUCKET_CAPACITY` (default: `10`)
- `CAFE24_LEAK_RATE_PER_SECOND` (default: `2`)
- `CAFE24_PER_MALL_MAX_RPS` (default: `2`)

## Cafe24 검증 예시
```bash
curl 'http://localhost:8080/api/cafe24/call?mallId=mall-a&word=hello'
curl 'http://localhost:8080/api/cafe24/call?mallId=mall-b&word=gamma'
curl 'http://localhost:8080/api/cafe24/metrics?mallId=mall-a'
```
