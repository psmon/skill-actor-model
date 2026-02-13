# 스킬활용 프롬프트 테스트

작성코드는 커밋되지 않으며 스킬을 활용하고 해당 스킬이 코드생성및 수행을 잘하는지 셀프 개선 활동을 위한 프롬프트입니다.

## 코틀린 Typed 액터 코드 작성 테스트

```
/skill-actor-model:kotlin-pekko-typed skill-test/projects/sample1 하위폴터에 hello world 액터코드 작성, 코틀린 콘솔프로젝트로 수행
```

## 자바 Akka Classic 액터 코드 작성 테스트


```
/skill-actor-model:java-akka-classic skill-test/projects/sample2 하위폴터에 hello world 액터코드 작성, 자바 콘솔프로젝트로 수행
```

## 닷넷 Akka.NET 액터 코드 작성 테스트

```
/skill-actor-model:dotnet-akka-net skill-test/projects/sample3 하위폴터에 hello world 액터코드 작성, 닷넷 콘솔프로젝트로 수행
```

## 스킬업데이트 지침
- skill-test/projects 생성된 코드를 참고해, 스킬에 더욱 다양한 액터모델 패턴을 반영해 스킬을 개선합니다.
  - skill-maker/Skill-MargetPlace.md : 스킬 플러그인을 관리하는 방법을 먼저참고합니다.
  - plugins : 스킬구성된 플로그인 경로입니다.
  - 기존 스킬을 참고해 개선합니다. 중복인경우 업데이트를 하지 않습니다.
  - .claude-plugin/marketplace.json 버전도 업데이트해줄것


