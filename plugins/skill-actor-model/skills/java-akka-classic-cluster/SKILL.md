---
name: java-akka-classic-cluster
description: Java + Akka Classic 클러스터 코드를 생성합니다. Java로 Akka Classic 기반 클러스터를 구현하거나, 클러스터 멤버십, Singleton, Sharding, Distributed PubSub, 클러스터 라우팅, Split Brain Resolver 등 Akka 클러스터 패턴을 작성할 때 사용합니다.
argument-hint: "[클러스터패턴] [요구사항]"
---

# Java + Akka Classic 클러스터 스킬

Java + Akka Classic(2.7.x) 기반의 클러스터 코드를 생성하는 스킬입니다.

## 참고 문서

- 기본 액터 스킬: [plugins/skill-actor-model/skills/java-akka-classic/SKILL.md](../java-akka-classic/SKILL.md)
- 클러스터 문서: [skill-maker/docs/actor/cluster/cluster.md](../../../../skill-maker/docs/actor/cluster/cluster.md)
- 크로스 플랫폼 비교: [skill-maker/docs/actor/05-cross-platform-comparison.md](../../../../skill-maker/docs/actor/05-cross-platform-comparison.md)

## 호환 버전

- **프레임워크**: Akka Classic 2.7.x (기본 스킬과 동일)
- **언어**: Java 11+
- **빌드**: Gradle / Maven
- **라이선스**: BSL (Business Source License)
- **클러스터 패키지**:
  - `akka-cluster` — 클러스터 멤버십, 이벤트, 라우팅
  - `akka-cluster-tools` — Singleton, Distributed PubSub
  - `akka-cluster-sharding` — Cluster Sharding
- **HOCON 네임스페이스**: `akka { }`
- **프로토콜**: `akka://`

### Gradle 의존성

```groovy
def AkkaVersion = "2.7.1"
dependencies {
    implementation "com.typesafe.akka:akka-actor_2.13:${AkkaVersion}"
    implementation "com.typesafe.akka:akka-cluster_2.13:${AkkaVersion}"
    implementation "com.typesafe.akka:akka-cluster-tools_2.13:${AkkaVersion}"
    implementation "com.typesafe.akka:akka-cluster-sharding_2.13:${AkkaVersion}"

    testImplementation "com.typesafe.akka:akka-testkit_2.13:${AkkaVersion}"
}
```

## 지원 패턴

### 1. 클러스터 설정 & 멤버십 (Cluster Membership)
- `Cluster.get(system)` 클러스터 확장
- `ClusterEvent.MemberUp`, `UnreachableMember`, `MemberRemoved` 이벤트
- `Cluster.get(system).subscribe()` 이벤트 구독
- `Cluster.get(system).selfMember().hasRole("role")` 역할 체크
- seed-nodes HOCON 설정

```java
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class ClusterListenerActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final Cluster cluster = Cluster.get(getContext().getSystem());

    public static Props props() {
        return Props.create(ClusterListenerActor.class);
    }

    @Override
    public void preStart() {
        cluster.subscribe(getSelf(),
            ClusterEvent.initialStateAsEvents(),
            ClusterEvent.MemberUp.class,
            ClusterEvent.UnreachableMember.class,
            ClusterEvent.MemberRemoved.class);
    }

    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ClusterEvent.MemberUp.class, event -> {
                log.info("Member is Up: {}", event.member());
            })
            .match(ClusterEvent.UnreachableMember.class, event -> {
                log.warning("Member unreachable: {}", event.member());
            })
            .match(ClusterEvent.MemberRemoved.class, event -> {
                log.info("Member removed: {}", event.member());
            })
            .build();
    }
}
```

#### 역할 기반 액터 배포

```java
Cluster cluster = Cluster.get(system);
if (cluster.selfMember().hasRole("backend")) {
    system.actorOf(WorkerActor.props(), "worker");
}
if (cluster.selfMember().hasRole("frontend")) {
    system.actorOf(FrontendActor.props(), "frontend");
}
```

