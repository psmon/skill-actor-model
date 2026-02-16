# C# + Akka.NET 분산 클러스터 인프라 가이드

Akka.NET 1.5.x 기반 액터 시스템의 분산 클러스터링 시 **서비스 디스커버리(Service Discovery)** 설정 가이드입니다.

## 호환 버전

| 컴포넌트 | 버전 | 비고 |
|---------|------|------|
| Akka.NET | 1.5.60 | 기본 스킬 기준 |
| Akka.Management | 1.5.59 | Cluster Bootstrap 포함 (v1.0.0+) |
| Akka.Discovery | 1.5.60 | Base Discovery 추상화 |
| Akka.Discovery.KubernetesApi | 1.5.59 | Kubernetes Pod Discovery |
| Akka.Discovery.Azure | 1.5.59 | Azure Table Storage Discovery |
| Akka.Discovery.Dns | 1.5.59 | DNS A/AAAA/SRV Discovery |
| Akka.Coordination.KubernetesApi | 1.5.42 | K8s CRD 기반 분산 잠금 (SBR용) |
| Akka.Hosting | 1.5.60 | HOCON-less 구성 지원 |
| .NET | 6.0 ~ 9.0 | .NET Standard 2.0 호환 |

> **Lighthouse 대체**: Akka.NET 1.5+에서는 `Akka.Management` + `Akka.Discovery`가 Lighthouse 시드 노드 패턴을 대체합니다. 신규 프로젝트는 Management 방식을 권장합니다.

## HOCON 네임스페이스

Akka.NET은 `akka { }` 블록을 사용합니다.

```
akka.discovery { }
akka.management { }
akka.remote.dot-netty.tcp { }
akka.cluster { }
```

프로토콜: `akka.tcp://`
트랜스포트: `dot-netty.tcp`

---

## 디스커버리 방법 개요

| 방법 | NuGet 패키지 | 권장 환경 |
|------|-------------|----------|
| **Config** | `Akka.Discovery` (내장) | Docker Compose, 로컬 개발 |
| **DNS** | `Akka.Discovery.Dns` | Kubernetes Headless Service |
| **Kubernetes API** | `Akka.Discovery.KubernetesApi` | Kubernetes (권장) |
| **Azure Table Storage** | `Akka.Discovery.Azure` | Azure 환경 |
| **Aggregate** | `Akka.Discovery` (내장) | 다중 방법 조합 |

---

## Type A: Docker Compose 기반 디스커버리

Kubernetes 종속 없이 Docker Compose 환경에서 클러스터를 구성하는 방법입니다.

### NuGet 패키지

```xml
<ItemGroup>
  <PackageReference Include="Akka" Version="1.5.60" />
  <PackageReference Include="Akka.Cluster" Version="1.5.60" />
  <PackageReference Include="Akka.Cluster.Sharding" Version="1.5.60" />
  <PackageReference Include="Akka.Cluster.Tools" Version="1.5.60" />
  <PackageReference Include="Akka.Discovery" Version="1.5.60" />
  <PackageReference Include="Akka.Hosting" Version="1.5.60" />
  <PackageReference Include="Akka.Cluster.Hosting" Version="1.5.60" />
  <PackageReference Include="Akka.Management" Version="1.5.59" />
</ItemGroup>
```

### 방법 1: Akka.Hosting + Config Discovery (권장)

HOCON 없이 C# 코드로 전체 구성하는 방법입니다.

```csharp
using Akka.Hosting;
using Akka.Cluster.Hosting;
using Akka.Management;
using Akka.Management.Cluster.Bootstrap;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddAkka("ClusterSystem", (configBuilder, provider) =>
{
    configBuilder
        .WithRemoting("0.0.0.0", 4053)
        .WithClustering(new ClusterOptions
        {
            SeedNodes = Array.Empty<string>(),  // Bootstrap 사용 시 비워야 함
            Roles = new[] { "backend" }
        })
        .WithAkkaManagement(port: 8558)
        .WithClusterBootstrap(
            serviceName: "my-cluster-service",
            requiredContactPoints: 2);
});

var app = builder.Build();
app.Run();
```

**HOCON 설정 (Config Discovery 엔드포인트)**:

```hocon
akka {
  discovery {
    method = config
    config {
      services {
        my-cluster-service {
          endpoints = [
            "node1:8558",
            "node2:8558",
            "node3:8558"
          ]
        }
      }
    }
  }

  cluster {
    downing-provider-class = "Akka.Cluster.SBR.SplitBrainResolverProvider, Akka.Cluster"
    split-brain-resolver {
      active-strategy = keep-majority
      stable-after = 20s
    }
  }
}
```

### 방법 2: Akka.Bootstrap.Docker + Lighthouse (레거시)

