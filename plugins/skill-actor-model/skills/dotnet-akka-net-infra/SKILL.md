---
name: dotnet-akka-net-infra
description: C# + Akka.NET 분산 클러스터 인프라 코드를 생성합니다. Docker Compose 또는 Kubernetes 환경에서 Akka.NET 클러스터의 서비스 디스커버리, Bootstrap, Management HTTP, RBAC, Deployment YAML 등 인프라 구성 코드를 작성할 때 사용합니다.
argument-hint: "[docker-compose|kubernetes] [요구사항]"
---

# C# + Akka.NET 인프라 스킬

C# + Akka.NET(1.5.x) 기반 분산 클러스터의 인프라(서비스 디스커버리, Docker Compose, Kubernetes) 코드를 생성하는 스킬입니다.

## 참고 문서

- 기본 액터 스킬: [plugins/skill-actor-model/skills/dotnet-akka-net/SKILL.md](../dotnet-akka-net/SKILL.md)
- 클러스터 스킬: [plugins/skill-actor-model/skills/dotnet-akka-net-cluster/SKILL.md](../dotnet-akka-net-cluster/SKILL.md)
- 인프라 문서: [skill-maker/docs/actor/infra/infra-dotnet-akka-net.md](../../../../skill-maker/docs/actor/infra/infra-dotnet-akka-net.md)

## 호환 버전

| 컴포넌트 | 버전 | 비고 |
|---------|------|------|
| Akka.NET | 1.5.60 | 기본 스킬 기준 |
| Akka.Management | 1.5.59 | Cluster Bootstrap 포함 |
| Akka.Discovery | 1.5.60 | Base Discovery 추상화 |
| Akka.Discovery.KubernetesApi | 1.5.59 | Kubernetes Pod Discovery |
| Akka.Coordination.KubernetesApi | 1.5.42 | K8s CRD 기반 분산 잠금 (SBR용) |
| Akka.Hosting | 1.5.60 | HOCON-less 구성 지원 |
| .NET | 6.0 ~ 9.0 | .NET Standard 2.0 호환 |

> **Lighthouse 대체**: Akka.NET 1.5+에서는 `Akka.Management` + `Akka.Discovery`가 Lighthouse 시드 노드 패턴을 대체합니다. 신규 프로젝트는 Management 방식을 권장합니다.

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

### Akka.Hosting + Config Discovery (권장)

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

### docker-compose.yml

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

> **환경변수 -> HOCON 매핑 규약**: `AKKA__CLUSTER__ROLES__0="worker"`는 `akka.cluster.roles = ["worker"]`로 변환. 점(`.`)은 이중 밑줄(`__`), 하이픈(`-`)은 단일 밑줄(`_`).

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
  publishNotReadyAddresses: true
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

## Type C: StatefulSet + seed-nodes (로컬 K8s / 간이 배포)

Management Bootstrap 없이 StatefulSet의 ordered startup과 seed-nodes 직접 지정으로 클러스터를 구성하는 방법입니다. Docker Desktop Kubernetes 등 로컬 환경에서 간단히 테스트할 때 적합합니다.

### 특징

- Akka.Management 의존성 불필요 (기본 클러스터 + Remote 의존성만 사용)
- `podManagementPolicy: OrderedReady` → pod-0(Seed)이 먼저 기동
- 코드 레벨 `Environment.GetEnvironmentVariable()` → 로컬 개발과 K8s 양립
- `imagePullPolicy: Never` → 로컬 빌드 이미지 직접 사용
- **K8s canonical hostname은 StatefulSet DNS 이름 사용** (Pod IP 사용 금지)

