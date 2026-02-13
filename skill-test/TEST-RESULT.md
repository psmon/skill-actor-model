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

## 크로스 플랫폼 비교 요약

| 항목 | sample1/4 (Kotlin Pekko) | sample2/5 (Java Akka) | sample3/6 (C# Akka.NET) |
|------|-------------------------|----------------------|------------------------|
| 액터 베이스 | `AbstractBehavior<T>` | `AbstractActor` | `ReceiveActor` |
| 메시지 정의 | `sealed class` 계층 | 내부 불변 클래스 | `record` |
| 응답 패턴 | `replyTo: ActorRef<T>` 명시적 | `getSender()` 암묵적 | `Sender.Tell()` 암묵적 |
| 라우터 API | `Routers.pool()` | `new XxxPool(n).props()` | `Props.WithRouter(new XxxPool(n))` |
| Broadcast | 커스텀 액터 구현 필요 | `BroadcastPool` 내장 | `BroadcastPool` 내장 |
| ConsistentHash | `ConsistentHashing` 키 매핑 | `ConsistentHashable` 인터페이스 | `IConsistentHashable` 인터페이스 |
| 빌드 | Gradle (Kotlin DSL) | Gradle (Groovy) | dotnet CLI |
