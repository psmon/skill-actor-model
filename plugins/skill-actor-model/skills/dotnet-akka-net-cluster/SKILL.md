---
name: dotnet-akka-net-cluster
description: C# + Akka.NET 클러스터 코드를 생성합니다. C#으로 Akka.NET 기반 클러스터를 구현하거나, 클러스터 멤버십, Singleton, Sharding, Distributed PubSub, 클러스터 라우팅, Member Roles 등 Akka.NET 클러스터 패턴을 작성할 때 사용합니다.
argument-hint: "[클러스터패턴] [요구사항]"
---

# C# + Akka.NET 클러스터 스킬

C# + Akka.NET(1.5.x) 기반의 클러스터 코드를 생성하는 스킬입니다.

## 참고 문서

- 기본 액터 스킬: [plugins/skill-actor-model/skills/dotnet-akka-net/SKILL.md](../dotnet-akka-net/SKILL.md)
- 클러스터 문서: [skill-maker/docs/actor/cluster/cluster.md](../../../../skill-maker/docs/actor/cluster/cluster.md)
- 크로스 플랫폼 비교: [skill-maker/docs/actor/05-cross-platform-comparison.md](../../../../skill-maker/docs/actor/05-cross-platform-comparison.md)

## 호환 버전

- **프레임워크**: Akka.NET 1.5.x (기본 스킬과 동일)
- **언어**: C# (.NET 9.0)
- **라이선스**: Apache License 2.0
- **클러스터 패키지**:
  - `Akka.Cluster` — 클러스터 멤버십, 이벤트
  - `Akka.Cluster.Sharding` — Cluster Sharding
  - `Akka.Cluster.Tools` — Singleton, Distributed PubSub
  - `Akka.Cluster.Hosting` — DI 통합 (Akka.Hosting 확장)
- **HOCON 네임스페이스**: `akka { }`
- **트랜스포트**: `dot-netty.tcp`
- **프로토콜**: `akka.tcp://`

### NuGet 패키지

```xml
<ItemGroup>
  <PackageReference Include="Akka" Version="1.5.60" />
  <PackageReference Include="Akka.Cluster" Version="1.5.60" />
  <PackageReference Include="Akka.Cluster.Sharding" Version="1.5.60" />
  <PackageReference Include="Akka.Cluster.Tools" Version="1.5.60" />
  <PackageReference Include="Akka.Cluster.Hosting" Version="1.5.60" />
</ItemGroup>
```

## 지원 패턴

### 1. 클러스터 설정 & 멤버십 (Cluster Membership)
- `Cluster.Get(system)` 클러스터 확장
- `cluster.Subscribe(self, typeof(ClusterEvent.MemberUp), ...)` 이벤트 구독
- `ClusterEvent.MemberUp`, `MemberRemoved`, `UnreachableMember` 이벤트
- `cluster.SelfMember.HasRole("role")` 역할 체크
- `cluster.Leave(address)` graceful leave

```csharp
using Akka.Actor;
using Akka.Cluster;
using Akka.Event;

public class ClusterListenerActor : ReceiveActor
{
    private readonly ILoggingAdapter _log = Context.GetLogger();
    private readonly Cluster _cluster = Cluster.Get(Context.System);

    public ClusterListenerActor()
    {
        Receive<ClusterEvent.MemberUp>(msg =>
        {
            _log.Info("Member is Up: {0}", msg.Member);
        });

        Receive<ClusterEvent.UnreachableMember>(msg =>
        {
            _log.Warning("Member unreachable: {0}", msg.Member);
        });

        Receive<ClusterEvent.MemberRemoved>(msg =>
        {
            _log.Info("Member removed: {0}", msg.Member);
        });

        Receive<ClusterEvent.CurrentClusterState>(msg =>
        {
            _log.Info("Current cluster state: {0} members", msg.Members.Count);
        });
    }

    protected override void PreStart()
    {
        _cluster.Subscribe(Self,
            ClusterEvent.SubscriptionInitialStateMode.InitialStateAsEvents,
            typeof(ClusterEvent.MemberUp),
            typeof(ClusterEvent.UnreachableMember),
            typeof(ClusterEvent.MemberRemoved));
    }

    protected override void PostStop()
    {
        _cluster.Unsubscribe(Self);
    }
}
```

#### 역할 기반 액터 배포

```csharp
var cluster = Cluster.Get(system);
if (cluster.SelfMember.HasRole("backend"))
{
    system.ActorOf(Props.Create<WorkerActor>(), "worker");
}
if (cluster.SelfMember.HasRole("frontend"))
{
    system.ActorOf(Props.Create<FrontendActor>(), "frontend");
}
```

