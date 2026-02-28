# Skill로 생성된 프로젝트 테스트 결과

> 테스트 일시: 2026-02-14 ~ 2026-02-15 | 환경: WSL2 Ubuntu, Java 21, .NET 9.0, Kotlin 1.9.x

## 종합 결과

| # | 프로젝트 | 플랫폼 | 스킬 | 결과 | 유닛테스트 |
|---|---------|--------|------|------|-----------|
| 1 | sample1 | Kotlin + Pekko Typed 1.1.3 | `/kotlin-pekko-typed` | PASS | N/A |
| 2 | sample2 | Java + Akka Classic 2.7.0 | `/java-akka-classic` | PASS | N/A |
| 3 | sample3 | C# + Akka.NET 1.5.60 | `/dotnet-akka-net` | PASS | N/A |
| 4 | sample4 | Kotlin + Pekko Typed 1.1.3 | `/kotlin-pekko-typed` | PASS | N/A |
| 5 | sample5 | Java + Akka Classic 2.7.0 | `/java-akka-classic` | PASS | N/A |
| 6 | sample6 | C# + Akka.NET 1.5.x | `/dotnet-akka-net` | PASS | N/A |
| 7 | sample7 | Kotlin + Pekko Typed 1.1.3 | `/kotlin-pekko-typed` | PASS | N/A |
| 8 | sample8 | Java + Akka Classic 2.7.0 | `/java-akka-classic` | PASS | N/A |
| 9 | sample9 | C# + Akka.NET 1.5.40 | `/dotnet-akka-net` | PASS | N/A |
| 10 | sample10 | Kotlin + Pekko Typed 1.1.3 | `/kotlin-pekko-typed` | PASS | N/A |
| 11 | sample11 | Java + Akka Classic 2.7.0 | `/java-akka-classic` | PASS | N/A |
| 12 | sample12 | C# + Akka.NET 1.5.25 | `/dotnet-akka-net` | PASS | N/A |
| 13 | sample13 | Kotlin + Pekko Typed 1.1.3 | `/kotlin-pekko-typed` | PASS | N/A |
| 14 | sample14 | Java + Akka Classic 2.7.0 | `/java-akka-classic` | PASS | N/A |
| 15 | sample15 | C# + Akka.NET 1.5.25 | `/dotnet-akka-net` | PASS | N/A |
| 16 | sample16 | Java + Kotlin AI Agent Console | `/actor-ai-agent-java`, `/actor-ai-agent-kotlin` | PASS | N/A |
| 17 | sample16/kotlin-pekko-fsm-sqlite | Kotlin + Pekko Typed 1.1.3 | `/kotlin-pekko-typed` | PASS | NO-SOURCE |
| 18 | sample17/java-akka-fsm-sqlite | Java + Akka Classic 2.7.0 | `/java-akka-classic` | PASS | NO-SOURCE |
| 19 | sample18/dotnet-akka-fsm-sqlite | C# + Akka.NET 1.5.30 | `/dotnet-akka-net` | PASS | 테스트 프로젝트 없음 |
| 20 | sample19 | Kotlin + Pekko Typed 1.1.3 | `/kotlin-pekko-typed` | PASS | 1 passed |
| 21 | sample20 | Java + Akka Classic 2.7.0 | `/java-akka-classic` | PASS | 1 passed |
| 22 | sample21 | C# + Akka.NET 1.5.60 | `/dotnet-akka-net` | PASS | 1 passed |
| 23 | sample-cluster-java | Java + Akka Classic 2.7.0 | `/java-akka-classic-cluster` | PASS | 7 passed (1-Node: 3, 2-Node: 4) |
| 24 | sample-cluster-kotlin | Kotlin + Pekko Typed 1.1.3 | `/kotlin-pekko-typed-cluster` | PASS | 6 passed (1-Node: 2, 2-Node: 4) |
| 25 | sample-cluster-dotnet | C# + Akka.NET 1.5.60 | `/dotnet-akka-net-cluster` | PASS | 7 passed (1-Node: 3, 2-Node: 4) |

---

## sample1 — Kotlin Pekko Typed Hello World

**컨셉**: Pekko Typed 기본 액터 통신. Guardian 액터가 HelloActor를 생성하고, sealed class 기반 타입 안전 메시지(`Hello`, `HelloResponse`)로 요청-응답 패턴을 시연한다. `replyTo: ActorRef<T>` 명시적 응답 패턴 사용.

**실행**: `./gradlew run`

```
04:42:32.473 [hello-world-system-pekko.actor.default-dispatcher-5] INFO  com.example.actor.HelloActor - HelloActor started
04:42:32.514 [hello-world-system-pekko.actor.default-dispatcher-5] INFO  com.example.actor.HelloActor - Received: Hello World!
04:42:32.515 [hello-world-system-pekko.actor.default-dispatcher-3] INFO  com.example.actor.MainKt - Main received response: Hello from Kotlin Pekko Typed!
04:42:34.564 [hello-world-system-pekko.actor.default-dispatcher-3] INFO  com.example.actor.HelloActor - HelloActor stopped
```

**검증**: HelloActor가 "Hello World!" 수신 후 "Hello from Kotlin Pekko Typed!" 응답 → responseHandler가 정상 수신. 액터 생명주기(started/stopped) 로그 확인.

---

## sample2 — Java Akka Classic Hello World

**컨셉**: Akka Classic 기본 액터 통신. `AbstractActor` + `receiveBuilder()` 패턴으로 HelloActor를 구현하고, `getSender().tell()` 암묵적 응답 패턴을 시연한다. ResponseHandler 액터가 sender로서 응답을 수신.

**실행**: `./gradlew run`

```
[INFO] [02/14/2026 04:42:46.981] [hello-world-system-akka.actor.default-dispatcher-5] [akka://hello-world-system/user/hello-actor] HelloActor started
[INFO] [02/14/2026 04:42:46.981] [hello-world-system-akka.actor.default-dispatcher-5] [akka://hello-world-system/user/hello-actor] Received: Hello World!
[INFO] [02/14/2026 04:42:46.982] [hello-world-system-akka.actor.default-dispatcher-5] [akka://hello-world-system/user/response-handler] Main received response: Hello from Java Akka Classic!
[INFO] [02/14/2026 04:42:49.150] [hello-world-system-akka.actor.default-dispatcher-7] [akka://hello-world-system/user/hello-actor] HelloActor stopped
```

**검증**: HelloActor가 메시지 수신 → ResponseHandler에 "Hello from Java Akka Classic!" 응답. 액터 경로(`/user/hello-actor`)와 생명주기 정상.

---

## sample3 — C# Akka.NET Hello World

**컨셉**: Akka.NET 기본 액터 통신. `ReceiveActor` + `Receive<T>()` 패턴. `record` 타입 불변 메시지(`Hello`, `HelloResponse`). `Sender.Tell()` 암묵적 응답 패턴.

**실행**: `dotnet run`

```
[INFO][02/13/2026 19:42:50.748Z][Thread 0007][akka://hello-world-system/user/hello-actor] HelloActor started
[INFO][02/13/2026 19:42:50.749Z][Thread 0007][akka://hello-world-system/user/hello-actor] Received: Hello World!
[INFO][02/13/2026 19:42:50.749Z][Thread 0007][akka://hello-world-system/user/response-handler] Main received response: Hello from C# Akka.NET!
[INFO][02/13/2026 19:42:52.747Z][Thread 0006][akka://hello-world-system/user/hello-actor] HelloActor stopped
```

**검증**: sample1/2와 동일한 Hello World 패턴을 C#으로 구현. 3개 플랫폼의 기본 액터 통신이 동일하게 동작함을 확인.

---

## sample4 — Kotlin Pekko Typed Pool Router (4종)

**컨셉**: Pekko Typed의 4가지 라우팅 전략을 5개 워커로 시연. `Routers.pool()` API로 RoundRobin/Random/ConsistentHashing을 구성하고, Broadcast는 Typed에 내장 전략이 없어 커스텀 액터(`BroadcastRouter`)로 직접 구현.

**실행**: `./gradlew run`

```
=== [1] RoundRobin Pool Router (5 workers) ===
[$a] 처리: "RoundRobin 메시지 #1"
[$b] 처리: "RoundRobin 메시지 #2"
[$c] 처리: "RoundRobin 메시지 #3"
[$d] 처리: "RoundRobin 메시지 #4"
[$e] 처리: "RoundRobin 메시지 #5"
[$a] 처리: "RoundRobin 메시지 #6"  ← $a로 순환 복귀
...

=== [2] Random Pool Router (5 workers) ===
[$a] 처리: "Random 메시지 #1"
[$d] 처리: "Random 메시지 #2"
[$d] 처리: "Random 메시지 #3"      ← 무작위 분배 (편차 있음)
[$a] 처리: "Random 메시지 #4"
[$b] 처리: "Random 메시지 #5"
...

=== [3] ConsistentHashing Pool Router (5 workers) ===
[$c] 처리: "Hash 메시지 round=1" (key=user-A)
[$e] 처리: "Hash 메시지 round=1" (key=user-B)
[$a] 처리: "Hash 메시지 round=1" (key=user-C)
[$c] 처리: "Hash 메시지 round=2" (key=user-A)  ← user-A → 항상 $c
[$e] 처리: "Hash 메시지 round=2" (key=user-B)  ← user-B → 항상 $e
[$a] 처리: "Hash 메시지 round=2" (key=user-C)  ← user-C → 항상 $a
...

=== [4] Broadcast Router (5 workers) - 커스텀 구현 ===
[broadcast-worker-1] 처리: "Broadcast 메시지 #1"
[broadcast-worker-2] 처리: "Broadcast 메시지 #1"
[broadcast-worker-3] 처리: "Broadcast 메시지 #1"  ← 1개 메시지 → 5개 모두 수신
[broadcast-worker-4] 처리: "Broadcast 메시지 #1"
[broadcast-worker-5] 처리: "Broadcast 메시지 #1"
```

