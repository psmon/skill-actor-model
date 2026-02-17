# skill-actor-model

Claude Code 스킬 기반으로 **액터 모델(Actor Model)을 다양한 언어에서 학습**할 수 있는 스킬셋입니다.

동일한 동시성 패턴(Tell, Ask, Router, Supervision, Timer, Batch, Stream, Cluster, Infrastructure 등)이 Java, Kotlin, C# 세 플랫폼에서 어떻게 구현되는지 비교하며 학습할 수 있도록 설계되었습니다.

## 스킬 목록

| 스킬 | 명령어 | 플랫폼 |
|------|--------|--------|
| Java Akka Classic | `/java-akka-classic` | Java + Akka Classic 2.7.x |
| Kotlin Pekko Typed | `/kotlin-pekko-typed` | Kotlin + Pekko Typed 1.4.x |
| C# Akka.NET | `/dotnet-akka-net` | C# + Akka.NET 1.5.x |
| Java Akka Classic Test | `/java-akka-classic-test` | Java + Akka Classic TestKit |
| Kotlin Pekko Typed Test | `/kotlin-pekko-typed-test` | Kotlin + Pekko Typed ActorTestKit |
| C# Akka.NET Test | `/dotnet-akka-net-test` | C# + Akka.TestKit.Xunit2 |
| Java Akka Classic Cluster | `/java-akka-classic-cluster` | Java + Akka Classic Cluster 2.7.x |
| Kotlin Pekko Typed Cluster | `/kotlin-pekko-typed-cluster` | Kotlin + Pekko Typed Cluster 1.4.x |
| C# Akka.NET Cluster | `/dotnet-akka-net-cluster` | C# + Akka.NET Cluster 1.5.x |
| Java Akka Classic Infra | `/java-akka-classic-infra` | Java + Akka Classic + Docker/K8s |
| Kotlin Pekko Typed Infra | `/kotlin-pekko-typed-infra` | Kotlin + Pekko Typed + Docker/K8s |
| C# Akka.NET Infra | `/dotnet-akka-net-infra` | C# + Akka.NET + Docker/K8s |
| AI Agent Pipeline (.NET) | `/actor-ai-agent` | C# + Akka.NET + LLM |
| AI Agent Pipeline (Java) | `/actor-ai-agent-java` | Java + Akka Classic + LLM |
| AI Agent Pipeline (Kotlin) | `/actor-ai-agent-kotlin` | Kotlin + Pekko Typed + LLM |


### 버전채택 
- Akka 2.7을 채택한 이유는 Akka의 본방이 BSD로전환후 기간이 지난 버전(약2~3년)  오픈소스로 점진전환 , 구현시점 2.7의 전환시점이 곧 다가와 채택 
- Open으로 전환한다는 의미가 사용은 가능하나 그동안 발생한 보안취약점 패치등을 지원한다는 내용은아니며, Pekko를 이용해야 보안패치를 등을 Fork이후 추가지원하기때문에 오픈소스 사용인경우 Pekko채택 권장
- Pekko 의 경우 2.6x 기반 1.1.0에서 어느정도 안정 버전을 갖추어 채택 - 
- 오픈소스는 버전체크 부지런해야 활용가능한것같으며 스킬에 이용되는 기반버전도 함께 올릴예정

| Akka  | Pekko  | Akka.NET |
|-------|--------|----------|
| 2.5.x | -      | 1.5.x    |
| 2.6.x | 1.0.x~ | -        |
| 2.7.x | 1.4.x~ | -        |

#### TypedActor의 특징
- Typed Actor는 2.6에 출시 Pekko-Kotlin 버전으로 다루는중 - 이벤트 발신/수신자 약손된 Type이 안맞는경우 컴파일타임에 오류를냄, 클래식에 익숙하다고 하면 Typed준수하느라 약간의 스트레스 알고보니 Behavior패턴의 일종으로 이 패턴에 익숙해야 강력해짐
- Classic Actor(Untyped Actor) 이용시에는 이벤트 전송 Type이 자유로운 장점이 있지만 단점이 될수 있기때문에 설계시 메시지 프로토콜을 더 명확히 정의하는것이 중요
- Akka.net 은 Untyped Actor가 주류, Typed Actor는 미지원인것으로 파악됨

