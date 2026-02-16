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

# 플래닝
```
           Claude Code v2.1.42
 ▐▛███▜▌   Opus 4.6 · Claude Max
▝▜█████▛▘  /mnt/d/Code/Webnori/skill-actor-model
  ▘▘ ▝▝    Opus 4.6 is here · $50 free extra usage · /extra-usage to enable

╭───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╮
│ Plan to implement                                                                                                                                                                                                 │
│                                                                                                                                                                                                                   │
│ 액터 시스템 인프라 스킬 생성 계획                                                                                                                                                                                 │
│                                                                                                                                                                                                                   │
│ Context                                                                                                                                                                                                           │
│                                                                                                                                                                                                                   │
│ 분산 액터 시스템의 인프라 구축(서비스 디스커버리, Docker Compose, Kubernetes)을 지원하는 3종의 새 스킬을 추가합니다. 기존 base/cluster/test 스킬과 동일 패턴으로 infra 변형 스킬을 생성합니다.                    │
│                                                                                                                                                                                                                   │
│ 생성할 파일 (5개)                                                                                                                                                                                                 │
│                                                                                                                                                                                                                   │
│ 1. 스킬 파일 (3개)                                                                                                                                                                                                │
│                                                                                                                                                                                                                   │
│ ┌──────────────────────────┬────────────────────────────────────────────────────────────────────┬─────────────────────────────────────────────┬─────────────────┐                                                 │
│ │           스킬           │                                경로                                │                  기본 버전                  │ Management 버전 │                                                 │
│ ├──────────────────────────┼────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────┼─────────────────┤                                                 │
│ │ kotlin-pekko-typed-infra │ plugins/skill-actor-model/skills/kotlin-pekko-typed-infra/SKILL.md │ Pekko 1.1.3 (Management는 1.1.5+ 필요 명시) │ 1.2.0           │                                                 │
│ ├──────────────────────────┼────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────┼─────────────────┤                                                 │
│ │ java-akka-classic-infra  │ plugins/skill-actor-model/skills/java-akka-classic-infra/SKILL.md  │ Akka 2.7.1                                  │ 1.2.0           │                                                 │
│ ├──────────────────────────┼────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────┼─────────────────┤                                                 │
│ │ dotnet-akka-net-infra    │ plugins/skill-actor-model/skills/dotnet-akka-net-infra/SKILL.md    │ Akka.NET 1.5.60                             │ 1.5.59          │                                                 │
│ └──────────────────────────┴────────────────────────────────────────────────────────────────────┴─────────────────────────────────────────────┴─────────────────┘                                                 │
│                                                                                                                                                                                                                   │
│ 각 SKILL.md 구조:                                                                                                                                                                                                 │
│ - YAML frontmatter (name, description, argument-hint)                                                                                                                                                             │
│ - 참고 문서 링크 (기본 스킬, 클러스터 스킬, infra 문서)                                                                                                                                                           │
│ - 호환 버전 테이블 (기본 모듈 + Management + Discovery 버전)                                                                                                                                                      │
│ - 의존성 (Gradle/NuGet)                                                                                                                                                                                           │
│ - Type A: Docker Compose Discovery (Config 기반)                                                                                                                                                                  │
│   - HOCON 설정, 초기화 코드, docker-compose.yml                                                                                                                                                                   │
│ - Type B: Kubernetes Discovery                                                                                                                                                                                    │
│   - HOCON 설정, 초기화 코드, RBAC, Deployment, Service YAML                                                                                                                                                       │
│ - 핵심 주의사항                                                                                                                                                                                                   │
│ - 코드 생성 규칙                                                                                                                                                                                                  │
│                                                                                                                                                                                                                   │
│ 2. 버전 업데이트 (2개)                                                                                                                                                                                            │
│                                                                                                                                                                                                                   │
│ - plugins/skill-actor-model/.claude-plugin/plugin.json: 1.11.0 → 1.12.0                                                                                                                                           │
│ - .claude-plugin/marketplace.json: version 3곳 모두 1.11.0 → 1.12.0, keywords에 "infra", "kubernetes", "docker" 추가                                                                                              │
│                                                                                                                                                                                                                   ││ 3. 가이드 문서 (1개)                                                                                                                                                                                              ││                                                                                                                                                                                                                   ││ - skill-maker/Skill-Infra-Guide.md: 인프라 스킬 설명 + 베스트 프랙티스                                                                                                                                            ││                                                                                                                                                                                                                   ││ 스킬 조회 테스트                                                                                                                                                                                                  ││                                                                                                                                                                                                                   ││ 스킬 생성 후 /skills 명령으로 새 스킬 3종이 인식되는지 확인합니다.                                                                                                                                                ││                                                                                                                                                                                                                   ││ 참조 문서                                                                                                                                                                                                         ││                                                                                                                                                                                                                   ││ - 스킬 구조 참고: skill-maker/Skill-Guide.md                                                                                                                                                                      ││ - 인프라 원본: skill-maker/docs/actor/infra/infra-*.md                                                                                                                                                            ││ - 기존 cluster 스킬 패턴: plugins/skill-actor-model/skills/*-cluster/SKILL.md                                                                                                                                     │╰───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╯

● I'll implement the infrastructure skills plan. Let me start by reading the reference documents and existing patterns in parallel.
                                                                                      
● Running 2 Task agents… (ctrl+o to expand)
   ├─ Read all reference documents · 7 tool uses · 18.8k tokens                                                                                                                                                         │  ⎿  Reading 7 files…                
   └─ Read existing cluster skills · 3 tool uses · 18.8k tokens                                                                                                                                                      
      ⎿  Reading 3 files…                                                          
     ctrl+b to run in background                                                      
               
✢ Cerebrating… (4m 59s · ↓ 580 tokens)                                                                                                                                                                                 ⎿  Tip: Run /install-github-app to tag @claude right from your Github issues and PRs

```

진행요원 : 클코드
- 스킬생성은 클코드에게, 코텍스에게 클코등용 스킬생성을 의뢰해보았으나 기존 스킬셋을 코덱스기준으로 변환시켜버리는 사태이후 클코드전용 스킬생성및 업데이트는 클코드에게 시킴
