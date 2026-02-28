# Cafe24 API 안전 호출 장치 사용 가이드

## 개요

카페24 API의 Leaky Bucket 호출 제약 정책에 대응하는 **Akka Streams 기반 백프레셔 안전 호출 장치**입니다. 사용자는 API 호출 제약(TPS, 429 에러, 버킷 상태)을 전혀 신경 쓰지 않고, 요청을 보내고 결과만 받으면 됩니다.

## 핵심 컴포넌트

### SafeApiCallerActor

호출 제약을 내부적으로 처리하는 재사용 가능한 액터입니다.

```java
// 생성: API 기본 URL + 초당 최대 호출 수 설정
ActorRef caller = system.actorOf(
    SafeApiCallerActor.props("https://api.cafe24.com", 2),
    "cafe24-api-caller"
);

// 요청: 단어와 응답 받을 ActorRef 전달
caller.tell(new ApiRequest("hello", myActorRef), ActorRef.noSender());

// 응답: ApiResponse 메시지로 수신
// - word: 원본 요청 단어
// - result: API 응답 본문
// - statusCode: HTTP 상태 코드
// - bucketUsed/bucketMax: 버킷 상태
```

### 내부 동작 (유압장치 비유)

```
요청 투입 ──→ [버퍼 탱크] ──→ [유량 조절 밸브] ──→ [비동기 펌프] ──→ 응답 배출
  (tell)     (100건 버퍼)   (throttle 2/s)   (mapAsync HTTP)   (replyTo.tell)
                                                    │
                                              [압력 게이지]
                                         (X-Api-Call-Limit 헤더 읽기)
                                                    │
                                          사용률 > 80%: +500ms 지연
                                          사용률 > 50%: +200ms 지연
                                          429 응답: 대기 후 재시도
```

## 사용 방법

### 1. 기본 사용법 (단일 API)

```java
// ActorSystem 초기화
ActorSystem system = ActorSystem.create("my-system");

// 안전 호출 장치 생성 (초당 2건 제한)
ActorRef apiCaller = system.actorOf(
    SafeApiCallerActor.props("https://mallid.cafe24api.com/api/v2", 2),
    "product-api"
);

// 요청 보내기 (호출 제약은 장치가 알아서 처리)
apiCaller.tell(new ApiRequest("products/count", self()), self());

// 응답 처리
receive(ApiResponse.class, response -> {
    System.out.println("결과: " + response.result());
    System.out.println("버킷 상태: " + response.bucketUsed() + "/" + response.bucketMax());
});
```

### 2. 다중 API 엔드포인트 (장치 재사용)

```java
// 서로 다른 API 엔드포인트에 독립적인 안전 호출 장치 생성
ActorRef productApi = system.actorOf(
    SafeApiCallerActor.props("https://mallid.cafe24api.com/api/v2/products", 2),
    "product-api"
);

ActorRef orderApi = system.actorOf(
    SafeApiCallerActor.props("https://mallid.cafe24api.com/api/v2/orders", 2),
    "order-api"
);

// 각 장치는 독립적으로 백프레셔 적용
productApi.tell(new ApiRequest("count", self()), self());
orderApi.tell(new ApiRequest("list", self()), self());
```

### 3. 대량 처리 (burst 안전)

```java
// 100건의 요청을 한번에 투입해도 안전
for (int i = 0; i < 100; i++) {
    apiCaller.tell(new ApiRequest("item-" + i, self()), self());
}
// → throttle이 초당 2건씩 처리
// → 버킷 오버플로 없이 전부 성공
// → 약 50초 후 모든 응답 수신
```

## 성능 튜닝

### throttle 설정 가이드

| Cafe24 버킷 설정 | 권장 throttle | 안전 마진 |
|-----------------|--------------|----------|
| capacity=40, leak=2/s | `maxRequestsPerSecond=2` | 최대 안전 (버킷 거의 미사용) |
| capacity=40, leak=2/s | `maxRequestsPerSecond=3` | 적절 (적응형 지연으로 보완) |
| capacity=40, leak=2/s | `maxRequestsPerSecond=5` | 공격적 (적응형 지연 의존) |

**권장**: `maxRequestsPerSecond <= leak rate`로 설정하면 버킷이 거의 차지 않아 가장 안전합니다.

### 버퍼 크기 조절

```java
// Source.actorRef의 버퍼 크기 (현재 100)
// 대량 burst가 예상되면 증가 가능
Source.<StreamEnvelope>actorRef(500, OverflowStrategy.dropNew())
```

| 버퍼 크기 | 용도 |
|----------|------|
| 100 | 일반 사용 (기본값) |
| 500 | 대량 burst 허용 |
| 1000 | 극대량 처리 |

### OverflowStrategy 선택

| 전략 | 설명 | 권장 상황 |
|------|------|----------|
| `dropNew()` | 버퍼 초과 시 새 요청 폐기 | 최신 요청 우선 (현재 설정) |
| `dropHead()` | 가장 오래된 요청 폐기 | 최신 요청 우선 |
| `backpressure()` | upstream에 압력 전파 | Source.queue 사용 시 |
| `fail()` | 스트림 실패 | 데이터 손실 불허 시 |

## 적응형 백프레셔 상세

SafeApiCallerActor는 API 응답 헤더를 읽어 동적으로 호출 속도를 조절합니다:

```
X-Api-Call-Limit: 8/10  → 사용률 80% → +500ms 지연 추가
X-Api-Call-Limit: 5/10  → 사용률 50% → +200ms 지연 추가
X-Api-Call-Limit: 3/10  → 사용률 30% → 추가 지연 없음
HTTP 429 응답           → X-Cafe24-Call-Remain 초만큼 대기 후 재시도 (최대 3회)
```

이 메커니즘은 throttle의 기본 유량 제한 위에 추가로 작동하여 이중 안전장치 역할을 합니다.

## 실제 Cafe24 API 적용 시 변경 사항

1. **DummyCafe24Server 제거**: 실제 API URL로 교체
2. **인증 헤더 추가**: `HttpRequest.newBuilder().header("Authorization", "Bearer " + token)`
3. **요청/응답 포맷 변경**: Messages.java의 ApiRequest/ApiResponse를 실제 API 스키마에 맞게 확장
4. **throttle 값 조정**: 실제 Cafe24 정책(capacity=40, leak=2/s)에 맞게 설정

```java
// 실제 적용 예시
ActorRef caller = system.actorOf(
    SafeApiCallerActor.props("https://mallid.cafe24api.com/api/v2", 2),
    "cafe24-safe-caller"
);
```

## 의존성

```groovy
dependencies {
    implementation "com.typesafe.akka:akka-actor_2.13:2.7.0"
    implementation "com.typesafe.akka:akka-stream_2.13:2.7.0"
}
```
