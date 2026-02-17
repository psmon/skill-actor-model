---
name: kotlin-pekko-typed-infra
description: Kotlin + Pekko Typed 분산 클러스터 인프라 코드를 생성합니다. Docker Compose 또는 Kubernetes 환경에서 Pekko 클러스터의 서비스 디스커버리, Bootstrap, Management HTTP, RBAC, Deployment YAML 등 인프라 구성 코드를 작성할 때 사용합니다.
argument-hint: "[docker-compose|kubernetes] [요구사항]"
---

# Kotlin + Pekko Typed 인프라 스킬

Kotlin + Apache Pekko Typed 기반 분산 클러스터의 인프라(서비스 디스커버리, Docker Compose, Kubernetes) 코드를 생성하는 스킬입니다.

## 참고 문서

- 기본 액터 스킬: [plugins/skill-actor-model/skills/kotlin-pekko-typed/SKILL.md](../kotlin-pekko-typed/SKILL.md)
- 클러스터 스킬: [plugins/skill-actor-model/skills/kotlin-pekko-typed-cluster/SKILL.md](../kotlin-pekko-typed-cluster/SKILL.md)
- 인프라 문서: [skill-maker/docs/actor/infra/infra-kotlin-pekko-typed.md](../../../../skill-maker/docs/actor/infra/infra-kotlin-pekko-typed.md)

## 호환 버전

| 컴포넌트 | 버전 | 비고 |
|---------|------|------|
| Apache Pekko | 1.4.x | 기본 스킬 기준 1.4.0 |
| Pekko Management | 1.2.0 | Pekko 1.4.x와 호환 |
| Pekko Discovery Kubernetes API | 1.2.0 | Management와 동일 버전 |
| Scala Binary | 2.13 | `_2.13` suffix |
| JDK | 11, 17, 21 | |
| Kotlin | 1.9.x | |

> **버전 주의**: Pekko 코어/클러스터/스트림/테스트 모듈은 동일 `pekkoVersion`(예: 1.4.0)으로 맞추고, Management/Bootstrap/Kubernetes Discovery는 `1.2.0` 계열로 고정하는 구성을 권장합니다.

## 1.1 -> 1.4 마이그레이션 권장사항 (중요)

실전 마이그레이션(`sample-cluster-kotlin`) 기준으로, Kubernetes 환경에서는 **고정 seed-nodes보다 Kubernetes API Discovery + Cluster Bootstrap 채택이 운영 안정성에 큰 도움**이 됩니다.

### 왜 도움이 되는가

1. Pod 재스케줄/재생성 시 주소 변경을 자동 흡수합니다.
2. 수동 seed-nodes 관리 부담을 줄여 운영 실수를 감소시킵니다.
3. 멤버십 형성 과정을 bootstrap 로그로 추적하기 쉬워 장애 분석이 빨라집니다.

### 적용 체크리스트

1. 의존성
- `pekko-management`
- `pekko-management-cluster-bootstrap`
- `pekko-discovery-kubernetes-api`

2. HOCON
- `pekko.discovery.method = kubernetes-api`
- `pekko.management.cluster.bootstrap.contact-point-discovery.discovery-method = kubernetes-api`
- `pekko.management.cluster.bootstrap.contact-point-discovery.port-name = "management"`

3. 런타임 초기화
- `PekkoManagement.get(system).start()`
- `ClusterBootstrap.get(system).start()`

4. Kubernetes 리소스
- RBAC(Pod `get/watch/list`) 필수
- Service/Pod에 management 포트(예: `8558`) 노출
- `required-contact-point-nr`는 최소 2 이상 권장

### 운영 트러블슈팅 포인트

1. 로그에 `Contact Point returning 0 seed-nodes`가 반복되면:
- discovery 포트 해석(`port-name`)과 management 포트 노출을 먼저 점검합니다.

2. `LowestAddressJoinDecider`가 self-join을 계속 미루면:
- self contact-point가 실제 reachable 주소인지 확인합니다.
- 필요 시 `MANAGEMENT_HOSTNAME`을 Pod IP(`status.podIP`)로 주입해 비교 안정성을 높입니다.

## 디스커버리 방법 개요

