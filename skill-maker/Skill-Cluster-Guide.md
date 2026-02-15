# 클러스터 스킬 가이드

## 개요

액터 모델 클러스터 스킬은 3개 플랫폼에서 분산 클러스터 패턴을 생성하는 독립 스킬입니다.
기본 액터 스킬(base skill)의 클러스터 섹션을 확장하여, 심층적인 코드 예시와 설정 템플릿을 제공합니다.

| 스킬 | 플랫폼 | 호환 버전 | 명령어 |
|------|--------|----------|--------|
| `java-akka-classic-cluster` | Java + Akka Classic | 2.7.x | `/java-akka-classic-cluster` |
| `kotlin-pekko-typed-cluster` | Kotlin + Pekko Typed | 1.1.x | `/kotlin-pekko-typed-cluster` |
| `dotnet-akka-net-cluster` | C# + Akka.NET | 1.5.x | `/dotnet-akka-net-cluster` |

## 기본 스킬과의 관계

```
base skill (액터 기본)          cluster skill (클러스터 확장)
├── BasicActor                   ├── Cluster Membership & Events
├── Router                       ├── Cluster Singleton
├── Timer                        ├── Cluster Sharding
├── Batch / FSM                  ├── Distributed PubSub
├── Supervision                  ├── Cluster-Aware Routing
├── Persistence                  ├── Split Brain Resolver
├── Streams                      └── HOCON 클러스터 설정 템플릿
└── 클러스터 (개요만)
```

기본 스킬에는 클러스터 개요(bullet point 수준)만 포함되어 있습니다.
클러스터 스킬은 각 패턴의 전체 코드, HOCON 설정, API 사용법을 상세하게 다룹니다.

## 각 스킬의 커버 패턴

### 공통 패턴 (3개 플랫폼 모두)

| # | 패턴 | 설명 |
|---|------|------|
| 1 | Cluster Membership | 클러스터 가입, seed-nodes, MemberUp/Removed 이벤트, 역할 기반 배포 |
| 2 | Cluster Singleton | 클러스터 내 단일 인스턴스 보장, 핸드오버, 감독 전략 |
| 3 | Cluster Sharding | 엔티티 분산, entityId 기반 자동 라우팅, 패시베이션 |
| 4 | Distributed PubSub | 토픽 기반 메시지 발행/구독, 클러스터 전체 전파 |
| 5 | Cluster-Aware Routing | 클러스터 노드에 라우티 자동 배포, Pool/Group |
| 6 | Split Brain Resolver | 네트워크 파티션 대응, keep-majority/keep-oldest 전략 |
| 7 | HOCON 설정 템플릿 | 전체 클러스터 설정 + 단일 노드 테스트 설정 |

### 플랫폼별 차이

| 항목 | Java (Akka Classic) | Kotlin (Pekko Typed) | C# (Akka.NET) |
|------|---------------------|---------------------|---------------|
| **HOCON** | `akka { }` | `pekko { }` | `akka { }` |
| **프로토콜** | `akka://` | `pekko://` | `akka.tcp://` |
| **Singleton API** | `ClusterSingletonManager` + `ClusterSingletonProxy` | `ClusterSingleton.get().init(SingletonActor.of())` | `ClusterSingletonManager` + `ClusterSingletonProxy` |
| **Sharding API** | `ClusterSharding.get().start()` + `MessageExtractor` | `ClusterSharding.get().init(Entity.of())` + `EntityTypeKey` | `ClusterSharding.Get().Start()` + `HashCodeMessageExtractor` |
| **PubSub** | `DistributedPubSub.get().mediator()` + `Publish/Subscribe` | `Topic.create()` 액터 기반 | `DistributedPubSub.Get().Mediator` + `Publish/Subscribe` |
| **서비스 발견** | N/A (Classic) | `ServiceKey` + `Receptionist` | N/A |
| **DI 통합** | Spring Boot | Spring Boot | `Akka.Hosting` (`.WithClustering()`) |
| **SBR 클래스** | `akka.cluster.sbr.SplitBrainResolverProvider` | `org.apache.pekko.cluster.sbr.SplitBrainResolverProvider` | 내장 SBR |

## 테스트 프로젝트

### 테스트 결과 요약

| 프로젝트 | 테스트 수 | 결과 | 빌드 도구 |
|---------|----------|------|-----------|
| `sample-cluster-java` | 3 | **ALL PASSED** | Gradle (Kotlin DSL) |
| `sample-cluster-kotlin` | 2 | **ALL PASSED** | Gradle (Kotlin DSL) |
| `sample-cluster-dotnet` | 3 | **ALL PASSED** | dotnet test (xUnit) |