**검증**:
- RoundRobin: 10개 메시지가 $a~$e에 각 2건씩 균등 분배
- Random: 불균등 분배 (편차 정상)
- ConsistentHashing: 동일 key가 동일 워커에 라우팅 (user-A→$c, user-B→$e, user-C→$a 고정)
- Broadcast: 메시지 3건 × 워커 5개 = 총 15건 처리

---

## sample5 — Java Akka Classic Pool Router (4종)

**컨셉**: Akka Classic의 4가지 Pool Router를 5개 작업자로 시연. `RoundRobinPool`, `BroadcastPool`, `ConsistentHashingPool`, `RandomPool` 내장 라우터 사용. `ConsistentHashingRouter.ConsistentHashable` 인터페이스 구현으로 해시 기반 라우팅.

**실행**: `./gradlew run`

```
=== [1/4] RoundRobin Pool Router - 순환 분배 ===
[처리] 작업자=$a | 메시지='Hello, Alice!' | 누적처리=1건
[처리] 작업자=$b | 메시지='Hello, Bob!' | 누적처리=1건
[처리] 작업자=$c | 메시지='Hello, Charlie!' | 누적처리=1건
[처리] 작업자=$d | 메시지='Hello, David!' | 누적처리=1건
[처리] 작업자=$e | 메시지='Hello, Eve!' | 누적처리=1건
[처리] 작업자=$a | 메시지='Hello, Frank!' | 누적처리=2건  ← 순환
...

=== [2/4] Broadcast Pool Router - 전체 브로드캐스트 ===
[처리] 작업자=$a | 메시지='Hello, World!' | 누적처리=1건
[처리] 작업자=$b | 메시지='Hello, World!' | 누적처리=1건  ← 모두 동일 메시지
[처리] 작업자=$c | 메시지='Hello, World!' | 누적처리=1건
[처리] 작업자=$d | 메시지='Hello, World!' | 누적처리=1건
[처리] 작업자=$e | 메시지='Hello, World!' | 누적처리=1건
...

=== [3/4] ConsistentHashing Pool Router - 해시 기반 고정 분배 ===
[처리] 작업자=$e | 메시지='Hello, Alice!' | 누적처리=1건   (hashKey=team-A)
[처리] 작업자=$e | 메시지='Hello, Bob!' | 누적처리=2건     (hashKey=team-A)
[처리] 작업자=$e | 메시지='Hello, Charlie!' | 누적처리=3건 (hashKey=team-B)
→ team-A, team-B, team-C 모두 $e에 할당 (해시 링 특성)
→ 종료 시 $e: 10건, 나머지: 0건

=== [4/4] Random Pool Router - 무작위 분배 ===
[처리] 작업자=$e | 메시지='Hello, Alpha!' | 누적처리=1건
[처리] 작업자=$e | 메시지='Hello, Beta!' | 누적처리=2건
[처리] 작업자=$b | 메시지='Hello, Gamma!' | 누적처리=1건  ← 무작위
[처리] 작업자=$c | 메시지='Hello, Delta!' | 누적처리=1건
→ 종료: $b=3건, $c=4건, $e=3건 (불균등)
```

**검증**:
- RoundRobin: 10명 → 5작업자 각 2건씩 균등
- Broadcast: 3메시지 × 5작업자 = 15건 (작업자당 3건)
- ConsistentHashing: 해시 링 특성상 3개 key가 모두 $e에 집중 (정상 동작, 소규모 풀에서 발생 가능)
- Random: 불균등 분배 확인 ($a=0건, $d=0건으로 편차 큼)

---

## sample6 — C# Akka.NET Pool Router (4종)

**컨셉**: Akka.NET의 4가지 Pool Router를 5개 작업자로 시연. `RoundRobinPool`, `BroadcastPool`, `ConsistentHashingPool`, `RandomPool` 사용. `IConsistentHashable` 인터페이스로 해시 키 구현. `record` 타입 불변 메시지.

**실행**: `dotnet run`

```
=== [1] RoundRobin Pool Router ===
[$a] 메시지 #1 수신 - "안녕 #1" (발신: RoundRobin)
[$b] 메시지 #1 수신 - "안녕 #2" (발신: RoundRobin)
[$c] 메시지 #1 수신 - "안녕 #3" (발신: RoundRobin)
[$d] 메시지 #1 수신 - "안녕 #4" (발신: RoundRobin)
[$e] 메시지 #1 수신 - "안녕 #5" (발신: RoundRobin)
[$a] 메시지 #2 수신 - "안녕 #6" (발신: RoundRobin)  ← 순환
...

=== [2] Broadcast Pool Router ===
[$a] 메시지 #1 수신 - "공지 #1" (발신: Broadcast)
[$b] 메시지 #1 수신 - "공지 #1" (발신: Broadcast)  ← 모두 동일 메시지
[$c] 메시지 #1 수신 - "공지 #1" (발신: Broadcast)
[$d] 메시지 #1 수신 - "공지 #1" (발신: Broadcast)
[$e] 메시지 #1 수신 - "공지 #1" (발신: Broadcast)
...

=== [3] ConsistentHashing Pool Router ===
[$a] 메시지 #1 수신 - "해시 메시지 #1" (해시키: user-A)
[$a] 메시지 #2 수신 - "해시 메시지 #2" (해시키: user-B)
[$e] 메시지 #1 수신 - "해시 메시지 #3" (해시키: user-C)
[$a] 메시지 #3 수신 - "해시 메시지 #4" (해시키: user-A)  ← user-A → 항상 $a
[$a] 메시지 #4 수신 - "해시 메시지 #5" (해시키: user-B)  ← user-B → 항상 $a
[$e] 메시지 #2 수신 - "해시 메시지 #6" (해시키: user-C)  ← user-C → 항상 $e
→ 종료: $a=6건, $e=3건, 나머지=0건

=== [4] Random Pool Router ===
[$b] 메시지 #1 수신 - "랜덤 #1" (발신: Random)
[$d] 메시지 #1 수신 - "랜덤 #2" (발신: Random)
[$c] 메시지 #1 수신 - "랜덤 #3" (발신: Random)  ← 무작위
[$b] 메시지 #2 수신 - "랜덤 #4" (발신: Random)
→ 종료: $b=2건, $c=3건, $d=3건, $e=1건, $a=1건
```

**검증**:
- RoundRobin: 10개 메시지 → 5작업자 각 2건 균등 분배
- Broadcast: 3메시지 × 5작업자 = 15건 (작업자당 3건)
- ConsistentHashing: user-A/B → $a, user-C → $e 고정 라우팅
- Random: 5작업자에 불균등 분배 (1~3건 편차)

---

## sample7 — Kotlin Pekko Typed Streams Throttle

**컨셉**: Pekko Streams 기반 이벤트 스로틀링과 백프레셔 처리. `Source.queue(100, DropNew)` → `.throttle(3/sec)` → `Sink.foreach` 파이프라인으로 초당 처리량을 제한한다. 3단계 페이즈로 시연: Phase 1은 일정 간격 투입(30개, 50ms간격 ≈ 20개/초), Phase 2는 버스트 투입(150개 즉시), Phase 3은 통계 수집.

**실행**: `./gradlew run`

```
╔══════════════════════════════════════════════════════════════╗
║   Pekko Typed - Throttle & Overflow 데모                    ║
║   초당 처리: 3개/초 | 버퍼: 100 | 전략: DropNew             ║
╚══════════════════════════════════════════════════════════════╝

──── Phase 1: 일정 간격 이벤트 투입 (30개, 50ms 간격 ≈ 20개/초) ────
[설정] 초당 처리량: 3개/초, 버퍼 크기: 100 (초과 시 신규 이벤트 드롭)
[처리 완료 #1/12] eventId=1, payload="scheduled-event-1" ← Throttle 통과 (3개/초 제한)
[처리 완료 #2/19] eventId=2, payload="scheduled-event-2" ← Throttle 통과
[처리 완료 #3/24] eventId=3, payload="scheduled-event-3" ← Throttle 통과
...

──── Phase 2: 버스트 이벤트 투입 (150개 즉시) ────
=== 버스트 모드: 150개 이벤트를 즉시 투입 (버퍼 100 초과 시 드롭 발생) ===
[드롭 #66] eventId=110 → 버퍼 100개 초과 - DropNew로 드롭
[드롭 #71] eventId=180 → 버퍼 100개 초과 - DropNew로 드롭
...

──── Phase 3: 최종 통계 요청 ────
╔══════════════════════════════════════════════════════════════╗
║   최종 처리 통계                                            ║
║   총 투입: 180개                                             ║
║   처리됨: 38개 (Throttle 통과)                              ║
║   드롭됨: 71개 (버퍼 오버플로우)                            ║
╚══════════════════════════════════════════════════════════════╝

[시스템 종료] 모든 액터를 정지합니다.
BUILD SUCCESSFUL in 24s
```