| 방법 | 설정 키 | 아티팩트 | 권장 환경 |
|------|---------|---------|----------|
| **Config** | `config` | `pekko-discovery` (core) | Docker Compose, 로컬 개발 |
| **DNS** | `pekko-dns` | `pekko-discovery` (core) | Kubernetes Headless Service |
| **Kubernetes API** | `kubernetes-api` | `pekko-discovery-kubernetes-api` | Kubernetes (권장) |
| **Aggregate** | `aggregate` | `pekko-discovery` (core) | 다중 방법 조합 |

> Config와 DNS는 core에 내장. Kubernetes API는 별도 아티팩트 추가 필요.

---

## Type A: Docker Compose 기반 디스커버리

Kubernetes 종속 없이 Docker Compose 환경에서 클러스터를 구성하는 방법입니다.

### Gradle 의존성 (Kotlin DSL)

```kotlin
val pekkoVersion = "1.4.0"
val pekkoManagementVersion = "1.2.0"
val scalaBinaryVersion = "2.13"

dependencies {
    // Core
    implementation("org.apache.pekko:pekko-actor-typed_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-typed_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-cluster-sharding-typed_$scalaBinaryVersion:$pekkoVersion")
    implementation("org.apache.pekko:pekko-discovery_$scalaBinaryVersion:$pekkoVersion")

    // Management + Bootstrap
    implementation("org.apache.pekko:pekko-management_$scalaBinaryVersion:$pekkoManagementVersion")
    implementation("org.apache.pekko:pekko-management-cluster-bootstrap_$scalaBinaryVersion:$pekkoManagementVersion")

    // Test
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_$scalaBinaryVersion:$pekkoVersion")
}
```

### HOCON 설정 (Config Discovery)

```hocon
pekko {
  actor {
    provider = "cluster"

    serialization-bindings {
      "com.example.CborSerializable" = jackson-json
    }
  }

  remote.artery {
    canonical {
      hostname = ${HOSTNAME}  # Docker 환경변수로 주입
      port = 25520
    }
  }

  cluster {
    # seed-nodes를 설정하지 않음 (Bootstrap과 혼용 금지)
    shutdown-after-unsuccessful-join-seed-nodes = 30s

    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
    split-brain-resolver {
      active-strategy = keep-majority
      stable-after = 20s
    }
  }

  discovery {
    method = config

    config.services = {
      my-cluster = {
        endpoints = [
          { host = "node1", port = 7626 },
          { host = "node2", port = 7626 },
          { host = "node3", port = 7626 }
        ]
      }
    }
  }

  management {
    http {
      hostname = "0.0.0.0"
      port = 7626
    }

    cluster.bootstrap {
      contact-point-discovery {
        service-name = "my-cluster"
        discovery-method = config
        required-contact-point-nr = 2
      }
    }
  }

  coordinated-shutdown.exit-jvm = on
}
```

### Kotlin 초기화 코드

```kotlin
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.management.javadsl.PekkoManagement
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap

fun main() {
    val system = ActorSystem.create(
        Behaviors.empty<Void>(), "ClusterSystem"
    )

    // Management HTTP + Cluster Bootstrap 시작
    PekkoManagement.get(system).start()
    ClusterBootstrap.get(system).start()
}
```

### docker-compose.yml

```yaml
version: "3.8"
services:
  node1:
    image: my-pekko-app:latest
    hostname: node1
    environment:
      - HOSTNAME=node1
    ports:
      - "25520:25520"
      - "7626:7626"
    networks:
      - cluster-net

  node2:
    image: my-pekko-app:latest
    hostname: node2
    environment:
      - HOSTNAME=node2
    ports:
      - "25521:25520"
      - "7627:7626"
    networks:
      - cluster-net

  node3:
    image: my-pekko-app:latest
    hostname: node3
    environment:
      - HOSTNAME=node3
    ports:
      - "25522:25520"
      - "7628:7626"
    networks:
      - cluster-net

networks:
  cluster-net:
    driver: bridge
```

### 클러스터 형성 과정

