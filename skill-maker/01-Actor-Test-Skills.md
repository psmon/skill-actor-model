다음 클로드코드 스킬가이드 문서를 참고해  skill-maker/Skill-Guide.md

이미작성된 스킬 : plugins/skill-actor-model 다음 스킬에서
- plugins/skill-actor-model/skills/dotnet-akka-net
- plugins/skill-actor-model/skills/java-akka-classic
- plugins/skill-actor-model/skills/kotlin-pekko-typed

유닛테스트와 AkkaTestKit을 활용하는 test스킬을 추가하고자합니다.

# 지침
- 언어/플랫폼별 지정된 스킬에 별도 분리해 test skill을 작성합니다.
- ex> plugins/skill-actor-model/skills/dotnet-akka-net-test
- 자바의 경우 프로젝트에 테스트코드가 포함되며, 닷넷의경우 일반적으로 분리구성 되는경우가 많으므로 닷넷의 경우 별도 프로젝트로 구성하며 이 부분을 고려해 스킬을 작성합니다.
- 스킬기본에 사용된 호환버전으로 유닛및 AkkaTestKit이 이용되어야함으로.. 기본스킬이 사용하는 기본모듈버전과 호환되는 스킬 버전을 명시화합니다.


# Testkit 자료
- skill-maker/docs/actor/testkit 하위에 testkit을 이용하는방법이 있습니다. 언어및 스펙이맞게 구분해 활용해주세요
- kotlint 테스트샘플코드는 없어서 참고해 - skill-maker/docs/actor/testkit/pekko-kotlin-typed-test.md 작성도 해주세요


# 스킬지침완성후 테스트
- 완성된 지침 스킬(plugins/skill-actor-model), SKILL.md을 참고해 실제 샘플코드를 만들고 유닛테스트를 수행합니다.
- 요구기능은 A액터가 B액터에게 Hello를 보내면 B액터는 A액터에게 World를 응답하는 간단한 시나리오입니다.
- skill-test/projects/sample19 , sample20, sample21 하위에 각각 코틀린(typed), 자바(classic), 닷넷 프로젝트를 생성해 테스트를 수행합니다.
- 유닛테스트를 통과하는과정중 , 실패(빌드포함)해 개선이 진행되는경우  생성한 스킬에서 고려하지 못한 부분일수 있음으로, 완성된 스킬을 함께 개선합니다.
- 모든작업이 완료되면 skill-maker/Skill-Test-Guide.md 에 생성한 테스트스킬을 설명하는내용과 그것을 이용하는 베스트 프랙티스를 작성합니다.


진행요원 : 코덱스