**검증**:
- Phase 1: 30개 이벤트를 20개/초로 투입 → Throttle이 3개/초로 제한하여 처리 지연 발생
- Phase 2: 150개 버스트 → 버퍼(100) 초과분 71개 DropNew로 드롭
- 최종: 투입 180개, 처리 38개(+통계 후 추가 처리 6개=44개), 드롭 71개 — 나머지는 버퍼 잔류 후 시스템 종료
- Pekko Streams의 `Source.queue` + `throttle` + `DropNew` 오버플로우 전략 정상 동작

---

## sample8 — Java Akka Classic Streams Throttle

**컨셉**: Akka Streams 기반 스로틀 파이프라인. `Source.actorRef(100, DropNew)` → `.throttle(3/sec)` → `Sink.actorRef(EventProcessorActor)` 구조. 150건 이벤트를 한번에 버스트 발행하여 버퍼(100) 초과 시 드롭, 초당 3건 처리율 제한을 시연한다.

**실행**: `./gradlew run`

```
========================================
  Akka Streams Throttle 데모 시작
  설정: 초당 3건 처리, 버퍼 100, 오버플로우 시 dropNew
========================================

[Main] Throttle 파이프라인 생성 완료 (버퍼=100, 초당 3건)
[Main] 이벤트 150 건 버스트 발행 시작 → 버퍼(100) 초과분은 드롭됩니다
[Main] 이벤트 150 건 발행 완료 (버퍼 100 초과 → 약 50 건 드롭 예상)

[Processor] 처리 #1 | Event[id=1, payload=sensor-data-1] | 지연시간: 42ms
[Processor] 처리 #2 | Event[id=2, payload=sensor-data-2] | 지연시간: 12ms
[Processor] 처리 #3 | Event[id=3, payload=sensor-data-3] | 지연시간: 12ms
[Processor] 처리 #4 | Event[id=4, payload=sensor-data-4] | 지연시간: 382ms  ← 스로틀 적용
...
[Processor] 처리 #50 | Event[id=50, payload=sensor-data-50] | 지연시간: 13188ms
...
[Processor] 처리 #100 | Event[id=100, payload=sensor-data-100] | 지연시간: 29857ms
...
[Processor] 처리 #104 | Event[id=104, payload=sensor-data-104] | 지연시간: 31188ms

[Processor] === 처리 통계 === 총 처리: 104건 | 경과: 37초 | 실측 처리량: 2.8/sec

========================================
  Throttle 데모 종료
========================================
BUILD SUCCESSFUL in 46s
```

**검증**:
- 150건 발행 → 버퍼 100 + 초기 3건 즉시 통과 + 약 1건 경합 = 104건 처리 (46건 드롭)
- 실측 처리량 2.8/sec (설정 3건/초에 근접)
- 지연시간이 42ms → 31,188ms로 점진적 증가: 스로틀에 의한 큐잉 효과 확인
- `Source.actorRef` + `throttle` + `Sink.actorRef` 파이프라인 정상 동작

---

## sample9 — C# Akka.NET Streams Throttle

**컨셉**: Akka.Streams 기반 스로틀 파이프라인. `Source.ActorRef` → `Throttle(3/sec)` → `Sink.ActorRef(EventProcessorActor)` 구조. 2단계 시나리오: 시나리오 1은 소량(5건) 처리, 시나리오 2는 대량 버스트(150건)로 버퍼 초과 및 DropNew 전략을 시연.

**실행**: `dotnet run`

```
========================================
 Akka.NET Streams Throttle 데모
 처리속도: 3건/초 | 버퍼: 100 | 오버플로우: DropNew
========================================

[시나리오 1] 5건 이벤트 발행 → 스로틀이 초당 3건씩 처리
[Producer] 이벤트 대량 생성 완료 - 발행=5건
[Processor] EventId=1, Payload="Event-0001", 대기시간=55ms
[Processor] EventId=2, Payload="Event-0002", 대기시간=55ms
[Processor] EventId=3, Payload="Event-0003", 대기시간=55ms  ← 첫 3건 즉시 통과
[Processor] EventId=4, Payload="Event-0004", 대기시간=399ms ← 스로틀 적용
[Processor] EventId=5, Payload="Event-0005", 대기시간=719ms

--- 시나리오 1 완료 ---

[시나리오 2] 150건 이벤트 발행 → 버퍼(100) 초과 시 DropNew 전략으로 드롭
[Producer] 이벤트 대량 생성 완료 - 발행=150건
[Throttle] 이벤트 수신 → 스로틀러 전달 - EventId=10, 누적수신=10건
[Throttle] 이벤트 수신 → 스로틀러 전달 - EventId=150, 누적수신=150건
[Processor] EventId=6, Payload="Event-0006", 대기시간=576ms
...
[Processor] EventId=49, Payload="Event-0049", 대기시간=14907ms
→ 15초 관찰 후 총 49건 처리 (시나리오1 포함, 3건/초 × 15초 ≈ 44건 + 시나리오1 5건)

========================================
 데모 종료
 스로틀이 초당 3건으로 처리를 제한했으며,
 버퍼(100) 초과 이벤트는 드롭되었습니다.
========================================
```

**검증**:
- 시나리오 1: 5건 → 전량 처리 (버퍼 내), 첫 3건 즉시 통과 후 나머지 ~333ms 간격 스로틀
- 시나리오 2: 150건 버스트 → 15초 관찰 동안 44건 추가 처리 (3건/초 일치)
- 대기시간 55ms → 14,907ms로 점진적 증가: 큐잉 효과 확인
- `Source.ActorRef` + `Throttle` + `Sink.ActorRef` 파이프라인 정상 동작

---

## sample10 — Kotlin Pekko Typed SQLite Event Store

**컨셉**: Pekko Typed 액터가 인메모리 상태 대신 SQLite(`actor_events` 테이블)에 이벤트를 직접 append하고, 재시작 시 `SELECT ... ORDER BY seq_nr` replay로 상태를 복구한다. `DurableStateBehavior` 대신 커스텀 이벤트 스토어 패턴.

**실행**: `./gradlew run`

```
00:41:35.628 ... Pekko Typed + SQLite 이벤트 영속 데모
00:41:35.628 ... DB 파일: /mnt/d/Code/Webnori/skill-actor-model/skill-test/projects/sample10/sample10-data/actor-events.db
00:41:35.652 ... [복구 완료] persistenceId=counter-1, recoveredEvents=6, value=24, lastSeqNr=6
00:41:35.660 ... [STATE] value=24, eventCount=6, lastSeqNr=6
00:41:35.686 ... [이벤트 저장] persistenceId=counter-1, seqNr=7, amount=+3, currentValue=27
00:41:35.687 ... [ACK] persistenceId=counter-1, seqNr=7, currentValue=27
00:41:35.708 ... [이벤트 저장] persistenceId=counter-1, seqNr=8, amount=+5, currentValue=32
00:41:35.708 ... [STATE] value=32, eventCount=8, lastSeqNr=8
00:41:36.695 ... [종료] ActorSystem 종료
BUILD SUCCESSFUL
```

**검증**:
- 시작 시점에 기존 이벤트 6건을 로드해 `value=24`로 복구
- 신규 이벤트 2건 저장 후 `seqNr=8`, `value=32`로 증가
- SQLite 파일 기반 영속 상태가 재실행 간 유지됨 확인

---

## sample11 — Java Akka Classic Persistence JDBC + SQLite

**컨셉**: `AbstractPersistentActor` + `akka-persistence-jdbc` 조합으로 SQLite 저널/스냅샷 영속화를 수행한다. 시작 시 스키마(`event_journal`, `event_tag`, `snapshot`)를 초기화하고 이벤트 replay로 복구.

**실행**: `./gradlew run`

```
Akka Classic + SQLite Persistence 데모
DB 파일: skill-test/projects/sample11/sample11-data/akka-persistence.db
00:41:35.243 ... [복구 완료] persistenceId=counter-1, counter=16, eventCount=4, lastSequenceNr=4
[시작 상태] counter=16, eventCount=4, lastSeqNr=4
00:41:35.432 ... [이벤트 저장] persistenceId=counter-1, seqNr=5, amount=+3, currentValue=19
[ACK] persistenceId=counter-1, seqNr=5, currentValue=19
00:41:35.498 ... [이벤트 저장] persistenceId=counter-1, seqNr=6, amount=+5, currentValue=24
[ACK] persistenceId=counter-1, seqNr=6, currentValue=24
[종료 직전 상태] counter=24, eventCount=6, lastSeqNr=6
00:41:35.521 ... [스냅샷 저장] sequenceNr=5
BUILD SUCCESSFUL
```