### 2. 클러스터 Singleton (Cluster Singleton)
- `ClusterSingletonManager.Props()` 싱글턴 매니저
- `ClusterSingletonProxy.Props()` 싱글턴 프록시
- 가장 오래된 노드에서 실행, 노드 이탈 시 자동 핸드오버
- `PoisonPill` 또는 커스텀 종료 메시지

```csharp
using Akka.Actor;
using Akka.Cluster.Tools.Singleton;
using Akka.Event;

// Singleton 대상 액터
public class CounterSingletonActor : ReceiveActor
{
    private readonly ILoggingAdapter _log = Context.GetLogger();
    private int _count = 0;

    // 메시지 정의
    public sealed record Increment;
    public sealed record GetCount(IActorRef ReplyTo);
    public sealed record CountValue(int Value);

    public CounterSingletonActor()
    {
        Receive<Increment>(_ =>
        {
            _count++;
            _log.Info("Counter incremented to {0}", _count);
        });

        Receive<GetCount>(msg =>
        {
            msg.ReplyTo.Tell(new CountValue(_count));
        });
    }
}
```

#### Singleton Manager & Proxy 초기화

```csharp
// Singleton Manager: 클러스터에서 단일 인스턴스 관리
system.ActorOf(
    ClusterSingletonManager.Props(
        singletonProps: Props.Create<CounterSingletonActor>(),
        terminationMessage: PoisonPill.Instance,
        settings: ClusterSingletonManagerSettings.Create(system)
            .WithRole("backend")
    ),
    name: "counterSingleton"
);

// Singleton Proxy: 싱글턴에 접근하기 위한 프록시
var proxy = system.ActorOf(
    ClusterSingletonProxy.Props(
        singletonManagerPath: "/user/counterSingleton",
        settings: ClusterSingletonProxySettings.Create(system)
            .WithRole("backend")
    ),
    name: "counterProxy"
);

// Proxy를 통해 메시지 전송
proxy.Tell(new CounterSingletonActor.Increment());
```

#### Akka.Hosting 방식 Singleton

```csharp
builder.Services.AddAkka("ClusterSystem", config =>
{
    config
        .WithRemoting("localhost", 2551)
        .WithClustering()
        .WithSingleton<CounterSingletonActor>(
            "counterSingleton",
            Props.Create<CounterSingletonActor>(),
            new ClusterSingletonOptions { Role = "backend" });
});
```

### 3. 클러스터 Sharding (Cluster Sharding)
- `ClusterSharding.Get(system).Start()` 샤딩 리전 시작
- `IMessageExtractor`: entityId, shardId 추출
- `ShardEnvelope` 래핑 또는 `IMessageExtractor` 직접 구현
- 패시베이션: 유휴 엔티티 자동 종료

```csharp
using Akka.Actor;
using Akka.Cluster.Sharding;
using Akka.Event;

// Shard Entity 액터
public class DeviceActor : ReceiveActor
{
    private readonly ILoggingAdapter _log = Context.GetLogger();
    private readonly string _entityId;
    private double _temperature = 0.0;

    // 메시지 정의 (entityId 포함 필수)
    public sealed record RecordTemperature(string DeviceId, double Value);
    public sealed record GetTemperature(string DeviceId);
    public sealed record TemperatureReading(string DeviceId, double Value);

    public DeviceActor(string entityId)
    {
        _entityId = entityId;

        Receive<RecordTemperature>(msg =>
        {
            _temperature = msg.Value;
            _log.Info("Device {0} temperature: {1}", _entityId, _temperature);
        });

        Receive<GetTemperature>(msg =>
        {
            Sender.Tell(new TemperatureReading(_entityId, _temperature));
        });
    }
}
```

#### MessageExtractor 구현

```csharp
public class DeviceMessageExtractor : HashCodeMessageExtractor
{
    public DeviceMessageExtractor(int maxNumberOfShards) : base(maxNumberOfShards) { }

    public override string EntityId(object message)
    {
        return message switch
        {
            DeviceActor.RecordTemperature msg => msg.DeviceId,
            DeviceActor.GetTemperature msg => msg.DeviceId,
            ShardRegion.StartEntity start => start.EntityId,
            _ => null
        };
    }

    public override object EntityMessage(object message)
    {
        return message;  // 메시지를 그대로 엔티티에 전달
    }
}
```

#### Sharding 초기화 & 사용

