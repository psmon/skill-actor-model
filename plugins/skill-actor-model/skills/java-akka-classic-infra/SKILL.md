---
name: java-akka-classic-infra
description: Java + Akka Classic 분산 클러스터 인프라 코드를 생성합니다. Docker Compose 또는 Kubernetes 환경에서 Akka 클러스터의 서비스 디스커버리, Bootstrap, Management HTTP, RBAC, Deployment YAML 등 인프라 구성 코드를 작성할 때 사용합니다.
argument-hint: "[docker-compose|kubernetes] [요구사항]"
---

# Java + Akka Classic 인프라 스킬

Java + Akka Classic(2.7.x) 기반 분산 클러스터의 인프라(서비스 디스커버리, Docker Compose, Kubernetes) 코드를 생성하는 스킬입니다.

## 참고 문서

- 기본 액터 스킬: [plugins/skill-actor-model/skills/java-akka-classic/SKILL.md](../java-akka-classic/SKILL.md)
- 클러스터 스킬: [plugins/skill-actor-model/skills/java-akka-classic-cluster/SKILL.md](../java-akka-classic-cluster/SKILL.md)
- 인프라 문서: [skill-maker/docs/actor/infra/infra-java-akka-classic.md](../../../../skill-maker/docs/actor/infra/infra-java-akka-classic.md)

## 호환 버전

| 컴포넌트 | 버전 | 비고 |
|---------|------|------|
| Akka Classic | 2.7.0 ~ 2.7.1 | 기본 스킬 기준 2.7.1 |
| Akka Management | 1.2.0 | Akka 2.7.0과 공식 페어링 |
| Akka Discovery Kubernetes API | 1.2.0 | Management와 동일 버전 |
| Akka HTTP | 10.4.0 | Management HTTP에 필요 |
| Scala Binary | 2.13 | `_2.13` suffix |
| JDK | 11+ | |

> **라이선스**: Akka 2.7.x는 BSL(Business Source License)입니다. 오픈소스 대안이 필요하면 Apache Pekko(Apache 2.0)를 고려하세요.

## 디스커버리 방법 개요

| 방법 | 설정 키 | 아티팩트 | 권장 환경 |
|------|---------|---------|----------|
| **Config** | `config` | `akka-discovery` (core) | Docker Compose, 로컬 개발 |
| **DNS** | `akka-dns` | `akka-discovery` (core) | Kubernetes Headless Service |
| **Kubernetes API** | `kubernetes-api` | `akka-discovery-kubernetes-api` | Kubernetes (권장) |
| **Aggregate** | `aggregate` | `akka-discovery` (core) | 다중 방법 조합 |

> Config와 DNS는 `akka-discovery`에 내장. Kubernetes API는 별도 아티팩트 추가 필요.

---

## Type A: Docker Compose 기반 디스커버리

Kubernetes 종속 없이 Docker Compose 환경에서 클러스터를 구성하는 방법입니다.

### Gradle 의존성 (Kotlin DSL)

```kotlin
val AkkaVersion = "2.7.1"
val AkkaHttpVersion = "10.4.0"
val AkkaManagementVersion = "1.2.0"
val scalaBinaryVersion = "2.13"

dependencies {
    // Core
    implementation("com.typesafe.akka:akka-actor_$scalaBinaryVersion:$AkkaVersion")
    implementation("com.typesafe.akka:akka-cluster_$scalaBinaryVersion:$AkkaVersion")
    implementation("com.typesafe.akka:akka-cluster-tools_$scalaBinaryVersion:$AkkaVersion")
    implementation("com.typesafe.akka:akka-cluster-sharding_$scalaBinaryVersion:$AkkaVersion")
    implementation("com.typesafe.akka:akka-discovery_$scalaBinaryVersion:$AkkaVersion")
    implementation("com.typesafe.akka:akka-stream_$scalaBinaryVersion:$AkkaVersion")

    // Management + Bootstrap
    implementation("com.lightbend.akka.management:akka-management_$scalaBinaryVersion:$AkkaManagementVersion")
    implementation("com.lightbend.akka.management:akka-management-cluster-bootstrap_$scalaBinaryVersion:$AkkaManagementVersion")

    // Test
    testImplementation("com.typesafe.akka:akka-testkit_$scalaBinaryVersion:$AkkaVersion")
}
```

### HOCON 설정 (Config Discovery)