**검증**:
- 기존 상태(`counter=16`)를 이벤트 replay로 복원
- 2건 persist 후 `lastSeqNr=6`, `counter=24`로 정상 증가
- 5번째 시퀀스에서 스냅샷 저장 로그 확인

---

## sample12 — C# Akka.NET Persistence Sqlite

**컨셉**: `ReceivePersistentActor` + `Akka.Persistence.Sqlite`로 이벤트 저널/스냅샷을 로컬 SQLite 파일에 저장한다. 단일 실행 내에서 ActorSystem을 2회 순차 기동해 재기동 복구를 즉시 시연.

**실행**: `dotnet run`

```
Akka.NET + SQLite Persistence 데모
DB 파일: /mnt/d/Code/Webnori/skill-actor-model/skill-test/projects/sample12/sample12-data/akka-persistence.db

--- 1차 실행 ---
[복구 완료] pid=sample12-counter-1, value=16, eventCount=4, lastSeqNr=4
[시작 상태] value=16, eventCount=4, lastSeqNr=4
[이벤트 저장] pid=sample12-counter-1, seqNr=5, amount=+3, currentValue=19
[이벤트 저장] pid=sample12-counter-1, seqNr=6, amount=+5, currentValue=24
[종료 직전 상태] value=24, eventCount=6, lastSeqNr=6
[스냅샷 복구] pid=sample12-counter-1, value=19, eventCount=5

--- 2차 실행(재기동) ---
[복구 완료] pid=sample12-counter-1, value=24, eventCount=6, lastSeqNr=6
[시작 상태] value=24, eventCount=6, lastSeqNr=6
[이벤트 저장] pid=sample12-counter-1, seqNr=7, amount=+3, currentValue=27
[이벤트 저장] pid=sample12-counter-1, seqNr=8, amount=+5, currentValue=32
[종료 직전 상태] value=32, eventCount=8, lastSeqNr=8
```

**검증**:
- 1차 종료 상태(`value=24`)가 2차 시작 복구 상태와 동일
- 2차에서 `seqNr=7,8`로 연속 증가해 이벤트 저널 연속성 확인
- SnapshotOffer 기반 스냅샷 복구 로그 확인

---

## sample13 — Kotlin Pekko Typed Streams WorkingWithGraph

**컨셉**: Akka/Pekko Streams GraphDSL 패턴. `Source(Random 1~100)`를 `Broadcast(2)`로 분기해 fan1(`+2`) / fan2(`+10`) 변환 후 `Merge(2)`로 합치고 콘솔 출력으로 검증한다.

**실행**: `./gradlew run`

```
Kotlin Pekko Typed - WorkingWithGraph 데모
Source(Random 1~100) -> Broadcast(2) -> (+2, +10) -> Merge -> Out
[Source] 입력 랜덤값: [18, 77, 34, 58, 82, 95, 82, 31]
[Source] emit=18
[Fan1] 18 + 2 = 20
[Out] merged=20
[Fan2] 18 + 10 = 28
[Out] merged=28
...
[Out] merged=41
Graph 실행 완료
BUILD SUCCESSFUL
```

**검증**:
- 각 입력값마다 fan1/fan2 두 경로가 모두 실행되어 출력 2건 생성
- `Merge` 결과가 순차적으로 sink에 전달됨 확인
- 유닛테스트 소스 없음(`N/A`)

---

## sample14 — Java Akka Classic Streams WorkingWithGraph

**컨셉**: Akka Streams `GraphDSL`로 `Broadcast`/`Merge` 기반 fan-out/fan-in을 구현. 랜덤 정수 입력을 fan1(`+2`)과 fan2(`+10`)로 분기 처리 후 병합 출력한다.

**실행**: `./gradlew run`

```
Java Akka Classic - WorkingWithGraph 데모
Source(Random 1~100) -> Broadcast(2) -> (+2, +10) -> Merge -> Out
[Source] 입력 랜덤값: [35, 24, 91, 68, 76, 16, 25, 68]
[Source] emit=35
[Fan1] 35 + 2 = 37
[Out] merged=37
[Fan2] 35 + 10 = 45
[Out] merged=45
...
[Out] merged=78
Graph 실행 완료
BUILD SUCCESSFUL
```

**검증**:
- 각 입력이 두 분기에서 각각 변환(+2, +10)되어 병합됨
- `Broadcast<Integer>(2)`와 `Merge<Integer>(2)` 타입 지정으로 그래프 정상 동작
- 유닛테스트 소스 없음(`N/A`)

---

## sample15 — C# Akka.NET Streams WorkingWithGraph

**컨셉**: Akka.NET Streams `GraphDsl` 패턴으로 fan-out/fan-in 구성. `Source(Random)` -> `Broadcast(2)` -> fan1(`+2`), fan2(`+10`) -> `Merge(2)` -> `Sink.ForEach` 출력.

**실행**: `dotnet run`

```
C# Akka.NET - WorkingWithGraph 데모
Source(Random 1~100) -> Broadcast(2) -> (+2, +10) -> Merge -> Out
[Source] 입력 랜덤값: [16, 53, 33, 7, 54, 6, 74, 41]
[Source] emit=16
[Fan1] 16 + 2 = 18
[Out] merged=18
[Fan2] 16 + 10 = 26
[Out] merged=26
...
[Out] merged=51
Graph 실행 완료
```

**검증**:
- 분기 2개(fan1/fan2)와 병합 1개가 의도대로 작동
- 출력이 입력 수의 2배로 생성되어 fan-out/fan-in 구조 확인
- 유닛테스트 소스 없음(`N/A`)

---

## sample16 — Java/Kotlin AI Agent Pipeline Console

**컨셉**: .NET 전용이었던 AI Agent 파이프라인 컨셉을 Java Akka Classic / Kotlin Pekko Typed 콘솔 데모로 분리 검증. 공통 파이프라인은 `[Analyze] -> [Search] -> [Decision] -> [Final] + [SideTask]`이며, 오케스트레이터가 단계 액터를 순차 호출하고 최종 응답 이후 사이드 태스크를 Fire-and-Forget으로 수행한다.

### sample16/java-ai-agent

**실행**: `./gradlew run`

```
Java Akka Classic AI Agent Pipeline (Console)
[Analyze] -> [Search] -> [Decision] -> [Final] + [SideTask]
[Analyze] message='RAG memory search for actor model tips'
[Search] query='RAG memory search for actor model tips'
[Decision] docs=3 -> top2
[Final] selectedDocs=[ActorModel.md, AkkaTips.md]
[Client] answer using docs=[ActorModel.md, AkkaTips.md]
[SideTask] title-generation + embedding queued for 'RAG memory search for actor model tips'
BUILD SUCCESSFUL
```

### sample16/kotlin-ai-agent

**실행**: `./gradlew run`

```
Kotlin Pekko Typed AI Agent Pipeline (Console)
[Analyze] -> [Search] -> [Decision] -> [Final] + [SideTask]
[Analyze] message='RAG memory search for actor model tips'
[Search] query='RAG memory search for actor model tips'
[Decision] docs=3 -> top2
[Final] selectedDocs=[ActorModel.md, PekkoTips.md]
[Client] answer using docs=[ActorModel.md, PekkoTips.md]
[SideTask] title-generation + embedding queued for 'RAG memory search for actor model tips'
BUILD SUCCESSFUL
```

**검증**:
- Java/Kotlin 모두 단계별 로그가 동일 순서로 출력되어 파이프라인 동작 확인
- 최종 응답 이후 사이드 태스크 로그가 별도 출력되어 Fire-and-Forget 경로 분리 확인
- 유닛테스트 소스 없음(`N/A`)

---

## sample16/kotlin-pekko-fsm-sqlite — Kotlin Pekko Typed FSM + Scheduler + SQLite

**컨셉**: Pekko Typed FSM 액터(`IDLE/ACTIVE`)가 `event1~event5`를 실시간 수집하고, 매 이벤트 즉시 insert 대신 3초 타이머마다 배치 저장한다. flush 시 최대 100개 chunk 단위로 SQLite(`event_log`)에 insert한다.

**실행**: `./gradlew run`

```
[IDLE->ACTIVE] first event arrived, bufferSize=1
[ACTIVE] buffered up to 10
[ACTIVE] buffered up to 20
[ACTIVE] buffered up to 60
[FLUSH:timer(3s)] inserted chunk=65, remain=0
[FLUSH:timer(3s)] completed, totalInserted=65
...
[FLUSH:stop] inserted chunk=25, remain=0
[FLUSH:stop] completed, totalInserted=25
[RESULT] kotlin saved rows = 445, db=.../sample16-events.db
BUILD SUCCESSFUL
```

**유닛테스트**: `./gradlew test` → `test NO-SOURCE`

**검증**:
- 3초 주기 타이머 flush 로그(`FLUSH:timer(3s)`) 반복 확인
- flush가 100개 이하 chunk로 저장됨(본 실행에서는 65/25건)
- SQLite 누적 row 증가 확인(`saved rows = 445`)

---

## sample17/java-akka-fsm-sqlite — Java Akka Classic FSM + Scheduler + SQLite

