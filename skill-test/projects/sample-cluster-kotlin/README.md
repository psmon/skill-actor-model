# sample-cluster-kotlin — Spring WebFlux + Coroutine + Pekko Typed

Spring Boot 3.5.x + Kotlin Coroutine(WebFlux) + Apache Pekko Typed 1.4.x 기반 클러스터 웹 애플리케이션입니다.

## 핵심 스택
- Java 21
- Spring Boot 3.5.0 (`spring-boot-starter-webflux`)
- Kotlin Coroutine (`suspend`, `kotlinx-coroutines-reactor`)
- Pekko Typed Cluster + Cluster Singleton
- Swagger UI (`/swagger`)
- Logback 파일 로깅(`logs/sample-cluster-kotlin*.log`)

## API
- `GET /api/heath`
- `GET /api/actor/hello` -> `wellcome actor world!`
- `GET /api/cluster/info` -> ActorSystem 클러스터 정보
- `POST /api/kafka/fire-event` -> Kafka 1회 발행/수신 round-trip
- `GET /api/cafe24/call?mallId={mallId}&word={word}` -> MallId별 안전 호출(더미 Cafe24)
- `GET /api/cafe24/metrics?mallId={mallId}` -> MallId별 호출 메트릭(클러스터 싱글턴 집계)

## 실행
```bash
./gradlew test
./gradlew bootRun
```

Swagger:
- `http://localhost:8080/swagger`

## Docker
```bash
docker build -f infra/Dockerfile -t sample-cluster-kotlin:latest .
```

## Kubernetes
```bash
kubectl apply -f ../../infra/k8s-kafka-standalone.yaml
kubectl apply -f infra/k8s-cluster.yaml
kubectl get pods -w
kubectl logs pekko-cluster-0
kubectl logs pekko-cluster-1
```

## 주요 환경변수
- `SERVER_PORT` (default: `8080`)
- `CLUSTER_HOSTNAME`, `CLUSTER_PORT`, `CLUSTER_MIN_NR`, `CLUSTER_SEED_NODES`
- `CLUSTER_BOOTSTRAP_SERVICE_NAME`, `CLUSTER_BOOTSTRAP_REQUIRED_CONTACT_POINTS`, `CLUSTER_BOOTSTRAP_PORT_NAME`
- `MANAGEMENT_HOSTNAME`, `MANAGEMENT_PORT`
- `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC`, `KAFKA_GROUP_ID_PREFIX`
- `CAFE24_BUCKET_CAPACITY` (default: `10`)
- `CAFE24_LEAK_RATE_PER_SECOND` (default: `2`)
- `CAFE24_PER_MALL_MAX_RPS` (default: `2`)

## Cafe24 검증 예시
```bash
curl 'http://localhost:8080/api/cafe24/call?mallId=mall-a&word=hello'
curl 'http://localhost:8080/api/cafe24/call?mallId=mall-b&word=alpha'
curl 'http://localhost:8080/api/cafe24/metrics?mallId=mall-a'
```