### 테스트 시나리오

**Java (sample-cluster-java)**:
1. `clusterListenerReceivesMemberUpEvent` — ClusterListenerActor가 MemberUp 이벤트를 수신하여 reportTo에 알림
2. `counterSingletonActorCountsCorrectly` — CounterSingletonActor의 Increment/GetCount 메시지 처리 검증
3. `pubSubDeliversMessageToSubscriber` — DistributedPubSub mediator를 통한 Publish/Subscribe 메시지 전달

**Kotlin (sample-cluster-kotlin)**:
1. `counter singleton actor counts correctly` — CounterSingletonActor의 Increment/GetCount 동작 검증
2. `pubsub delivers message to subscriber` — Topic.create() 기반 PubSub 발행/구독 메시지 전달

**C# (sample-cluster-dotnet)**:
1. `ClusterListener_should_receive_MemberUp_event` — ClusterListenerActor의 MemberUp 이벤트 수신 검증
2. `CounterSingleton_should_count_correctly` — CounterSingletonActor의 카운터 동작 검증
3. `PubSub_should_deliver_message_to_subscriber` — DistributedPubSub Mediator를 통한 메시지 전달

### 단일 노드 클러스터 테스트 패턴

모든 테스트는 **단일 JVM/프로세스**에서 실행됩니다. 클러스터를 자기 자신에게 조인하여 테스트합니다.

```java
// Java
Cluster cluster = Cluster.get(system);
cluster.join(cluster.selfAddress());
```

```kotlin
// Kotlin
val cluster = Cluster.get(system)
cluster.manager().tell(Join.create(cluster.selfMember().address()))
```

```csharp
// C#
var cluster = Cluster.Get(system);
cluster.Join(cluster.SelfAddress);
```

클러스터 형성까지 약 3초의 대기 시간이 필요합니다 (`Thread.sleep(3000)`).

## 베스트 프랙티스

### 1. 스킬 호출 시 권장 프롬프트

```
/java-akka-classic-cluster Singleton 카운터 액터 + PubSub로 상태 브로드캐스트
/kotlin-pekko-typed-cluster Sharding 디바이스 엔티티 + 패시베이션 2분 설정
/dotnet-akka-net-cluster 클러스터 설정 + Singleton + Sharding 통합 예제
```

### 2. 기본 스킬과 함께 사용

클러스터 스킬은 기본 스킬을 보완합니다. 기본 액터 패턴(ReceiveActor, AbstractActor, AbstractBehavior)은 기본 스킬을 사용하고, 클러스터 배포/분산은 클러스터 스킬을 사용합니다.

```
# 기본 액터 생성
/dotnet-akka-net 주문 처리 액터 + FSM 배치 처리

# 클러스터 배포 설정 추가
/dotnet-akka-net-cluster 위 주문 액터를 Sharding으로 분산 + Singleton 오케스트레이터
```

### 3. 플랫폼별 주의사항

**Java (Akka Classic 2.7.x)**:
- BSL 라이선스 — 상용 환경에서 라이선스 확인 필요
- Classic API 사용 (Typed가 아님) — `AbstractActor`, `getSender()` 패턴
- `MessageExtractor` 직접 구현 필요 (Sharding)

**Kotlin (Pekko Typed 1.1.x)**:
- 패키지는 `org.apache.pekko.*` (절대 `akka.*` 사용 금지)
- HOCON은 `pekko { }` 블록 (절대 `akka { }` 사용 금지)
- `Routers.pool(size, behavior)` — 트레일링 람다 금지, 두 번째 인자로 직접 전달
- `sealed class` 메시지 계층 필수

**C# (Akka.NET 1.5.x)**:
- 트랜스포트: `dot-netty.tcp`, 프로토콜: `akka.tcp://`
- `HashCodeMessageExtractor` 상속으로 Sharding 구현
- `Akka.Hosting` 패턴으로 DI 통합 권장 (`.WithClustering()`, `.WithSingleton()`, `.WithShardRegion()`)
- `record` 타입으로 메시지 정의 권장

### 4. 프로덕션 체크리스트

- [ ] Split Brain Resolver 설정 (auto-down은 개발 전용)
- [ ] seed-nodes 최소 2~3개 설정
- [ ] 역할(role) 기반 노드 기능 분리
- [ ] Sharding numberOfShards = 최대 노드 수 x 10
- [ ] 메시지 직렬화 확인 (Jackson JSON/CBOR 또는 Serializable)
- [ ] Failure Detector 튜닝 (threshold, heartbeat-pause)
- [ ] Singleton에 supervision + graceful stop 메시지 설정
- [ ] 패시베이션으로 유휴 엔티티 메모리 관리
