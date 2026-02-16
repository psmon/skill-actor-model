다음 클로드코드 스킬가이드 문서를 참고해  skill-maker/Skill-Guide.md

이미작성된 스킬 : plugins/skill-actor-model 다음 스킬에서
- plugins/skill-actor-model/skills/dotnet-akka-net
- plugins/skill-actor-model/skills/java-akka-classic
- plugins/skill-actor-model/skills/kotlin-pekko-typed

인프라 구축과 관련된 스킬을 추가하려고합니다.

# 지침
- 언어/플랫폼별 지정된 스킬에 별도 분리해 cluster skill을 작성합니다.
- ex> plugins/skill-actor-model/skills/dotnet-akka-net-infra
- 스킬기본에 활용될 호환버전으로  Infra스킬이 함께 이용되어야함으로.. 기본스킬이 사용하는 기본모듈버전과 호환되는 스킬 버전을 명시화합니다.


# 액터시스템 인프라 관련자료
- skill-maker/docs/actor/infra : 하위파일에 액터시스템을 다루는 인프라 파일들이 있습니다.


# 스킬지침완성후 테스트
- 스킬작업이 완성되면 스킬이 추가되었나 조회를 해서 체크해봅니다.
- 완료되면 skill-maker/Skill-Infra-Guide.md 에 생성한 테스트스킬을 설명하는내용과 그것을 이용하는 베스트 프랙티스를 작성합니다.


진행요원 : 클코드