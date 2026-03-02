
## Codex 스킬업데이트 from 클코드 스킬
```
plugins/skill-actor-model/skills 클로드 코드 원본 스킬이며 너는 codex임... codex기준 스킬셋(~/.codex/skills) 과 일치하는지 체크
일치하지않으면 클코드 기준 파일로 codex skill 파일구성 규칙을 확인후 복제해죠, 스킬의 파일이 완전하게 동일해야함

- 업데이트후 codex 재시작 필요
```

## 1.0.6 - CAFE24 API호출 안전처리장치 by 백프레셔이용

- skill-test/plan/TEST-Cafe24APILimit.md : 이 지침에의해 로컬로의 검증활동이 완료되었습니다.
  - 이 지침및 결과물을 확인해 Kottlin Pekko Typed, Java Akka Classic, C# Akka.NET 3종 세트에 이 컨셉을 추가합니다.
- 로컬 vs 클러스터 차이에따른 고려사항 
  - 제약은 Cafe24 내 MallId(기업구분) 별로 적용되며.. 로컬에서는 Mall구분이 없었으나~ 다양한 Mall을 분산배치 전략을 활용.. 샘플과 유사하게 Cafe24API 실호출없이 더미 API이용
  - 클러스터 내에서 분산처리된 액터의 속도를 조절제어해야하며, 호출량 측정및 모니터링을 위해 클러스터 싱글통 액터가 이용될수 있습니다.
- 기능이 추가됨에 따라 유닛테스트를 먼저작성하고 수행해주세요

### 테스트지침
- 개선후 새로운 유닛테스트를 포함 기존유닛테스트가 잘유지되는지 검증합니다. 유닛테스트가 통과후 쿠버를 이용한 구동및 통합 클러스터 테스트를 수행해주세요
- 유닛테스트 검증이 완료되면 이어서 쿠버 인프라를 통해 클러스터 멤버가 잘 조인되는지 확인후...기능확인도 수행해(쿠버를 통한 로그획득기능을 통해)
- 새로운 기능이 추가됨에따라 쿠버 구동후 신규기능에 대한 통합테스트도 수행해주세요
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
- skill-test/TEST-RESULT-PART3.md : 에 클러스터 어플리케이션 수행결과및 유닛테스트 결과를 작성및 업데이트해주세요
- skill-test/TEST-RESULT-PART3-Improve.md : 에 이활동에의해 개선된 내용들을 작성및 업데이트 해주세요, 스킬에 참조한내용과 스킬에 없는 내용을 구분해서 정리
- 개선완료후에는 추가적용된 기능에대한 설명에대해 프로젝트별/README.md 업데이트 해주세요
- plugins/skill-actor-model/skills : 
  - 하위에 스킬파일들이 있습니다. 이 개선활동에의해 추가된 기술요소 스킬업데이트를 합니다.
  - 중복내용인 경우 기존내용을 충분히 확인후 보강합니다.  스킬의 내용이 너무 길어지지 개선과정중 스킬이 가이드를 못해 실패해 재시도한 부분을 특히 파악해 다음에 실수없이 구현가능하게 업데이트합니다.

수행자 : by Codex

