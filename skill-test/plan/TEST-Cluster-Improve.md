# 스킬활용 프롬프트 테스트 - 클러스터편

여기서 제공되는 스킬에 의해 다음과같은 비교적 난이도가 높은 클러스터 기능코드가 최초 완성
- skill-test/projects/sample-cluster-dotnet
- skill-test/projects/sample-cluster-java
- skill-test/projects/sample-cluster-kotlin
코드한방생성이 아닌 , 스킬을 통해 지속유지및 업데이트 관리가능한지? 테스트 진행 항목으로 기존 작성된 코드에서, 스킬베이스로 기능추가가 시도되는곳

권장 도커데탑옵션
- 클러스터 인프라 구성파트진행시, 도커데탑의 쿠버활성화 기능필요

## 스킬리스트 
다음 스킬의 복합사용 능력을 확인 

```
/java-akka-classic RoundRobinPool 라우터로 5개 워커에 메시지 분배
/java-akka-classic-test akka-testkit 기반 Hello->World 유닛테스트 작성
/java-akka-classic-cluster Singleton 카운터 액터 + PubSub로 상태 브로드캐스트

/kotlin-pekko-typed sealed class 기반 상태 전환 액터 구현
/kotlin-pekko-typed-test ActorTestKit + TestProbe 기반 Typed 테스트 작성
/kotlin-pekko-typed-cluster Sharding 디바이스 엔티티 + 패시베이션 2분 설정

/dotnet-akka-net FSM 배치 처리 액터, 1초 타임아웃 자동 플러시
/dotnet-akka-net-test Akka.TestKit.Xunit2 기반 분리 테스트 프로젝트 생성
/dotnet-akka-net-cluster 클러스터 설정 + Singleton + Sharding 통합 예제

/actor-ai-agent RAG 기반 질의분석 -> 검색 -> 평가 -> 응답 파이프라인 설계
/actor-ai-agent-java Java Akka 기반 질의분석 -> 검색 -> 평가 -> 응답 파이프라인 설계
/actor-ai-agent-kotlin Kotlin Pekko 기반 질의분석 -> 검색 -> 평가 -> 응답 파이프라인 설계
```

자바/코틀린은 동일 JVM에서 구동이 가능하기때문에 복합 사용테스트가 가능하지만(스칼라도 마찬가지)
dotnet은 런타임자체가 달라 동일프로젝트내 다른언어와 복합 구현은 불가능
하지만 이 스킬의 목적이 혼종을 만든느것은 아니기에 동일한 언어/플랫폼내에서만 스킬구성을 이용

프롬프트 작성개선 : 개행 \ 입력안해도, shift+enter로 개행이 가능하도록 클코드 업데이트사항을 확인(복붙프롬프트 작성할때 조금더 편이)

## 1.0.0 - 클러스터링 구성 개선지침 by Claude Code

TIP : 각각 독립적으로 수행할수 있는 3가지 작업을 효율적으로 멀티TASK를 알아서 활용해 빠르게 완료하는지? 

3가지 유형의 동일기능으로 구현된 액터모델 프로젝트를 개선하려고합니다.
수행지침
- 현재 1Node만 이용하 클러스터 테스트를 수행을하며, 이 내용을 유지하고 동일하게 2Node를 이용한 테스트 코드를 작성해주세요
- 2Node 구성하기위한 클러스터 구성을 해야하며... 첫번째 구동 Node만 Seednode로 설정하고, 두번째 노드는 첫번째 노드에 조인합니다. 
- skill-test/projects/sample1~100 : 넘버링된 형태의 소스는 참고하지 않습니다.
- 각진영의 언어의 구현스펙을 존중하며 목표수행을 위해 타진영에서 구현된 좋은 패턴이 있다고하면 도입하되 일관성확보가 더 좋요합니다.
  - skill-test/projects/sample-cluster-dotnet
  - skill-test/projects/sample-cluster-java
  - skill-test/projects/sample-cluster-kotlin

### Kottlin Pekko Typed
```
kotlin-pekko-typed kotlin-pekko-typed-cluster kotlin-pekko-typed-test 스킬을 활용
다음경로 프로젝트 개선 : skill-test/projects/sample-cluster-kotlin

```

### Java Akka Classic
```
java-akka-classic java-akka-classic-test akka-testkit java-akka-classic-cluster 스킬을 활용해서
다음경로 프로젝트 개선 : skill-test/projects/sample-cluster-java
```

### C# Akka.NET
```
dotnet-akka-net dotnet-akka-net-test dotnet-akka-net-cluster 스킬을 활용해서
다음경로 프로젝트 개선 : skill-test/projects/sample-cluster-dotnet
```

## 개선완료후 지침 (문서및 스킬업데이트)
- skill-test/TEST-RESULT.md : 에 수행결과를 업데이트해주세요
- skill-test/LANG-SPECS-RESULT.md : 수행과정중 발견한 언어스펙의 차이에따른 구현을 비교하는 문서를 생성하고 지속업데이트해주세요
- plugins/skill-actor-model : 이 활동에의해 추가발견된 개념인 경우 기존 스킬셋트과 비교해 없는경우 추가업데이트및 개선하고, 스킬버전업도 함께 진행해주세요


## 이 프롬프트의 실행계획

TIP : 결과론만 중요한 AI세상이 된듯하지만, 스킬의 경우 어떠한 실행계획을 하느냐 여정을 체크하는것도 중요한 활동이라 판단, 플랜을 비교하기로함 (홀수차:클코드에게,짝수차:코덱스에게 시켜 비교하는 부가활동)