링크 : https://pekko.apache.org/docs/pekko/current/release-notes/releases-1.3.html

#### 액터프레임워크 정보

아파치 Flink에서(Spark보다 조금더 대세 스트리밍 툴) Akka를 Pekko로전환, AWS에도 주력으로 밀고있는 클라우드 제품이여서 오픈소스인 Pekko채택및 전환
- https://docs.aws.amazon.com/ko_kr/managed-flink/latest/java/flink-1-18.html : AWS의 Pekko 전환 주요뉴스
- https://issues.apache.org/jira/browse/FLINK-32468 : 아파치 Fling에서 Akka에서 Pekko로 전환
- https://akka.io/technical-support-for-apache-pekko  : Akka본방에서 Pekko지원을 약속

닷넷진영은 Akka의 본방을 계승 포팅을해 오픈소스로 성공적 활동중
- https://petabridge.com/bootcamp/ : akka.net 을 딜리버리및 지원하는 Petabridge의 공식홈
- https://petabridge.com/blog/  : 개인적으로 이벤트드리븐,분산처리 닷넷관련 고급 기술테크들을 빠르게 다루는듯 -MsLearn보다 여기서 닷넷구현 패턴들을 주로 학습 
- https://learn.microsoft.com/en-us/dotnet/orleans/overview?pivots=orleans-10-0 - MS에도 액터시스템 본방이 있지만, MS플랫폼내에서만 주로사용되는듯(게임포함 Azure 클라우드 엣지전반적으로 액터시스템사용)


## 설치 방법

### 방법 1: 플러그인 마켓플레이스로 설치 (권장)

Claude Code에서 아래 명령어를 순서대로 실행합니다.

```
# 1. 마켓플레이스 등록
/plugin marketplace add psmon/skill-actor-model

# 2. 플러그인 설치
/plugin install skill-actor-model@actor-model-skills

- Browser Plugin 선택
- Install : skill-actor-model - 선택
   Install for you (user scope)
   Install for all collaborators on this repository (project scope)
 > Install for you, in this repo only (local scope)

# 3. 플러그인 업데이트및 제거
/plugin
- 좌우키 ( Installed, Marketplace ) 탭이용
```

- .claude/settings.json : 인스톨 인식잘 안되는경우 다음 설정 true한후 클코드 다시시작
```
{
  "enabledPlugins": {
    "skill-actor-model@actor-model-skills": true
  }
}
```


설치 후 `/java-akka-classic`, `/kotlin-pekko-typed`, `/dotnet-akka-net`, `/java-akka-classic-test`, `/kotlin-pekko-typed-test`, `/dotnet-akka-net-test`, `/java-akka-classic-cluster`, `/kotlin-pekko-typed-cluster`, `/dotnet-akka-net-cluster`, `/java-akka-classic-infra`, `/kotlin-pekko-typed-infra`, `/dotnet-akka-net-infra`, `/actor-ai-agent`, `/actor-ai-agent-java`, `/actor-ai-agent-kotlin` 명령어를 사용할 수 있습니다.

> 플러그인을 최신 버전으로 업데이트하려면: `/plugin marketplace update`

### 방법 2: Git 저장소 클론 (로컬 개발용)

저장소를 클론하면 `.claude/skills/` 디렉토리의 스킬이 자동으로 인식됩니다.

```bash
git clone https://github.com/psmon/skill-actor-model.git
cd skill-actor-model
claude   # 스킬이 자동 로드됩니다
```

## 사용 방법

Claude Code에서 슬래시 명령어로 원하는 플랫폼의 액터 패턴 코드를 생성합니다.