1. 각 노드가 Management HTTP(7626)를 노출
2. Bootstrap이 Config Discovery에서 엔드포인트 목록 조회
3. `required-contact-point-nr`(2개) 이상 발견될 때까지 반복
4. 발견된 노드의 `/bootstrap/seed-nodes` 프로브
5. 기존 클러스터가 없으면 **가장 낮은 주소의 노드**가 self-join
6. 나머지 노드가 해당 클러스터에 합류

---

## Type B: Kubernetes 기반 디스커버리

Kubernetes API를 통해 Pod를 자동 발견하여 클러스터를 형성합니다.

### Gradle 의존성 (추가분)

```kotlin
// Type A 의존성 + Kubernetes Discovery
implementation("org.apache.pekko:pekko-discovery-kubernetes-api_$scalaBinaryVersion:$pekkoManagementVersion")
```

### HOCON 설정 (Kubernetes API Discovery)

```hocon
pekko {
  actor {
    provider = "cluster"

    serialization-bindings {
      "com.example.CborSerializable" = jackson-json
    }
  }

  remote.artery {
    canonical {
      hostname = ${?HOSTNAME}
      port = 25520
    }
  }

  cluster {
    shutdown-after-unsuccessful-join-seed-nodes = 30s

    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
    split-brain-resolver {
      active-strategy = keep-majority
      stable-after = 20s
    }
  }

  discovery {
    method = kubernetes-api

    kubernetes-api {
      pod-namespace-path = "/var/run/secrets/kubernetes.io/serviceaccount/namespace"
      pod-label-selector = "app=%s"
    }
  }

  management {
    http {
      hostname = ${?HOSTNAME}
      port = 7626
    }

    cluster.bootstrap {
      contact-point-discovery {
        service-name = "my-pekko-app"
        discovery-method = kubernetes-api
        required-contact-point-nr = 2
      }
    }
  }

  coordinated-shutdown.exit-jvm = on
}
```

### Kubernetes RBAC

```yaml
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-reader
rules:
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "watch", "list"]
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: read-pods
subjects:
  - kind: ServiceAccount
    name: default
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-pekko-app
  labels:
    app: my-pekko-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-pekko-app
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  template:
    metadata:
      labels:
        app: my-pekko-app
    spec:
      containers:
        - name: app
          image: my-pekko-app:latest
          ports:
            - name: remoting
              containerPort: 25520
              protocol: TCP
            - name: management
              containerPort: 7626
              protocol: TCP
          readinessProbe:
            httpGet:
              path: /ready
              port: management
          livenessProbe:
            httpGet:
              path: /alive
              port: management
```

### Headless Service (내부 디스커버리용)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-pekko-app-internal
  annotations:
    service.alpha.kubernetes.io/tolerate-unready-endpoints: "true"
spec:
  clusterIP: None
  publishNotReadyAddresses: true
  selector:
    app: my-pekko-app
  ports:
    - name: management
      port: 7626
      protocol: TCP
    - name: remoting
      port: 25520
      protocol: TCP
```

### External Service (외부 트래픽용)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-pekko-app
spec:
  type: LoadBalancer
  selector:
    app: my-pekko-app
  ports:
    - name: http
      port: 8080
      targetPort: 8080
      protocol: TCP
```

---

## Type C: StatefulSet + seed-nodes (로컬 K8s / 간이 배포)

Management Bootstrap 없이 StatefulSet의 ordered startup과 seed-nodes 직접 지정으로 클러스터를 구성하는 방법입니다. Docker Desktop Kubernetes 등 로컬 환경에서 간단히 테스트할 때 적합합니다.

### 특징

- Management/Bootstrap 의존성 불필요 (기본 클러스터 의존성만 사용)
- `podManagementPolicy: OrderedReady` → pod-0(Seed)이 먼저 기동
- 코드 레벨 `ConfigFactory.parseString(overrides)` → 로컬 개발과 K8s 양립
- `imagePullPolicy: Never` → 로컬 빌드 이미지 직접 사용
- **K8s canonical hostname은 StatefulSet DNS 이름 사용** (Pod IP 사용 금지)

> **HOCON `${?ENV_VAR}` 주의**: HOCON의 `${?ENV_VAR}` fallback은 문자열/숫자 타입에는 동작하지만, **리스트 타입(`seed-nodes`)에는 문자열로 파싱되어 타입 오류가 발생**합니다. 따라서 환경변수 기반 설정은 코드 레벨 `ConfigFactory.parseString()` 오버라이드 패턴을 사용합니다.