```
 Here is Claude's plan:
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
 2-Node Cluster Test 구현 계획

 Context

 현재 3개 클러스터 프로젝트(Java/Kotlin/C#)는 1-Node 자기조인 방식의 클러스터 테스트만 보유. 실제 클러스터 시나리오(노드 간 통신, PubSub 전파, 멤버십 이벤트)를 검증하기 위해 2-Node 테스트를 추가한다. 기존 1-Node
 테스트는 그대로 유지.

 핵심 아키텍처

 - Node 1 (seed): 고정 포트 바인딩 (Java: 25520, Kotlin: 25521, C#: 25522)
 - Node 2 (joining): 포트 0 (자동할당), Node 1을 seed-nodes로 지정
 - min-nr-of-members = 2: 두 노드 모두 조인 후에야 Up 상태 전이
 - 클러스터 형성 대기: Cluster.state().members 폴링 (최대 15초 타임아웃)
 - 종료 순서: joining 노드 먼저 leave → shutdown, 그 다음 seed 노드 shutdown

 파일 변경 목록 (8개 신규, 0개 수정)

 Java Akka Classic (sample-cluster-java)

 ┌────────┬────────────────────────────────────────────────────┐
 │ Action │                        File                        │
 ├────────┼────────────────────────────────────────────────────┤
 │ CREATE │ src/test/resources/two-node-seed.conf              │
 ├────────┼────────────────────────────────────────────────────┤
 │ CREATE │ src/test/resources/two-node-joining.conf           │
 ├────────┼────────────────────────────────────────────────────┤
 │ CREATE │ src/test/java/cluster/java/TwoNodeClusterTest.java │
 └────────┴────────────────────────────────────────────────────┘

 Kotlin Pekko Typed (sample-cluster-kotlin)

 ┌────────┬────────────────────────────────────────────────────────┐
 │ Action │                          File                          │
 ├────────┼────────────────────────────────────────────────────────┤
 │ CREATE │ src/test/resources/two-node-seed.conf                  │
 ├────────┼────────────────────────────────────────────────────────┤
 │ CREATE │ src/test/resources/two-node-joining.conf               │
 ├────────┼────────────────────────────────────────────────────────┤
 │ CREATE │ src/main/kotlin/cluster/kotlin/ClusterListenerActor.kt │
 ├────────┼────────────────────────────────────────────────────────┤
 │ CREATE │ src/test/kotlin/cluster/kotlin/TwoNodeClusterTest.kt   │
 └────────┴────────────────────────────────────────────────────────┘

 C# Akka.NET (sample-cluster-dotnet)

 ┌────────┬────────────────────────────────────────────┐
 │ Action │                    File                    │
 ├────────┼────────────────────────────────────────────┤
 │ CREATE │ ClusterActors.Tests/TwoNodeClusterTests.cs │
 └────────┴────────────────────────────────────────────┘

 문서

 ┌────────┬─────────────────────────────────┐
 │ Action │              File               │
 ├────────┼─────────────────────────────────┤
 │ UPDATE │ skill-test/TEST-RESULT.md       │
 ├────────┼─────────────────────────────────┤
 │ CREATE │ skill-test/LANG-SPECS-RESULT.md │
 └────────┴─────────────────────────────────┘

 플랫폼별 구현 상세

 1. Java Akka Classic

 - Config: 외부 .conf 파일 2개 (seed/joining), akka://TwoNodeClusterSystem 프로토콜
 - 테스트 구조: @BeforeAll에서 2개 ActorSystem 생성, 폴링으로 클러스터 형성 대기
 - 크로스노드 통신: ActorSelection (경로: akka://TwoNodeClusterSystem@127.0.0.1:25520/user/actor-name)
 - PubSub: DistributedPubSub mediator - subscriber는 joining 노드, publisher는 seed 노드
 - 테스트 4개: 멤버십 검증, ClusterListener 2개 MemberUp, Counter 크로스노드, PubSub 크로스노드

 2. Kotlin Pekko Typed

 - Config: 외부 .conf 파일 2개, pekko://TwoNodeClusterSystem 프로토콜
 - ClusterListenerActor 신규 생성: messageAdapter로 ClusterEvent.MemberUp → typed 메시지 변환 (Java/C#과의 일관성 확보)
 - 테스트 구조: companion object에서 2개 ActorTestKit 생성
 - 크로스노드 통신: Receptionist 패턴 (Typed API의 관용적 서비스 디스커버리)
 - PubSub: Topic<T> API - 양쪽 노드에 PubSubManagerActor 각각 생성, Topic이 클러스터를 통해 자동 디스커버리
 - 테스트 4개: 멤버십 검증, ClusterListener MemberUp, Counter via Receptionist, PubSub 크로스노드

 3. C# Akka.NET

 - Config: 인라인 HOCON 문자열 (기존 패턴 유지), akka.tcp://TwoNodeClusterSystem 프로토콜
 - 테스트 구조: TestKit 상속 불가 (2개 시스템 필요) → IDisposable 구현, 2개 ActorSystem 직접 생성
 - TestProbe: CreateTestProbe() 대신 별도 방식으로 프로브 확보 (TestKit helper wrapping 또는 forwarding actor)
 - 크로스노드 통신: ActorSelection (경로: akka.tcp://TwoNodeClusterSystem@127.0.0.1:25522/user/actor-name)
 - PubSub: DistributedPubSub mediator - Java와 동일 패턴
 - 테스트 4개: 멤버십 검증, ClusterListener 2개 MemberUp, Counter 크로스노드, PubSub 크로스노드

 플랫폼간 주요 차이점 (LANG-SPECS-RESULT.md 포함 내용)

 ┌──────────────────────┬─────────────────────────────────────────┬──────────────────────────────────────────┬───────────────────────────────────────────┐
 │         항목         │            Java Akka Classic            │            Kotlin Pekko Typed            │                C# Akka.NET                │
 ├──────────────────────┼─────────────────────────────────────────┼──────────────────────────────────────────┼───────────────────────────────────────────┤
 │ 크로스노드 액터 참조 │ ActorSelection (경로 기반)              │ Receptionist (서비스 키 기반)            │ ActorSelection (경로 기반)                │
 ├──────────────────────┼─────────────────────────────────────────┼──────────────────────────────────────────┼───────────────────────────────────────────┤
 │ 클러스터 이벤트 구독 │ cluster.subscribe(self, MemberUp.class) │ messageAdapter + Cluster.subscriptions() │ cluster.Subscribe(Self, typeof(MemberUp)) │
 ├──────────────────────┼─────────────────────────────────────────┼──────────────────────────────────────────┼───────────────────────────────────────────┤
 │ PubSub 메커니즘      │ DistributedPubSub mediator              │ Topic<T> 내장 API                        │ DistributedPubSub mediator                │
 ├──────────────────────┼─────────────────────────────────────────┼──────────────────────────────────────────┼───────────────────────────────────────────┤
 │ 프로토콜             │ akka:// (Artery)                        │ pekko:// (Artery)                        │ akka.tcp:// (dot-netty)                   │
 ├──────────────────────┼─────────────────────────────────────────┼──────────────────────────────────────────┼───────────────────────────────────────────┤
 │ 테스트 시스템 생성   │ ActorSystem.create()                    │ ActorTestKit.create()                    │ ActorSystem.Create()                      │
 ├──────────────────────┼─────────────────────────────────────────┼──────────────────────────────────────────┼───────────────────────────────────────────┤
 │ 2-시스템 테스트 패턴 │ 2개 ActorSystem + TestKit wrapper       │ 2개 ActorTestKit                         │ IDisposable + 2개 ActorSystem             │
 └──────────────────────┴─────────────────────────────────────────┴──────────────────────────────────────────┴───────────────────────────────────────────┘

 실행 순서

 1. Java: config 파일 → TwoNodeClusterTest.java → ./gradlew clean test
 2. Kotlin: config 파일 → ClusterListenerActor.kt → TwoNodeClusterTest.kt → ./gradlew clean test
 3. C#: TwoNodeClusterTests.cs → dotnet test ClusterActors.sln
 4. TEST-RESULT.md 업데이트
 5. LANG-SPECS-RESULT.md 생성

 검증

 각 플랫폼에서:
 - 기존 1-Node 테스트 통과 확인
 - 새 2-Node 테스트 4개 통과 확인
 - 테스트 실행 로그에서 2개 노드 Up 확인
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
```

### 이활동으로 개선된 스킬

TIP : 클러스터 테스트의 경우 N개의 복합노드 구성 테스트가 필수, 1Node로만 테스트를 하면 네트워크가아닌 인메모리 참조해 객체를 해석할수 있으나, 네트워크인 경우 직렬화를 필수 이해해야함
이 부분은 Akka와 상관없이 네트워크를 이용해 객체를 전달하는 모든 장치 (redis,kafka등)에 모두 해당됨, 이 부분의 기술을 json string을 활용하는경우 신경쓸게 가장적으나
네트워크 오버헤드, 객체저장의 비효율 저장비용/성능문제등이 발생할수있기때문에 최적화를 위해 가장 최적의 네트워크 지원 직렬화(구글 프로토콜버퍼 기반 활용도가능)를 채택해야함