기존 프로젝트와의 호환성을 위한 전통적 방식입니다.

**NuGet 추가**: `Akka.Bootstrap.Docker`

```csharp
using Akka.Bootstrap.Docker;

var config = ConfigurationFactory.ParseString(File.ReadAllText("app.conf"));
var system = ActorSystem.Create("ClusterSystem", config.BootstrapFromDocker());
```

**docker-compose.yml (Lighthouse 방식)**:

```yaml
version: "3.8"
services:
  lighthouse:
    image: petabridge/lighthouse:latest
    hostname: lighthouse
    environment:
      ACTORSYSTEM: "ClusterSystem"
      CLUSTER_PORT: "4053"
      CLUSTER_IP: "lighthouse"
      CLUSTER_SEEDS: "akka.tcp://ClusterSystem@lighthouse:4053"
    ports:
      - "4053:4053"
      - "9110:9110"
    networks:
      - cluster-net

  worker:
    build: .
    environment:
      CLUSTER_IP: "worker"
      CLUSTER_PORT: "4053"
      CLUSTER_SEEDS: "akka.tcp://ClusterSystem@lighthouse:4053"
    depends_on:
      - lighthouse
    networks:
      - cluster-net

networks:
  cluster-net:
    driver: bridge
```

> **환경변수 → HOCON 매핑 규약**: `AKKA__CLUSTER__ROLES__0="worker"`는 `akka.cluster.roles = ["worker"]`로 변환. 점(`.`)은 이중 밑줄(`__`), 하이픈(`-`)은 단일 밑줄(`_`).

### docker-compose.yml (Management + Config Discovery, 권장)

```yaml
version: "3.8"
services:
  node1:
    build: .
    hostname: node1
    environment:
      AKKA__REMOTE__DOT_NETTY__TCP__HOSTNAME: "node1"
      AKKA__REMOTE__DOT_NETTY__TCP__PORT: "4053"
    ports:
      - "4053:4053"
      - "8558:8558"
    networks:
      - cluster-net

  node2:
    build: .
    hostname: node2
    environment:
      AKKA__REMOTE__DOT_NETTY__TCP__HOSTNAME: "node2"
      AKKA__REMOTE__DOT_NETTY__TCP__PORT: "4053"
    ports:
      - "4054:4053"
      - "8559:8558"
    networks:
      - cluster-net

  node3:
    build: .
    hostname: node3
    environment:
      AKKA__REMOTE__DOT_NETTY__TCP__HOSTNAME: "node3"
      AKKA__REMOTE__DOT_NETTY__TCP__PORT: "4053"
    ports:
      - "4055:4053"
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
3. `requiredContactPoints`(2개) 이상 발견될 때까지 반복
4. 발견된 노드의 Management 엔드포인트 프로브
5. 기존 클러스터가 없으면 자동으로 조율하여 클러스터 형성
6. 나머지 노드가 해당 클러스터에 합류

---

## Type B: Kubernetes 기반 디스커버리

Kubernetes API를 통해 Pod를 자동 발견하여 클러스터를 형성합니다.

### NuGet 패키지 (추가분)

```xml
<ItemGroup>
  <!-- Type A 패키지 + Kubernetes Discovery -->
  <PackageReference Include="Akka.Discovery.KubernetesApi" Version="1.5.59" />
  <PackageReference Include="Akka.Coordination.KubernetesApi" Version="1.5.42" />