```csharp
// Sharding 리전 시작
int numberOfShards = 100;
var shardRegion = ClusterSharding.Get(system).Start(
    typeName: "Device",
    entityPropsFactory: entityId => Props.Create(() => new DeviceActor(entityId)),
    settings: ClusterShardingSettings.Create(system).WithRole("backend"),
    messageExtractor: new DeviceMessageExtractor(numberOfShards)
);

// 메시지 전송 (entityId 기반 자동 라우팅)
shardRegion.Tell(new DeviceActor.RecordTemperature("sensor-1", 23.5));
shardRegion.Tell(new DeviceActor.GetTemperature("sensor-1"));
```

#### Akka.Hosting 방식 Sharding

```csharp
builder.Services.AddAkka("ClusterSystem", config =>
{
    config
        .WithRemoting("localhost", 2551)
        .WithClustering()
        .WithShardRegion<DeviceActor>(
            "Device",
            (_, _, _) => entityId => Props.Create(() => new DeviceActor(entityId)),
            new DeviceMessageExtractor(100),
            new ShardOptions { Role = "backend" });
});
```

> **numberOfShards**: 계획된 최대 클러스터 노드 수의 10배를 권장합니다.

### 4. Distributed PubSub (분산 발행-구독)
- `DistributedPubSub.Get(system).Mediator` 미디에이터
- `Subscribe(topic, actorRef)` / `Unsubscribe(topic, actorRef)` 토픽 구독
- `Publish(topic, message)` 토픽 발행
- `Send(path, message, localAffinity)` 특정 경로 전달

```csharp
using Akka.Actor;
using Akka.Cluster.Tools.PublishSubscribe;
using Akka.Event;

// 구독자 액터
public class SubscriberActor : ReceiveActor
{
    private readonly ILoggingAdapter _log = Context.GetLogger();

    public SubscriberActor(string topic)
    {
        var mediator = DistributedPubSub.Get(Context.System).Mediator;
        mediator.Tell(new Subscribe(topic, Self));

        Receive<SubscribeAck>(msg =>
        {
            _log.Info("Subscribed to topic: {0}", topic);
        });

        Receive<string>(msg =>
        {
            _log.Info("Received from topic: {0}", msg);
            Sender.Tell($"ack:{msg}");
        });
    }
}

// 발행자 액터
public class PublisherActor : ReceiveActor
{
    private readonly IActorRef _mediator;

    public PublisherActor()
    {
        _mediator = DistributedPubSub.Get(Context.System).Mediator;

        Receive<string>(msg =>
        {
            _mediator.Tell(new Publish("notifications", msg));
        });
    }
}
```

| API | 설명 |
|-----|------|
| `Subscribe(topic, actorRef)` | 토픽에 구독 등록 |
| `Unsubscribe(topic, actorRef)` | 토픽 구독 해제 |
| `Publish(topic, message)` | 토픽의 모든 구독자에게 메시지 전파 |
| `Send(path, message, localAffinity)` | 특정 경로의 액터에게 전달 |
| `SubscribeAck` | 구독 완료 확인 응답 |

### 5. 클러스터 라우팅 (Cluster-Aware Routing)
- `FromConfig.Instance`로 HOCON 기반 클러스터 라우터
- `cluster { enabled = on }` 설정으로 클러스터 확장
- Pool / Group 라우터 모두 지원
- `use-role`로 역할 기반 필터링

```hocon
akka.actor.deployment {
  /workerRouter {
    router = round-robin-pool
    max-nr-of-instances-per-node = 5
    cluster {
      enabled = on
      use-role = backend
    }
  }
  /groupRouter {
    router = consistent-hashing-group
    routees.paths = ["/user/worker"]
    nr-of-instances = 3
    cluster {
      enabled = on
      use-role = backend
    }
  }
}
```

```csharp
using Akka.Actor;
using Akka.Routing;

// HOCON 기반 클러스터 라우터 생성
var poolRouter = system.ActorOf(
    Props.Create<WorkerActor>().WithRouter(FromConfig.Instance),
    "workerRouter"
);

// 코드 기반 클러스터 라우터 (Pool)
var codeRouter = system.ActorOf(
    Props.Create<WorkerActor>().WithRouter(
        new Akka.Cluster.Routing.ClusterRouterPool(
            new RoundRobinPool(0),
            new Akka.Cluster.Routing.ClusterRouterPoolSettings(
                totalInstances: 100,
                maxInstancesPerNode: 5,
                allowLocalRoutees: true,
                useRole: "backend"
            )
        )
    ),
    "codeRouter"
);
```

### 6. Member Roles (멤버 역할)
- HOCON `akka.cluster.roles` 설정
- `cluster.SelfMember.HasRole("role")` 역할 체크
- 역할별 모듈 활성화 (Sharding, Singleton, PubSub)