**컨셉**: Akka Classic `AbstractFSM` 기반 배치 인서트 액터. `IDLE`에서 첫 이벤트 수신 시 `ACTIVE` 전환 + 3초 flush timer 시작, `ACTIVE`에서 이벤트 누적 후 타이머/중지 시 SQLite 배치 저장을 수행한다(최대 100개 chunk).

**실행**: `./gradlew run`

```
[IDLE->ACTIVE] first event=event4, bufferSize=1
[ACTIVE] buffered up to 10
[ACTIVE] buffered up to 20
[ACTIVE] buffered up to 60
[FLUSH:timer(3s)] inserted chunk=65, remain=0
[FLUSH:timer(3s)] completed, totalInserted=65
...
[FLUSH:stop] inserted chunk=25, remain=0
[FLUSH:stop] completed, totalInserted=25
[RESULT] java saved rows = 445, db=.../sample17-events.db
BUILD SUCCESSFUL
```

**유닛테스트**: `./gradlew test` → `test NO-SOURCE`

**검증**:
- 타이머 기반 3초 flush 정상 동작
- 이벤트 즉시 저장이 아닌 버퍼링 후 배치 저장 동작 확인
- SQLite 누적 row 증가 확인(`saved rows = 445`)

---

## sample18/dotnet-akka-fsm-sqlite — C# Akka.NET FSM + Scheduler + SQLite

**컨셉**: Akka.NET `FSM<ActorState, BufferData>`로 `event1~event5`를 수집하고, 스케줄러 tick 기반 입력을 3초 timer flush로 배치 저장한다. 저장은 SQLite(`event_log`) 트랜잭션 insert이며 flush 단위는 최대 100개다.

**실행**: `dotnet run`

```
[IDLE->ACTIVE] first event=event2, bufferSize=1
[ACTIVE] buffered up to 10
[ACTIVE] buffered up to 20
[ACTIVE] buffered up to 60
[FLUSH:timer(3s)] inserted chunk=65, remain=0
[FLUSH:timer(3s)] completed, totalInserted=65
...
[FLUSH:stop] inserted chunk=25, remain=0
[FLUSH:stop] completed, totalInserted=25
[RESULT] csharp saved rows = 440, db=.../sample18-events.db
```

**유닛테스트**: `dotnet test -v minimal` 실행, 테스트 프로젝트가 없어 restore 확인 후 종료(ExitCode 0)

**검증**:
- 3초 타이머 flush와 stop flush 동작 로그 확인
- flush 단위가 최대 100개 이하로 유지됨(본 실행 65/25건)
- SQLite 누적 row 증가 확인(`saved rows = 440`)

---

## 크로스 플랫폼 비교 요약

### Hello World (sample1/2/3) & Router (sample4/5/6)

| 항목 | Kotlin Pekko Typed | Java Akka Classic | C# Akka.NET |
|------|-------------------|-------------------|-------------|
| 액터 베이스 | `AbstractBehavior<T>` | `AbstractActor` | `ReceiveActor` |
| 메시지 정의 | `sealed class` 계층 | 내부 불변 클래스 | `record` |
| 응답 패턴 | `replyTo: ActorRef<T>` 명시적 | `getSender()` 암묵적 | `Sender.Tell()` 암묵적 |
| 라우터 API | `Routers.pool()` | `new XxxPool(n).props()` | `Props.WithRouter(new XxxPool(n))` |
| Broadcast | 커스텀 액터 구현 필요 | `BroadcastPool` 내장 | `BroadcastPool` 내장 |
| ConsistentHash | `ConsistentHashing` 키 매핑 | `ConsistentHashable` 인터페이스 | `IConsistentHashable` 인터페이스 |
| 빌드 | Gradle (Kotlin DSL) | Gradle (Groovy) | dotnet CLI |

### Streams Throttle (sample7/8/9)