```
 클러스터 스킬 업데이트 계획: 크로스노드 통신 & 멀티노드 테스트 패턴 추가                                                                                                                                          │
│                                                                                                                                                                                                                   │
│ Context                                                                                                                                                                                                           │
│                                                                                                                                                                                                                   │
│ 2-Node 클러스터 테스트 구현 과정에서 기존 스킬에 없는 새로운 패턴들이 발견됨:                                                                                                                                     │
│ - 크로스노드 액터 참조 (ActorSelection / Receptionist 크로스노드 디스커버리)                                                                                                                                      │
│ - 크로스노드 메시지 직렬화 요건 (Java/Kotlin은 Serializable 필수, HOCON 설정 필요)                                                                                                                                │
│ - 멀티노드 HOCON 설정 (seed 노드 vs joining 노드, min-nr-of-members = 2)                                                                                                                                          │
│ - 2-시스템 클러스터 테스트 패턴 (JUnit @BeforeAll, companion object, IClassFixture)                                                                                                                               │
│ - 크로스노드 PubSub 전파 (mediator 직접 발행, Topic 양쪽 노드 생성 등)                                                                                                                                            │
│                                                                                                                                                                                                                   │
│ 기존 스킬은 단일 노드 API 사용법까지만 다루고 있어, 실제 멀티노드 클러스터 운용에 필요한 실전 패턴이 누락되어 있다.                                                                                               │
│                                                                                                                                                                                                                   │
│ 변경 대상 파일 (5개)                                                                                                                                                                                              │
│                                                                                                                                                                                                                   │
│ ┌────────┬──────────────────────────────────────────────────────────────────────┬──────────────────────┐                                                                                                          │
│ │ Action │                                 File                                 │         설명         │                                                                                                          │
│ ├────────┼──────────────────────────────────────────────────────────────────────┼──────────────────────┤                                                                                                          │
│ │ EDIT   │ plugins/skill-actor-model/skills/java-akka-classic-cluster/SKILL.md  │ 섹션 8 추가          │                                                                                                          │
│ ├────────┼──────────────────────────────────────────────────────────────────────┼──────────────────────┤                                                                                                          │
│ │ EDIT   │ plugins/skill-actor-model/skills/kotlin-pekko-typed-cluster/SKILL.md │ 섹션 8 추가          │                                                                                                          │
│ ├────────┼──────────────────────────────────────────────────────────────────────┼──────────────────────┤                                                                                                          │
│ │ EDIT   │ plugins/skill-actor-model/skills/dotnet-akka-net-cluster/SKILL.md    │ 섹션 8 추가          │                                                                                                          │
│ ├────────┼──────────────────────────────────────────────────────────────────────┼──────────────────────┤                                                                                                          │
│ │ EDIT   │ plugins/skill-actor-model/.claude-plugin/plugin.json                 │ 1.9.0 → 1.10.0       │                                                                                                          │
│ ├────────┼──────────────────────────────────────────────────────────────────────┼──────────────────────┤                                                                                                          │
│ │ EDIT   │ .claude-plugin/marketplace.json                                      │ 1.9.0 → 1.10.0 (2곳) │                                                                                                          │
│ └────────┴──────────────────────────────────────────────────────────────────────┴──────────────────────┘                                                                                                          │
│                                                                                                                                                                                                                   │
│ 추가할 내용: 각 스킬에 "섹션 8. 크로스노드 통신 & 멀티노드 테스트" 신설                                                                                                                                           │
│                                                                                                                                                                                                                   │
│ 기존 스킬 구조(1~7번)는 변경하지 않고, 코드 생성 규칙 바로 위에 새 섹션 8을 삽입한다.   
```

## 1.0.1 - 클러스터링 테스트방법 개선지침 by Codex

사전수행: 클코드 스킬기준으로 버전관리되 수행전 코덱스 스킬셋 마이그레이션
다음과 같이해야 클코드 스킬 안망치고 코덱스-로컬 용으로 변환함
```
순서
- 현재 스킬 리스트업
- 다음은 클코드용 스킬셋인데.. 이거 건들지말고 코덱스용으로 안전하게 복사해 : plugins/skill-actor-model

• 복사 완료했습니다. plugins/skill-actor-model 원본은 건드리지 않았고, 코덱스용으로 안전 복사했습니다.                                                                                                                                                                                                                     
  - 소스: plugins/skill-actor-model/skills                                                                                                                                                                           
  - 대상: /home/psmon/.codex/skills                                                                                                                                                                                  
  - 기존 코덱스 스킬 백업: /home/psmon/.codex/skills-backup-20260216-161748                                                                                                                                          
  - 검증: 12개 스킬 모두 SKILL.md SHA256 일치 확인 (OK)                                                                                                                                                                                                                                                                                                                                                                                   
  Codex가 이미 실행 중이면, 새 스킬 반영을 위해 재시작하면 됩니다. 
```

3가지 유형의 동일기능으로 구현된 액터모델 프로젝트를 개선하려고합니다.
수행지침

- AkkaTestToolkit은 블락없이(Sleep)없이 대상 클러스터 완료및 액터의 이벤트를 관찰자관점에서 수신받을수 있는데 이를 활용해 테스트중 Sleep를 통한 대기코드를 가급적 없앨것
- 일반 유닛테스트와 AkkaToolkit으로 유닛테스트를 했을때 의도적인 블락킹이 없는 점이 AkkaTookit을 이용한 유닛테스트의 장점이며 이를 최대한 살릴것, 테스트 수행을위한 관찰자액터가 필요하면 이것을 활용하고 테스트가능한 구조로 만들것
- skill-test/projects/sample1~100 : 넘버링된 형태의 소스는 참고하지 않습니다.
- 각진영의 언어의 구현스펙을 존중하며 목표수행을 위해 타진영에서 구현된 좋은 패턴이 있다고하면 도입하되 일관성확보가 더 좋요합니다.
  - skill-test/projects/sample-cluster-dotnet
  - skill-test/projects/sample-cluster-java
  - skill-test/projects/sample-cluster-kotlin

### Kottlin Pekko Typed
```
kotlin-pekko-typed kotlin-pekko-typed-cluster kotlin-pekko-typed-test 스킬을 활용
다음경로 프로젝트 개선 : skill-test/projects/sample-cluster-kotlin

```

### Java Akka Classic
```
java-akka-classic java-akka-classic-test akka-testkit java-akka-classic-cluster 스킬을 활용해서
다음경로 프로젝트 개선 : skill-test/projects/sample-cluster-java
```

### C# Akka.NET
```
dotnet-akka-net dotnet-akka-net-test dotnet-akka-net-cluster 스킬을 활용해서
다음경로 프로젝트 개선 : skill-test/projects/sample-cluster-dotnet
```

