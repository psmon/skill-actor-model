# 언어별 스펙 차이에 따른 구현 비교

> 2-Node 클러스터 테스트 구현 과정에서 발견한 Java Akka Classic / Kotlin Pekko Typed / C# Akka.NET 간의 언어 스펙 및 프레임워크 차이를 정리한다.

## 1. 크로스노드 액터 참조 (Service Discovery)

2-Node 클러스터에서 다른 노드의 액터에 접근하는 방식이 플랫폼마다 다르다.

### Java Akka Classic — ActorSelection (경로 기반)

```java
// seed 노드 주소를 직접 조합하여 ActorSelection 생성
Address seedAddress = Cluster.get(seedSystem).selfAddress();
String counterPath = seedAddress + "/user/" + actorName;
ActorSelection remoteCounter = joiningSystem.actorSelection(counterPath);
remoteCounter.tell(new CounterSingletonActor.Increment(), ActorRef.noSender());
```

- `akka://TwoNodeClusterSystem@127.0.0.1:25520/user/counter-2node` 형태의 전체 경로 필요
- Classic API는 untyped이므로 경로 문자열만으로 리모트 액터에 메시지 전송 가능
- 경로가 잘못되면 dead letter로 조용히 실패

### Kotlin Pekko Typed — Receptionist (서비스 키 기반)

```kotlin
// Seed 노드: ServiceKey로 액터 등록
val counterKey = ServiceKey.create(CounterCommand::class.java, "counter-2node")
seedTestKit.system().receptionist().tell(Receptionist.register(counterKey, counter))

// Joining 노드: ServiceKey로 액터 디스커버리
joiningTestKit.system().receptionist().tell(
    Receptionist.subscribe(counterKey, listingProbe.ref())
)
val listing = listingProbe.expectMessageClass(Receptionist.Listing::class.java)
val remoteCounter = listing.getServiceInstances(counterKey).first()
```

- Typed API의 관용적 서비스 디스커버리 패턴
- `ServiceKey<T>`로 타입 안전한 액터 참조 획득
- Receptionist가 클러스터 전체에 자동 전파 (gossip 기반, 2초 정도 대기 필요)
- 경로 문자열 대신 타입 안전한 `ActorRef<T>` 반환

### C# Akka.NET — ActorSelection (경로 기반)

```csharp
// Java와 동일한 ActorSelection 패턴, 프로토콜만 다름
var seedAddress = Cluster.Get(_f.SeedSystem).SelfAddress;
var counterPath = $"{seedAddress}/user/{actorName}";
var remoteCounter = _f.JoiningSystem.ActorSelection(counterPath);
remoteCounter.Tell(new CounterSingletonActor.Increment());
```

- `akka.tcp://TwoNodeClusterSystem@127.0.0.1:25522/user/counter-2node` 형태
- Java와 동일 패턴이나 프로토콜이 `akka.tcp://` (dot-netty 트랜스포트)

### 비교 요약

| 항목 | Java Akka Classic | Kotlin Pekko Typed | C# Akka.NET |
|------|-------------------|-------------------|-------------|
| 방식 | `ActorSelection` (경로 문자열) | `Receptionist` (서비스 키) | `ActorSelection` (경로 문자열) |
| 타입 안전성 | 없음 (경로 기반) | 있음 (`ServiceKey<T>`) | 없음 (경로 기반) |
| 전파 메커니즘 | 즉시 사용 가능 (경로 직접 지정) | Gossip 전파 (~2초 대기) | 즉시 사용 가능 (경로 직접 지정) |
| 실패 모드 | Dead letter (조용한 실패) | Listing에 빈 집합 반환 | Dead letter (조용한 실패) |

---

## 2. 크로스노드 PubSub 메커니즘

### Java Akka Classic — DistributedPubSub Mediator

```java
// 구독: joining 노드에서 PubSubSubscriberActor 생성 (내부에서 mediator.tell(Subscribe))
joiningSystem.actorOf(Props.create(PubSubSubscriberActor.class, "test-topic", collector));

// 발행: seed 노드의 mediator에 직접 Publish 메시지 전송
ActorRef seedMediator = DistributedPubSub.get(seedSystem).mediator();
seedMediator.tell(new DistributedPubSubMediator.Publish("test-topic", "cross-node-hello"),
                  ActorRef.noSender());
```

- `DistributedPubSub.get(system).mediator()`로 중앙 mediator 액터 참조
- Mediator가 클러스터 gossip으로 구독 정보를 전파 (5초 대기 필요)
- SubscribeAck 메시지로 구독 완료 확인 가능

### Kotlin Pekko Typed — Topic<T> API