> **canonical hostname과 seed-nodes 일치 필수**: K8s에서 `status.podIP`를 canonical hostname으로 사용하면 seed-nodes의 DNS 주소와 불일치하여 클러스터 조인이 실패합니다. 반드시 StatefulSet DNS 이름(`$(POD_NAME).{service}.default.svc.cluster.local`)을 사용해야 합니다.

### HOCON 설정 (순수 기본값)

```hocon
pekko {
  actor {
    provider = "cluster"
    allow-java-serialization = on
  }
  remote.artery {
    canonical.hostname = "127.0.0.1"
    canonical.port = 0
  }
  cluster {
    seed-nodes = []
    min-nr-of-members = 1
    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
  }
  coordinated-shutdown.exit-jvm = on
}
```

### Kotlin Main (코드 레벨 환경변수 오버라이드)

```kotlin
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.Behaviors

fun main() {
    val overrides = buildString {
        System.getenv("CLUSTER_HOSTNAME")?.let { appendLine("pekko.remote.artery.canonical.hostname = \"$it\"") }
        System.getenv("CLUSTER_PORT")?.let { appendLine("pekko.remote.artery.canonical.port = $it") }
        System.getenv("CLUSTER_SEED_NODES")?.let { appendLine("pekko.cluster.seed-nodes = $it") }
        System.getenv("CLUSTER_MIN_NR")?.let { appendLine("pekko.cluster.min-nr-of-members = $it") }
    }

    val config = ConfigFactory.parseString(overrides)
        .withFallback(ConfigFactory.load())

    val system = ActorSystem.create(
        Behaviors.setup<Nothing> { ctx ->
            ctx.spawn(MyClusterListenerActor.create(...), "clusterListener")
            Behaviors.empty()
        },
        "ClusterSystem",
        config
    )
    system.whenTerminated.toCompletableFuture().join()
}
```

### Dockerfile (멀티스테이지)

```dockerfile
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/my-app/ ./
EXPOSE 25520
ENTRYPOINT ["./bin/my-app"]
```

### k8s-cluster.yaml (Headless Service + StatefulSet)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: pekko-cluster
spec:
  clusterIP: None
  selector:
    app: pekko-cluster
  ports:
    - name: remoting
      port: 25520
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: pekko-cluster
spec:
  serviceName: pekko-cluster
  replicas: 2
  podManagementPolicy: OrderedReady
  selector:
    matchLabels:
      app: pekko-cluster
  template:
    metadata:
      labels:
        app: pekko-cluster
    spec:
      containers:
        - name: pekko-node
          image: my-pekko-app:latest
          imagePullPolicy: Never
          ports:
            - containerPort: 25520
          env:
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: CLUSTER_HOSTNAME
              value: "$(POD_NAME).pekko-cluster.default.svc.cluster.local"
            - name: CLUSTER_PORT
              value: "25520"
            - name: CLUSTER_SEED_NODES
              value: '["pekko://ClusterSystem@pekko-cluster-0.pekko-cluster.default.svc.cluster.local:25520"]'
            - name: CLUSTER_MIN_NR
              value: "2"
          readinessProbe:
            tcpSocket:
              port: 25520
            initialDelaySeconds: 15
            periodSeconds: 5
