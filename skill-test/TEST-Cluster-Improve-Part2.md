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

### 개선완료후 지침 (문서및 스킬업데이트)
- skill-test/TEST-RESULT-Pekko(1.1 to 1.4).md : 문서에 마이그레이션에따라 패치된 주요 핵심기능을 요약해주세요(사소한 버그fix제외)
- skill-test/TEST-RESULT-PART2.md : 에 클러스터 어플리케이션 수행결과및 유닛테스트 결과를 작성및 업데이트해주세요
- skill-test/TEST-RESULT-PART2-Improve.md : 에 이활동에의해 개선된 내용들을 작성및 업데이트 해주세요, 스킬에 참조한내용과 스킬에 없는 내용을 구분해서 정리
- 개선완료후에는 추가적용된 기능에대한 설명에대해 프로젝트별/README.md 업데이트 해주세요
- plugins/skill-actor-model/skills : 개선사항에따라 관련스킬 지침 모두업데이트
- README.md도 이 개선에 따른 업데이트

수행자 : by Codex







