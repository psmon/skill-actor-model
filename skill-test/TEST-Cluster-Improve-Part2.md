# 스킬활용 프롬프트 테스트 - 클러스터편

기본코드작성-클러스터능력-테스트수행-인프라구성 스킬 4종셋트가 갖추어지면서 구현및 작동검증까지 복합능력이 가능해졌으며
다양한 변종실험이 진행되는곳

## Codex 스킬업데이트 from 클코드 스킬
```
plugins/skill-actor-model/skills 클로드 코드 원본 스킬이며 너는 codex임... codex기준 스킬셋(~/.codex/skills) 과 일치하는지 체크
일치하지않으면 클코드 기준 파일로 codex skill 파일구성 규칙을 확인후 복제해죠, 스킬의 파일이 완전하게 동일해야함
- 업데이트후 codex 재시작 필요
```


## 1.0.3 - kafka 연동
- https://petabridge.com/blog/akka-streams-kafka-best-kafka-client-dotnet/ 페이지를 통해 카프카 베스트 프랙티스의 기능을 파악해 3종류 프로젝트 동일 개선
- 기능추가는 이 페이지를 통해 너가 아이디어를 채택할것 핵심은 kafka를 akka-stream과 연동하는것이 주요핵심임
- skill-test/TEST-Cluster-Improve-Part2-Plan.md 플래닝은 이 페이지에 작성및 업데이트 할것(플래너:Codex)
- 기능추가로 인한 유닛테스트 코드도 필수로 작성할것
- 카프카는 쿠버를 이용해 하나만 구성하고.. 프로젝트는 3개 독립적으로 각각 영향없이 작동하기때문에 토픽명으로 구분해 
- 현재 쿠버로 작동중이며 구성수는 동일하게 2개(1개는 시드노드)로 작동하면됨 노드수 변경은 없음
- 완성된 기능 작동은 타이머를 이용해 노드연결 구성이 다된것을 확인후 1회 , 추가한 기능을 자동수행하는 모드로 코드적용 클러스터 구동후 단한번만 작동해야하기때문에 싱글톤 클러스터 활용하면 좋을듯, 다른방법이 있다고하면 활용해도됨

### 테스트지침
- 개선후 새로운 유닛테스트를 포함 기존유닛테스트가 잘유지되는지 검증합니다.
- 유닛테스트 검증이 완료되면 이어서 쿠버 인프라를 통해 클러스터 멤버가 잘 조인되는지 확인후...기능확인도 수행해(쿠버를 통한 로그획득기능을 통해)
- 카프카는 먼저구동되어야하기때문에 쿠버클러스터에 스탠드얼론모드로 가장 가볍게 구동해죠 -인프라 테스트 수행전 1회수행 카프카가 잘작동된거 확인후 완성된 프로젝트 쿠버구동진행
- 프로젝트당 쿠버작동이 모두 확인되면.. 그레이스풀 셧다운으로 종료


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

### 개선완료후 지침 (문서및 스킬업데이트)
- skill-test/TEST-RESULT-PART2.md : 에 클러스터 어플리케이션 수행결과및 유닛테스트 결과를 작성및 업데이트해주세요
- skill-test/TEST-RESULT-PART2-Improve.md : 에 이활동에의해 개선된 내용들을 작성및 업데이트 해주세요, 스킬에 참조한내용과 스킬에 없는 내용을 구분해서 정리
- 개선완료후에는 추가적용된 기능에대한 설명에대해 프로젝트별/README.md 업데이트 해주세요

수행자 : by Codex


## 1.0.4 - Pekko Migration

- Apache Pekko 1.1.x -> 1.4.x 로 마이그레이션을 하려고 합니다.

참고 : 
- https://pekko.apache.org/docs/pekko/current/release-notes/releases-1.2.html
- https://pekko.apache.org/docs/pekko/current/release-notes/releases-1.3.html
- https://pekko.apache.org/docs/pekko/current/release-notes/releases-1.4.html#release-notes-1-4-x-
- Scala 2.13.18


### 테스트지침
- 개선후 새로운 유닛테스트를 포함 기존유닛테스트가 잘유지되는지 검증합니다.
- 유닛테스트 검증이 완료되면 이어서 쿠버 인프라를 통해 클러스터 멤버가 잘 조인되는지 확인후...기능확인도 수행해(쿠버를 통한 로그획득기능을 통해)
- skill-test/infra : 카프카는 쿠버를 통해 작동중이기 때문에 그대로 활용 - 작동안하고 있으면 재기동  
- 쿠버작동이 모두 확인되면.. 그레이스풀 셧다운으로 종료


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

### 개선완료후 지침 (문서및 스킬업데이트)
- skill-test/TEST-RESULT-Pekko(1.1 to 1.4).md : 문서에 마이그레이션에따라 패치된 주요 핵심기능을 요약해주세요(사소한 버그fix제외) 
- skill-test/TEST-RESULT-PART2.md : 에 클러스터 어플리케이션 수행결과및 유닛테스트 결과를 작성및 업데이트해주세요
- skill-test/TEST-RESULT-PART2-Improve.md : 에 이활동에의해 개선된 내용들을 작성및 업데이트 해주세요, 스킬에 참조한내용과 스킬에 없는 내용을 구분해서 정리
- 개선완료후에는 추가적용된 기능에대한 설명에대해 프로젝트별/README.md 업데이트 해주세요
- plugins/skill-actor-model/skills : 개선사항에따라 연관된 관련스킬 지침 모두업데이트
- README.md도 이 개선에 따른 업데이트

