# CLAUDE CODE vs CODEX

클로드 코드와 코덱스를 상호운영하며 스킬/스킬을통한 코드생성/테스트 여정중 차이점을 기록
둘의 미묘한 차이를 알아야 상호전환하면서 활용가능

## 스킬을 너가 알고 있니? 인지(Awareness) 테스트

클코드베이스로 스킬을 작성하고 -> Codex호환으로 마이그레이션하는 형태로 관리하고 있으며
마이그레이션 부탁은 코덱스에게(코덱스의 스킬구조를 몰라도 명령어로 진행 잘되는점 확인됨)

- 지금 보유한 스킬을 조회해바

```claude code
현재 보유한 스킬 목록을 조회하겠습니다.

● Searched for 1 pattern, read 2 files (ctrl+o to expand)                                                                                                                                                               
● 보유 스킬 현황 (v1.9.0)                                                                                                                                                                                                              
  총 12종 스킬이 등록되어 있습니다.                                                                                                                                                                                     
  액터 모델 기본 (3종)                                                                                                                                                                                               
─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
  
  ┌─────────────────────┬────────────────────────────┬─────────────────────────────────────────┐
  │       명령어        │           플랫폼           │                  설명                   │
  ├─────────────────────┼────────────────────────────┼─────────────────────────────────────────┤
  │ /java-akka-classic  │ Java + Akka Classic 2.7.x  │ AbstractActor 기반 액터 코드 생성       │
  ├─────────────────────┼────────────────────────────┼─────────────────────────────────────────┤
  │ /kotlin-pekko-typed │ Kotlin + Pekko Typed 1.1.x │ AbstractBehavior<T> 기반 액터 코드 생성 │
  ├─────────────────────┼────────────────────────────┼─────────────────────────────────────────┤
  │ /dotnet-akka-net    │ C# + Akka.NET 1.5.x        │ ReceiveActor 기반 액터 코드 생성        │
  └─────────────────────┴────────────────────────────┴─────────────────────────────────────────┘

  액터 테스트킷 (3종)

  ┌──────────────────────────┬────────┬─────────────────────────────────┐
  │          명령어          │ 플랫폼 │              설명               │
  ├──────────────────────────┼────────┼─────────────────────────────────┤
  │ /java-akka-classic-test  │ Java   │ Akka Classic 테스트킷 코드 생성 │
  ├──────────────────────────┼────────┼─────────────────────────────────┤
  │ /kotlin-pekko-typed-test │ Kotlin │ Pekko Typed 테스트킷 코드 생성  │
  ├──────────────────────────┼────────┼─────────────────────────────────┤
  │ /dotnet-akka-net-test    │ C#     │ Akka.NET 테스트킷 코드 생성     │
  └──────────────────────────┴────────┴─────────────────────────────────┘

  액터 클러스터 (3종)

  ┌─────────────────────────────┬────────┬─────────────────────────────────┐
  │           명령어            │ 플랫폼 │              설명               │
  ├─────────────────────────────┼────────┼─────────────────────────────────┤
  │ /java-akka-classic-cluster  │ Java   │ Akka Classic 클러스터 코드 생성 │
  ├─────────────────────────────┼────────┼─────────────────────────────────┤
  │ /kotlin-pekko-typed-cluster │ Kotlin │ Pekko Typed 클러스터 코드 생성  │
  ├─────────────────────────────┼────────┼─────────────────────────────────┤
  │ /dotnet-akka-net-cluster    │ C#     │ Akka.NET 클러스터 코드 생성     │
  └─────────────────────────────┴────────┴─────────────────────────────────┘

  AI 에이전트 파이프라인 (3종)

  ┌────────────────────────┬────────────────────────────┬──────────────────────────────────┐
  │         명령어         │           플랫폼           │               설명               │
  ├────────────────────────┼────────────────────────────┼──────────────────────────────────┤
  │ /actor-ai-agent        │ C# + Akka.NET + LLM        │ AI 에이전트 파이프라인 코드 생성 │
  ├────────────────────────┼────────────────────────────┼──────────────────────────────────┤
  │ /actor-ai-agent-java   │ Java + Akka Classic + LLM  │ AI 에이전트 파이프라인 코드 생성 │
  ├────────────────────────┼────────────────────────────┼──────────────────────────────────┤
  │ /actor-ai-agent-kotlin │ Kotlin + Pekko Typed + LLM │ AI 에이전트 파이프라인 코드 생성 │
  └────────────────────────┴────────────────────────────┴──────────────────────────────────┘

  플러그인 설치 시 네임스페이스 접두사 형태: /skill-actor-model:kotlin-pekko-typed

```

