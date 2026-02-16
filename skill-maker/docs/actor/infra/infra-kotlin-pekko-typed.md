# Kotlin + Pekko Typed 분산 클러스터 인프라 가이드

Pekko Typed 1.1.x 기반 액터 시스템의 분산 클러스터링 시 **서비스 디스커버리(Service Discovery)** 설정 가이드입니다.

## 호환 버전

| 컴포넌트 | 버전 | 비고 |
|---------|------|------|
| Apache Pekko | 1.1.3 ~ 1.1.5 | 기본 스킬 기준 1.1.3 |
| Pekko Management | 1.2.0 | Pekko 1.1.5+ 필요 |
| Pekko Discovery Kubernetes API | 1.2.0 | Management와 동일 버전 |
| Scala Binary | 2.13 | `_2.13` suffix |
| JDK | 11, 17, 21 | |
| Kotlin | 1.9.x | |

> **버전 주의**: `pekko-management` 1.2.0은 Pekko 1.1.5 이상을 요구합니다. 기존 스킬이 1.1.3을 사용하는 경우 `pekkoVersion`을 1.1.5로 올려야 합니다. 1.1.x 마이너 간 호환성이 유지됩니다.

## HOCON 네임스페이스

Pekko는 반드시 `pekko { }` 블록을 사용합니다 (`akka { }`가 아님).

```
pekko.discovery { }
pekko.management { }
pekko.remote.artery { }
pekko.cluster { }
```

프로토콜: `pekko://`

---

## 디스커버리 방법 개요

| 방법 | 설정 키 | 아티팩트 | 권장 환경 |
|------|---------|---------|----------|
| **Config** | `config` | `pekko-discovery` (core) | Docker Compose, 로컬 개발 |
| **DNS** | `pekko-dns` | `pekko-discovery` (core) | Kubernetes Headless Service |
| **Kubernetes API** | `kubernetes-api` | `pekko-discovery-kubernetes-api` | Kubernetes (권장) |
| **Aggregate** | `aggregate` | `pekko-discovery` (core) | 다중 방법 조합 |
| **Consul** | `pekko-consul` | `pekko-discovery-consul` | Consul 인프라 |

> Config와 DNS는 core에 내장. Kubernetes API는 별도 아티팩트 추가 필요.

---

## Type A: Docker Compose 기반 디스커버리

Kubernetes 종속 없이 Docker Compose 환경에서 클러스터를 구성하는 방법입니다.

### Gradle 의존성

```kotlin
val pekkoVersion = "1.1.5"
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

## 참고 문서

- [Pekko Discovery Methods](https://pekko.apache.org/docs/pekko-management/current/discovery/index.html)
- [Pekko Kubernetes Discovery](https://pekko.apache.org/docs/pekko-management/current/discovery/kubernetes.html)
- [Pekko Cluster Bootstrap](https://pekko.apache.org/docs/pekko-management/current/bootstrap/index.html)