### 2. 클러스터 Singleton (Cluster Singleton)
- `ClusterSingletonManager`: 클러스터 내 단일 인스턴스 보장
- `ClusterSingletonProxy`: 싱글턴에 대한 프록시 접근
- 가장 오래된 노드에서 실행, 노드 이탈 시 자동 핸드오버
- `PoisonPill` 또는 커스텀 종료 메시지

```java
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;

// Singleton 대상 액터
public class CounterSingletonActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private int count = 0;

    public static Props props() {
        return Props.create(CounterSingletonActor.class);
    }

    // 메시지 정의
    public static final class Increment {
        public static final Increment INSTANCE = new Increment();
    }

    public static final class GetCount {
        private final ActorRef replyTo;
        public GetCount(ActorRef replyTo) { this.replyTo = replyTo; }
        public ActorRef getReplyTo() { return replyTo; }
    }

    public static final class CountValue {
        private final int value;
        public CountValue(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Increment.class, msg -> {
                count++;
                log.info("Counter incremented to {}", count);
            })
            .match(GetCount.class, msg -> {
                msg.getReplyTo().tell(new CountValue(count), getSelf());
            })
            .build();
    }
}
```

#### Singleton Manager & Proxy 초기화

```java
// Singleton Manager: 클러스터에서 단일 인스턴스 관리
system.actorOf(
    ClusterSingletonManager.props(
        CounterSingletonActor.props(),
        akka.actor.PoisonPill.getInstance(),
        ClusterSingletonManagerSettings.create(system)
            .withRole("backend")
    ),
    "counterSingleton"
);

// Singleton Proxy: 싱글턴에 접근하기 위한 프록시
ActorRef proxy = system.actorOf(
    ClusterSingletonProxy.props(
        "/user/counterSingleton",
        ClusterSingletonProxySettings.create(system)
            .withRole("backend")
    ),
    "counterProxy"
);

// Proxy를 통해 메시지 전송
proxy.tell(CounterSingletonActor.Increment.INSTANCE, ActorRef.noSender());
```

| 항목 | 설명 |
|------|------|
| Manager | 가장 오래된 노드에서 실제 액터 실행 |
| Proxy | 어느 노드에서든 싱글턴에 접근 가능 |
| 핸드오버 | 노드 이탈 시 다음 oldest 노드로 자동 이전 |
| 종료 메시지 | `PoisonPill` 또는 커스텀 종료 메시지 지정 |

### 3. 클러스터 Sharding (Cluster Sharding)
- `ClusterSharding.get(system).start()` 로 샤딩 리전 초기화
- `MessageExtractor`: 메시지에서 `entityId`, `shardId` 추출
- `entityId`로 엔티티 자동 생성/라우팅
- 패시베이션: 유휴 엔티티 자동 종료

```java
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;
import akka.event.Logging;
import akka.event.LoggingAdapter;

// Shard Entity 액터
public class DeviceActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final String entityId;
    private double temperature = 0.0;

    public DeviceActor(String entityId) {
        this.entityId = entityId;
    }

    public static Props props(String entityId) {
        return Props.create(DeviceActor.class, entityId);
    }

    // 메시지 정의 (entityId 포함 필수)
    public static final class RecordTemperature {
        private final String deviceId;
        private final double value;
        public RecordTemperature(String deviceId, double value) {
            this.deviceId = deviceId;
            this.value = value;
        }
        public String getDeviceId() { return deviceId; }
        public double getValue() { return value; }
    }

    public static final class GetTemperature {
        private final String deviceId;
        public GetTemperature(String deviceId) { this.deviceId = deviceId; }
        public String getDeviceId() { return deviceId; }
    }

    public static final class TemperatureReading {
        private final String deviceId;
        private final double value;
        public TemperatureReading(String deviceId, double value) {
            this.deviceId = deviceId;
            this.value = value;
        }
        public String getDeviceId() { return deviceId; }
        public double getValue() { return value; }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(RecordTemperature.class, msg -> {
                temperature = msg.getValue();
                log.info("Device {} temperature recorded: {}", entityId, temperature);
            })
            .match(GetTemperature.class, msg -> {
                getSender().tell(new TemperatureReading(entityId, temperature), getSelf());
            })
            .matchEquals("passivate", msg -> {
                getContext().getParent().tell(
                    new ShardRegion.Passivate(akka.actor.PoisonPill.getInstance()), getSelf());
            })
            .build();
    }
}
```