```
/java-akka-classic RoundRobinPool 라우터로 5개 워커에 메시지 분배
/kotlin-pekko-typed sealed class 기반 상태 전환 액터 구현
/dotnet-akka-net FSM 배치 처리 액터, 1초 타임아웃 자동 플러시
/java-akka-classic-test akka-testkit 기반 Hello->World 유닛테스트 작성
/kotlin-pekko-typed-test ActorTestKit + TestProbe 기반 Typed 테스트 작성
/dotnet-akka-net-test Akka.TestKit.Xunit2 기반 분리 테스트 프로젝트 생성
/java-akka-classic-cluster Singleton 카운터 액터 + PubSub로 상태 브로드캐스트
/kotlin-pekko-typed-cluster Sharding 디바이스 엔티티 + 패시베이션 2분 설정
/dotnet-akka-net-cluster 클러스터 설정 + Singleton + Sharding 통합 예제
/java-akka-classic-infra docker-compose 3노드 클러스터 서비스 디스커버리 구성
/kotlin-pekko-typed-infra kubernetes 3-replica Deployment + RBAC + Headless Service 생성
/dotnet-akka-net-infra kubernetes Akka.Hosting 방식 K8s Discovery + lease-majority SBR 구성
/actor-ai-agent RAG 기반 질의분석 -> 검색 -> 평가 -> 응답 파이프라인 설계
/actor-ai-agent-java Java Akka 기반 질의분석 -> 검색 -> 평가 -> 응답 파이프라인 설계
/actor-ai-agent-kotlin Kotlin Pekko 기반 질의분석 -> 검색 -> 평가 -> 응답 파이프라인 설계
```

플러그인으로 설치한 경우 네임스페이스가 붙습니다.

```
/skill-actor-model:java-akka-classic RoundRobinPool 라우터
/skill-actor-model:dotnet-akka-net ReceiveActor 기본 패턴
/skill-actor-model:java-akka-classic-test TestKit 유닛테스트
/skill-actor-model:kotlin-pekko-typed-test ActorTestKit 유닛테스트
/skill-actor-model:dotnet-akka-net-test Akka.TestKit.Xunit2 유닛테스트
/skill-actor-model:java-akka-classic-cluster Cluster Singleton + Sharding
/skill-actor-model:kotlin-pekko-typed-cluster Cluster PubSub + ServiceKey
/skill-actor-model:dotnet-akka-net-cluster Cluster Membership + SBR 설정
/skill-actor-model:java-akka-classic-infra Docker Compose Config Discovery
/skill-actor-model:kotlin-pekko-typed-infra Kubernetes API Discovery + Bootstrap
/skill-actor-model:dotnet-akka-net-infra Kubernetes Akka.Hosting Discovery
```

자연어로 질문해도 관련 스킬이 자동 활성화됩니다.

## 프로젝트 구조

```
skill-actor-model/
├── .claude-plugin/
│   └── marketplace.json               # 플러그인 마켓플레이스 카탈로그
├── plugins/skill-actor-model/         # 플러그인 배포용
│   ├── .claude-plugin/plugin.json
│   └── skills/                        # 배포되는 스킬
│       ├── java-akka-classic/SKILL.md
│       ├── kotlin-pekko-typed/SKILL.md
│       ├── dotnet-akka-net/SKILL.md
│       ├── java-akka-classic-test/SKILL.md
│       ├── kotlin-pekko-typed-test/SKILL.md
│       ├── dotnet-akka-net-test/SKILL.md
│       ├── java-akka-classic-cluster/SKILL.md
│       ├── kotlin-pekko-typed-cluster/SKILL.md
│       ├── dotnet-akka-net-cluster/SKILL.md
│       ├── java-akka-classic-infra/SKILL.md
│       ├── kotlin-pekko-typed-infra/SKILL.md
│       ├── dotnet-akka-net-infra/SKILL.md
│       ├── actor-ai-agent/SKILL.md
│       ├── actor-ai-agent-java/SKILL.md
│       └── actor-ai-agent-kotlin/SKILL.md
├── .claude/skills/                    # 로컬 개발용 스킬 (동일 내용)
├── skill-maker/
│   ├── docs/actor/                    # 플랫폼별 액터 패턴 참조 문서
│   │   ├── 00-actor-model-overview.md
│   │   ├── 01-java-akka-classic/
│   │   ├── 02-kotlin-pekko-typed/
│   │   ├── 03-dotnet-akka-net/
│   │   ├── 04-memorizer-ai-agent/
│   │   ├── 05-cross-platform-comparison.md
│   │   └── infra/                     # 인프라 디스커버리 참조 문서
│   └── guides/
│       └── actor-skills.md            # 스킬 활용 베스트 프랙티스
└── README.md
```

