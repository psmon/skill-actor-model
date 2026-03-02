# TEST-RESULT-PART3-Improve

## 개선사항 요약
- 대상: Kotlin Pekko Typed / Java Akka Classic / C# Akka.NET 클러스터 샘플
- 목표: Cafe24 API 제한 대응 장치를 클러스터 샘플에 공통 패턴으로 내재화

## 스킬 참조로 적용한 내용

### kotlin-pekko-typed / cluster / test / infra
- Typed actor + Behavior 기반으로 MallId별 child actor 라우팅 구현
- `ClusterSingleton`으로 메트릭 집계 싱글턴 구성
- `ActorTestKit` 기반 신규 단위 테스트 추가
- Kubernetes 배포 + 2노드 조인 확인 패턴 재사용

### java-akka-classic / cluster / test / infra
- `AbstractActor` + `ClusterSingletonManager/Proxy` 기반 메트릭 싱글턴 구성
- `akka-stream` throttle + `mapAsync` 조합으로 비동기 안전호출
- `akka-testkit` 기반 신규 단위 테스트 추가
- 기존 클러스터 검증 패턴(2노드 Up + 로그 확인) 재사용

### dotnet-akka-net / cluster / test / infra
- `ReceiveActor` + `ClusterSingletonManager/Proxy` 구성
- `Akka.Streams` throttle 파이프라인 적용
- `Akka.TestKit.Xunit2` 기반 신규 단위 테스트 추가
- Kubernetes StatefulSet + 로그 검증 패턴 재사용

## 스킬에 없어서 추가로 정리한 내용
- MallId별 독립 버킷(분산 제약)을 dummy API 내부 상태로 모델링
- 안전호출 결과를 클러스터 싱글턴에 중앙 누적(totalCalls, throttled429, avgQueueDelayMs)
- 통합 검증 시 실제 API 호출 체인(포트포워드 + /api/cafe24/* 호출 + 로그 매칭) 절차 표준화
- 프로젝트별 동일 API 계약(`/api/cafe24/call`, `/api/cafe24/metrics`) 정렬

## 실패/재시도 기반 개선 포인트 (다음 구현 시 주의)
- Kotlin Typed
  - `Source.actorRef`는 classic `ActorRef`를 반환하므로 typed `ActorRef<T>`로 직접 받지 말 것
  - child 조회는 `context.getChild(name)`로 처리하고 명시적 cast 필요
- Java Classic
  - `getContext().child(name)`는 `scala.Option`이므로 `Optional`/`orElseGet` 패턴 사용 금지
  - `LoggingAdapter.info` 포맷 인자 수 제한(placeholder 과다 사용 시 컴파일 실패)
- Dotnet
  - Streams + actor 조합 시 queue delay/재시도 처리 후 최종 결과만 응답하도록 단계 분리
- Kubernetes 통합
  - 환경 제한이 있으면 `kubectl port-forward`/API 호출은 권한상승 경로를 사용

## 최종 개선 효과
- 3개 플랫폼에서 같은 운영 관점(백프레셔/재시도/관측성/클러스터 집계)을 동일하게 확보
- 테스트 우선 작성 후 구현 보강 흐름으로 회귀 안정성 유지
- 다음 유사 과제에서 재사용 가능한 공통 패턴(요청분리 + 싱글턴 집계 + 통합검증 루틴) 정립
