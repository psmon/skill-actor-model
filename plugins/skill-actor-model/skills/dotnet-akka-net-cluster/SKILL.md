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

### 8. 크로스노드 통신 & 멀티노드 테스트

단일 노드 API 사용법은 1~7번 섹션으로 충분하지만, 실제 멀티노드 클러스터 운용 시에는 크로스노드 통신에 필요한 추가 패턴이 있습니다.

#### 8-1. 크로스노드 액터 참조

`ActorSelection`으로 다른 노드의 액터에 경로 기반으로 접근합니다.

```csharp
// 다른 노드의 액터에 접근 (원격 주소 + 액터 경로)
var seedAddress = Cluster.Get(seedSystem).SelfAddress;
var remoteActor = joiningSystem.ActorSelection($"{seedAddress}/user/myActor");
remoteActor.Tell(message);
```

| 방법 | 코드 | 설명 |
|------|------|------|
| `ActorSelection` | `system.ActorSelection("akka.tcp://System@host:port/user/actor")` | 경로 기반 리모트 접근 |
| `SelfAddress` | `Cluster.Get(system).SelfAddress` | 클러스터 노드의 원격 주소 획득 |

#### 8-2. 크로스노드 메시지 직렬화

Akka.NET은 기본 직렬화기(Hyperion)가 대부분의 C# 타입을 자동 처리하므로, **추가 직렬화 설정이 불필요**합니다.

```csharp
// record 메시지는 추가 설정 없이 크로스노드 통신 가능
public sealed record Increment;
public sealed record GetCount(IActorRef ReplyTo);
public sealed record CountValue(int Value);
```

> **참고**: 커스텀 직렬화가 필요한 경우 `akka.actor.serialization-bindings`에서 설정할 수 있습니다.

#### 8-3. 멀티노드 HOCON 설정 템플릿

**Seed 노드** (고정 포트):

```hocon
akka {
  actor.provider = cluster
  remote.dot-netty.tcp {
    hostname = "127.0.0.1"
    port = 25522
  }
  cluster {
    seed-nodes = ["akka.tcp://ClusterSystem@127.0.0.1:25522"]
    min-nr-of-members = 2
  }
}
```

**Joining 노드** (자동 포트):

```hocon
akka {
  actor.provider = cluster
  remote.dot-netty.tcp {
    hostname = "127.0.0.1"
    port = 0
  }
  cluster {
    seed-nodes = ["akka.tcp://ClusterSystem@127.0.0.1:25522"]
    min-nr-of-members = 2
  }
}
```

| 설정 | Seed 노드 | Joining 노드 |
|------|-----------|-------------|
| `port` | 고정 포트 (예: 25522) | `0` (자동 할당) |
| `seed-nodes` | 자기 자신 포함 | seed 노드만 지정 |
| `min-nr-of-members` | `2` (멀티노드) | `2` (동일) |
| 트랜스포트 | `dot-netty.tcp` | `dot-netty.tcp` (동일) |

#### 8-4. 2-시스템 클러스터 테스트 패턴

xUnit의 `IClassFixture<T>`로 2개의 `ActorSystem`을 공유하고, `MessageCollectorActor`로 메시지를 수집합니다.

**클러스터 Fixture**:

```csharp
public sealed class TwoNodeClusterFixture : IDisposable
{
    public ActorSystem SeedSystem { get; }
    public ActorSystem JoiningSystem { get; }

    public TwoNodeClusterFixture()
    {
        SeedSystem = ActorSystem.Create("ClusterSystem",
            ConfigurationFactory.ParseString(SeedConfig));
        JoiningSystem = ActorSystem.Create("ClusterSystem",
            ConfigurationFactory.ParseString(JoiningConfig));
        WaitForClusterUp(SeedSystem, 2, 15);
    }

    private static void WaitForClusterUp(ActorSystem system,
        int expectedMembers, int timeoutSeconds)
    {
        var cluster = Cluster.Get(system);
        var deadline = DateTime.UtcNow.AddSeconds(timeoutSeconds);
        while (DateTime.UtcNow < deadline)
        {
            var upCount = cluster.State.Members
                .Count(m => m.Status == MemberStatus.Up);
            if (upCount >= expectedMembers) return;
            Thread.Sleep(500);
        }
        throw new Exception(
            $"Cluster did not form with {expectedMembers} members");
    }

    public void Dispose()
    {
        // joining 먼저 leave → seed 마지막 terminate
        var joiningCluster = Cluster.Get(JoiningSystem);
        joiningCluster.Leave(joiningCluster.SelfAddress);
        JoiningSystem.Terminate().Wait(TimeSpan.FromSeconds(10));
        SeedSystem.Terminate().Wait(TimeSpan.FromSeconds(10));
    }
}
```