```

### Type A/B와의 비교

| 항목 | Type A/B (Bootstrap) | Type C (seed-nodes) |
|------|---------------------|---------------------|
| 추가 의존성 | pekko-management, bootstrap | 없음 |
| 디스커버리 | 자동 (Config/K8s API) | 수동 (seed-nodes 환경변수) |
| 노드 추가 | 자동 발견 | seed-nodes 재설정 필요 |
| 적합 환경 | 프로덕션, 동적 스케일링 | 로컬 개발, 고정 노드 수 |
| 복잡도 | 높음 (RBAC, Management HTTP) | 낮음 |

---

## 핵심 주의사항

| 항목 | 설명 |
|------|------|
| seed-nodes 혼용 금지 | Bootstrap 사용 시 `pekko.cluster.seed-nodes`를 설정하면 안 됨 |
| required-contact-point-nr | 1로 설정하면 단일 노드가 독립 클러스터를 형성하여 split-brain 위험. 최소 2 이상 |
| HOCON 네임스페이스 | 반드시 `pekko { }` 사용 (`akka { }`가 아님) |
| 프로토콜 | `pekko://ClusterSystem@host:port` |
| Management 포트 일관성 | 모든 노드가 동일한 Management HTTP 포트 사용 |
| SBR 필수 | 프로덕션에서 `SplitBrainResolverProvider` 반드시 설정 |
| coordinated-shutdown | `exit-jvm = on`으로 JVM 안전 종료 보장 |
| Docker Compose DNS 제약 | SRV 레코드 미지원. Config Discovery 권장 |
| Kubernetes publishNotReadyAddresses | `true`로 설정하여 readiness 교착상태 방지 |
| Management 버전 호환 | `pekko-management` 1.2.0은 Pekko 1.4.x와 호환 |

## 코드 생성 규칙

1. **패키지는 `org.apache.pekko.*`**를 사용합니다 (`akka.*`가 아님).
2. **HOCON 설정**은 `pekko { }` 블록으로 작성합니다 (`akka { }`가 아님).
3. **프로토콜**은 `pekko://`를 사용합니다 (`akka://`가 아님).
4. **Bootstrap 사용 시 `seed-nodes`를 설정하지 않습니다** (혼용 금지).
5. **Management HTTP**는 `PekkoManagement.get(system).start()`로 시작합니다.
6. **Cluster Bootstrap**은 `ClusterBootstrap.get(system).start()`로 시작합니다.
7. **Docker Compose**에서는 Config Discovery(`method = config`)를 사용합니다.
8. **Kubernetes**에서는 Kubernetes API Discovery(`method = kubernetes-api`)를 사용합니다.
9. **Kubernetes RBAC**에서 Pod `get`, `watch`, `list` 권한을 부여합니다.
10. **Headless Service**에 `publishNotReadyAddresses: true`를 설정합니다.
11. **SBR(Split Brain Resolver)**을 프로덕션에서 반드시 설정합니다.
12. **`required-contact-point-nr`**은 최소 2 이상으로 설정합니다.
13. **`coordinated-shutdown.exit-jvm = on`**으로 JVM 안전 종료를 보장합니다.
14. **Management 포트**(7626)는 모든 노드에서 동일하게 설정합니다.

$ARGUMENTS

## WebApplication 통합 업데이트 (2026-02-17)

- 콘솔 엔트리 중심 샘플을 웹 API 중심으로 확장할 때 아래 기본 API를 우선 제공합니다.
  - `GET /api/heath`
  - `GET /api/actor/hello`
  - `GET /api/cluster/info`
  - `POST /api/kafka/fire-event`
- Kafka 실행은 스케줄러 자동 실행보다 API 트리거 방식을 우선합니다.
- Swagger/OpenAPI와 파일 기반 로깅 구성을 기본 포함합니다.
- 플랫폼별 권장 웹 모드:
  - .NET: ASP.NET Core (.NET 10)
  - Java: Spring Boot MVC (Java 21, Spring Boot 3.5.x)
  - Kotlin: Spring WebFlux + Coroutine (Spring Boot 3.5.x)

## Web + Infra 보강 지침 (2026-02)

1. readiness/liveness는 remoting 포트 대신 HTTP `/api/heath`를 기준으로 설정합니다.
2. 로컬 Kubernetes에서는 `imagePullPolicy: Never`와 고유 태그를 사용해 이미지 캐시 오염을 피합니다.
3. `$(POD_NAME)` 문자열 확장에 의존하지 말고, 런타임에서 `POD_NAME + SERVICE_NAME`로 hostname을 조합합니다.
4. ENV 우선순위는 `ENV > 계산값(POD DNS/IP) > 설정파일`로 통일합니다.
5. seed-node, management, bootstrap 포트 정의를 서비스/컨테이너/설정 간 동일하게 유지합니다.
6. 배포 후 `rollout status`만 보지 말고, 로그에서 `Member is Up`와 singleton 동작까지 확인합니다.
7. Kafka 연동 검증은 API 호출 후 producer/consumer 성공 로그를 함께 수집합니다.