```kotlin
// 구독: joining 노드에서 PubSubManagerActor가 Topic 액터 생성
joiningPubSub.tell(SubscribeToTopic("cross-topic", subscriberProbe.ref()))
// 발행 측에도 동일 토픽 생성 필요 (Topic 액터 간 Receptionist 디스커버리)
seedPubSub.tell(SubscribeToTopic("cross-topic", seedDummyProbe.ref()))

Thread.sleep(5000) // 양쪽 Topic 액터가 서로 발견할 때까지 대기

// 발행
seedPubSub.tell(PublishMessage("cross-topic", "cross-node-hello"))
```

- `Topic.create(String::class.java, topicName)`으로 토픽 액터 직접 생성
- **핵심 차이**: 양쪽 노드 모두에 Topic 액터가 존재해야 크로스노드 전파 가능
- Topic 액터끼리 Receptionist를 통해 서로를 발견 (gossip 기반)
- Mediator 중앙 집중형이 아닌 분산형 토픽 액터 패턴

### C# Akka.NET — DistributedPubSub Mediator

```csharp
// Java와 동일 패턴
var seedMediator = DistributedPubSub.Get(_f.SeedSystem).Mediator;
// 구독: PubSubSubscriberActor가 내부에서 mediator에 Subscribe
// 발행: seed mediator에 직접 Publish
seedMediator.Tell(new Publish("test-topic", "cross-node-hello"));
```

### 비교 요약

| 항목 | Java Akka Classic | Kotlin Pekko Typed | C# Akka.NET |
|------|-------------------|-------------------|-------------|
| API | `DistributedPubSub` mediator | `Topic<T>` 액터 | `DistributedPubSub` mediator |
| 아키텍처 | 중앙 mediator | 분산 토픽 액터 | 중앙 mediator |
| 양쪽 노드 생성 필요 | 아니오 (mediator 자동) | **예** (Topic 액터 필요) | 아니오 (mediator 자동) |
| 타입 안전성 | 없음 (Object) | 있음 (`Topic<String>`) | 없음 (Object) |
| 구독 확인 | `SubscribeAck` 메시지 | 별도 확인 없음 | `SubscribeAck` 메시지 |

---

## 3. 직렬화 (Serialization)

크로스노드 메시지 전송 시 직렬화가 필수적이며, 각 플랫폼의 기본 설정이 다르다.

### Java Akka Classic (2.7.x)

```hocon
# Akka 2.7부터 Java 직렬화가 기본 비활성화
akka.actor.allow-java-serialization = on
akka.actor.warn-about-java-serializer-usage = off
```

```java
// 모든 크로스노드 메시지 클래스에 Serializable 인터페이스 필수
public static class Increment implements Serializable { }
public static class GetCount implements Serializable { ... }
public static class CountValue implements Serializable { ... }
```

- Akka 2.7에서 `DisabledJavaSerializer`가 기본값 → 크로스노드 메시지 전송 시 `JavaSerializationException` 발생
- 프로덕션에서는 Jackson/Protobuf 직렬화 권장, 테스트용으로 Java 직렬화 활성화

### Kotlin Pekko Typed (1.1.x)

```hocon
# Pekko도 동일하게 Java 직렬화 기본 비활성화
pekko.actor.allow-java-serialization = on
pekko.actor.warn-about-java-serializer-usage = off
```

```kotlin
// sealed class 계층에 Serializable 인터페이스 추가
sealed class CounterCommand : Serializable
sealed class PubSubCommand : Serializable
```

- Pekko는 Akka 포크이므로 동일한 직렬화 제한 적용
- Kotlin sealed class에 `: Serializable` 추가 시 모든 서브클래스가 자동 상속

### C# Akka.NET (1.5.x)

```csharp
// record 타입은 기본적으로 직렬화 가능 — 추가 설정 불필요
public record Increment();
public record GetCount(IActorRef ReplyTo);
public record CountValue(int Value);
```

- Akka.NET의 기본 직렬화가 .NET record/class를 자동 처리
- Java/Kotlin과 달리 `allow-java-serialization` 같은 설정이 불필요
- Hyperion serializer가 기본 내장

### 비교 요약

| 항목 | Java Akka Classic | Kotlin Pekko Typed | C# Akka.NET |
|------|-------------------|-------------------|-------------|
| 기본 직렬화 | 비활성화 (2.7+) | 비활성화 | 활성화 (Hyperion) |
| 추가 설정 필요 | `allow-java-serialization = on` | `allow-java-serialization = on` | 불필요 |
| 메시지 인터페이스 | `implements Serializable` | `: Serializable` | 불필요 (record 자동) |
| 프로덕션 권장 | Jackson / Protobuf | Jackson / Protobuf | Hyperion (기본) |

