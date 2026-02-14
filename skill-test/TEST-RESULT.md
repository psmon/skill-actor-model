# Skill로 생성된 프로젝트 테스트 결과

> 테스트 일시: 2026-02-14 | 환경: WSL2 Ubuntu, Java 21, .NET 9.0, Kotlin 1.9.x

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