## 개선완료후 지침 (문서및 스킬업데이트)
- skill-test/TEST-RESULT.md : 에 수행결과를 업데이트해주세요
- skill-test/LANG-SPECS-RESULT.md : 수행과정중 발견한 언어스펙의 차이에따른 구현을 비교하는 문서를 생성하고 지속업데이트해주세요
- plugins/skill-actor-model : 이 활동에의해 추가발견된 개념인 경우 기존 스킬셋트과 비교해 없는경우 추가업데이트및 개선하고, 스킬버전업도 함께 진행해주세요
- 이개선 지침은 테스트와 방법과 관련된것이니 테스트관련 스킬을 주로 업데이트할것

## 이 프롬프트의 실행계획

Tip : 클코드는 플래닝모드선택 여부와 상관없이 플래닝을 먼저 세우고 진행할까요? 묻는데... 코덱스는 파악후 바로진행 ( 순정옵션모드일때 )

```
스킬 가이드를 확인했고, 공통 원칙은 TestProbe/within/awaitAssert 중심으로 Sleep 제거입니다. 지금부터 3개 프로젝트 테스트/액터 코드를 읽어, 최소 변경으로 동일한 관찰자 패턴을 적용하겠습니다.

─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

• 핵심 원인 파악이 끝났습니다. 다음으로 Sleep을 이벤트 기반 대기로 바꾸기 위해 각 테스트 파일을 수정하고, 완료 후 결과 문서(TEST-RESULT, LANG-SPECS-RESULT)와 plugins/skill-actor-model의 테스트 스킬 문구를 같은 원 
  칙으로 업데이트하겠습니다.

• Explored
  └ Read LANG-SPECS-RESULT.md, TEST-RESULT.md, SKILL.md
    List skills
    List skills
    Search test|cluster|SKILL.md

─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

• 이제 실제 코드 수정에 들어갑니다. 우선 Java는 TestKit.awaitAssert/within으로 Thread.sleep을 제거하고, Kotlin은 Typed TestKit의 awaitAssert 계열 API 확인 후 같은 방식으로 대기를 이벤트 기반으로 바꾸겠습니다.     

• Explored
  └ Read ClusterActors.Tests.csproj, build.gradle.kts
    Search awaitAssert|awaitCond|within\( in sample-cluster-java
    Search awaitAssert|awaitCond|within\( in sample-cluster-java
    
• 구현 변경 계획을 확정했습니다: Java/Kotlin은 TestKit의 awaitAssert로 대체하고, .NET은 Cluster/Collector 관찰자 액터 + 스케줄러로 Sleep/Task.Delay를 제거합니다. 지금부터 테스트 코드부터 순서대로 패치하겠습니다.                                                                                                                                                                                                                       
• Edited skill-test/projects/sample-cluster-java/src/test/java/cluster/java/ClusterActorTest.java (+41 -24)

↳ Interacted with background terminal · dotnet test skill-test/projects/sample-cluster-dotnet/ClusterActors.sln
  └ (waited)

─ Worked for 1m 51s ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

• Java/Kotlin 테스트는 통과했습니다. .NET 테스트가 현재 장시간 실행 중이라(무출력 대기) 새로 넣은 관찰자 로직에서 대기 누수가 있는지 확인 중입니다.
- Sleep/고정 지연 제거:                                                                                                                                                                                            
    - Java: Thread.sleep 제거, awaitAssert 기반으로 전환                                                                                                                                                           
    - Kotlin: Thread.sleep 제거, TestProbe.awaitAssert 기반으로 전환                                                                                                                                               
    - .NET: Thread.Sleep/Task.Delay 제거, AwaitAssert + 관찰자 액터(MessageCollectorActor) + scheduler 반복 publish 패턴 적용  
                                                                   
```
- 코덱스 최신을 이용해본결과... 플래닝은 다소아쉽지만.. 클코드 대비 이상하게 빠르다(순차인지,멀티테스크인지 구분안되지만...진행속도(토큰고갈)가 빠름 
- 스킬버전을 SKILL.md 자체에 업데이트하는 실수를 해서 , 다음과 같이 plugins/skill-actor-model/.claude-plugin/plugin.json 플러그인 버전업을해달라고 추가수행
  - 모든 코드구현체 주석에 바이너리 어플버전을 주석으로 다 다는형국 


## 1.0.2 - 클러스터링 로컬 쿠버 인프라 구동지침 by Claude Code

3가지 유형의 동일기능으로 구현된 기존 프로젝트를 쿠버기반 로컬에서 구동을 위한 인프라구성 하려고 합니다.
- 동일프로젝트를 두개의 로컬 클러스터로 작동시켜주세요... 작동순서도 유의합니다.
- 첫번째는 Seed노드로 먼저 작동되어야합니다.
- 두번째는 Seed노드에 조인하는 노드로.. Seed노드가 준비되면 작동됩니다.
- 쿠버 cli를 이용할수 있으니 로컬 인프라 구동전에는 쿠버 cli가 잘작동하느 확인후 진행합니다.
- Main Class가 작동후 바로종료이면 작동유지 될수 있도록 헤드리스모드로 작동을 지속모드로 개선해주세요
- 필요한 환경주입은 yml정의시 주입될수 있도록 어플리케이션 환경설정부분과 상호작용하도록합니다.
- 인프라정의 코드는 프로젝트별 하위에 infra라는 폴더를 만들어서 그안에 정의해주세요

## 테스트지침
- 개선후 기존유닛테스트가 잘유지되는지 검증합니다.
- 인프라정의 코드를 통해 작동된 인프라구동이 잘되는지 검증합니다. 인프라 구동이 검증되면 그레이스풀 셧다운으로 종료까지 해주세요

### Kottlin Pekko Typed
```
kotlin-pekko-typed kotlin-pekko-typed-infra kotlin-pekko-typed-cluster kotlin-pekko-typed-test 스킬을 활용
다음경로 프로젝트 개선: skill-test/projects/sample-cluster-kotlin

```

### Java Akka Classic
```
java-akka-classic java-akka-classic-infra java-akka-classic-test akka-testkit java-akka-classic-cluster 스킬을 활용해서
다음경로 프로젝트 개선 : skill-test/projects/sample-cluster-java
```

### C# Akka.NET
```
dotnet-akka-net dotnet-akka-net-infra dotnet-akka-net-test dotnet-akka-net-cluster 스킬을 활용해서
다음경로 프로젝트 개선 : skill-test/projects/sample-cluster-dotnet
```

## 개선완료후 지침 (문서및 스킬업데이트)
- skill-test/TEST-RESULT.md : 에 수행결과를 업데이트해주세요
- skill-test/LANG-SPECS-RESULT.md : 수행과정중 발견한 언어스펙의 차이에따른 구현을 비교하는 문서를 생성하고 지속업데이트해주세요
- plugins/skill-actor-model : 이 활동에의해 추가발견된 개념인 경우 기존 스킬셋트과 비교해 없는경우 추가업데이트및 개선
- 스킬이 개선되어 업데이트된경우  plugins/skill-actor-model/.claude-plugin/plugin.json : 이경로에 버전도 업데이트 
- 이개선 지침은 인프라와 방법과 관련된것이니 인프라관련 스킬을 주로 업데이트할것
- 개선완료후에는 이 프로젝트를 구동하는 방법을 프로젝트별/README.md를 만들어 문서도 업데이트 해주세요


## 이 프롬프트의 실행계획
```
● 종합적인 계획을 수립했습니다. 이제 최종 플랜 파일을 작성하겠습니다.                                                                                                                                                                                
● Updated plan                                                                                                                                                                                                         ⎿  /plan to preview                                   
                                                
─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
 Ready to code?                                                                                                                                                                                                      
                                                                                                                                                                                                                      Here is Claude's plan:                                                                                                                                                                                              
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
 K8s 로컬 클러스터 인프라 구성 계획

 Context

 3개 액터 모델 클러스터 프로젝트(Kotlin Pekko, Java Akka, C# Akka.NET)를 Docker Desktop Kubernetes 환경에서 2노드 클러스터로 구동하기 위한 인프라 구성. 각 프로젝트의 Main 클래스가 즉시 종료되는 문제를 해결하고,   
 StatefulSet 기반 ordered startup으로 Seed→Joining 순서를 보장한다.

 아키텍처 결정

 StatefulSet + Headless Service + seed-nodes 패턴 채택 (Management Bootstrap 불필요)

 - podManagementPolicy: OrderedReady → pod-0(Seed) 먼저 기동, pod-1(Joining) 이후 기동
 - Headless Service → 안정적 DNS: {pod}-{ordinal}.{service}.default.svc.cluster.local
 - imagePullPolicy: Never → 로컬 Docker 이미지 직접 사용
 - HOCON 환경변수 fallback 패턴 → 로컬 개발과 K8s 양립

 ┌──────────────┬─────────────┬─────────────┬──────────────────────┐
 │   프로젝트   │ 리모팅 포트 │  프로토콜   │  HOCON 네임스페이스  │
 ├──────────────┼─────────────┼─────────────┼──────────────────────┤
 │ Kotlin Pekko │ 25520       │ pekko://    │ pekko { }            │
 ├──────────────┼─────────────┼─────────────┼──────────────────────┤
 │ Java Akka    │ 2551        │ akka://     │ akka { }             │
 ├──────────────┼─────────────┼─────────────┼──────────────────────┤
 │ C# Akka.NET  │ 4053        │ akka.tcp:// │ akka { } (dot-netty) │
 └──────────────┴─────────────┴─────────────┴──────────────────────┘

 ---
 Phase 1: Kotlin Pekko Typed

 1-1. Main.kt 개선 (헤드리스 모드)

 파일: skill-test/projects/sample-cluster-kotlin/src/main/kotlin/cluster/kotlin/Main.kt
 - 현재: stub (println만 하고 종료)
 - 개선: ActorSystem 생성 → ClusterListenerActor 스폰 → whenTerminated.toCompletableFuture().join() 블로킹

 1-2. application.conf 환경변수 주입

 파일: skill-test/projects/sample-cluster-kotlin/src/main/resources/application.conf
 - HOCON fallback 패턴: 기본값 → ${?ENV_VAR} 오버라이드
 - coordinated-shutdown.exit-jvm = on 추가
 - allow-java-serialization = on 추가 (직렬화 호환)
 - 로컬 기본값: hostname=127.0.0.1, port=0, seed-nodes=[], min-nr=1

 1-3. infra/ 디렉토리 생성

 새 파일:
 - skill-test/projects/sample-cluster-kotlin/infra/Dockerfile — gradle:8.5-jdk17 멀티스테이지
 - skill-test/projects/sample-cluster-kotlin/infra/k8s-cluster.yaml — Headless Service + StatefulSet(replicas=2)

 1-4. 테스트 영향 없음 확인

 - two-node-seed.conf / two-node-joining.conf → 독립 로드 (ConfigFactory.load("two-node-seed"))
 - ClusterActorTest.kt → 인라인 config가 우선, application.conf fallback 무해

 ---
 Phase 2: Java Akka Classic

 2-1. Main.java 개선 (헤드리스 모드)

 파일: skill-test/projects/sample-cluster-java/src/main/java/cluster/java/Main.java
 - 현재: ActorSystem 생성 후 즉시 종료
 - 개선: ClusterListenerActor 스폰 → system.getWhenTerminated().toCompletableFuture().join() 블로킹

 2-2. application.conf 환경변수 주입

 파일: skill-test/projects/sample-cluster-java/src/main/resources/application.conf
 - Kotlin과 동일 패턴, akka 네임스페이스 사용

 2-3. infra/ 디렉토리 생성

 새 파일:
 - skill-test/projects/sample-cluster-java/infra/Dockerfile — gradle:8.5-jdk17 멀티스테이지
 - skill-test/projects/sample-cluster-java/infra/k8s-cluster.yaml — Headless Service + StatefulSet

 ---
 Phase 3: C# Akka.NET

 3-1. ClusterActors.csproj 수정 (Exe 변환)

 파일: skill-test/projects/sample-cluster-dotnet/ClusterActors/ClusterActors.csproj
 - <OutputType>Exe</OutputType> 추가
 - 테스트 프로젝트는 Exe 참조 가능 (영향 없음)

 3-2. Program.cs 생성 (헤드리스 모드)

 새 파일: skill-test/projects/sample-cluster-dotnet/ClusterActors/Program.cs
 - 코드 레벨 환경변수 오버라이드 (Akka.NET HOCON 파서 호환성 보장)
 - 기본 HOCON 인라인 정의 + Environment.GetEnvironmentVariable() 오버라이드
 - coordinated-shutdown.exit-process = on
 - await system.WhenTerminated 블로킹

 3-3. infra/ 디렉토리 생성

 새 파일:
 - skill-test/projects/sample-cluster-dotnet/infra/Dockerfile — dotnet/sdk:9.0 멀티스테이지
 - skill-test/projects/sample-cluster-dotnet/infra/k8s-cluster.yaml — Headless Service + StatefulSet

 ---
 Phase 4: 검증

 4-1. 유닛테스트 검증

 # Kotlin: sed -i 's/\r$//' gradlew && ./gradlew test
 # Java: sed -i 's/\r$//' gradlew && ./gradlew test
 # C#: dotnet test

 4-2. Docker 이미지 빌드

 # 각 프로젝트에서 docker build -f infra/Dockerfile -t {image-name}:latest .

 4-3. K8s 배포 및 클러스터 형성 확인

 kubectl apply -f infra/k8s-cluster.yaml  # 각 프로젝트
 kubectl get pods -w  # pod-0 Running → pod-1 Running 순서 확인
 kubectl logs {pod-name}  # "Member is Up" 로그 확인 (각 노드에서 2개)

 4-4. 그레이스풀 셧다운

 kubectl delete -f infra/k8s-cluster.yaml  # 각 프로젝트
 # coordinated-shutdown이 클러스터 leave 후 종료 처리

 ---
 Phase 5: 문서 및 스킬 업데이트

 5-1. 결과 문서

 - skill-test/TEST-RESULT.md — 인프라 구동 수행 결과 추가
 - skill-test/LANG-SPECS-RESULT.md — 3개 언어 인프라 구현 차이점 비교 추가

 5-2. 프로젝트별 README.md

 - skill-test/projects/sample-cluster-kotlin/README.md — 빌드/테스트/K8s 배포 방법
 - skill-test/projects/sample-cluster-java/README.md — 동일
 - skill-test/projects/sample-cluster-dotnet/README.md — 동일

 5-3. 인프라 스킬 업데이트 (발견사항 반영)

 - plugins/skill-actor-model/skills/*-infra/SKILL.md — StatefulSet seed-nodes 패턴 보완 (있는 경우)
 - plugins/skill-actor-model/.claude-plugin/plugin.json — 버전 업데이트 (변경 있을 시)

 ---
 파일 변경 요약

 ┌──────────────────────────────────────────────────────────────┬────────┐
 │                             파일                             │  액션  │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-kotlin/src/main/kotlin/cluster/kotlin/Main.kt │ MODIFY │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-kotlin/src/main/resources/application.conf    │ MODIFY │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-kotlin/infra/Dockerfile                       │ CREATE │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-kotlin/infra/k8s-cluster.yaml                 │ CREATE │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-java/src/main/java/cluster/java/Main.java     │ MODIFY │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-java/src/main/resources/application.conf      │ MODIFY │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-java/infra/Dockerfile                         │ CREATE │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-java/infra/k8s-cluster.yaml                   │ CREATE │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-dotnet/ClusterActors/ClusterActors.csproj     │ MODIFY │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-dotnet/ClusterActors/Program.cs               │ CREATE │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-dotnet/infra/Dockerfile                       │ CREATE │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ sample-cluster-dotnet/infra/k8s-cluster.yaml                 │ CREATE │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ 프로젝트별 README.md × 3                                     │ CREATE │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ skill-test/TEST-RESULT.md                                    │ MODIFY │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ skill-test/LANG-SPECS-RESULT.md                              │ MODIFY │
 ├──────────────────────────────────────────────────────────────┼────────┤
 │ 인프라 스킬 SKILL.md (필요 시)                               │ MODIFY │
 └──────────────────────────────────────────────────────────────┴────────┘

 총: 6개 수정, 9개 생성 (코드) + 문서 5~7개

```
- 구현계획을 세우는데 10분정도 걸림 : 인프라까지 이해하고 분산처리시스템을 구동해야하기때문에 가장어렵고 난이도 있는 작업으로 예상 
  - 도커파일 작성이 내포되어 있음 : 도커고생해서 작성안해도됨
  - 쿠버정의가 포함되어 있음 : 작동하는 쿠버 yml작성및 구동
  - 쿠버인프라 테스트가 포함됨 : 분산처리 클러스터를 요구사항대로 작동시키고, 기능을 확인해야함(콘솔로깅)
  - 쿠버 Graceful shutdown이 포함됨 : 쿠버에서 삭제명령후 클러스터가 정상적으로 종료되는지 확인해야함

## 추가지침
- 쿠버에서 구동후 클러스터 조인이 잘 되는지 확인가능? 각각 로깅을 파악해 클러스터조인이 잘되는지 로깅을 통해 확인까지할것
- 클러스터 조인이 안되면 원인파악해 수정 ... 3가지 프로젝트 모두 진행..
- 그리고 실테스트 결과를 skill-test/TEST-RESULT.md 업데이트할것 (유닛테스트와 별개로 로컬인프라 구동 테스트 항목부분을 추가할것)


## 주가지침 수행
-쿠버 로컬인프라를 3대 동시에 빌드하고 구동해 버림 : LLM파워도 중요한데.. 개발컴퓨터 성능도 좋아야할듯
```                                                                                                                                                                                                                     
● 3개 빌드가 병렬 실행 중입니다. 결과를 기다립니다.                                                                                                                                                                                                                                                                                                                                                                                       
  Task Output bb29871                                                                                                                                                                                                
  Build Kotlin Pekko Docker image                                                                                                                                                                                    
     Waiting for task (esc to give additional instructions)                                                                                                                                                          
                                                                                                                                                                                                                     
✢ Building Kotlin Pekko Docker image… (1m 28s · ↓ 851 tokens)                                                                                                                                                          ⎿  ◼ Build and deploy Kotlin Pekko to K8s                                                                                                                                                                          
     ◻ Build and deploy Java Akka to K8s                                                                                                                                                                                  ◻ Build and deploy C# Akka.NET to K8s                                                                                                                                                                           
     ◻ Update TEST-RESULT.md with K8s results        
```
```
● DNS는 지금 해석됩니다. 아마 pod-1이 시작되기 전에 DNS가 없어서 실패했고, 이제는 해석되고 있습니다. 좀 더 기다리면 클러스터가 형성될 수 있습니다. 최신 로그를 확인합니다.
● Bash(kubectl logs pekko-cluster-0 2>&1 | grep -E "(Member is Up|Leader is moving)" | head -5 && echo "---" && kubectl logs pekko-cluster-1 2>&1 | grep -E "(Member is…)
  ⎿  12:13:15.454 [ClusterSystem-pekko.actor.default-dispatcher-21] INFO  org.apache.pekko.cluster.Cluster - Cluster Node [pekko://ClusterSystem@pekko-cluster-0.pekko-cluster.default.svc.cluster.local:25520]      
     - Leader is moving node [pekko://ClusterSystem@pekko-cluster-0.pekko-cluster.default.svc.cluster.local:25520] to [Up]
     12:13:15.454 [ClusterSystem-pekko.actor.default-dispatcher-21] INFO  org.apache.pekko.cluster.Cluster - Cluster Node [pekko://ClusterSystem@pekko-cluster-0.pekko-cluster.default.svc.cluster.local:25520]      
     … +10 lines (ctrl+o to expand)
```
- 쿠버에 DNS까지 고급 인프라기술을 로컬에서 다루기 시작함 , 코드개선-빌드-실행-클러스터 확인 완수할 때까지 무한뺑뺑이
- 비교적 긴수행의 작업이 진행된경우 다음에 시간절약을 위해 스킬을 재업데이트

최종 테스트 완료후: 스킬업데이트
- 방금 개선한 부분은 중요한 부분임...방금 진행된 코드개선및 인프라개선부분을 참고해 스킬업데이트
- plugins/skill-actor-model : 연관된 파트부분을 업데이트할것

```                                                                                                                                                                                                                   │
│ Context                                                                                                                                                                                                           │
│                                                                                                                                                                                                                   │
│ K8s 로컬 클러스터 배포 테스트 중 2가지 치명적 문제를 발견하고 해결했다:                                                                                                                                           │
│                                                                                                                                                                                                                   │
│ 1. HOCON ${?ENV_VAR} fallback은 리스트 타입(seed-nodes)에서 실패 → 코드 레벨 ConfigFactory.parseString(overrides) 패턴으로 전환                                                                                   │
│ 2. status.podIP를 canonical hostname으로 사용하면 seed-nodes DNS 주소와 불일치 → StatefulSet DNS 이름($(POD_NAME).{service}.default.svc.cluster.local)으로 전환                                                   │
│                                                                                                                                                                                                                   │
│ 현재 인프라 스킬(Type C 섹션)은 이 2가지 문제가 그대로 남아있어, 스킬로 생성한 코드가 K8s에서 동작하지 않는다. 실제 검증된 패턴으로 업데이트해야 한다.                                                            │
│                                                                                                                                                                                                                   │
│ ---                                                                                                                                                                                                               │
│ 변경 대상 파일 (8개)                                                                                                                                                                                              │
│                                                                                                                                                                                                                   │
│ 인프라 스킬 3개 (Type C 섹션 수정)                                                                                                                                                                                │
│                                                                                                                                                                                                                   │
│ 1. plugins/skill-actor-model/skills/kotlin-pekko-typed-infra/SKILL.md                                                                                                                                             │
│ 2. plugins/skill-actor-model/skills/java-akka-classic-infra/SKILL.md                                                                                                                                              │
│ 3. plugins/skill-actor-model/skills/dotnet-akka-net-infra/SKILL.md                                                                                                                                                │
│                                                                                                                                                                                                                   │
│ 인프라 참조문서 3개 (동기화)                                                                                                                                                                                      │
│                                                                                                                                                                                                                   │
│ 4. skill-maker/docs/actor/infra/infra-kotlin-pekko-typed.md                                                                                                                                                       │
│ 5. skill-maker/docs/actor/infra/infra-java-akka-classic.md                                                                                                                                                        │
│ 6. skill-maker/docs/actor/infra/infra-dotnet-akka-net.md                                                                                                                                                          │
│                                                                                                                                                                                                                   │
│ 버전 파일 2개                                                                                                                                                                                                     │
│                                                                                                                                                                                                                   │
│ 7. plugins/skill-actor-model/.claude-plugin/plugin.json — 1.13.0 → 1.14.0                                                                                                                                         │
│ 8. .claude-plugin/marketplace.json — 1.13.0 → 1.14.0 (3곳)                                                                                                                                                        │
│                                                                                                                                                                                                                   │
│ ---                                                                                                                                                                                                               │
│ 변경 내용 상세                                                                                                                                                                                                    │
│                                                                                                                                                                                                                   │
│ 1. Kotlin Pekko Typed Infra — Type C 수정                                                                                                                                                                         │
│                                                                                                                                                                                                                   │
│ 문제 1: HOCON ${?ENV_VAR} → 코드 레벨 오버라이드                                                                                                                                                                  │
│                                                                                                                                                                                                                   │
│ 현재 (잘못됨):                                                                                                                                                                                                    │
│ seed-nodes = []                                                                                                                                                                                                   │
│ seed-nodes = ${?CLUSTER_SEED_NODES}  # ← 문자열로 파싱되어 타입 오류                                                                                                                                              │
│                                                                                                                                                                                                                   │
│ 수정:                                                                                                                                                                                                             │
│ - application.conf에서 ${?ENV_VAR} 제거, 순수 기본값만 유지                                                                                                                                                       │
│ - Main 클래스에서 ConfigFactory.parseString(overrides).withFallback(ConfigFactory.load()) 패턴 적용                                                                                                               │
│                                                                                                                                                                                                                   │
│ pekko {                                                                                                                                                                                                           │
│   actor { provider = "cluster"; allow-java-serialization = on }                                                                                                                                                   │
│   remote.artery { canonical.hostname = "127.0.0.1"; canonical.port = 0 }                                                                                                                                          │
│   cluster {                                                                                                                                                                                                       │
│     seed-nodes = []                                                                                                                                                                                               │
│     min-nr-of-members = 1                                                                                                                                                                                         │
│     downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"                                                                                                                            │
│   }                                                                                                                                                                                                               │
│   coordinated-shutdown.exit-jvm = on                                                                                                                                                                              │
│ }                                                                                                                                                                                                                 │
│                                                                                                                                                                                                                   │
│ fun main() {                                                                                                                                                                                                      │
│     val overrides = buildString {                                                                                                                                                                                 │
│         System.getenv("CLUSTER_HOSTNAME")?.let {                                                                                                                                                                  │
│             appendLine("""pekko.remote.artery.canonical.hostname = "$it"""")                                                                                                                                      │
│         }                                                                                                                                                                                                         │
│         System.getenv("CLUSTER_PORT")?.let {                                                                                                                                                                      │
│             appendLine("pekko.remote.artery.canonical.port = $it")                                                                                                                                                │
│         }                                                                                                                                                                                                         │
│         System.getenv("CLUSTER_SEED_NODES")?.let {                                                                                                                                                                │
│             appendLine("pekko.cluster.seed-nodes = $it")                                                                                                                                                          │
│         }                                                                                                                                                                                                         │
│         System.getenv("CLUSTER_MIN_NR")?.let {                                                                                                                                                                    │
│             appendLine("pekko.cluster.min-nr-of-members = $it")                                                                                                                                                   │
│         }                                                                                                                                                                                                         │
│     }                                                                                                                                                                                                             │
│     val config = ConfigFactory.parseString(overrides)                                                                                                                                                             │
│         .withFallback(ConfigFactory.load())                                                                                                                                                                       │
│                                                                                                                                                                                                                   │
│     val system = ActorSystem.create(                                                                                                                                                                              │
│         Behaviors.setup<Nothing> { ctx ->                                                                                                                                                                         │
│             ctx.spawn(MyClusterListenerActor.create(...), "clusterListener")                                                                                                                                      │
│             Behaviors.empty()                                                                                                                                                                                     │
│         },                                                                                                                                                                                                        │
│         "ClusterSystem",                                                                                                                                                                                          │
│         config                                                                                                                                                                                                    │
│     )                                                                                                                                                                                                             │
│     system.whenTerminated.toCompletableFuture().join()                                                                                                                                                            │
│ }                                                                                                                                                                                                                 │
│                                                                                                                                                                                                                   │
│ 문제 2: status.podIP → StatefulSet DNS                                                                                                                                                                            │
│                                                                                                                                                                                                                   │
│ 현재 (잘못됨):                                                                                                                                                                                                    │
│ - name: CLUSTER_HOSTNAME                                                                                                                                                                                          │
│   valueFrom:                                                                                                                                                                                                      │
│     fieldRef:                                                                                                                                                                                                     │
│       fieldPath: status.podIP  # ← seed-nodes 주소와 불일치                                                                                                                                                       │
│                                                                                                                                                                                                                   │
│ 수정:                                                                                                                                                                                                             │
│ - name: POD_NAME                                                                                                                                                                                                  │
│   valueFrom:                                                                                                                                                                                                      │
│     fieldRef:                                                                                                                                                                                                     │
│       fieldPath: metadata.name                                                                                                                                                                                    │
│ - name: CLUSTER_HOSTNAME                                                                                                                                                                                          │
│   value: "$(POD_NAME).pekko-cluster.default.svc.cluster.local"                                                                                                                                                    │
│                                                                                                                                                                                                                   │
│ 추가: 트러블슈팅 주의사항                                                                                                                                                                                         │
│                                                                                                                                                                                                                   │
│ Type C 특징 섹션에 주의사항 추가:                                                                                                                                                                                 │
│ - HOCON ${?ENV_VAR}는 문자열/숫자에만 동작, 리스트(seed-nodes)에는 코드 레벨 오버라이드 필수                                                                                                                      │
│ - canonical hostname은 반드시 seed-nodes 주소 형식과 일치해야 함 (DNS이름 vs Pod IP 혼용 불가)                                                                                                                    │
│                                                                                                                                                                                                                   │
│ 2. Java Akka Classic Infra — Type C 수정                                                                                                                                                                          │
│                                                                                                                                                                                                                   │
│ Kotlin과 동일한 2가지 문제 수정:                                                                                                                                                                                  │
│ - HOCON 에서 ${?ENV_VAR} 제거 → Main.java에서 ConfigFactory.parseString(overrides) 패턴                                                                                                                           │
│ - k8s-cluster.yaml에서 status.podIP → $(POD_NAME).akka-cluster.default.svc.cluster.local                                                                                                                          │
│                                                                                                                                                                                                                   │
│ public static void main(String[] args) {                                                                                                                                                                          │
│     StringBuilder overrides = new StringBuilder();                                                                                                                                                                │
│     String hostname = System.getenv("CLUSTER_HOSTNAME");                                                                                                                                                          │
│     if (hostname != null) overrides.append("akka.remote.artery.canonical.hostname = \"")                                                                                                                          │
│         .append(hostname).append("\"\n");                                                                                                                                                                         │
│     String port = System.getenv("CLUSTER_PORT");                                                                                                                                                                  │
│     if (port != null) overrides.append("akka.remote.artery.canonical.port = ")                                                                                                                                    │
│         .append(port).append("\n");                                                                                                                                                                               │
│     String seedNodes = System.getenv("CLUSTER_SEED_NODES");                                                                                                                                                       │
│     if (seedNodes != null) overrides.append("akka.cluster.seed-nodes = ")                                                                                                                                         │
│         .append(seedNodes).append("\n");                                                                                                                                                                          │
│     String minNr = System.getenv("CLUSTER_MIN_NR");                                                                                                                                                               │
│     if (minNr != null) overrides.append("akka.cluster.min-nr-of-members = ")                                                                                                                                      │
│         .append(minNr).append("\n");                                                                                                                                                                              │
│                                                                                                                                                                                                                   │
│     Config config = ConfigFactory.parseString(overrides.toString())                                                                                                                                               │
│         .withFallback(ConfigFactory.load());                                                                                                                                                                      │
│     ActorSystem system = ActorSystem.create("ClusterSystem", config);                                                                                                                                             │
│     system.actorOf(ClusterListenerActor.props(system.deadLetters()), "clusterListener");                                                                                                                          │
│     system.getWhenTerminated().toCompletableFuture().join();                                                                                                                                                      │
│ }                                                                                                                                                                                                                 │
│                                                                                                                                                                                                                   │
│ 3. C# Akka.NET Infra — Type C 수정                                                                                                                                                                                │
│                                                                                                                                                                                                                   │
│ C#은 이미 코드 레벨 ConfigurationFactory.ParseString()을 사용하므로 문제 1은 해당 없음.                                                                                                                           │
│ 문제 2만 수정: k8s-cluster.yaml에서 status.podIP → $(POD_NAME).akkanet-cluster.default.svc.cluster.local                                                                                                          │
│                                                                                                                                                                                                                   │
│ 추가: HOCON 환경변수 차이 설명 노트를 개선 — "Akka.NET만의 제한"이 아니라 "JVM에서도 리스트 타입에는 동일한 문제 발생"으로 정정                                                                                   │
│                                                                                                                                                                                                                   │
│ 4. 인프라 참조문서 동기화                                                                                                                                                                                         │
│                                                                                                                                                                                                                   │
│ skill-maker/docs/actor/infra/ 하위 3개 문서에 Type C 섹션이 있다면 스킬과 동일하게 수정.                                                                                                                          │
│ 없다면 변경 불필요.                                                                                                                                                                                               │
│                                                                                                                                                                                                                   │
│ 5. 버전 범프                                                                                                                                                                                                      │
│                                                                                                                                                                                                                   │
│ - plugins/skill-actor-model/.claude-plugin/plugin.json: "version": "1.13.0" → "1.14.0"                                                                                                                            │
│ - .claude-plugin/marketplace.json: 3곳 모두 "1.13.0" → "1.14.0"                                                                                                                                                   │
│                                                                                                                                                                                                                   │
│ ---                                                                                                                                                                                                               │
│ 변경하지 않는 것                                                                                                                                                                                                  │
│                                                                                                                                                                                                                   │
│ - Type A/B 섹션: Bootstrap 기반 디스커버리는 seed-nodes를 사용하지 않으므로 영향 없음                                                                                                                             │
│ - 클러스터 스킬 (6종): 코드 패턴만 다루고 인프라 배포는 다루지 않으므로 변경 불필요                                                                                                                               │
│ - 기본 액터 스킬 (6종): 관련 없음                                                                                                                                                                                 │
│ - 코드 생성 규칙: 기존 14개 규칙에 변경 없음                                                                                                                                                                      │
│                                                                                                                                                                                                                   │
│ ---                                                                                                                                                                                                               │
│ 검증                                                                                                                                                                                                              │
│                                                                                                                                                                                                                   │
│ - 수정된 스킬의 Type C 코드가 실제 배포 테스트에서 검증된 코드와 일치하는지 확인                                                                                                                                  │
│   - Kotlin: skill-test/projects/sample-cluster-kotlin/src/main/kotlin/cluster/kotlin/Main.kt                                                                                                                      │
│   - Java: skill-test/projects/sample-cluster-java/src/main/java/cluster/java/Main.java                                                                                                                            │
│   - C#: skill-test/projects/sample-cluster-dotnet/ClusterActors/Program.cs                                                                                                                                        │
│   - K8s YAML: 각 프로젝트의 infra/k8s-cluster.yaml                                                                                                                                                                │
╰───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╯
 인프라 스킬 3개 — Type C 섹션 수정                                                                                                                                                                                                                                                                                                                                                                                                      
  1. Kotlin Pekko Typed Infra (kotlin-pekko-typed-infra/SKILL.md)
  - HOCON에서 ${?ENV_VAR} fallback 제거 → 순수 기본값만 유지
  - Kotlin Main을 ConfigFactory.parseString(overrides).withFallback(ConfigFactory.load()) 패턴으로 전환
  - k8s-cluster.yaml: status.podIP → $(POD_NAME).pekko-cluster.default.svc.cluster.local (StatefulSet DNS)
  - 트러블슈팅 주의사항 2개 추가 (리스트 타입 HOCON fallback 실패, hostname/seed-nodes 불일치)

  2. Java Akka Classic Infra (java-akka-classic-infra/SKILL.md)
  - 동일한 2가지 문제 수정 (HOCON → 코드 레벨 오버라이드, podIP → StatefulSet DNS)
  - Java Main을 StringBuilder + ConfigFactory.parseString() 패턴으로 전환
  - 동일한 트러블슈팅 주의사항 추가

  3. C# Akka.NET Infra (dotnet-akka-net-infra/SKILL.md)
  - k8s-cluster.yaml: status.podIP → $(POD_NAME).akkanet-cluster.default.svc.cluster.local
  - HOCON 환경변수 차이 설명을 "Akka.NET만의 제한" → "JVM/C# 공통 문제"로 정정
  - 동일한 트러블슈팅 주의사항 추가

```
- 분선처리를 어플리케이션 다룰려면 쿠버도 함께 학습해야할 필요성을 느끼며 스킬업그레이드 종료
