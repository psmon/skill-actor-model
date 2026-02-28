# TEST-Cafe24APILimit 테스트 결과

## 테스트 개요

카페24 API의 Leaky Bucket 호출 제약 정책에 대응하여, Akka Streams 백프레셔를 활용한 안전 호출 장치를 구현하고 검증한 결과입니다.

- **프로젝트 위치**: `skill-test/projects/sample22`
- **사용 스킬**: `java-akka-classic`, `java-akka-classic-test`
- **플랫폼**: Java 23 + Akka Classic 2.7.0 + Akka Streams
- **테스트 일자**: 2026-02-28

## 더미 서버 설정 (1노드 테스트)

| 항목 | 값 | 설명 |
|------|-----|------|
| Bucket Capacity | 10 | 최대 동시 요청 수 |
| Leak Rate | 2/sec | 초당 버킷 감소량 |
| 안전 호출 TPS | 2/sec | 스트림 throttle 설정값 |

## 테스트 결과

### 전체: 5건 PASSED / 0건 FAILED

| # | 테스트명 | 결과 | 비고 |
|---|---------|------|------|
| 1 | hello → world 변환 | PASSED | 요청 "hello" → 응답 "world" 정상 |
| 2 | 일반 단어 에코 | PASSED | 요청 "akka-stream" → 응답 "akka-stream" 정상 |
| 3 | 직접호출 429 발생 확인 | PASSED | 20건 동시 → 200: 10건, 429: **10건** |
| 4 | 백프레셔 burst 안전 처리 | PASSED | 15건 burst → 200: **15건**, 429: 0건 |
| 5 | 버킷 상태 헤더 검증 | PASSED | bucketUsed/bucketMax 정상 포함 |

### 핵심 비교: 직접 호출 vs 백프레셔 적용

```
[직접호출 - 백프레셔 미적용]
  총 20건 동시 요청 → 200: 10건, 429: 10건 (50% 실패)
  버킷 capacity(10) 초과로 절반이 거부됨

[백프레셔 적용 - SafeApiCallerActor]
  총 15건 burst 요청 → 200: 15건, 429: 0건 (100% 성공)
  버킷 사용량: 1~3 수준으로 안정적 유지
```

### 백프레셔 적용 시 버킷 사용량 추이

```
safe-0  → bucket=1/10
safe-1  → bucket=2/10
safe-2  → bucket=3/10
safe-3  → bucket=2/10  ← leak으로 감소
safe-4  → bucket=3/10
safe-5  → bucket=2/10  ← leak으로 감소
safe-6  → bucket=3/10
...반복 패턴 (2~3 수준 유지)
```

throttle(2 req/sec)과 서버 leak rate(2/sec)가 균형을 이루어 버킷이 안전 범위(1~3) 내에서 안정적으로 유지됩니다.

## 아키텍처 구성

```
[사용자] → ApiRequest → [SafeApiCallerActor]
                              │
                    ┌─────────▼──────────┐
                    │  Akka Streams 파이프라인  │
                    │                    │
                    │  Source.actorRef    │  ← 유입 밸브 (buffer=100)
                    │       │            │
                    │  throttle(2/s)     │  ← 유량 조절기
                    │       │            │
                    │  mapAsync(1)       │  ← 비동기 HTTP 펌프
                    │  + 적응형 지연      │     (429 자동 재시도)
                    │       │            │
                    │  Sink.foreach      │  ← 응답 배출구
                    └───────│────────────┘
                            ▼
[사용자] ← ApiResponse ← replyTo.tell()
```

## 생성된 파일

| 파일 | 역할 |
|------|------|
| `message/Messages.java` | ApiRequest, ApiResponse, StreamEnvelope, StreamResult 메시지 정의 |
| `api/DummyCafe24Server.java` | Leaky Bucket 더미 API 서버 (JDK HttpServer 기반) |
| `actor/SafeApiCallerActor.java` | 백프레셔 적용 안전 호출 액터 (재사용 가능) |
| `SafeApiCallerTest.java` | 5건 유닛테스트 (TestKit 기반) |