**MessageCollectorActor** (TestKit 대신 사용):

```csharp
public class MessageCollectorActor : ReceiveActor
{
    private readonly List<object> _messages = new();

    public sealed record GetCollected(TaskCompletionSource<List<object>> Tcs);

    public MessageCollectorActor()
    {
        Receive<GetCollected>(msg =>
            msg.Tcs.TrySetResult(new List<object>(_messages)));
        ReceiveAny(msg => _messages.Add(msg));
    }

    public static async Task<List<object>> WaitForMessages(
        IActorRef collector, int count, TimeSpan timeout)
    {
        var deadline = DateTime.UtcNow.Add(timeout);
        while (DateTime.UtcNow < deadline)
        {
            var tcs = new TaskCompletionSource<List<object>>();
            collector.Tell(new GetCollected(tcs));
            var result = await tcs.Task;
            if (result.Count >= count) return result;
            await Task.Delay(200);
        }
        throw new TimeoutException($"Expected {count} messages");
    }
}
```

**테스트 클래스**:

```csharp
public class TwoNodeClusterTests : IClassFixture<TwoNodeClusterFixture>
{
    private readonly TwoNodeClusterFixture _f;

    public TwoNodeClusterTests(TwoNodeClusterFixture fixture)
    {
        _f = fixture;
    }

    [Fact]
    public void BothNodes_should_be_Up()
    {
        var seedCluster = Cluster.Get(_f.SeedSystem);
        Assert.Equal(2, seedCluster.State.Members.Count);
    }
}
```

> **TestKit 대신 MessageCollectorActor**: Akka.NET TestKit은 단일 시스템에 바인딩되므로, 2-시스템 테스트에서는 `MessageCollectorActor`로 메시지를 수집하고 폴링으로 대기합니다.

#### 8-5. 크로스노드 PubSub

기존 4번 PubSub과 달리, 크로스노드 전파 시 추가 주의사항이 있습니다.

```csharp
// 1. 양쪽 노드의 mediator를 사전 초기화
var seedMediator = DistributedPubSub.Get(seedSystem).Mediator;

// 2. 한쪽 노드에서 구독
joiningSystem.ActorOf(
    Props.Create(() => new SubscriberActor("topic", collector)),
    "subscriber");

// 3. 구독 정보 클러스터 전파 대기 (약 3~5초)
await Task.Delay(5000);

// 4. 다른 노드의 mediator로 직접 발행 (크로스노드)
seedMediator.Tell(new Publish("topic", "cross-node-msg"));
```

| 주의사항 | 설명 |
|---------|------|
| mediator 사전 초기화 | 발행 노드에서 `DistributedPubSub.Get(system).Mediator` 호출 필요 |
| 전파 대기 | 구독 후 크로스노드 전파까지 3~5초 대기 필요 |
| 직접 발행 | `PublisherActor` 대신 mediator에 직접 `Publish` 전달 가능 |

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
12. **크로스노드 통신** 시 메시지 직렬화 설정은 추가 불필요합니다 (Hyperion이 기본 직렬화기).
13. **멀티노드 테스트** 시 seed 노드(고정 포트)와 joining 노드(포트 0)의 HOCON을 분리하고, `min-nr-of-members = 2`를 설정합니다.
14. **크로스노드 PubSub**은 발행 노드의 mediator를 사전 초기화하고, 구독 전파 대기(3~5초) 후 발행합니다.

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

## Web 클러스터 연동 주의사항 (2026-02)

1. cluster join 완료 전에 `/api/cluster/info`가 빈 값일 수 있으므로, readiness 이후 검증합니다.
2. singleton actor 경로(manager/proxy)와 API 호출 대상(proxy)을 일치시킵니다.
3. 클러스터 멤버 조건(min members)과 readiness 조건을 동일한 기준으로 맞춥니다.
4. seed-node 주소는 pod DNS 기준으로 고정하고, 런타임에서 hostname 우선순위를 명시합니다.
5. 클러스터 이벤트 로그(`Member is Up`)를 API 테스트 결과와 함께 검증합니다.
