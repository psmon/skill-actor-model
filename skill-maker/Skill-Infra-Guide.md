# 액터 시스템 인프라 스킬 가이드

분산 액터 시스템의 인프라 구축(서비스 디스커버리, Docker Compose, Kubernetes)을 지원하는 3종의 인프라 스킬 설명 및 베스트 프랙티스입니다.

## 인프라 스킬 목록

| 스킬 | 슬래시 명령어 | 플랫폼 | 디스커버리 |
|------|-------------|--------|----------|
| kotlin-pekko-typed-infra | `/kotlin-pekko-typed-infra` | Kotlin + Pekko Typed 1.1.x | Config / Kubernetes API |
| java-akka-classic-infra | `/java-akka-classic-infra` | Java + Akka Classic 2.7.x | Config / Kubernetes API |
| dotnet-akka-net-infra | `/dotnet-akka-net-infra` | C# + Akka.NET 1.5.x | Config / Kubernetes API |

## 스킬 계층 구조

인프라 스킬은 기본(base) → 클러스터(cluster) → 인프라(infra) 3단계 계층의 최상위입니다.

```
base 스킬 (액터 기본 패턴)
  └── cluster 스킬 (클러스터 멤버십, Singleton, Sharding, PubSub)
        └── infra 스킬 (서비스 디스커버리, Docker Compose, Kubernetes)
```

각 계층의 스킬은 하위 계층의 스킬을 참조합니다:
- **infra 스킬**은 cluster/base 스킬의 액터 패턴을 전제로, 인프라 배포 구성에 집중합니다.
- 액터 코드 자체 생성은 base/cluster 스킬을, 인프라 배포 구성은 infra 스킬을 사용합니다.

## 디스커버리 방식

### Type A: Docker Compose (Config Discovery)

- **용도**: 로컬 개발, CI 테스트, 소규모 배포
- **원리**: HOCON에 노드 엔드포인트를 정적으로 나열, Management + Bootstrap이 자동 클러스터 형성
- **장점**: Kubernetes 없이 즉시 사용 가능
- **제약**: 노드 추가/제거 시 설정 변경 필요

### Type B: Kubernetes (Kubernetes API Discovery)

- **용도**: 프로덕션 Kubernetes 배포
- **원리**: Kubernetes API로 Pod를 자동 발견, label selector 기반
- **장점**: 동적 스케일링, Pod 자동 디스커버리
- **필수 구성**: RBAC(Pod reader), Headless Service, `publishNotReadyAddresses: true`

## 베스트 프랙티스

### 1. Bootstrap과 seed-nodes 혼용 금지

Management Bootstrap 사용 시 `seed-nodes`를 설정하면 안 됩니다. 두 가지가 동시에 동작하면 클러스터 형성이 예측 불가능해집니다.

### 2. required-contact-point-nr은 최소 2

1로 설정하면 단일 노드가 독립 클러스터를 형성하여 split-brain 위험이 있습니다.

### 3. Split Brain Resolver 필수

- Docker Compose: `keep-majority` 전략 권장
- Kubernetes: `lease-majority` 전략 권장 (CRD 기반 분산 잠금)

### 4. Kubernetes Headless Service 필수 설정

```yaml
spec:
  clusterIP: None
  publishNotReadyAddresses: true  # 필수: readiness 교착상태 방지
```

`publishNotReadyAddresses: true`가 없으면 Pod이 ready 상태가 되어야 디스커버리가 되고, 클러스터를 형성해야 ready가 되는 교착상태가 발생합니다.

### 5. HOCON 네임스페이스 주의

| 플랫폼 | 네임스페이스 | 프로토콜 |
|--------|------------|---------|
| Kotlin Pekko | `pekko { }` | `pekko://` |
| Java Akka | `akka { }` | `akka://` |
| C# Akka.NET | `akka { }` | `akka.tcp://` |

### 6. Management 포트 일관성

모든 노드가 동일한 Management HTTP 포트를 사용해야 합니다:
- Pekko: 7626
- Akka Classic: 8558
- Akka.NET: 8558

### 7. coordinated-shutdown 설정

JVM/CLR 안전 종료를 위해 반드시 설정합니다:
- JVM: `coordinated-shutdown.exit-jvm = on`
- .NET: Akka.Hosting이 자동 처리

## 사용 예시

### Docker Compose 인프라 생성

```
/kotlin-pekko-typed-infra docker-compose 3노드 클러스터 구성
```

### Kubernetes 인프라 생성

```
/java-akka-classic-infra kubernetes 3-replica Deployment, RBAC, Headless Service 생성
```

### C# Akka.NET Kubernetes 배포

```
/dotnet-akka-net-infra kubernetes Akka.Hosting 방식으로 K8s Discovery + lease-majority SBR 구성
```

## 플랫폼별 특이사항

### Kotlin Pekko Typed

- Management 1.2.0은 **Pekko 1.1.5+** 필요 (기본 스킬 1.1.3에서 버전업 필요)
- 패키지: `org.apache.pekko:pekko-management_2.13`

### Java Akka Classic

- **BSL 라이선스** 주의 (상용 환경 라이선스 조건 확인)
- 아티팩트 GroupId 구분: Management는 `com.lightbend.akka.management`, Core는 `com.typesafe.akka`

### C# Akka.NET

- **Akka.Hosting** 방식 권장 (HOCON-less 구성)
- `Akka.Coordination.KubernetesApi` — K8s CRD 기반 분산 잠금 (SBR lease-majority용)
- **Deployment를 StatefulSet보다 권장** (surge 전략으로 리밸런싱 최소화)
- 환경변수 HOCON 매핑: 점(`.`) → 이중 밑줄(`__`), 하이픈(`-`) → 단일 밑줄(`_`)

## 참조 문서

- 인프라 원본 문서: `skill-maker/docs/actor/infra/`
  - [infra.md](docs/actor/infra/infra.md) — 인프라 개요
  - [infra-kotlin-pekko-typed.md](docs/actor/infra/infra-kotlin-pekko-typed.md)
  - [infra-java-akka-classic.md](docs/actor/infra/infra-java-akka-classic.md)
  - [infra-dotnet-akka-net.md](docs/actor/infra/infra-dotnet-akka-net.md)
- 스킬 생성 가이드: [Skill-Guide.md](Skill-Guide.md)
- 클러스터 스킬 가이드: [Skill-Cluster-Guide.md](Skill-Cluster-Guide.md)
- 마켓플레이스 배포: [Skill-MarketPlace.md](Skill-MarketPlace.md)