#### MessageExtractor 구현

```java
public class DeviceMessageExtractor extends ShardRegion.MessageExtractor {
    private final int numberOfShards;

    public DeviceMessageExtractor(int numberOfShards) {
        this.numberOfShards = numberOfShards;
    }

    @Override
    public String entityId(Object message) {
        if (message instanceof DeviceActor.RecordTemperature msg) {
            return msg.getDeviceId();
        } else if (message instanceof DeviceActor.GetTemperature msg) {
            return msg.getDeviceId();
        }
        return null;
    }

    @Override
    public Object entityMessage(Object message) {
        return message;  // 메시지를 그대로 엔티티에 전달
    }

    @Override
    public String shardId(Object message) {
        if (message instanceof DeviceActor.RecordTemperature msg) {
            return String.valueOf(Math.abs(msg.getDeviceId().hashCode()) % numberOfShards);
        } else if (message instanceof DeviceActor.GetTemperature msg) {
            return String.valueOf(Math.abs(msg.getDeviceId().hashCode()) % numberOfShards);
        }
        return null;
    }
}
```

#### Sharding 초기화 & 사용

```java
// Sharding 리전 시작
int numberOfShards = 100;
ActorRef shardRegion = ClusterSharding.get(system).start(
    "Device",                                              // typeName
    DeviceActor.props(""),                                 // entityProps
    ClusterShardingSettings.create(system).withRole("backend"),
    new DeviceMessageExtractor(numberOfShards)
);

// 메시지 전송 (entityId 기반 자동 라우팅)
shardRegion.tell(new DeviceActor.RecordTemperature("sensor-1", 23.5), ActorRef.noSender());
shardRegion.tell(new DeviceActor.GetTemperature("sensor-1"), testProbe.getRef());
```

> **numberOfShards**: 계획된 최대 클러스터 노드 수의 10배를 권장합니다.

### 4. Distributed PubSub (분산 발행-구독)
- `DistributedPubSub.get(system).mediator()` 미디에이터
- `DistributedPubSubMediator.Subscribe` / `Unsubscribe` 토픽 구독
- `DistributedPubSubMediator.Publish` 토픽 발행
- `DistributedPubSubMediator.Send` 특정 경로 전달
- 클러스터 전체에 메시지 전파

```java
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;

// 구독자 액터
public class SubscriberActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final ActorRef mediator = DistributedPubSub.get(getContext().getSystem()).mediator();

    public static Props props(String topic) {
        return Props.create(SubscriberActor.class, topic);
    }

    public SubscriberActor(String topic) {
        mediator.tell(new DistributedPubSubMediator.Subscribe(topic, getSelf()), getSelf());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(DistributedPubSubMediator.SubscribeAck.class, msg -> {
                log.info("Subscribed to topic");
            })
            .match(String.class, msg -> {
                log.info("Received from topic: {}", msg);
                getSender().tell("ack:" + msg, getSelf());
            })
            .build();
    }
}

// 발행자 액터
public class PublisherActor extends AbstractActor {
    private final ActorRef mediator = DistributedPubSub.get(getContext().getSystem()).mediator();

    public static Props props() {
        return Props.create(PublisherActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, msg -> {
                mediator.tell(
                    new DistributedPubSubMediator.Publish("notifications", msg),
                    getSelf()
                );
            })
            .build();
    }
}
```

#### PubSub 사용

```java
// 구독자 생성
ActorRef subscriber = system.actorOf(SubscriberActor.props("notifications"), "sub1");

// 발행자 생성 및 메시지 발행
ActorRef publisher = system.actorOf(PublisherActor.props(), "pub1");
publisher.tell("Hello Cluster!", ActorRef.noSender());
```

| API | 설명 |
|-----|------|
| `Subscribe(topic, actorRef)` | 토픽에 구독 등록 |
| `Unsubscribe(topic, actorRef)` | 토픽 구독 해제 |
| `Publish(topic, message)` | 토픽의 모든 구독자에게 메시지 전파 |
| `Send(path, message, localAffinity)` | 특정 경로의 액터에게 전달 |
| `SubscribeAck` | 구독 완료 확인 응답 |