```hocon
akka {
  actor {
    provider = cluster

    serializers {
      jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
    }
    serialization-bindings {
      "com.example.CborSerializable" = jackson-json
    }
  }

  remote.artery {
    canonical {
      hostname = ${HOSTNAME}  # Docker 환경변수로 주입
      port = 2551
    }
  }

  cluster {
    # seed-nodes를 설정하지 않음 (Bootstrap과 혼용 금지)
    shutdown-after-unsuccessful-join-seed-nodes = 30s

    downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
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
          { host = "node1", port = 8558 },
          { host = "node2", port = 8558 },
          { host = "node3", port = 8558 }
        ]
      }
    }
  }

  management {
    http {
      hostname = "0.0.0.0"
      port = 8558
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

### Java 초기화 코드

```java
import akka.actor.ActorSystem;
import akka.management.javadsl.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import com.typesafe.config.ConfigFactory;

public class Main {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("ClusterSystem",
            ConfigFactory.load());

        // Management HTTP + Cluster Bootstrap 시작
        AkkaManagement.get(system).start();
        ClusterBootstrap.get(system).start();

        // 액터 생성 등 비즈니스 로직
        system.actorOf(ClusterListenerActor.props(), "clusterListener");
    }
}
```

### docker-compose.yml

```yaml
version: "3.8"
services:
  node1:
    image: my-akka-app:latest
    hostname: node1
    environment:
      - HOSTNAME=node1
    ports:
      - "2551:2551"
      - "8558:8558"
    networks:
      - cluster-net

  node2:
    image: my-akka-app:latest
    hostname: node2
    environment:
      - HOSTNAME=node2
    ports:
      - "2552:2551"
      - "8559:8558"
    networks:
      - cluster-net

  node3:
    image: my-akka-app:latest
    hostname: node3
    environment:
      - HOSTNAME=node3
    ports:
      - "2553:2551"
      - "8560:8558"
    networks:
      - cluster-net

networks:
  cluster-net:
    driver: bridge
```

### 클러스터 형성 과정

1. 각 노드가 Management HTTP(8558)를 노출
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
implementation("com.lightbend.akka.discovery:akka-discovery-kubernetes-api_$scalaBinaryVersion:$AkkaManagementVersion")
```

### HOCON 설정 (Kubernetes API Discovery)

```hocon
akka {
  actor {
    provider = cluster

    serializers {
      jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
    }
    serialization-bindings {
      "com.example.CborSerializable" = jackson-json
    }
  }

  remote.artery {
    canonical {
      hostname = ${?HOSTNAME}
      port = 2551
    }
  }

  cluster {
    shutdown-after-unsuccessful-join-seed-nodes = 30s

    downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
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
      port = 8558
      bind-hostname = "0.0.0.0"
      bind-port = 8558
    }

    cluster.bootstrap {
      contact-point-discovery {
        service-name = "my-akka-app"
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
  name: my-akka-app
  labels:
    app: my-akka-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-akka-app
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  template:
    metadata:
      labels:
        app: my-akka-app
    spec:
      containers:
        - name: app
          image: my-akka-app:latest
          ports:
            - name: remoting
              containerPort: 2551
              protocol: TCP
            - name: management
              containerPort: 8558
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
  name: my-akka-app-internal
  annotations:
    service.alpha.kubernetes.io/tolerate-unready-endpoints: "true"
spec:
  clusterIP: None
  publishNotReadyAddresses: true
  selector:
    app: my-akka-app
  ports:
    - name: management
      port: 8558
      protocol: TCP
    - name: remoting
      port: 2551
      protocol: TCP
```