---

## 4. 클러스터 이벤트 구독

### Java Akka Classic

```java
// Classic: cluster.subscribe로 직접 구독, self가 이벤트 수신
Cluster cluster = Cluster.get(getContext().getSystem());
cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
    ClusterEvent.MemberUp.class);
```

- untyped `ActorRef`(self)가 이벤트를 직접 수신
- `initialStateAsEvents()` 옵션으로 기존 Up 멤버에 대해서도 MemberUp 이벤트 발생

### Kotlin Pekko Typed

```kotlin
// Typed: messageAdapter로 클러스터 이벤트를 typed 메시지로 변환
val memberUpAdapter = ctx.messageAdapter(
    ClusterEvent.MemberUp::class.java
) { event -> WrappedMemberUp(event) }

Cluster.get(ctx.system).subscriptions().tell(
    Subscribe.create(memberUpAdapter, ClusterEvent.MemberUp::class.java)
)
```

- Typed 액터는 `Behavior<T>`로 메시지 타입이 고정 → 클러스터 이벤트를 직접 수신 불가
- `messageAdapter`로 `ClusterEvent.MemberUp` → `WrappedMemberUp`(커스텀 sealed class)으로 변환 필요
- 타입 안전성 확보를 위한 추가 보일러플레이트

### C# Akka.NET

```csharp
// C#: Java Classic과 유사, cluster.Subscribe로 구독
var cluster = Cluster.Get(Context.System);
cluster.Subscribe(Self, ClusterEvent.InitialStateAsEvents,
    typeof(ClusterEvent.MemberUp));
```

- Java Classic과 거의 동일한 API
- `typeof()` 연산자로 이벤트 타입 지정

### 비교 요약

| 항목 | Java Akka Classic | Kotlin Pekko Typed | C# Akka.NET |
|------|-------------------|-------------------|-------------|
| 구독 방식 | `cluster.subscribe(self, ...)` | `messageAdapter` + `subscriptions()` | `cluster.Subscribe(Self, ...)` |
| 이벤트 수신 | 직접 수신 (untyped) | adapter로 typed 변환 필요 | 직접 수신 (untyped) |
| 추가 코드 | 없음 | `WrappedMemberUp` sealed class 정의 | 없음 |
| 타입 안전성 | 런타임 | 컴파일타임 | 런타임 |

---

## 5. 2-시스템 테스트 패턴

### Java — 2개 ActorSystem + JUnit 5 Lifecycle

```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TwoNodeClusterTest {
    static ActorSystem seedSystem;
    static ActorSystem joiningSystem;

    @BeforeAll
    static void setup() {
        seedSystem = ActorSystem.create("TwoNodeClusterSystem",
            ConfigFactory.load("two-node-seed"));
        joiningSystem = ActorSystem.create("TwoNodeClusterSystem",
            ConfigFactory.load("two-node-joining"));
        waitForClusterUp(seedSystem, 2, 15);
    }

    @AfterAll
    static void teardown() {
        // joining 먼저 leave → shutdown, 그 다음 seed
    }
}
```

- 외부 `.conf` 파일에서 설정 로드
- JUnit 5 `@BeforeAll`/`@AfterAll`로 생명주기 관리
- `TestProbe` 사용 가능 (akka-testkit)

### Kotlin — 2개 ActorTestKit + companion object

```kotlin
class TwoNodeClusterTest {
    companion object {
        private lateinit var seedTestKit: ActorTestKit
        private lateinit var joiningTestKit: ActorTestKit

        @JvmStatic @BeforeAll
        fun setup() {
            seedTestKit = ActorTestKit.create("TwoNodeClusterSystem",
                ConfigFactory.load("two-node-seed"))
            joiningTestKit = ActorTestKit.create("TwoNodeClusterSystem",
                ConfigFactory.load("two-node-joining"))
            waitForClusterUp(seedTestKit, 2, 15)
        }
    }
}
```

- `ActorTestKit`이 `ActorSystem`을 내부적으로 관리
- `TestProbe<T>` 내장 — 타입 안전한 메시지 검증
- `companion object` + `@JvmStatic`으로 JUnit 5 정적 메서드 호환

### C# — IClassFixture + 커스텀 프로브

