# TEST Cluster Improve Part2 Plan (Planner: Codex)

## 목표
- Petabridge Kafka 베스트 프랙티스(Streams 중심, backpressure, supervision-friendly 구성)를 기준으로 3개 클러스터 샘플(`dotnet/java/kotlin`)에 동일한 Kafka 연동 개선 적용
- 각 프로젝트는 독립 토픽을 사용하고, 클러스터 2노드 조인 완료 후 **1회만** 자동 수행
- 기능 추가에 대한 유닛 테스트 추가 + 기존 테스트 유지 확인
- Kubernetes에서 Kafka standalone 1개를 먼저 구동하고, 프로젝트별 클러스터/기능 로그 검증 후 graceful shutdown

## 참조 근거
- Petabridge: https://petabridge.com/blog/akka-streams-kafka-best-kafka-client-dotnet/
- Akka.Streams.Kafka (Akka.NET): https://github.com/akkadotnet/Akka.Streams.Kafka
- Alpakka Kafka (Akka Classic): https://doc.akka.io/libraries/alpakka-kafka/current/
- Pekko Connectors Kafka (Pekko Typed): https://pekko.apache.org/docs/pekko-connectors-kafka/current/

## 공통 설계
1. 각 프로젝트에 Kafka Streams 실행 전담 러너(`runOnce`) + 클러스터 싱글톤 액터 추가
2. 클러스터 리스너가 `min-nr-of-members` 도달 확인 후 `delay` 타이머로 싱글톤에 시작 메시지 전달
3. 싱글톤은 내부 idempotent 플래그로 다중 트리거 방지 (정확히 1회 실행)
4. Streams 파이프라인:
   - Producer `plainSink`로 고유 payload 1건 발행
   - Consumer `plainSource`로 동일 payload 1건 수신 확인
5. 토픽 분리
   - dotnet: `cluster-dotnet-events`
   - java: `cluster-java-events`
   - kotlin: `cluster-kotlin-events`

## 구현 단계
- [x] Dotnet: 의존성 + 싱글톤/러너 + Program/Listener 연결 + 테스트 추가
- [x] Java: 의존성 + 싱글톤/러너 + Main/Listener 연결 + 테스트 추가
- [x] Kotlin: 의존성 + 싱글톤/러너 + Main/Listener 연결 + 테스트 추가
- [x] Kafka standalone k8s 매니페스트 추가
- [x] 프로젝트별 k8s env(Kafka bootstrap/topic/group/delay) 반영
- [x] 3개 유닛테스트 실행 및 결과 수집
- [x] kubectl 기반 통합 검증(카프카 선기동 → 프로젝트별 배포/로그 확인 → graceful shutdown)
- [x] 결과 문서(`TEST-RESULT-PART2.md`, `TEST-RESULT-PART2-Improve.md`) 작성
- [x] 프로젝트별 README 업데이트

## 리스크/대응
- Kafka 커넥터 버전 호환성: 각 프레임워크 계열에 맞는 안정 버전 사용
- 테스트 환경에서 외부 Kafka 의존: 유닛테스트는 fake runner 기반으로 1회 실행 보장 로직 검증
- 클러스터 이벤트 타이밍: `MemberUp` 이벤트 + 현재 `Up` 멤버수 재검증 + 지연 타이머로 안정화
