# sample-cluster-java — Spring MVC + Akka Classic

Spring Boot 3.5.x + Java 21 + Akka Classic 2.7.x 기반 클러스터 웹 애플리케이션입니다.

## 핵심 스택
- Java 21
- Spring Boot 3.5.0 (`spring-boot-starter-web`)
- Akka Classic Cluster + Cluster Singleton
- Spring-Akka Extension 기반 DI(`helloActorBean`)
- Swagger UI (`/swagger`)
- Logback 파일 로깅(`logs/sample-cluster-java*.log`)

## API
- `GET /api/heath`
- `GET /api/actor/hello` -> `wellcome actor world!`
- `GET /api/cluster/info` -> ActorSystem 클러스터 정보
- `POST /api/kafka/fire-event` -> Kafka 1회 발행/수신 round-trip

## 실행
```bash
./gradlew test
./gradlew bootRun
```

Swagger:
- `http://localhost:8080/swagger`

## Docker
```bash
docker build -f infra/Dockerfile -t sample-cluster-java:latest .
```

## Kubernetes
```bash
kubectl apply -f ../../infra/k8s-kafka-standalone.yaml
kubectl apply -f infra/k8s-cluster.yaml
kubectl get pods -w
kubectl logs akka-cluster-0
kubectl logs akka-cluster-1
```

## 주요 환경변수
- `SERVER_PORT` (default: `8080`)
- `CLUSTER_HOSTNAME`, `CLUSTER_PORT`, `CLUSTER_MIN_NR`, `CLUSTER_SEED_NODES`
- `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC`, `KAFKA_GROUP_ID_PREFIX`