### 5. 클러스터 라우팅 (Cluster-Aware Routing)
- `ClusterRouterPool`: 클러스터 노드에 라우티 풀 자동 배포
- `ClusterRouterGroup`: 기존 액터 경로 기반 클러스터 라우팅
- `ClusterRouterPoolSettings`: 총 인스턴스, 노드당 최대 인스턴스
- 역할(role) 기반 배포 필터링

```java
import akka.cluster.routing.ClusterRouterPool;
import akka.cluster.routing.ClusterRouterPoolSettings;
import akka.cluster.routing.ClusterRouterGroup;
import akka.cluster.routing.ClusterRouterGroupSettings;
import akka.routing.RoundRobinPool;
import akka.routing.ConsistentHashingGroup;

// ClusterRouterPool: 클러스터 전체에 라우티 풀 분산
ActorRef poolRouter = system.actorOf(
    new ClusterRouterPool(
        new RoundRobinPool(0),
        new ClusterRouterPoolSettings(
            100,    // totalInstances: 클러스터 전체 최대 라우티 수
            3,      // maxInstancesPerNode: 노드당 최대 라우티 수
            true,   // allowLocalRoutees: 로컬 노드에도 배포
            "backend"  // useRole: 해당 역할의 노드에만 배포
        )
    ).props(WorkerActor.props()),
    "workerRouter"
);

// ClusterRouterGroup: 기존 액터 경로 기반 클러스터 라우팅
ActorRef groupRouter = system.actorOf(
    new ClusterRouterGroup(
        new ConsistentHashingGroup(java.util.Collections.emptyList()),
        new ClusterRouterGroupSettings(
            100,
            java.util.Arrays.asList("/user/statsWorker"),
            true,
            java.util.Collections.singleton("compute")
        )
    ).props(),
    "statsRouter"
);
```

#### HOCON 선언적 클러스터 라우터

```hocon
akka.actor.deployment {
  /workerRouter {
    router = round-robin-pool
    cluster {
      enabled = on
      max-nr-of-instances-per-node = 3
      allow-local-routees = on
      use-role = backend
    }
  }
  /statsRouter {
    router = consistent-hashing-group
    routees.paths = ["/user/statsWorker"]
    cluster {
      enabled = on
      allow-local-routees = on
      use-roles = ["compute"]
    }
  }
}
```

```java
// FromConfig로 HOCON 설정 기반 클러스터 라우터 생성
import akka.routing.FromConfig;

ActorRef router = system.actorOf(
    FromConfig.getInstance().props(WorkerActor.props()),
    "workerRouter"
);
```

| 유형 | 클래스 | 설명 |
|------|--------|------|
| Pool | `ClusterRouterPool` | 클러스터 노드에 라우티 자동 생성/배포 |
| Group | `ClusterRouterGroup` | 기존 액터 경로를 클러스터 전체에서 라우팅 |
| 설정 | `ClusterRouterPoolSettings` | totalInstances, maxPerNode, role 필터 |
| HOCON | `cluster { enabled = on }` | 코드 변경 없이 설정으로 클러스터 라우팅 |

### 6. Split Brain Resolver / Downing 전략
- `auto-down-unreachable-after`: 단순 자동 다운 (프로덕션 비권장)
- Split Brain Resolver (SBR): 네트워크 파티션 대응
- 다운 전략: keep-majority, keep-oldest, keep-referee, down-all, lease-majority

```hocon
# 프로덕션 환경 - Split Brain Resolver 권장
akka.cluster {
  downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"

  split-brain-resolver {
    # 전략: keep-majority | keep-oldest | keep-referee | down-all | lease-majority
    active-strategy = keep-majority

    # 안정 상태 대기 시간 (파티션 감지 후 결정까지)
    stable-after = 20s

    # keep-majority: 과반수 쪽을 유지
    keep-majority {
      role = ""  # 특정 역할로 한정 (빈 문자열이면 전체)
    }

    # keep-oldest: 가장 오래된 노드 쪽을 유지
    keep-oldest {
      down-if-alone = on
      role = ""
    }
  }
}

# 개발/테스트 환경 - 간단한 자동 다운
akka.cluster {
  auto-down-unreachable-after = 10s
}
```