> **HOCON `${?ENV_VAR}` 주의 (JVM/C# 공통)**: HOCON의 `${?ENV_VAR}` fallback은 문자열/숫자 타입에는 동작하지만, **리스트 타입(`seed-nodes`)에는 문자열로 파싱되어 타입 오류가 발생**합니다. 이 문제는 Akka.NET뿐 아니라 JVM(Akka Classic, Pekko Typed)에서도 동일합니다. 따라서 환경변수 기반 설정은 코드 레벨에서 HOCON 문자열을 동적으로 조합하는 패턴을 사용합니다.

> **canonical hostname과 seed-nodes 일치 필수**: K8s에서 `status.podIP`를 canonical hostname으로 사용하면 seed-nodes의 DNS 주소와 불일치하여 클러스터 조인이 실패합니다. 반드시 StatefulSet DNS 이름(`$(POD_NAME).{service}.default.svc.cluster.local`)을 사용해야 합니다.

### C# Program.cs (환경변수 기반 HOCON)

```csharp
var hostname = Environment.GetEnvironmentVariable("CLUSTER_HOSTNAME") ?? "127.0.0.1";
var port = Environment.GetEnvironmentVariable("CLUSTER_PORT") ?? "0";
var seedNodes = Environment.GetEnvironmentVariable("CLUSTER_SEED_NODES") ?? "";
var minNr = Environment.GetEnvironmentVariable("CLUSTER_MIN_NR") ?? "1";

var seedNodesHocon = string.IsNullOrEmpty(seedNodes)
    ? "seed-nodes = []"
    : $"seed-nodes = [{seedNodes}]";

var hocon = $@"
akka {{
    actor.provider = cluster
    remote {{
        dot-netty.tcp {{
            hostname = ""{hostname}""
            port = {port}
        }}
    }}
    cluster {{
        {seedNodesHocon}
        min-nr-of-members = {minNr}
        downing-provider-class = ""Akka.Cluster.SBR.SplitBrainResolverProvider, Akka.Cluster""
    }}
    coordinated-shutdown.exit-clr = on
}}
";

var config = ConfigurationFactory.ParseString(hocon);
var system = ActorSystem.Create("ClusterSystem", config);
system.ActorOf(Props.Create(() => new ClusterListenerActor(system.DeadLetters)), "clusterListener");
await system.WhenTerminated;
```

> **코드 레벨 HOCON 조합**: `Environment.GetEnvironmentVariable()`로 환경변수를 읽어 HOCON 문자열을 동적으로 조합합니다. HOCON `${?ENV_VAR}` fallback은 리스트 타입(`seed-nodes`)에서 타입 오류가 발생하므로(JVM/C# 공통), 코드 레벨 조합이 안전합니다.

### csproj 변경 (Library → Exe)

```xml
<PropertyGroup>
    <OutputType>Exe</OutputType>
    <TargetFramework>net9.0</TargetFramework>
</PropertyGroup>
<ItemGroup>
    <PackageReference Include="Akka.Remote" Version="1.5.60" />
    <!-- Akka.Cluster, Akka.Cluster.Tools 등 기존 패키지 유지 -->
</ItemGroup>
```

### Dockerfile (멀티스테이지)

```dockerfile
FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
WORKDIR /src
COPY MyApp/MyApp.csproj MyApp/
RUN dotnet restore MyApp/MyApp.csproj
COPY MyApp/ MyApp/
RUN dotnet publish MyApp/MyApp.csproj -c Release -o /app/publish

FROM mcr.microsoft.com/dotnet/runtime:9.0
WORKDIR /app
COPY --from=build /app/publish ./
EXPOSE 4053
ENTRYPOINT ["dotnet", "MyApp.dll"]
```

### k8s-cluster.yaml (Headless Service + StatefulSet)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: akkanet-cluster
spec:
  clusterIP: None
  selector:
    app: akkanet-cluster
  ports:
    - name: remoting
      port: 4053
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: akkanet-cluster
spec:
  serviceName: akkanet-cluster
  replicas: 2
  podManagementPolicy: OrderedReady
  selector:
    matchLabels:
      app: akkanet-cluster
  template:
    metadata:
      labels:
        app: akkanet-cluster
    spec:
      containers:
        - name: akkanet-node
          image: my-akka-service:latest
          imagePullPolicy: Never
          ports:
            - containerPort: 4053
          env:
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: CLUSTER_HOSTNAME
              value: "$(POD_NAME).akkanet-cluster.default.svc.cluster.local"
            - name: CLUSTER_PORT
              value: "4053"
            - name: CLUSTER_SEED_NODES
              value: '"akka.tcp://ClusterSystem@akkanet-cluster-0.akkanet-cluster.default.svc.cluster.local:4053"'
            - name: CLUSTER_MIN_NR
              value: "2"
          readinessProbe:
            tcpSocket:
              port: 4053
            initialDelaySeconds: 15
            periodSeconds: 5
```

### Type A/B와의 비교

| 항목 | Type A/B (Bootstrap) | Type C (seed-nodes) |
|------|---------------------|---------------------|
| 추가 NuGet | Akka.Management, Discovery | Akka.Remote のみ |
| 디스커버리 | 자동 (Config/K8s API) | 수동 (seed-nodes 환경변수) |
| 노드 추가 | 자동 발견 | seed-nodes 재설정 필요 |
| 적합 환경 | 프로덕션, 동적 스케일링 | 로컬 개발, 고정 노드 수 |
| 복잡도 | 높음 (RBAC, Management HTTP) | 낮음 |

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
| SBR 기본 전략 | Docker에서는 `keep-majority`, K8s에서는 `lease-majority` 권장 |
| Lighthouse 대체 | 1.5+ 신규 프로젝트는 Akka.Management 방식 권장 |
| Deployment vs StatefulSet | Deployment 권장 (surge 전략으로 리밸런싱 최소화) |
| publishNotReadyAddresses | Kubernetes Headless Service에서 `true` 필수 |
| 환경변수 매핑 | 점(`.`)은 이중 밑줄(`__`), 하이픈(`-`)은 단일 밑줄(`_`) |

## 코드 생성 규칙

1. **HOCON 설정**은 `akka { }` 블록으로 작성합니다.
2. **트랜스포트**는 `dot-netty.tcp`를 사용하고, 프로토콜은 `akka.tcp://`입니다.
3. **Bootstrap 사용 시 `SeedNodes`를 비웁니다** (`Array.Empty<string>()`). 혼용 금지.
4. **Akka.Hosting**의 `.WithAkkaManagement()` + `.WithClusterBootstrap()` 패턴을 권장합니다.
5. **Docker Compose**에서는 Config Discovery(`method = config`)를 사용합니다.
6. **Kubernetes**에서는 `.WithKubernetesDiscovery()` 또는 HOCON `method = kubernetes-api`를 사용합니다.
7. **Kubernetes RBAC**에서 Pod `get`, `watch`, `list` 권한을 부여합니다.
8. **Headless Service**에 `publishNotReadyAddresses: true`를 설정합니다.
9. **SBR(Split Brain Resolver)**을 프로덕션에서 반드시 설정합니다. K8s에서는 `lease-majority` 권장.
10. **`requiredContactPoints`**는 최소 2 이상으로 설정합니다.
11. **Management 포트**(8558)는 모든 노드에서 동일하게 설정합니다.
12. **Deployment**를 StatefulSet보다 권장합니다 (surge 전략).
13. **환경변수 매핑**: 점(`.`)은 이중 밑줄(`__`), 하이픈(`-`)은 단일 밑줄(`_`)로 변환합니다.
14. **Kubernetes SBR**에서는 `Akka.Coordination.KubernetesApi`의 `lease-majority` 전략을 사용합니다.

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

## Cafe24 API 제한 대응 업데이트 (2026-03)

- K8s 배포에 아래 변수 추가를 권장합니다.
  - `CAFE24_BUCKET_CAPACITY`
  - `CAFE24_LEAK_RATE_PER_SECOND`
  - `CAFE24_PER_MALL_MAX_RPS`
- 통합 검증 순서: rollout -> cluster/info -> cafe24 call/metrics -> logs -> graceful shutdown.