수행자 : by Codex

## 1.0.5 - WebApp Migration

헤드리스모드(콘솔모드)로 작동하는 3종셋트의 프로젝트를 웹어플리케이션 API 기반으로 업그레이하고자합니다.

먼저 웹어플리케이션은 다음 규칙을 참고해 각각 채택해주세요
- ASP.NET Core .NET 10 을 채택해주세요
- Spring Boot 채택의 경우 Java의 경우 MVC, Kotlin의경우 Reactive 를 채택해주세요.. MVC와 Reactive 모드의 개발경험을 모두 쌓기위한 용도입니다.
- Spring Boot 3.5.x / Java 21  : 스프링은 모두 3.5버전대 자바 21버전을 이용해주세요

Akka System을 생성하고 DI를 관리하는 방법은 다음을 참고 웹어플리케이션에 자연스럽게 통합해주세요
- https://pekko.apache.org/docs/pekko-connectors/current/spring-web.html
- https://www.baeldung.com/akka-with-spring
- https://getakka.net/articles/actors/dependency-injection.html

웹이 생성됨에 따라 모두 Swagger를 지원해주고
- /api/heath : 헬스체크 API를 기본으로 만들어주세요
- /api/actor/hello : 액터에게 헬로우를 메시지를 전송하고, 액터를 통해 "wellcome actor world!" 응답받는 기본 API를 작성해주세요
- /api/cluster/info : 액터가 가진 기능을 이용해 현재 클러스터정보, 구성원 현황등 기본적인 Akka 클러스터의 정보를 볼수 있는 info api기능을 만들어주세요 해당정보는 actorsystem으로붙터 획득한 정보여야합니다.
- /api/kafka/fire-event : 현재 카프카 발행할수 있는 기능이 포함되어있습니다.(시작후 N초뒤).. 스케줄러를 제거하고 api로 발행기능을 옮깁니다.
- 파일기반 로깅(log4-back)을 채택하고, 웹어플리케이션화됨에따라 환경설정파일도 잘 구성해주세요(기본설정,Akka설정)

### 테스트지침
- 개선후 새로운 유닛테스트를 포함 기존유닛테스트가 잘유지되는지 검증합니다.
- 유닛테스트 검증이 완료되면 이어서 쿠버 인프라를 통해 클러스터 멤버가 잘 조인되는지 확인후...(쿠버를 통한 로그획득기능을 통해)
- 기능테스트는 구성원이 모두 조인된후... 추가된 API를 확인해(swagger화) API가 잘수행되는지 테스트해죠
- ex> kafka fire-event의 경우 발행을 하고, 수신검증은 쿠버로그를 통해 확인 
- skill-test/infra : 카프카 구동은 이 인프라정의 코드를 통해 쿠버를 통해 작동- 작동안하고 있으면 재기동
- 통합테스트가 모두 완료되면.. 어플리케이션 그레이스풀 셧다운으로 종료도 확인 (카프카는 별도운영 장치기이기때문에 카프카를 내리는 테스트는 제외 유지할것)

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

### 개선완료후 지침 (문서및 스킬업데이트)
- skill-test/TEST-RESULT-WebApplication.md : 각진영의 웹어플리케이션에따른 Akka시스템과 통합방법 , DI전략등을 문서화로 정리해주세요
- skill-test/TEST-RESULT-PART2.md : 에 클러스터 어플리케이션 수행결과및 유닛테스트 결과를 작성및 업데이트해주세요
- skill-test/TEST-RESULT-PART2-Improve.md : 에 이활동에의해 개선된 내용들을 작성및 업데이트 해주세요, 스킬에 참조한내용과 스킬에 없는 내용을 구분해서 정리
- 개선완료후에는 추가적용된 기능에대한 설명에대해 프로젝트별/README.md 업데이트 해주세요
- plugins/skill-actor-model/skills : 개선사항에따라 관련스킬 지침 모두업데이트
- README.md도 이 개선에 따른 업데이트

수행자 : by Codex
- 3진영 웹어플리케이션까지 적용하는 작업을 한꺼번에 시키니.. 코덱스 컨텍스트 기준 100% 소진후.. 압축후 다시진행됨 (보통이런작업은 언어별 나눠서 진행하는게 권장되지만, 3언어의 차이를 동일 컨텍스트가 해석해야했기에 한방진행됨)

#### 추가개선

TIP : 빌드오류및 인프라빌드를 완성하는과정중 실수한부분을 중점으로 스킬을 후속 업데이트 - 동일컨텍스트내에서 미션완료후 진행권장 (플래닝내에 포함되면 중간쯤 수행되 마지막 개선이 반영안될수 있음)
- skill-maker/Skill-History.md 에 추가된기능 업데이트 문서를 만들어.. 금일개선을 베이스로.. 주의해야할점 주 웹+AKK통합,Infra적인 부분 내용도 추가 보강해 plugins/skill-actor-model/skills
- plugins/skill-actor-model/.claude-plugin/plugin.json 버전하나올릴것

## 최초 버전히스토리관려
skill-maker/Skill-History.md 는 버전관리 기준으로 설명해죠
지금작성된 내용은 최신이며....plugins/skill-actor-model/.claude-plugin/plugin.json 버전하나올리고 표현하면됨
그리고 과거 히스토리는 git log를 통해 파악할수 있을거임  plugins/skill-actor-model/.claude-plugin/plugin.json git 버전변경이력을 파악해
이전버전에 대한 변경사항 skill-maker/Skill-History.md  에 업데이트해