다음 클로드코드 스킬가이드 문서를 참고해  skill-maker/Skill-Guide.md

이미작성된 스킬 : plugins/skill-actor-model 다음 스킬에서
- plugins/skill-actor-model/skills/dotnet-akka-net
- plugins/skill-actor-model/skills/java-akka-classic
- plugins/skill-actor-model/skills/kotlin-pekko-typed

클러스터와 관련된 스킬을 분리해 추가하려합니다.

# 지침
- 언어/플랫폼별 지정된 스킬에 별도 분리해 cluster skill을 작성합니다.
- ex> plugins/skill-actor-model/skills/dotnet-akka-net-cluster
- 스킬기본에 사용된 호환버전으로  Cluster가 이용되어야함으로.. 기본스킬이 사용하는 기본모듈버전과 호환되는 스킬 버전을 명시화합니다.


# Clsuter 자료
- skill-maker/docs/actor/cluster 하위에 cluster와 관련된 문서링크가 있습니다. 언어및 스펙이맞게 구분해 변환해 활용해주세요
- 클러스터의 경우 다루는 주제가 많으며 연관링크도 참고합니다.

# 스킬지침완성후 테스트
- 완성된 지침 스킬(plugins/skill-actor-model), SKILL.md을 참고해 실제 샘플코드를 만들고 유닛테스트를 수행합니다.
- 유닛테스트는 AkkaToolkit의 테스트 스킬을 이용합니다. 
  - plugins/skill-actor-model/skills/dotnet-akka-net-test
  - plugins/skill-actor-model/skills/java-akka-classic-test
  - plugins/skill-actor-model/skills/kotlin-pekko-typed-test
- 유닛테스트를 통과하는과정중 , 실패(빌드포함)해 개선이 진행되는경우  생성한 스킬에서 고려하지 못한 부분일수 있음으로, 완성된 스킬을 함께 개선합니다.
- 모든작업이 완료되면 skill-maker/Skill-Cluster-Guide.md 에 생성한 테스트스킬을 설명하는내용과 그것을 이용하는 베스트 프랙티스를 작성합니다.


진행요원 : 클코드