```csharp
public sealed class TwoNodeClusterFixture : IDisposable {
    public ActorSystem SeedSystem { get; }
    public ActorSystem JoiningSystem { get; }
    // 생성자에서 2개 시스템 생성 + 클러스터 형성 대기
    // Dispose()에서 joining leave → seed shutdown
}

// MessageCollectorActor: 경량 프로브 대체
public class MessageCollectorActor : ReceiveActor {
    // 수신한 메시지를 리스트에 수집, 비동기 대기 제공
}

public class TwoNodeClusterTests : IClassFixture<TwoNodeClusterFixture> {
    // 테스트 메서드에서 _f.SeedSystem / _f.JoiningSystem 사용
}
```

- `Akka.TestKit.Xunit2`의 `TestKit`은 단일 `ActorSystem`에 바인딩 → 2시스템 테스트 불가
- `IClassFixture<T>` + `IDisposable` 패턴으로 직접 생명주기 관리
- `TestProbe`를 사용할 수 없어 `MessageCollectorActor` 커스텀 구현 필요

### 비교 요약

| 항목 | Java Akka Classic | Kotlin Pekko Typed | C# Akka.NET |
|------|-------------------|-------------------|-------------|
| 시스템 생성 | `ActorSystem.create()` × 2 | `ActorTestKit.create()` × 2 | `ActorSystem.Create()` × 2 |
| 생명주기 | `@BeforeAll`/`@AfterAll` | `companion object` + `@BeforeAll` | `IClassFixture` + `IDisposable` |
| 프로브 | `TestProbe` (akka-testkit) | `TestProbe<T>` (typed testkit) | `MessageCollectorActor` (커스텀) |
| TestKit 제한 | 없음 (2시스템 지원) | 없음 (2시스템 지원) | 단일 시스템만 지원 → 우회 필요 |
| 비동기 검증 | `probe.expectMsg(timeout)` | `probe.expectMessage(duration)` | `await WaitForMessages(collector, n, timeout)` |

---

## 6. Scala 컬렉션 호환성 (JVM 플랫폼 한정)

Akka/Pekko는 Scala로 작성되어 있어 Java/Kotlin에서 Scala 컬렉션을 다룰 때 호환성 이슈가 발생한다.

### Java — StreamSupport 필요

```java
// Akka의 cluster.state().getMembers()는 Scala Iterable<Member> 반환
// Java의 Collection.size()나 .stream() 사용 불가
import java.util.stream.StreamSupport;

long upCount = StreamSupport.stream(
    cluster.state().getMembers().spliterator(), false)
    .filter(m -> m.status().equals(MemberStatus.up()))
    .count();
```

- `getMembers()`가 `scala.collection.immutable.SortedSet`을 반환
- Java에서는 `StreamSupport.stream(iterable.spliterator(), false)`로 Java Stream 변환 필요
- `.size()` 직접 호출 시 Scala의 `size` 메서드와 Java의 `size()` 시그니처 불일치로 컴파일 에러

### Kotlin — .count() 확장 함수

```kotlin
// Kotlin은 Iterable에 대한 확장 함수가 풍부하여 상대적으로 간결
val upCount = cluster.state().members.count { it.status() == MemberStatus.up() }
// 단, .size() 프로퍼티는 Scala SortedSet에서 직접 접근 불가
val memberCount = seedCluster.state().members.count() // .size가 아닌 .count()
```

- Kotlin의 `Iterable<T>.count()` 확장 함수로 Scala 컬렉션을 자연스럽게 처리
- `.size` 프로퍼티 접근 시 Scala 메서드 시그니처 충돌로 실패 → `.count()` 사용

### C# — 해당 없음

```csharp
// Akka.NET은 .NET 네이티브이므로 Scala 호환성 이슈 없음
var upCount = cluster.State.Members.Count(m => m.Status == MemberStatus.Up);
```

- LINQ의 `.Count()` 직접 사용 가능
- Scala 컬렉션 호환성 문제가 없는 것이 C# 구현의 장점

---

## 7. HOCON 설정 차이

### 프로토콜 및 네임스페이스

| 항목 | Java Akka Classic | Kotlin Pekko Typed | C# Akka.NET |
|------|-------------------|-------------------|-------------|
| HOCON 루트 키 | `akka { }` | `pekko { }` | `akka { }` |
| 트랜스포트 | `akka.remote.artery` | `pekko.remote.artery` | `akka.remote.dot-netty.tcp` |
| 프로토콜 URI | `akka://System@host:port` | `pekko://System@host:port` | `akka.tcp://System@host:port` |
| 포트 설정 | `artery.canonical.port` | `artery.canonical.port` | `dot-netty.tcp.port` |
| SBR 클래스 | `akka.cluster.sbr.SplitBrainResolverProvider` | `org.apache.pekko.cluster.sbr.SplitBrainResolverProvider` | N/A (기본 내장) |

### 설정 로드 방식