</ItemGroup>
```

### C# Akka.Hosting 설정

```csharp
builder.Services.AddAkka("ClusterSystem", (configBuilder, provider) =>
{
    configBuilder
        .WithRemoting("", 4053)
        .WithClustering(new ClusterOptions
        {
            SeedNodes = Array.Empty<string>(),
            Roles = new[] { "backend" }
        })
        .WithAkkaManagement(port: 8558)
        .WithClusterBootstrap(serviceName: "my-akka-service")
        .WithKubernetesDiscovery();
});
```

### HOCON 설정 (Kubernetes API Discovery)

```hocon
akka {
  discovery {
    method = kubernetes-api
    kubernetes-api {
      class = "Akka.Discovery.KubernetesApi.KubernetesApiServiceDiscovery, Akka.Discovery.KubernetesApi"
      api-ca-path = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
      api-token-path = "/var/run/secrets/kubernetes.io/serviceaccount/token"
      api-service-host-env-name = "KUBERNETES_SERVICE_HOST"
      api-service-port-env-name = "KUBERNETES_SERVICE_PORT"
      pod-namespace-path = "/var/run/secrets/kubernetes.io/serviceaccount/namespace"
      pod-label-selector = "app={0}"
      use-raw-ip = true
    }
  }

  cluster {
    downing-provider-class = "Akka.Cluster.SBR.SplitBrainResolverProvider, Akka.Cluster"
    split-brain-resolver {
      active-strategy = lease-majority
      stable-after = 20s
      lease-majority {
        lease-implementation = "akka.coordination.lease.kubernetes"
      }
    }
  }

  coordination.lease.kubernetes {
    lease-class = "Akka.Coordination.KubernetesApi.KubernetesLease, Akka.Coordination.KubernetesApi"
    api-ca-path = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
    api-token-path = "/var/run/secrets/kubernetes.io/serviceaccount/token"
    api-service-host-env-name = "KUBERNETES_SERVICE_HOST"
    api-service-port-env-name = "KUBERNETES_SERVICE_PORT"
    namespace-path = "/var/run/secrets/kubernetes.io/serviceaccount/namespace"
  }
}
```

> Kubernetes 환경에서는 `lease-majority` SBR 전략을 권장합니다. CRD 기반 분산 잠금으로 split-brain을 방지합니다.

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

Akka.NET에서는 **Deployment를 StatefulSet보다 권장**합니다. Deployment의 surge 전략이 새 Pod을 먼저 추가한 후 기존 Pod을 제거하므로 클러스터 리밸런싱이 최소화됩니다.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-akka-service
  labels:
    app: my-akka-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-akka-service
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  template:
    metadata:
      labels:
        app: my-akka-service
    spec:
      containers:
        - name: app
          image: my-akka-service:latest
          ports:
            - name: remoting
              containerPort: 4053
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
  name: my-akka-service
spec:
  clusterIP: None
  publishNotReadyAddresses: true   # 필수: readiness 교착상태 방지
  selector:
    app: my-akka-service
  ports:
    - name: management
      port: 8558
      targetPort: 8558
      protocol: TCP
    - name: remoting
      port: 4053
      targetPort: 4053
      protocol: TCP
```

> **`publishNotReadyAddresses: true` 필수**: 이 설정이 없으면 Pod이 ready 상태가 되어야 디스커버리가 되고, 클러스터를 형성해야 ready가 되는 교착상태가 발생합니다.

### External Service (외부 트래픽용)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-akka-service-external
spec:
  type: LoadBalancer
  selector:
    app: my-akka-service
  ports:
    - name: http
      port: 8080
      targetPort: 8080
      protocol: TCP
```

---

## Akka.Hosting Discovery 확장 메서드 요약

| 메서드 | NuGet | 설명 |
|--------|-------|------|
| `WithAkkaManagement(port)` | `Akka.Management` | Management HTTP 시작 |
| `WithClusterBootstrap(serviceName, requiredContactPoints)` | `Akka.Management` | 자동 클러스터 형성 |
| `WithKubernetesDiscovery()` | `Akka.Discovery.KubernetesApi` | K8s API Pod 디스커버리 |
| `WithAzureDiscovery(connectionString)` | `Akka.Discovery.Azure` | Azure Table Storage 디스커버리 |
| `WithDnsDiscovery()` | `Akka.Discovery.Dns` | DNS A/AAAA/SRV 디스커버리 |

---

## 핵심 주의사항

| 항목 | 설명 |
|------|------|
| SeedNodes 비우기 | Bootstrap 사용 시 `SeedNodes = Array.Empty<string>()` 필수. 혼용 금지 |
| requiredContactPoints | 1로 설정하면 단일 노드가 독립 클러스터를 형성하여 split-brain 위험. 최소 2 이상 |
| HOCON 네임스페이스 | `akka { }` 사용 |
| 트랜스포트 | `dot-netty.tcp` |
| 프로토콜 | `akka.tcp://ClusterSystem@host:port` |
| Management 포트 일관성 | 모든 노드가 동일한 Management HTTP 포트(8558) 사용 |
| SBR 기본 전략 | Akka.NET 1.5.2+에서 기본값은 `keep-majority`. K8s에서는 `lease-majority` 권장 |
| 직렬화 | Hyperion이 기본 직렬화기. 크로스노드 메시지에 추가 설정 불필요 |
| Lighthouse 대체 | 1.5+ 신규 프로젝트는 Akka.Management 방식 권장 |
| Deployment vs StatefulSet | Deployment 권장 (surge 전략으로 리밸런싱 최소화) |
| publishNotReadyAddresses | Kubernetes Headless Service에서 `true` 필수 |

## 참고 문서

- [Akka.NET Kubernetes Guide (Petabridge)](https://petabridge.com/blog/akkadotnet-guide-to-kubernetes/)
- [Akka.Bootstrap.Docker (GitHub)](https://github.com/petabridge/akkadotnet-bootstrap/tree/dev/src/Akka.Bootstrap.Docker)
- [Akka.NET Service Discovery](https://getakka.net/articles/discovery/index.html)
- [Akka.Management GitHub](https://github.com/akkadotnet/Akka.Management)
