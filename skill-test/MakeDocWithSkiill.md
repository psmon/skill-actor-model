# 스킬활용 문서생성 스킬을 위한 문서생성

TIP : 스킬을 생성하기전 스킬생성을 위한 문서생성 활동부터 진행, 로컬기반(쿠버포함)에서 인프라를 자동구축해 클러스터 환경을 만들어주는 스킬작성을 위한 초석작업입니다. 
스킬에 필요한 문서는 스킬을 참고해, 스킬에 도움되는 주변 테크스킬을 추가하기위한 목적으로 문서작성이 잘되면 스킬생성활동으로 이어짐
(스킬에 노이즈 적용방지를 위한 문서생성후 검토목적)


## 쿠버/도커 기반 클러스터 인프라 생성을 위한 문서생성지침

스킬이 구현을 지원하는 버전내에서.. 분산구동시 인프라와 관련된 기술문서를 먼저 정리하려고 합니다.
이를 참고하여 문서생성을 진행해주세요

참고문서 : 해당문서에는 언어별 분산처리작동시 Discovery및 쿠버를 이용하는 방법이 링크형태로 정리
skill-maker/docs/actor/infra/infra.md

### Kottlin Pekko Typed
```
kotlin-pekko-typed kotlin-pekko-typed-cluster kotlin-pekko-typed-test 스킬을 활용
다음경로 문서추가및 업데이트 : skill-maker/docs/actor/infra/infra-kotlin-pekko-typed.md

```

### Java Akka Classic
```
java-akka-classic java-akka-classic-test akka-testkit java-akka-classic-cluster 스킬을 활용해서
다음경로 문서추가및 업데이트 : skill-maker/docs/actor/infra/infra-java-akka-classic.md
```

### C# Akka.NET
```
dotnet-akka-net dotnet-akka-net-test dotnet-akka-net-cluster 스킬을 활용해서
다음경로 문서추가및 업데이트 : skill-maker/docs/actor/infra/infra-dotnet-akka.md
```

## 문서생성시 중요지침
- 문서 가이드에 있는 의존요소가 실제 존재하는지 확인 (호환버전내에 가급적 마이너 최신버전, 메이저버전이 다르면 호환안될것으로 예상)
- 기존 스킬이 사용하고 있는 버전에서..문서로 제안하는 버전이 상호 호환성이 있는지 체크 (언어,플랫폼별)
- 이 문서의 최종목표... 액터시스템을 분산 클러스터링했을때 디스커버리가 중요하며  쿠버버전 그리고 쿠버종속적이지 않은 도커컴포저 기반에 디스커버리방법 크게 두종류 Type의 액터시스템 인프라 지침 문서가 필요합니다.