```codex
╭──────────────────────────────────────────────────╮
│ >_ OpenAI Codex (v0.101.0)                       │
│                                                  │
│ model:     gpt-5.3-codex   /model to change      │
│ directory: /mnt/d/Code/Webnori/skill-actor-model │
╰──────────────────────────────────────────────────╯

  Tip: Use /init to create an AGENTS.md with project-specific guidance.



• 현재 세션에서 보유한 스킬은 아래 6개입니다.
  4. kotlin-pekko-typed
  5. skill-creator
  6. skill-installer

• 요청하신 내용은 로컬 Codex 스킬을 최신 기준(클코드 기준)으로 다시 동기화하는 작업으로 이해했습니다. 먼저 skill-installer 스킬 지침을 열어 업데이트 절차를 확인한 뒤, 현재 설치 상태와 갱신 가능한 소스를 점검하겠습
  니다.


  Would you like to run the following command?

  Reason: 클코드 최신 스킬을 Codex 설치 경로(/home/psmon/.codex/skills)에 반영하려면 해당 경로에 쓰기 권한이 필요합니다. 지금 동기화 진행할까요?

  $ set -euo pipefail
  SRC="/mnt/d/Code/Webnori/skill-actor-model/plugins/skill-actor-model/skills"
  DEST="/home/psmon/.codex/skills"
  STAMP="$(date +%Y%m%d-%H%M%S)"
  BACKUP="/tmp/codex-skills-backup-${STAMP}"
  mkdir -p "$BACKUP"
  for d in actor-ai-agent dotnet-akka-net java-akka-classic kotlin-pekko-typed actor-ai-agent-java actor-ai-agent-kotlin dotnet-akka-net-cluster dotnet-akka-net-test java-akka-classic-cluster
  java-akka-classic-test kotlin-pekko-typed-cluster kotlin-pekko-typed-test; do
    if [ -d "$DEST/$d" ]; then
      cp -a "$DEST/$d" "$BACKUP/"
  [… 20 lines] ctrl + a view all

› 1. Yes, proceed (y)
  2. Yes, and don't ask again for commands that start with `cp -a` (p)
  3. No, and tell Codex what to do differently (esc)

```

- Codex의경우 스킬업데이트후 클코드처럼 HotReload가 안되어 수행후 재시작 ( 클코드->Codex 스킬 마이그레이션)

```
• 현재 세션에서 보유/사용 가능한 스킬은 아래와 같습니다.
                                                                                                                                                                                                                     
  - actor-ai-agent                                                                                                                                                                                                   
  - actor-ai-agent-java                                                                                                                                                                                              
  - actor-ai-agent-kotlin                                                                                                                                                                                            
  - dotnet-akka-net                                                                                                                                                                                                  
  - dotnet-akka-net-cluster                                                                                                                                                                                          
  - dotnet-akka-net-test
  - java-akka-classic
  - java-akka-classic-cluster
  - java-akka-classic-test
  - kotlin-pekko-typed
  - kotlin-pekko-typed-cluster
  - kotlin-pekko-typed-test
  - skill-creator
  - skill-installer
원하면 각 스킬별로 "언제 쓰는지" 까지 짧게 붙여서 정리해줄게  
```