| 항목 | sample7 (Kotlin Pekko) | sample8 (Java Akka) | sample9 (C# Akka.NET) |
|------|----------------------|---------------------|----------------------|
| Streams API | `Source.queue` + `throttle` | `Source.actorRef` + `throttle` | `Source.ActorRef` + `Throttle` |
| 오버플로우 전략 | `OverflowStrategy.dropNew()` | `OverflowStrategy.dropNew()` | `OverflowStrategy.DropNew` |
| 버퍼 크기 | 100 | 100 | 100 |
| 스로틀 속도 | 3건/초 | 3건/초 | 3건/초 |
| 버스트 테스트 | 150건 → 71건 드롭 | 150건 → 46건 드롭 | 150건 → 관찰 시간 내 49건 처리 |
| 실측 처리량 | ~3건/초 | 2.8건/초 | ~3건/초 |
| 실행 시간 | 24초 | 46초 (전량 처리) | 15초 (관찰 후 종료) |
| 데모 구조 | 3페이즈(정상→버스트→통계) | 단일 버스트 + 완전 소진 | 2시나리오(소량→대량) |

### Persistence SQLite (sample10/11/12)

| 항목 | sample10 (Kotlin Pekko) | sample11 (Java Akka) | sample12 (C# Akka.NET) |
|------|-------------------------|----------------------|------------------------|
| 영속 방식 | 커스텀 이벤트 저장소(JDBC 직접) | Akka Persistence JDBC | Akka.Persistence.Sqlite |
| 액터 베이스 | `Behavior<T>` (Typed) | `AbstractPersistentActor` | `ReceivePersistentActor` |
| 이벤트 저장 | 수동 INSERT + 트랜잭션 | `persist()` | `Persist()` |
| 복구 방식 | 시작 시 replay (`SELECT ... ORDER BY`) | `createReceiveRecover()` replay | `Recover<T>()` replay |
| 스냅샷 | 미사용 | `saveSnapshot()` | `SaveSnapshot()` |
| DB 파일 | `sample10-data/actor-events.db` | `sample11-data/akka-persistence.db` | `sample12-data/akka-persistence.db` |
| 검증 포인트 | seqNr 6→8 증가 | lastSeqNr 4→6 증가 | 1차/2차 재기동에서 seqNr 4→8 증가 |

### WorkingWithGraph (sample13/14/15)

| 항목 | sample13 (Kotlin Pekko) | sample14 (Java Akka) | sample15 (C# Akka.NET) |
|------|--------------------------|----------------------|-------------------------|
| Graph 구성 | `GraphDSL + Broadcast + Merge` | `GraphDSL + Broadcast + Merge` | `GraphDsl + Broadcast + Merge` |
| Source | 랜덤 정수 8개 (`1..100`) | 랜덤 정수 8개 (`1..100`) | 랜덤 정수 8개 (`1..100`) |
| fan1 | `n + 2` | `n + 2` | `n + 2` |
| fan2 | `n + 10` | `n + 10` | `n + 10` |
| Sink | `Sink.foreach` | `Sink.foreach` | `Sink.ForEach` |
| 결과 검증 | 입력당 출력 2건 | 입력당 출력 2건 | 입력당 출력 2건 |

---

## sample19 — Kotlin Pekko Typed TestKit Hello/World

**컨셉**: A 액터가 B 액터에게 `Hello`를 보내고, B가 A에게 `World`를 응답한 뒤, A가 probe로 결과를 전달하는 Typed 테스트 패턴. `ActorTestKit` + `TestProbe<String>`로 검증.

**실행**: `cd skill-test/projects/sample19 && ./gradlew test --no-daemon`

**결과**: PASS (`BUILD SUCCESSFUL`, 테스트 통과)

---

## sample20 — Java Akka Classic TestKit Hello/World

**컨셉**: Classic `TestKit` 기반으로 A/B 액터 상호작용을 검증. `expectMsgEquals("World")`와 `expectNoMessage`를 사용해 응답과 불필요 메시지 부재를 함께 확인.

**실행**: `cd skill-test/projects/sample20 && ./gradlew test --no-daemon`

**결과**: PASS (`BUILD SUCCESSFUL`, 테스트 통과)

---

## sample21 — C# Akka.NET TestKit Hello/World (분리 프로젝트)

**컨셉**: 닷넷 권장 구조에 맞춰 운영 프로젝트(`HelloActors`)와 테스트 프로젝트(`HelloActors.Tests`)를 분리. `Akka.TestKit.Xunit2`의 `TestKit` 상속 테스트로 `World` 응답을 검증.

**실행**: `cd skill-test/projects/sample21 && dotnet test sample21.sln`

**결과**: PASS (`Passed: 1, Failed: 0`)

---

## sample-cluster-java — Java Akka Classic Cluster 패턴 유닛테스트

**컨셉**: Akka Classic 2.7.x 클러스터 패턴 검증. 단일 JVM에서 클러스터를 자기 자신에게 조인(`cluster.join(cluster.selfAddress())`)하여 클러스터 Membership 이벤트, Singleton 액터, DistributedPubSub를 테스트한다. `akka-cluster`, `akka-cluster-tools` 의존성 사용.

**실행**: `cd skill-test/projects/sample-cluster-java && ./gradlew clean test`

**테스트 결과**: 3/3 PASSED (1.488s)

| 테스트 | 설명 | 결과 |
|--------|------|------|
| `clusterListenerReceivesMemberUpEvent` | ClusterListenerActor가 MemberUp 이벤트를 수신하여 reportTo에 "member-up" 알림 | PASS |
| `counterSingletonActorCountsCorrectly` | CounterSingletonActor에 Increment 3회 → GetCount 시 count=3 반환 | PASS |
| `pubSubDeliversMessageToSubscriber` | DistributedPubSub mediator로 "test-topic"에 Publish → Subscriber가 수신 | PASS |

```
[INFO] Cluster Node [akka://ClusterTestSystem@127.0.0.1:44765] - Started up successfully
[INFO] Cluster Node [akka://ClusterTestSystem@127.0.0.1:44765] - Node [...] is JOINING itself and forming new cluster
[INFO] Cluster Node [akka://ClusterTestSystem@127.0.0.1:44765] - Leader is moving node [...] to [Up]
[INFO] Member is Up: Member(akka://ClusterTestSystem@127.0.0.1:44765, Up)
[INFO] Subscribed to topic: test-topic
[INFO] Publishing message to test-topic: hello-cluster
[INFO] Received message on topic [test-topic]: hello-cluster
[INFO] Counter incremented to 1
[INFO] Counter incremented to 2
[INFO] Counter incremented to 3
[INFO] Returning count: 3
BUILD SUCCESSFUL
```

---

## sample-cluster-kotlin — Kotlin Pekko Typed Cluster 패턴 유닛테스트

**컨셉**: Pekko Typed 1.1.x 클러스터 패턴 검증. `ActorTestKit`과 단일 노드 클러스터(`Join.create(selfMember.address)`)를 사용하여 Singleton 액터와 Topic 기반 PubSub를 테스트한다. `pekko-cluster-typed`, `pekko-cluster-sharding-typed` 의존성 사용. sealed class 메시지 계층 패턴 적용.

**실행**: `cd skill-test/projects/sample-cluster-kotlin && sed -i 's/\r$//' gradlew && ./gradlew clean test`

**테스트 결과**: 2/2 PASSED

| 테스트 | 설명 | 결과 |
|--------|------|------|
| `counter singleton actor counts correctly` | CounterSingletonActor에 Increment 3회 → GetCount 시 count=3 응답 | PASS |
| `pubsub delivers message to subscriber` | Topic.create() 기반 PubSub에서 publish → subscriber가 메시지 수신 | PASS |

```
[INFO] Cluster Node [pekko://ClusterTestSystem@127.0.0.1:xxxxx] - Started up successfully
[INFO] Cluster Node [...] - Node [...] is JOINING itself and forming new cluster
[INFO] Cluster Node [...] - Leader is moving node [...] to [Up]
[INFO] CounterSingletonActor: Incremented to 1
[INFO] CounterSingletonActor: Incremented to 2
[INFO] CounterSingletonActor: Incremented to 3
[INFO] CounterSingletonActor: Returning count=3
BUILD SUCCESSFUL
```

---

## sample-cluster-dotnet — C# Akka.NET Cluster 패턴 유닛테스트

**컨셉**: Akka.NET 1.5.x 클러스터 패턴 검증. `Akka.TestKit.Xunit2` 기반으로 단일 프로세스 클러스터(`cluster.Join(cluster.SelfAddress)`)를 구성하여 Membership 이벤트, Singleton 카운터 액터, DistributedPubSub를 테스트한다. `Akka.Cluster`, `Akka.Cluster.Tools` NuGet 패키지 사용. HOCON 인라인 설정으로 `dot-netty.tcp` 트랜스포트 구성.

**실행**: `cd skill-test/projects/sample-cluster-dotnet && dotnet test ClusterActors.sln`

**테스트 결과**: 3/3 PASSED

| 테스트 | 설명 | 결과 |
|--------|------|------|
| `ClusterListener_should_receive_MemberUp_event` | ClusterListenerActor가 MemberUp 이벤트 수신 시 "member-up" 메시지 전달 | PASS |
| `CounterSingleton_should_count_correctly` | CounterSingletonActor에 Increment 3회 → GetCount 시 count=3 반환 | PASS |
| `PubSub_should_deliver_message_to_subscriber` | DistributedPubSub Mediator로 "test-topic"에 Publish → Subscriber 수신 | PASS |

```
Starting test execution, please wait...
A total of 1 test files matched the specified pattern.
  Cluster started, joining self...
  ClusterListenerActor: MemberUp received for akka.tcp://...
  CounterSingletonActor: Incremented to 1
  CounterSingletonActor: Incremented to 2
  CounterSingletonActor: Incremented to 3
  CounterSingletonActor: Count is 3
  PubSubSubscriberActor: Subscribed to test-topic
  PubSubPublisherActor: Published 'hello-cluster' to test-topic
  PubSubSubscriberActor: Received 'hello-cluster' on test-topic

Passed!  - Failed:     0, Passed:     3, Skipped:     0, Total:     3
```

---

## sample-cluster-java (2-Node) — Java Akka Classic 2-Node 클러스터 테스트

**컨셉**: 기존 1-Node 자기조인 테스트를 보완하여 실제 2노드 클러스터를 구성. seed 노드(고정 포트 25520)와 joining 노드(자동 포트)로 크로스노드 통신, 리모트 액터 접근, PubSub 전파를 검증한다. `ActorSelection`으로 리모트 카운터에 접근하고, `DistributedPubSub` mediator로 크로스노드 메시지 발행을 수행.

**실행**: `cd skill-test/projects/sample-cluster-java && ./gradlew clean test`

**테스트 결과**: 7/7 PASSED (1-Node: 3, 2-Node: 4)

| 테스트 | 설명 | 결과 |
|--------|------|------|
| `bothNodesShouldBeUpInCluster` | seed/joining 양쪽 노드가 2개 멤버를 인식하고 모두 Up 상태 | PASS |
| `clusterListenerShouldReceiveTwoMemberUpEvents` | ClusterListenerActor가 MemberUp 이벤트 2개를 수신 | PASS |
| `counterShouldWorkAcrossNodes` | Seed 노드 카운터에 2회 증가 → Joining 노드에서 ActorSelection으로 1회 증가 → 카운트 3 확인 | PASS |
| `pubSubShouldDeliverAcrossNodes` | Joining 노드에서 구독 → Seed 노드의 mediator로 발행 → 크로스노드 수신 확인 | PASS |

```
[INFO] Cluster Node [akka://TwoNodeClusterSystem@127.0.0.1:25520] - Node is JOINING itself and forming new cluster
[INFO] Cluster Node [...] - Leader is moving node [...:25520] to [Up]
[INFO] Cluster Node [...] - Leader is moving node [...:xxxxx] to [Up]
[INFO] Member is Up: akka://TwoNodeClusterSystem@127.0.0.1:25520
[INFO] Member is Up: akka://TwoNodeClusterSystem@127.0.0.1:xxxxx
[INFO] Counter incremented to 3
[INFO] Returning count: 3
[INFO] Subscribed to topic: test-topic
[INFO] Received message on topic [test-topic]: cross-node-hello
BUILD SUCCESSFUL in 32s
```

---

## sample-cluster-kotlin (2-Node) — Kotlin Pekko Typed 2-Node 클러스터 테스트

**컨셉**: 2개의 `ActorTestKit`으로 seed 노드(고정 포트 25521)와 joining 노드(자동 포트)를 구성. Typed API 관용적 패턴인 `Receptionist`로 크로스노드 액터 디스커버리를 수행하고, `Topic<T>` API로 크로스노드 PubSub를 검증한다. `ClusterListenerActor`(신규 생성)는 `messageAdapter`로 `ClusterEvent.MemberUp`을 typed 메시지로 변환.

**실행**: `cd skill-test/projects/sample-cluster-kotlin && sed -i 's/\r$//' gradlew && ./gradlew clean test`

**테스트 결과**: 6/6 PASSED (1-Node: 2, 2-Node: 4)

| 테스트 | 설명 | 결과 |
|--------|------|------|
| `both nodes should be Up in cluster` | seed/joining 양쪽 노드가 2개 멤버를 인식하고 모두 Up 상태 | PASS |
| `cluster listener should receive two MemberUp events` | ClusterListenerActor가 messageAdapter 경유로 MemberUp 이벤트 2개 수신 | PASS |
| `counter should work across nodes via Receptionist` | Seed 노드 카운터를 Receptionist에 등록 → Joining 노드에서 ServiceKey로 디스커버리 → 크로스노드 증가 | PASS |
| `pubsub should deliver across nodes` | 양쪽 노드에 Topic 생성 → Seed 노드 발행 → Joining 노드 구독자 수신 | PASS |

```
[INFO] Cluster Node [pekko://TwoNodeClusterSystem@127.0.0.1:25521] - Node is JOINING itself and forming new cluster
[INFO] Cluster Node [...] - Leader is moving node [...:25521] to [Up]
[INFO] Cluster Node [...] - Leader is moving node [...:xxxxx] to [Up]
[INFO] Counter incremented to 3
BUILD SUCCESSFUL in 28s
```

---

## sample-cluster-dotnet (2-Node) — C# Akka.NET 2-Node 클러스터 테스트

**컨셉**: `IClassFixture<TwoNodeClusterFixture>` 패턴으로 seed 노드(고정 포트 25522)와 joining 노드(자동 포트)의 2개 `ActorSystem`을 공유. `MessageCollectorActor`(경량 프로브)로 비동기 메시지 수집 및 대기를 구현. `ActorSelection`으로 리모트 카운터 접근, `DistributedPubSub`로 크로스노드 PubSub 검증.

**실행**: `cd skill-test/projects/sample-cluster-dotnet && dotnet test ClusterActors.sln`

**테스트 결과**: 7/7 PASSED (1-Node: 3, 2-Node: 4)

| 테스트 | 설명 | 결과 |
|--------|------|------|
| `BothNodes_should_be_Up_in_cluster` | seed/joining 양쪽 노드가 2개 멤버를 인식하고 모두 Up 상태 | PASS |
| `ClusterListener_should_receive_two_MemberUp_events` | ClusterListenerActor가 collector에 "member-up" 메시지 2개 전달 | PASS |
| `Counter_should_work_across_nodes` | Seed 노드 카운터에 2회 증가 → Joining 노드에서 ActorSelection으로 1회 증가 → 카운트 3 확인 | PASS |
| `PubSub_should_deliver_across_nodes` | Joining 노드에서 PubSubSubscriberActor로 구독 → Seed 노드 mediator로 발행 → 크로스노드 수신 | PASS |

```
[INFO] Cluster Node [akka.tcp://TwoNodeClusterSystem@127.0.0.1:25522] - Started up successfully
[INFO] Cluster Node [...] - Leader is moving node [...:25522] to [Up]
[INFO] Cluster Node [...] - Leader is moving node [...:xxxxx] to [Up]
[INFO] Counter incremented to 3
[INFO] Returning count: 3
[INFO] Subscribed to topic: test-topic
[INFO] Received message on topic [test-topic]: cross-node-hello
Passed!  - Failed: 0, Passed: 7, Skipped: 0, Total: 7
```

---

## 크로스 플랫폼 클러스터 테스트 비교

### 1-Node 테스트 비교

| 항목 | sample-cluster-java (Java Akka) | sample-cluster-kotlin (Kotlin Pekko) | sample-cluster-dotnet (C# Akka.NET) |
|------|--------------------------------|-------------------------------------|-------------------------------------|
| 클러스터 조인 | `cluster.join(selfAddress)` | `cluster.manager().tell(Join.create(...))` | `cluster.Join(SelfAddress)` |
| TestKit | `akka-testkit` + JUnit 5 | `pekko-actor-testkit-typed` + JUnit 5 | `Akka.TestKit.Xunit2` + xUnit |
| HOCON 네임스페이스 | `akka { }` | `pekko { }` | `akka { }` |
| 프로토콜 | `akka://` (Artery) | `pekko://` (Artery) | `akka.tcp://` (dot-netty) |
| PubSub API | `DistributedPubSub.get().mediator()` | `Topic.create()` 액터 기반 | `DistributedPubSub.Get().Mediator` |
| 1-Node 테스트 수 | 3 | 2 | 3 |
| 빌드 도구 | Gradle (Kotlin DSL) | Gradle (Kotlin DSL) | dotnet test (xUnit) |

### 2-Node 테스트 비교

| 항목 | Java Akka Classic | Kotlin Pekko Typed | C# Akka.NET |
|------|-------------------|-------------------|-------------|
| seed 포트 | 25520 | 25521 | 25522 |
| joining 포트 | 0 (자동) | 0 (자동) | 0 (자동) |
| min-nr-of-members | 2 | 2 | 2 |
| 크로스노드 액터 참조 | `ActorSelection` (경로 기반) | `Receptionist` (서비스 키 기반) | `ActorSelection` (경로 기반) |
| 크로스노드 PubSub | `DistributedPubSub` mediator 직접 발행 | `Topic<T>` 양쪽 노드 생성 | `DistributedPubSub` mediator 직접 발행 |
| 클러스터 이벤트 구독 | `cluster.subscribe(self, MemberUp.class)` | `messageAdapter` + `Cluster.subscriptions()` | `cluster.Subscribe(Self, typeof(MemberUp))` |
| 직렬화 설정 | `allow-java-serialization = on` + `Serializable` | `allow-java-serialization = on` + `Serializable` | 기본 직렬화 (추가 설정 불필요) |
| 테스트 시스템 생성 | 2개 `ActorSystem` + `@BeforeAll` | 2개 `ActorTestKit` + `companion object` | `IClassFixture` + 2개 `ActorSystem` |
| 프로브 패턴 | `TestProbe` (akka-testkit) | `TestProbe<T>` (typed testkit) | `MessageCollectorActor` (커스텀) |
| 클러스터 형성 대기 | `StreamSupport` 폴링 (Scala Iterable) | `.count()` 폴링 (Kotlin 확장) | `.Count()` 폴링 (LINQ) |
| 2-Node 테스트 수 | 4 | 4 | 4 |
| **총 테스트 수** | **7** | **6** | **7** |

---

## 2026-02-16 테스트 개선 (Sleep 제거 중심)

대상 프로젝트:
- `skill-test/projects/sample-cluster-dotnet`
- `skill-test/projects/sample-cluster-java`
- `skill-test/projects/sample-cluster-kotlin`

핵심 개선:
- 테스트 코드의 `Thread.sleep` / `Task.Delay` 제거
- TestKit 기반 `awaitAssert`, `expectMessage`, `expectNoMessage` 중심으로 대기 로직 전환
- .NET 2-node 테스트는 폴링 딜레이 대신 `MessageCollectorActor` + scheduler 기반 반복 publish로 안정화

실행 결과:
1. Java Akka Classic: `./gradlew test` → **PASS (5 passed)**
2. Kotlin Pekko Typed: `./gradlew test` → **PASS (6 passed)**
3. C# Akka.NET: `dotnet test ClusterActors.sln --no-restore --disable-build-servers` → **PASS (7 passed)**

비고:
- `sample1~100` 번호형 프로젝트는 이번 개선 범위에서 제외.
- 클러스터 형성/전파 대기는 고정 Sleep 대신 이벤트/어설션 기반으로 통일.

---

## 2026-02-16 K8s 로컬 클러스터 인프라 구성

### 목적

3개 클러스터 프로젝트(Kotlin Pekko, Java Akka, C# Akka.NET)를 Docker Desktop Kubernetes 환경에서 2노드 클러스터로 구동하기 위한 인프라 구성.

### 아키텍처 결정

**StatefulSet + Headless Service + seed-nodes 패턴** 채택 (Management Bootstrap 불필요)

- `podManagementPolicy: OrderedReady` → pod-0(Seed) 먼저 기동, pod-1(Joining) 이후 기동
- Headless Service → 안정적 DNS: `{pod}-{ordinal}.{service}.default.svc.cluster.local`
- `imagePullPolicy: Never` → 로컬 Docker 이미지 직접 사용
- HOCON 환경변수 fallback 패턴 → 로컬 개발과 K8s 양립

### 변경 내역

#### 공통: Main 클래스 헤드리스 모드 전환

각 프로젝트의 Main 클래스가 `ActorSystem` 종료까지 블로킹하도록 개선:

| 프로젝트 | 블로킹 API | 변경 전 |
|----------|-----------|---------|
| Kotlin Pekko | `system.whenTerminated.toCompletableFuture().join()` | println만 하고 즉시 종료 |
| Java Akka | `system.getWhenTerminated().toCompletableFuture().join()` | ActorSystem 생성 후 즉시 종료 |
| C# Akka.NET | `await system.WhenTerminated` | 엔트리포인트 없음 (Library 프로젝트) |

#### 공통: application.conf 환경변수 주입

HOCON fallback 패턴으로 기본값과 환경변수 오버라이드를 양립:

```hocon
# 예시 (Kotlin Pekko)
pekko.remote.artery.canonical.hostname = "127.0.0.1"
pekko.remote.artery.canonical.hostname = ${?CLUSTER_HOSTNAME}
```

- `CLUSTER_HOSTNAME`, `CLUSTER_PORT`, `CLUSTER_SEED_NODES`, `CLUSTER_MIN_NR` 4개 환경변수 통일
- `coordinated-shutdown.exit-jvm = on` (JVM) / `exit-clr = on` (.NET) 추가
- `allow-java-serialization = on` 추가 (Java/Kotlin)

#### 공통: infra/ 디렉토리 생성

각 프로젝트에 Dockerfile + k8s-cluster.yaml 생성:

| 프로젝트 | 베이스 이미지 | 리모팅 포트 | 프로토콜 |
|----------|-------------|-----------|---------|
| Kotlin Pekko | `gradle:8.5-jdk17` → `eclipse-temurin:17-jre` | 25520 | `pekko://` |
| Java Akka | `gradle:8.5-jdk17` → `eclipse-temurin:17-jre` | 2551 | `akka://` |
| C# Akka.NET | `dotnet/sdk:9.0` → `dotnet/runtime:9.0` | 4053 | `akka.tcp://` |

#### C# 추가: Exe 변환 + Program.cs

- `ClusterActors.csproj`에 `<OutputType>Exe</OutputType>` 추가
- `Akka.Remote` NuGet 패키지 추가 (dot-netty.tcp 트랜스포트용)
- `Program.cs` 신규 생성 (코드 레벨 HOCON 구성 + 환경변수 오버라이드)

### 유닛테스트 결과

기존 테스트에 영향 없음을 확인:

| 프로젝트 | 테스트 수 | 결과 |
|----------|----------|------|
| Kotlin Pekko | 6 (1-Node: 2, 2-Node: 4) | PASS |
| Java Akka | 7 (1-Node: 3, 2-Node: 4) | PASS |
| C# Akka.NET | 7 (1-Node: 3, 2-Node: 4) | PASS |

### K8s 배포 방법

```bash
# 1. Docker 이미지 빌드 (각 프로젝트 디렉토리에서)
docker build -f infra/Dockerfile -t sample-cluster-kotlin:latest .
docker build -f infra/Dockerfile -t sample-cluster-java:latest .
docker build -f infra/Dockerfile -t sample-cluster-dotnet:latest .

# 2. K8s 배포
kubectl apply -f infra/k8s-cluster.yaml

# 3. 클러스터 형성 확인
kubectl get pods -w           # pod-0 Running → pod-1 Running
kubectl logs {pod-name}       # "Member is Up" 로그 × 2

# 4. 정리
kubectl delete -f infra/k8s-cluster.yaml
```

### K8s 실제 배포 테스트 결과 (2026-02-16)

> 환경: Docker Desktop Kubernetes (WSL2), `imagePullPolicy: Never`

#### 트러블슈팅: HOCON `${?ENV_VAR}` → 코드 레벨 오버라이드 전환

초기 구현에서는 `application.conf`에 HOCON `${?ENV_VAR}` fallback 패턴을 사용했으나, `seed-nodes` 같은 리스트 타입 설정에서 환경변수가 문자열로 파싱되어 타입 오류 발생:

```
ConfigException$ValidationFailed: pekko.cluster.seed-nodes: Wrong value type, expecting: list but got: string
```

**해결**: 3개 프로젝트 모두 Main 클래스에서 `ConfigFactory.parseString(overrides).withFallback(ConfigFactory.load())` 패턴으로 전환. 환경변수 값을 HOCON 문자열에 직접 임베딩하여 리스트 리터럴로 올바르게 파싱.

#### 트러블슈팅: Canonical Hostname ↔ Seed-nodes 주소 불일치

Pod IP를 canonical hostname으로 사용하면 seed-nodes 주소(DNS 이름)와 불일치하여 Handshake 실패:

```
Dropping Handshake Request addressed to unknown local address [...@pekko-cluster-0.pekko-cluster...]. Local address is [...@10.1.0.29:25520]
```

**해결**: `CLUSTER_HOSTNAME`을 `status.podIP` 대신 `$(POD_NAME).{service}.default.svc.cluster.local` K8s 환경변수 확장으로 변경.

#### Kotlin Pekko Typed — PASS

```
=== pekko-cluster-0 (Seed) ===
Remoting started; listening on [pekko://ClusterSystem@pekko-cluster-0.pekko-cluster.default.svc.cluster.local:25520]
Cluster Node [...] - Starting up, Pekko version [1.1.3] ...
Cluster Node [...] - Started up successfully
Cluster Node [...] - Node [...pekko-cluster-0...] is JOINING itself and forming new cluster
Cluster system started: ClusterSystem
Cluster Node [...] - Node [...pekko-cluster-1...] is JOINING, roles [dc-default]
Cluster Node [...] - Leader is moving node [...pekko-cluster-0...] to [Up]
Cluster Node [...] - Leader is moving node [...pekko-cluster-1...] to [Up]
Member is Up: Member(pekko://ClusterSystem@pekko-cluster-0.pekko-cluster.default.svc.cluster.local:25520, Up)
Member is Up: Member(pekko://ClusterSystem@pekko-cluster-1.pekko-cluster.default.svc.cluster.local:25520, Up)

=== pekko-cluster-1 (Joining) ===
Remoting started; listening on [pekko://ClusterSystem@pekko-cluster-1.pekko-cluster.default.svc.cluster.local:25520]
Cluster Node [...] - Started up successfully
Cluster system started: ClusterSystem
Cluster Node [...] - Welcome from [...pekko-cluster-0...]
Member is Up: Member(pekko://ClusterSystem@pekko-cluster-0.pekko-cluster.default.svc.cluster.local:25520, Up)
Member is Up: Member(pekko://ClusterSystem@pekko-cluster-1.pekko-cluster.default.svc.cluster.local:25520, Up)
```

#### Java Akka Classic — PASS

```
=== akka-cluster-0 (Seed) ===
Remoting started with transport [Artery tcp]; listening on [akka://ClusterSystem@akka-cluster-0.akka-cluster.default.svc.cluster.local:2551]
Cluster Node [...] - Starting up, Akka version [2.7.0] ...
Cluster Node [...] - Started up successfully
Cluster Node [...] - Node [...akka-cluster-0...] is JOINING itself and forming new cluster
Cluster system started: ClusterSystem
Cluster Node [...] - Received InitJoin from [...akka-cluster-1...]
Cluster Node [...] - Node [...akka-cluster-1...] is JOINING
Cluster Node [...] - Leader is moving node [...akka-cluster-0...] to [Up]
Cluster Node [...] - Leader is moving node [...akka-cluster-1...] to [Up]
Member is Up: Member(akka://ClusterSystem@akka-cluster-0.akka-cluster.default.svc.cluster.local:2551, Up)
Member is Up: Member(akka://ClusterSystem@akka-cluster-1.akka-cluster.default.svc.cluster.local:2551, Up)

=== akka-cluster-1 (Joining) ===
Remoting started with transport [Artery tcp]; listening on [akka://ClusterSystem@akka-cluster-1.akka-cluster.default.svc.cluster.local:2551]
Cluster Node [...] - Started up successfully
Cluster system started: ClusterSystem
Cluster Node [...] - Welcome from [...akka-cluster-0...]
Member is Up: Member(akka://ClusterSystem@akka-cluster-0.akka-cluster.default.svc.cluster.local:2551, Up)
Member is Up: Member(akka://ClusterSystem@akka-cluster-1.akka-cluster.default.svc.cluster.local:2551, Up)
```

#### C# Akka.NET — PASS

```
=== akkanet-cluster-0 (Seed) ===
Remoting started; listening on [akka.tcp://ClusterSystem@akkanet-cluster-0.akkanet-cluster.default.svc.cluster.local:4053]
Cluster Node [...] - Starting up...
Cluster Node [...] - Started up successfully
Cluster system started: ClusterSystem
Cluster Node [...] - Node [...akkanet-cluster-0...] is JOINING itself and forming a new cluster
Cluster Node [...] - Received InitJoin from [...akkanet-cluster-1...]
Cluster Node [...] - Node [...akkanet-cluster-1...] is JOINING
Cluster Node [...] - Leader is moving node [...akkanet-cluster-0...] to [Up]
Cluster Node [...] - Leader is moving node [...akkanet-cluster-1...] to [Up]
Member is Up: Member(address = akka.tcp://ClusterSystem@akkanet-cluster-0.akkanet-cluster.default.svc.cluster.local:4053, status = Up)
Member is Up: Member(address = akka.tcp://ClusterSystem@akkanet-cluster-1.akkanet-cluster.default.svc.cluster.local:4053, status = Up)

=== akkanet-cluster-1 (Joining) ===
Remoting started; listening on [akka.tcp://ClusterSystem@akkanet-cluster-1.akkanet-cluster.default.svc.cluster.local:4053]
Cluster Node [...] - Started up successfully
Cluster system started: ClusterSystem
Cluster Node [...] - Welcome from [...akkanet-cluster-0...]
Member is Up: Member(address = akka.tcp://ClusterSystem@akkanet-cluster-0.akkanet-cluster.default.svc.cluster.local:4053, status = Up)
Member is Up: Member(address = akka.tcp://ClusterSystem@akkanet-cluster-1.akkanet-cluster.default.svc.cluster.local:4053, status = Up)
```

#### K8s 배포 종합

| 프로젝트 | StatefulSet | 프로토콜 | 포트 | 클러스터 형성 | 양쪽 노드 Member Up |
|----------|------------|---------|------|-------------|-------------------|
| Kotlin Pekko Typed | `pekko-cluster` (2 replicas) | `pekko://` | 25520 | PASS | PASS |
| Java Akka Classic | `akka-cluster` (2 replicas) | `akka://` | 2551 | PASS | PASS |
| C# Akka.NET | `akkanet-cluster` (2 replicas) | `akka.tcp://` | 4053 | PASS | PASS |

핵심 검증 포인트:
- **OrderedReady**: pod-0(Seed) → pod-1(Joining) 순서 기동 확인
- **Headless Service DNS**: `{pod}.{service}.default.svc.cluster.local` 주소 해석 정상
- **Seed-nodes Join**: pod-1이 seed(pod-0)에 InitJoin → Welcome 수신 → 클러스터 합류
- **Leader Election**: pod-0이 리더로 선출되어 양쪽 노드를 Up으로 전환
- **ClusterListenerActor**: 양쪽 노드 모두에서 "Member is Up" × 2 로그 확인
- **coordinated-shutdown**: `kubectl delete` 시 graceful leave 후 종료