| 항목 | Java | Kotlin | C# |
|------|------|--------|-----|
| 2-Node 설정 | 외부 `.conf` 파일 (`ConfigFactory.load()`) | 외부 `.conf` 파일 (`ConfigFactory.load()`) | 인라인 HOCON 문자열 (`ConfigurationFactory.ParseString()`) |
| 설정 파일 위치 | `src/test/resources/` | `src/test/resources/` | 테스트 클래스 내 `static readonly string` |

---

## 8. 구현 과정 핵심 트러블슈팅

| 이슈 | 원인 | 해결 | 영향 플랫폼 |
|------|------|------|------------|
| `DisabledJavaSerializer` 예외 | Akka 2.7+ / Pekko에서 Java 직렬화 기본 비활성화 | `allow-java-serialization = on` + `Serializable` 인터페이스 | Java, Kotlin |
| Scala `Iterable.size()` 호환 불가 | Scala SortedSet이 Java/Kotlin의 `.size()` 시그니처와 불일치 | Java: `StreamSupport`, Kotlin: `.count()` | Java, Kotlin |
| PubSub dead letter | Mediator 초기화 전에 메시지 전송 시도 | seed 노드 mediator 사전 초기화 후 직접 발행 | Java |
| Topic 크로스노드 미전파 | 한쪽 노드에만 Topic 액터가 있으면 Receptionist 디스커버리 불가 | 양쪽 노드 모두에 Topic 액터 생성 | Kotlin |
| C# TestKit 2시스템 제한 | `TestKit` base class가 단일 ActorSystem에 바인딩 | `IClassFixture` + `MessageCollectorActor` 커스텀 프로브 | C# |
| JMX MBean 충돌 | 동일 JVM에 같은 이름의 2개 ActorSystem 기동 | `jmx.multi-mbeans-in-same-jvm = on` | Java, Kotlin |
| CRLF 줄바꿈 | WSL2에서 Windows 파일시스템의 CRLF 줄바꿈 | `sed -i 's/\r$//' gradlew` | Kotlin (Gradle wrapper) |
| xUnit1031 경고 | `.GetAwaiter().GetResult()` 동기 블로킹 호출 | `async Task` + `await` 비동기 전환 | C# |

---

## 2026-02-16 테스트 대기 전략 비교 (Sleep 제거 관점)

이번 개선에서 공통 목표는 "고정 시간 Sleep 제거"였고, 언어/테스트킷별로 다음 패턴을 사용했다.

### Java Akka Classic (akka-testkit)
- 사용 패턴: `awaitAssert(max, interval, assertion)` + `expectMsg*`
- 적용 포인트:
  - 클러스터 Up 대기: 폴링 + `Thread.sleep` → `awaitAssert`
  - 크로스노드 카운터 검증: 고정 대기 제거 후 `awaitAssert` 내부 재검증
  - PubSub 전파: 고정 5초 대기 제거, `awaitAssert` 내부 publish+expect 반복
- 장점: Classic TestKit이 retry 루프를 내장해 별도 대기 스레드 코드가 불필요.

### Kotlin Pekko Typed (ActorTestKit/TestProbe)
- 사용 패턴: `TestProbe.awaitAssert(max, interval) { ... }`
- 적용 포인트:
  - 클러스터 Up 대기: `Thread.sleep` 폴링 제거
  - Receptionist 디스커버리: 빈 Listing 가능성을 `awaitAssert`로 흡수
  - PubSub 전파: publish/expect를 `awaitAssert`로 감싸 고정 대기 제거
- 장점: Typed TestProbe에서 await 계열 API를 직접 제공해 타입 안전 검증 흐름 유지.

### C# Akka.NET (Akka.TestKit.Xunit2 + 관찰자 액터)
- 사용 패턴:
  - 단일 노드: `AwaitAssert`로 클러스터 Up 확인
  - 2노드: `MessageCollectorActor` + `TaskCompletionSource` 기반 이벤트 대기
  - PubSub 전파: scheduler 반복 publish + collector 수신 완료 시 cancel
- 적용 포인트:
  - `Thread.Sleep`/`Task.Delay` 제거
  - 테스트 흐름을 메시지 수신 완료 조건으로 전환
- 장점: TestKit이 닿지 않는 2-system 픽스처 영역도 관찰자 액터로 논블로킹 구조화 가능.

### 통합 결론
- 세 구현 모두 "고정 시간 블로킹 대기"를 제거하고, "조건 기반 재시도/이벤트 수신"으로 통일 가능.
- Kotlin/Java는 TestKit await API가 중심, .NET은 다중 시스템 테스트에서 관찰자 액터 패턴이 특히 유효했다.
