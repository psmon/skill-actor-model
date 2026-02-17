# Skill History (Version Management)

기준 파일: `plugins/skill-actor-model/.claude-plugin/plugin.json`

## 현재 버전
- `1.16.0` (최신)
- 반영 내용:
  - 코어 스킬군(dotnet/java/kotlin)의 Web+Actor 통합 주의사항 보강
  - cluster/infra/test 스킬에 운영/검증 체크포인트 보강
  - agent 스킬은 웹 주의점 제외, 코어 스킬 조합 방식(최신 기준)으로 업데이트

## 버전 관리 원칙
1. 플러그인 배포 기준 버전은 `plugin.json`의 `version`을 단일 소스로 사용합니다.
2. 기능/지침 확장 시 버전을 1단계 상향합니다.
3. 세부 변경은 본 문서에 기록하고, 상세 diff는 git log/commit에서 추적합니다.

## 과거 버전 이력 (git 기준)
아래 이력은 `plugins/skill-actor-model/.claude-plugin/plugin.json` 변경 커밋 기준입니다.

| Version | Date | Commit | Change Summary |
|---|---|---|---|
| 1.16.0 | 2026-02-17 | (working tree) | Web+Actor/Infra/Test 지침 보강, agent 조합 방식 최신화 |
| 1.15.0 | 2026-02-17 | bd692be | Pekko Migration (1.1.x -> 1.4.x) |
| 1.14.0 | 2026-02-16 | b1daeb4 | [스킬] 인프라스킬 등록 |
| 1.12.0 | 2026-02-16 | 6ae3f5d | [Skill] Infra스킬 추가 |
| 1.11.0 | 2026-02-16 | e5a9038 | 클러스터링 테스트방법 개선지침 |
| 1.10.0 | 2026-02-16 | 28293cf | 클러스터 스킬 멀티노드 테스트 업데이트 |
| 1.9.0 | 2026-02-15 | 15eaae3 | cluster 스킬 추가 |
| 1.8.0 | 2026-02-15 | 17ad4b2 | 액터 테스트킷 추가 |
| 1.7.0 | 2026-02-15 | 4c0b774 | FSM Actor with Timer 스킬 추가 |
| 1.5.0 | 2026-02-15 | ebe78a2 | workingwithgraphs 추가 |
| 1.4.0 | 2026-02-15 | c153169 | Persistence 추가 |
| 1.3.0 | 2026-02-14 | 5255a81 | Throttle 스킬 추가 |
| 1.2.0 | 2026-02-14 | dbe203f | Router 스킬 추가 |
| 1.1.0 | 2026-02-14 | 49ecc30 | 스킬 1.1.0 업데이트 |
| 1.0.0 | 2026-02-14 | 17b9b51 | 마켓플레이스용 전환 |

참고:
- `1.13.0`, `1.6.0`은 본 파일 기준 커밋 이력에서 확인되지 않았습니다.
- 과거 상세 내용은 아래 명령으로 확인 가능합니다.

```bash
git log --follow --date=short --pretty=format:'%h|%ad|%s' -- plugins/skill-actor-model/.claude-plugin/plugin.json
```