### External Service (외부 트래픽용)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-akka-app
spec:
  type: LoadBalancer
  selector:
    app: my-akka-app
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
akka {
  actor {
    provider = cluster
    allow-java-serialization = on
  }
  remote.artery {
    canonical.hostname = "127.0.0.1"
    canonical.port = 0
  }
  cluster {
    seed-nodes = []
    min-nr-of-members = 1
    downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
  }
  coordinated-shutdown.exit-jvm = on
}
```

### Java Main (코드 레벨 환경변수 오버라이드)

```java
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Main {
    public static void main(String[] args) {
        StringBuilder overrides = new StringBuilder();
        String hostname = System.getenv("CLUSTER_HOSTNAME");
        if (hostname != null) overrides.append("akka.remote.artery.canonical.hostname = \"")
            .append(hostname).append("\"\n");
        String port = System.getenv("CLUSTER_PORT");
        if (port != null) overrides.append("akka.remote.artery.canonical.port = ")
            .append(port).append("\n");
        String seedNodes = System.getenv("CLUSTER_SEED_NODES");
        if (seedNodes != null) overrides.append("akka.cluster.seed-nodes = ")
            .append(seedNodes).append("\n");
        String minNr = System.getenv("CLUSTER_MIN_NR");
        if (minNr != null) overrides.append("akka.cluster.min-nr-of-members = ")
            .append(minNr).append("\n");

        Config config = ConfigFactory.parseString(overrides.toString())
            .withFallback(ConfigFactory.load());

        ActorSystem system = ActorSystem.create("ClusterSystem", config);
        system.actorOf(ClusterListenerActor.props(system.deadLetters()), "clusterListener");
        system.getWhenTerminated().toCompletableFuture().join();
    }
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
EXPOSE 2551
ENTRYPOINT ["./bin/my-app"]
```

### k8s-cluster.yaml (Headless Service + StatefulSet)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: akka-cluster
spec:
  clusterIP: None
  selector:
    app: akka-cluster
  ports:
    - name: remoting
      port: 2551
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: akka-cluster
spec:
  serviceName: akka-cluster
  replicas: 2
  podManagementPolicy: OrderedReady
  selector:
    matchLabels:
      app: akka-cluster
  template:
    metadata:
      labels:
        app: akka-cluster
    spec:
      containers:
        - name: akka-node
          image: my-akka-app:latest
          imagePullPolicy: Never
          ports:
            - containerPort: 2551
          env:
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: CLUSTER_HOSTNAME
              value: "$(POD_NAME).akka-cluster.default.svc.cluster.local"
            - name: CLUSTER_PORT
              value: "2551"
            - name: CLUSTER_SEED_NODES
              value: '["akka://ClusterSystem@akka-cluster-0.akka-cluster.default.svc.cluster.local:2551"]'
            - name: CLUSTER_MIN_NR
              value: "2"
          readinessProbe:
            tcpSocket:
              port: 2551
            initialDelaySeconds: 15
            periodSeconds: 5
```

### Type A/B와의 비교

| 항목 | Type A/B (Bootstrap) | Type C (seed-nodes) |
|------|---------------------|---------------------|
| 추가 의존성 | akka-management, bootstrap | 없음 |
| 디스커버리 | 자동 (Config/K8s API) | 수동 (seed-nodes 환경변수) |
| 노드 추가 | 자동 발견 | seed-nodes 재설정 필요 |
| 적합 환경 | 프로덕션, 동적 스케일링 | 로컬 개발, 고정 노드 수 |
| 복잡도 | 높음 (RBAC, Management HTTP) | 낮음 |

---

## 핵심 주의사항

| 항목 | 설명 |
|------|------|
| seed-nodes 혼용 금지 | Bootstrap 사용 시 `akka.cluster.seed-nodes`를 설정하면 안 됨 |
| required-contact-point-nr | 1로 설정하면 단일 노드가 독립 클러스터를 형성하여 split-brain 위험. 최소 2 이상 |
| HOCON 네임스페이스 | `akka { }` 사용 |
| 프로토콜 | `akka://ClusterSystem@host:port` |
| Management 포트 일관성 | 모든 노드가 동일한 Management HTTP 포트(8558) 사용 |
| SBR 필수 | 프로덕션에서 `SplitBrainResolverProvider` 반드시 설정 |
| coordinated-shutdown | `exit-jvm = on`으로 JVM 안전 종료 보장 |
| Docker Compose DNS 제약 | SRV 레코드 미지원. Config Discovery 권장 |
| Kubernetes publishNotReadyAddresses | `true`로 설정하여 readiness 교착상태 방지 |
| BSL 라이선스 | Akka 2.7.x는 BSL. 상용 환경에서 라이선스 조건 확인 필요 |
| 아티팩트 GroupId | Management는 `com.lightbend.akka.management`, Discovery는 `com.lightbend.akka.discovery`, Core는 `com.typesafe.akka` |

## 코드 생성 규칙

1. **HOCON 설정**은 `akka { }` 블록으로 작성합니다.
2. **프로토콜**은 `akka://`를 사용합니다.
3. **Bootstrap 사용 시 `seed-nodes`를 설정하지 않습니다** (혼용 금지).
4. **Management HTTP**는 `AkkaManagement.get(system).start()`로 시작합니다.
5. **Cluster Bootstrap**은 `ClusterBootstrap.get(system).start()`로 시작합니다.
6. **Docker Compose**에서는 Config Discovery(`method = config`)를 사용합니다.
7. **Kubernetes**에서는 Kubernetes API Discovery(`method = kubernetes-api`)를 사용합니다.
8. **Kubernetes RBAC**에서 Pod `get`, `watch`, `list` 권한을 부여합니다.
9. **Headless Service**에 `publishNotReadyAddresses: true`를 설정합니다.
10. **SBR(Split Brain Resolver)**을 프로덕션에서 반드시 설정합니다.
11. **`required-contact-point-nr`**은 최소 2 이상으로 설정합니다.
12. **`coordinated-shutdown.exit-jvm = on`**으로 JVM 안전 종료를 보장합니다.
13. **Management 포트**(8558)는 모든 노드에서 동일하게 설정합니다.
14. **아티팩트 GroupId**를 구분합니다: Management는 `com.lightbend.akka.management`, Discovery는 `com.lightbend.akka.discovery`.

$ARGUMENTS