## 다루는 패턴

| 패턴 | Java | Kotlin | C# | AI Agent |
|------|:----:|:------:|:--:|:--------:|
| Tell / Ask / Forward | O | O | O | - |
| Router | O | O | O | - |
| Supervision | O | O | O | O |
| Timer | O | O | O | - |
| FSM / Batch | O | O | O | - |
| Persistence | - | O | O | - |
| Streams / Throttle | O | O | O | - |
| Cluster Membership | O | O | O | - |
| Cluster Singleton | O | O | O | - |
| Cluster Sharding | O | O | O | - |
| Distributed PubSub | O | O | O | - |
| Split Brain Resolver | O | O | O | - |
| Docker Compose Infra | O | O | O | - |
| Kubernetes Infra | O | O | O | - |
| SSE / WebSocket | - | O | O | O |
| AI Pipeline | - | - | - | O |

## 스킬 테스트

스킬이 코드를 올바르게 생성하고 실행하는지 검증하기 위한 셀프 테스트 프롬프트를 제공합니다. 

액터모델 작성경험이 없어도, 각 플랫폼(Java, Kotlin, C#)별로 다양한 액터모델 고급패턴을 스킬이용 구현할수 있습니다. - 지속업데이트

-> [스킬 테스트 프롬프트 바로가기](skill-test/TEST.md)

## 참고 프로젝트

이 스킬셋의 참조 문서는 아래 실제 프로젝트의 소스코드에서 추출되었습니다. - AI 없던시절 한땀 연구하고 작성했던 코드들(이제는 바이브 모드기반으로 활동 전환)

| 프로젝트 | 플랫폼 | 링크 |
|---------|--------|------|
| springweb | Java + Akka Classic | [java-labs/springweb](https://github.com/psmon/java-labs/tree/master/springweb) |
| KotlinBootLabs | Kotlin + Akka Typed | [java-labs/KotlinBootLabs](https://github.com/psmon/java-labs/tree/master/KotlinBootLabs) |
| KotlinBootReactiveLabs | Kotlin + Pekko Typed | [kopring-reactive-labs](https://github.com/psmon/kopring-reactive-labs/tree/main/KotlinBootReactiveLabs) |
| NetCoreLabs | C# + Akka.NET | [NetCoreLabs](https://github.com/psmon/NetCoreLabs) |
| memorizer-v1 | C# + Akka.NET + AI | [memorizer-v1](https://github.com/psmon/memorizer-v1) |

## 웹 애플리케이션 샘플 (Part2)

`skill-test/projects` 하위 3개 샘플은 콘솔 모드에서 웹 API 모드로 확장되었습니다.
이 활동으로 인해 스킬도 다음기준 최신언어와 플랫폼버전을 고려해 코드생성을 각각 지원합니다.

- `sample-cluster-dotnet`: ASP.NET Core(.NET 10) + Akka.NET
- `sample-cluster-java`: Spring Boot 3.5.x MVC + Java 21 + Akka Classic
- `sample-cluster-kotlin`: Spring Boot 3.5.x WebFlux + Kotlin Coroutine + Pekko Typed

공통 API:
- `GET /api/heath`
- `GET /api/actor/hello`
- `GET /api/cluster/info`
- `POST /api/kafka/fire-event`

결과 문서:
- `skill-test/TEST-RESULT-WebApplication.md`
- `skill-test/TEST-RESULT-PART2.md`
- `skill-test/TEST-RESULT-PART2-Improve.md`