```hocon
akka.cluster {
  roles = ["web", "backend"]

  # 역할별 모듈 활성화
  sharding {
    role = "backend"
  }
  singleton {
    role = "backend"
  }
  pub-sub {
    role = "web"
  }
}
```

### 7. HOCON 클러스터 설정 템플릿

```hocon
akka {
  actor {
    provider = cluster
  }

  remote {
    dot-netty.tcp {
      hostname = "127.0.0.1"
      port = 8081
    }
  }

  cluster {
    seed-nodes = [
      "akka.tcp://ClusterSystem@127.0.0.1:8081",
      "akka.tcp://ClusterSystem@127.0.0.1:8082"
    ]

    roles = ["backend"]

    # 최소 클러스터 크기
    min-nr-of-members = 1

    # Downing 설정 (프로덕션: SBR, 개발: auto-down)
    # auto-down-unreachable-after = 10s  # 개발용

    # Failure Detector
    failure-detector {
      threshold = 8.0
      acceptable-heartbeat-pause = 3s
      heartbeat-interval = 1s
    }

    # Singleton 설정
    singleton {
      singleton-name = "singleton"
      role = ""
      hand-over-retry-interval = 1s
    }

    # Singleton Proxy 설정
    singleton-proxy {
      singleton-name = ${akka.cluster.singleton.singleton-name}
      role = ""
      singleton-identification-interval = 1s
      buffer-size = 1000
    }

    # Sharding 설정
    sharding {
      # 총 샤드 수 (클러스터 최대 노드 수의 10배 권장)
      number-of-shards = 100
      role = ""

      # 패시베이션 (유휴 엔티티 자동 종료)
      passivation {
        default-idle-strategy.idle-entity.timeout = 2 minutes
      }
    }

    # Distributed PubSub 설정
    pub-sub {
      role = ""
      routing-logic = random
    }
  }
}
```

#### 단일 노드 테스트용 설정

```hocon
akka {
  actor.provider = cluster
  remote.dot-netty.tcp {
    hostname = "127.0.0.1"
    port = 0
  }
  cluster {
    seed-nodes = []
    min-nr-of-members = 1
  }
}
```

```csharp
// 테스트용 단일 노드 클러스터 자동 조인
var cluster = Cluster.Get(system);
cluster.Join(cluster.SelfAddress);
```

#### Akka.Hosting 방식 전체 클러스터 설정

```csharp
builder.Services.AddAkka("ClusterSystem", config =>
{
    config
        .WithRemoting("localhost", 8081)
        .WithClustering(new ClusterOptions
        {
            Roles = new[] { "backend" },
            SeedNodes = new[] { "akka.tcp://ClusterSystem@localhost:8081" }
        })
        .WithSingleton<CounterSingletonActor>(
            "counter",
            Props.Create<CounterSingletonActor>(),
            new ClusterSingletonOptions { Role = "backend" })
        .WithShardRegion<DeviceActor>(
            "Device",
            (_, _, _) => entityId => Props.Create(() => new DeviceActor(entityId)),
            new DeviceMessageExtractor(100),
            new ShardOptions { Role = "backend" })
        .WithActors((system, registry) =>
        {
            var listener = system.ActorOf(
                Props.Create<ClusterListenerActor>(), "cluster-listener");
            registry.Register<ClusterListenerActor>(listener);
        });
});
```

## 코드 생성 규칙

1. **액터는 `ReceiveActor`를 상속**하고 생성자에서 `Receive<T>(handler)`를 등록합니다.
2. **메시지 클래스는 `record`**로 정의합니다.
3. **Singleton**은 `ClusterSingletonManager` + `ClusterSingletonProxy` 쌍으로 생성합니다.
4. **Sharding**은 `HashCodeMessageExtractor`를 상속하여 `EntityId()`, `EntityMessage()`를 구현합니다.
5. **PubSub**은 `DistributedPubSub.Get(system).Mediator`를 통해 `Publish`/`Subscribe` 합니다.
6. **클러스터 라우터**는 HOCON `cluster { enabled = on }`으로 설정하거나 `ClusterRouterPool`을 사용합니다.
7. **HOCON 설정**은 `akka { }` 블록에 작성하며, `actor.provider = cluster`를 지정합니다.
8. **트랜스포트**는 `dot-netty.tcp`를 사용하고, 프로토콜은 `akka.tcp://`입니다.
9. **DI 통합**은 `Akka.Hosting`의 `.WithClustering()` + `.WithSingleton()` + `.WithShardRegion()` 패턴을 권장합니다.
10. **역할(role)**을 활용하여 노드별 기능을 분리합니다.
11. **단일 노드 테스트** 시 `cluster.Join(cluster.SelfAddress)`로 자기 자신에게 조인합니다.

$ARGUMENTS