| 전략 | 설명 | 사용 시나리오 |
|------|------|-------------|
| `keep-majority` | 노드 수가 더 많은 쪽 유지 | 일반적인 프로덕션 환경 |
| `keep-oldest` | 가장 오래된 노드 포함 쪽 유지 | Singleton 보호 필요 시 |
| `keep-referee` | 지정된 레퍼리 노드 포함 쪽 유지 | 핵심 노드 지정 가능 시 |
| `down-all` | 파티션 감지 시 전체 다운 | 안전 최우선 환경 |
| `lease-majority` | 분산 잠금 기반 결정 | 외부 리스 서비스 사용 시 |

### 7. HOCON 클러스터 설정 템플릿

```hocon
akka {
  actor {
    provider = cluster

    # 직렬화 설정 (클러스터 환경 필수)
    serializers {
      jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
    }
    serialization-bindings {
      "com.example.CborSerializable" = jackson-json
    }
  }

  remote {
    artery {
      canonical {
        hostname = "127.0.0.1"
        port = 2551
      }
    }
  }

  cluster {
    seed-nodes = [
      "akka://ClusterSystem@127.0.0.1:2551",
      "akka://ClusterSystem@127.0.0.1:2552"
    ]

    roles = ["backend"]

    # 최소 클러스터 크기 (이 수 이상의 노드가 up이어야 기능 활성화)
    min-nr-of-members = 1

    # 역할별 최소 멤버 수
    role {
      backend.min-nr-of-members = 1
    }

    # Downing (프로덕션: SBR, 개발: auto-down)
    downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"

    # Failure Detector 튜닝
    failure-detector {
      threshold = 8.0
      acceptable-heartbeat-pause = 3s
      heartbeat-interval = 1s
    }

    # 싱글턴 설정
    singleton {
      singleton-name = "singleton"
      role = ""
      hand-over-retry-interval = 1s
      min-number-of-hand-over-retries = 15
    }

    # 샤딩 설정
    sharding {
      number-of-shards = 100
      role = ""
      passivation {
        default-idle-strategy.idle-entity.timeout = 2 minutes
      }
    }
  }
}
```

#### 단일 노드 테스트용 설정

```hocon
akka {
  actor.provider = cluster
  remote.artery {
    canonical.hostname = "127.0.0.1"
    canonical.port = 0
  }
  cluster {
    seed-nodes = []
    min-nr-of-members = 1
    # 테스트 시 자신에게 자동 조인
    downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
  }
}
```

```java
// 테스트용 단일 노드 클러스터 자동 조인
Cluster cluster = Cluster.get(system);
cluster.join(cluster.selfAddress());
```

## 코드 생성 규칙

1. **클러스터 멤버십 이벤트**는 `Cluster.get(system).subscribe(self, ...)` 로 구독합니다.
2. **Singleton**은 `ClusterSingletonManager` + `ClusterSingletonProxy` 쌍으로 생성합니다.
3. **Sharding**은 `MessageExtractor`를 구현하여 `entityId`와 `shardId`를 추출합니다.
4. **PubSub**은 `DistributedPubSub.get(system).mediator()`를 통해 `Publish`/`Subscribe` 합니다.
5. **클러스터 라우터**는 `ClusterRouterPool`/`ClusterRouterGroup` + Settings를 사용합니다.
6. **프로덕션 환경**에서는 반드시 Split Brain Resolver를 설정합니다.
7. **메시지는 직렬화 가능**해야 합니다. Jackson JSON 또는 Java Serializable을 구현합니다.
8. **HOCON 설정**은 `akka { }` 블록에 작성하며, `actor.provider = cluster`를 지정합니다.
9. **역할(role)**을 활용하여 노드별 기능을 분리합니다.
10. **단일 노드 테스트** 시 `cluster.join(cluster.selfAddress())`로 자기 자신에게 조인합니다.

$ARGUMENTS
