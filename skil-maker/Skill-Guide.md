# 클로드 코드 스킬생성 가이드

> ## Documentation Index
> Fetch the complete documentation index at: https://code.claude.com/docs/llms.txt
> Use this file to discover all available pages before exploring further.

# Claude를 기술로 확장하기

> Claude Code에서 기술을 생성, 관리 및 공유하여 Claude의 기능을 확장합니다. 사용자 정의 슬래시 명령어를 포함합니다.

기술은 Claude가 할 수 있는 것을 확장합니다. 지침이 포함된 `SKILL.md` 파일을 만들면 Claude가 이를 도구 모음에 추가합니다. Claude는 관련이 있을 때 기술을 사용하거나 `/skill-name`으로 직접 호출할 수 있습니다.

<Note>
  `/help` 및 `/compact`와 같은 기본 제공 명령어는 [대화형 모드](/ko/interactive-mode#built-in-commands)를 참조하세요.

**사용자 정의 슬래시 명령어가 기술로 병합되었습니다.** `.claude/commands/review.md`의 파일과 `.claude/skills/review/SKILL.md`의 기술 모두 `/review`를 생성하고 동일한 방식으로 작동합니다. 기존 `.claude/commands/` 파일은 계속 작동합니다. 기술은 선택적 기능을 추가합니다: 지원 파일을 위한 디렉토리, [누가 기술을 호출하는지 제어](#control-who-invokes-a-skill)하기 위한 프론트매터, 그리고 Claude가 관련이 있을 때 자동으로 로드할 수 있는 기능입니다.
</Note>

Claude Code 기술은 여러 AI 도구에서 작동하는 [Agent Skills](https://agentskills.io) 개방형 표준을 따릅니다. Claude Code는 [호출 제어](#control-who-invokes-a-skill), [서브에이전트 실행](#run-skills-in-a-subagent), [동적 컨텍스트 주입](#inject-dynamic-context)과 같은 추가 기능으로 표준을 확장합니다.

## 시작하기

### 첫 번째 기술 만들기

이 예제는 Claude에게 시각적 다이어그램과 유추를 사용하여 코드를 설명하도록 가르치는 기술을 만듭니다. 기본 프론트매터를 사용하므로 Claude는 어떤 것이 어떻게 작동하는지 물어볼 때 자동으로 로드하거나 `/explain-code`로 직접 호출할 수 있습니다.

<Steps>
  <Step title="기술 디렉토리 만들기">
    개인 기술 폴더에 기술을 위한 디렉토리를 만듭니다. 개인 기술은 모든 프로젝트에서 사용할 수 있습니다.

    ```bash  theme={null}
    mkdir -p ~/.claude/skills/explain-code
    ```
  </Step>

  <Step title="SKILL.md 작성">
    모든 기술에는 `SKILL.md` 파일이 필요합니다. 두 부분으로 구성됩니다: Claude에게 기술을 언제 사용할지 알려주는 YAML 프론트매터(`---` 마커 사이)와 기술이 호출될 때 Claude가 따르는 지침이 포함된 마크다운 콘텐츠입니다. `name` 필드는 `/slash-command`가 되고, `description`은 Claude가 자동으로 로드할 시기를 결정하는 데 도움이 됩니다.

    `~/.claude/skills/explain-code/SKILL.md` 만들기:

    ```yaml  theme={null}
    ---
    name: explain-code
    description: 시각적 다이어그램과 유추를 사용하여 코드를 설명합니다. 코드가 어떻게 작동하는지 설명하거나, 코드베이스에 대해 가르치거나, 사용자가 "이것이 어떻게 작동하나요?"라고 물을 때 사용합니다.
    ---

    코드를 설명할 때 항상 포함하세요:

    1. **유추로 시작**: 코드를 일상 생활의 무언가와 비교합니다.
    2. **다이어그램 그리기**: ASCII 아트를 사용하여 흐름, 구조 또는 관계를 표시합니다.
    3. **코드 단계별 설명**: 무슨 일이 일어나는지 단계별로 설명합니다.
    4. **주의할 점 강조**: 일반적인 실수나 오해는 무엇입니까?

    설명을 대화체로 유지합니다. 복잡한 개념의 경우 여러 유추를 사용합니다.
    ```
  </Step>

  <Step title="기술 테스트">
    두 가지 방법으로 테스트할 수 있습니다:

    **Claude가 자동으로 호출하도록 하기** - 설명과 일치하는 항목을 물어봅니다:

    ```
    이 코드가 어떻게 작동하나요?
    ```

    **또는 기술 이름으로 직접 호출**:

    ```
    /explain-code src/auth/login.ts
    ```

    어느 쪽이든 Claude는 설명에 유추와 ASCII 다이어그램을 포함해야 합니다.
  </Step>
</Steps>

### 기술이 있는 위치

기술을 저장하는 위치는 누가 사용할 수 있는지를 결정합니다:

| 위치     | 경로                                       | 적용 대상         |
| :----- | :--------------------------------------- | :------------ |
| 엔터프라이즈 | [관리 설정](/ko/iam#managed-settings) 참조     | 조직의 모든 사용자    |
| 개인     | `~/.claude/skills/<skill-name>/SKILL.md` | 모든 프로젝트       |
| 프로젝트   | `.claude/skills/<skill-name>/SKILL.md`   | 이 프로젝트만       |
| 플러그인   | `<plugin>/skills/<skill-name>/SKILL.md`  | 플러그인이 활성화된 위치 |

프로젝트 기술은 같은 이름의 개인 기술을 재정의합니다. `.claude/commands/`에 파일이 있으면 같은 방식으로 작동하지만 같은 이름의 명령어보다 기술이 우선합니다.

#### 중첩된 디렉토리에서 자동 검색

하위 디렉토리의 파일로 작업할 때 Claude Code는 중첩된 `.claude/skills/` 디렉토리에서 기술을 자동으로 검색합니다. 예를 들어 `packages/frontend/`의 파일을 편집하는 경우 Claude Code는 `packages/frontend/.claude/skills/`에서도 기술을 찾습니다. 이는 패키지가 자신의 기술을 가진 모노레포 설정을 지원합니다.

각 기술은 `SKILL.md`를 진입점으로 하는 디렉토리입니다:

```
my-skill/
├── SKILL.md           # 주요 지침 (필수)
├── template.md        # Claude가 작성할 템플릿
├── examples/
│   └── sample.md      # 예상 형식을 보여주는 예제 출력
└── scripts/
    └── validate.sh    # Claude가 실행할 수 있는 스크립트
```

`SKILL.md`에는 주요 지침이 포함되어 있으며 필수입니다. 다른 파일은 선택 사항이며 더 강력한 기술을 구축할 수 있습니다: Claude가 작성할 템플릿, 예상 형식을 보여주는 예제 출력, Claude가 실행할 수 있는 스크립트 또는 상세한 참조 문서입니다. `SKILL.md`에서 이러한 파일을 참조하여 Claude가 포함된 내용과 로드할 시기를 알 수 있도록 합니다. 자세한 내용은 [지원 파일 추가](#add-supporting-files)를 참조하세요.

<Note>
  `.claude/commands/`의 파일은 계속 작동하며 동일한 [프론트매터](#frontmatter-reference)를 지원합니다. 지원 파일과 같은 추가 기능을 지원하므로 기술이 권장됩니다.
</Note>

## 기술 구성

기술은 `SKILL.md` 상단의 YAML 프론트매터와 그 뒤에 오는 마크다운 콘텐츠를 통해 구성됩니다.

### 기술 콘텐츠 유형

기술 파일에는 모든 지침이 포함될 수 있지만 호출 방법을 생각하면 포함할 내용을 결정하는 데 도움이 됩니다:

**참조 콘텐츠**는 Claude가 현재 작업에 적용하는 지식을 추가합니다. 규칙, 패턴, 스타일 가이드, 도메인 지식입니다. 이 콘텐츠는 인라인으로 실행되므로 Claude가 대화 컨텍스트와 함께 사용할 수 있습니다.

```yaml  theme={null}
---
name: api-conventions
description: 이 코드베이스에 대한 API 설계 패턴
---

API 엔드포인트를 작성할 때:
- RESTful 명명 규칙 사용
- 일관된 오류 형식 반환
- 요청 검증 포함
```

**작업 콘텐츠**는 배포, 커밋 또는 코드 생성과 같은 특정 작업에 대한 단계별 지침을 제공합니다. 이는 Claude가 자동으로 결정하도록 하기보다는 `/skill-name`으로 직접 호출하려는 작업입니다. `disable-model-invocation: true`를 추가하여 Claude가 자동으로 트리거하는 것을 방지합니다.

```yaml  theme={null}
---
name: deploy
description: 애플리케이션을 프로덕션에 배포합니다.
context: fork
disable-model-invocation: true
---

애플리케이션 배포:
1. 테스트 스위트 실행
2. 애플리케이션 빌드
3. 배포 대상으로 푸시
```

`SKILL.md`에는 모든 것이 포함될 수 있지만 기술을 호출하는 방법(사용자, Claude 또는 둘 다)과 실행 위치(인라인 또는 서브에이전트)를 생각하면 포함할 내용을 결정하는 데 도움이 됩니다. 복잡한 기술의 경우 [지원 파일을 추가](#add-supporting-files)하여 주요 기술에 집중할 수 있습니다.

### 프론트매터 참조

마크다운 콘텐츠 외에도 `SKILL.md` 파일 상단의 `---` 마커 사이의 YAML 프론트매터 필드를 사용하여 기술 동작을 구성할 수 있습니다:

```yaml  theme={null}
---
name: my-skill
description: 이 기술이 하는 것
disable-model-invocation: true
allowed-tools: Read, Grep
---

기술 지침...
```

모든 필드는 선택 사항입니다. Claude가 기술을 사용할 시기를 알 수 있도록 `description`만 권장됩니다.

| 필드                         | 필수  | 설명                                                                                                |
| :------------------------- | :-- | :------------------------------------------------------------------------------------------------ |
| `name`                     | 아니요 | 기술의 표시 이름입니다. 생략하면 디렉토리 이름을 사용합니다. 소문자, 숫자 및 하이픈만 사용 가능(최대 64자).                                  |
| `description`              | 권장  | 기술이 하는 것과 사용 시기입니다. Claude는 이를 사용하여 기술을 적용할 시기를 결정합니다. 생략하면 마크다운 콘텐츠의 첫 번째 단락을 사용합니다.             |
| `argument-hint`            | 아니요 | 자동 완성 중에 표시되는 힌트로 예상 인수를 나타냅니다. 예: `[issue-number]` 또는 `[filename] [format]`.                     |
| `disable-model-invocation` | 아니요 | Claude가 이 기술을 자동으로 로드하는 것을 방지하려면 `true`로 설정합니다. `/name`으로 수동으로 트리거하려는 워크플로우에 사용합니다. 기본값: `false`. |
| `user-invocable`           | 아니요 | `/` 메뉴에서 숨기려면 `false`로 설정합니다. 사용자가 직접 호출해서는 안 되는 배경 지식에 사용합니다. 기본값: `true`.                       |
| `allowed-tools`            | 아니요 | 이 기술이 활성화되었을 때 Claude가 권한을 요청하지 않고 사용할 수 있는 도구입니다.                                                |
| `model`                    | 아니요 | 이 기술이 활성화되었을 때 사용할 모델입니다.                                                                         |
| `context`                  | 아니요 | 포크된 서브에이전트 컨텍스트에서 실행하려면 `fork`로 설정합니다.                                                            |
| `agent`                    | 아니요 | `context: fork`가 설정되었을 때 사용할 서브에이전트 유형입니다.                                                        |
| `hooks`                    | 아니요 | 이 기술의 수명 주기로 범위가 지정된 훅입니다. 구성 형식은 [Hooks](/ko/hooks)를 참조하세요.                                      |

#### 사용 가능한 문자열 대체

기술은 기술 콘텐츠의 동적 값에 대한 문자열 대체를 지원합니다:

| 변수                     | 설명                                                                              |
| :--------------------- | :------------------------------------------------------------------------------ |
| `$ARGUMENTS`           | 기술을 호출할 때 전달된 모든 인수입니다. `$ARGUMENTS`가 콘텐츠에 없으면 인수가 `ARGUMENTS: <value>`로 추가됩니다. |
| `${CLAUDE_SESSION_ID}` | 현재 세션 ID입니다. 로깅, 세션별 파일 생성 또는 기술 출력을 세션과 연관시키는 데 유용합니다.                         |

**대체를 사용한 예제:**

```yaml  theme={null}
---
name: session-logger
description: 이 세션에 대한 활동 로그
---

다음을 logs/${CLAUDE_SESSION_ID}.log에 로깅합니다:

$ARGUMENTS
```

### 지원 파일 추가

기술은 디렉토리에 여러 파일을 포함할 수 있습니다. 이렇게 하면 `SKILL.md`가 필수 사항에 집중하는 동시에 Claude가 필요할 때만 상세한 참조 자료에 액세스할 수 있습니다. 큰 참조 문서, API 사양 또는 예제 컬렉션은 기술이 실행될 때마다 컨텍스트에 로드될 필요가 없습니다.

```
my-skill/
├── SKILL.md (필수 - 개요 및 탐색)
├── reference.md (상세 API 문서 - 필요할 때 로드됨)
├── examples.md (사용 예제 - 필요할 때 로드됨)
└── scripts/
    └── helper.py (유틸리티 스크립트 - 실행됨, 로드되지 않음)
```

`SKILL.md`에서 지원 파일을 참조하여 Claude가 각 파일의 내용과 로드할 시기를 알 수 있도록 합니다:

```markdown  theme={null}
## 추가 리소스

- 완전한 API 세부 정보는 [reference.md](reference.md) 참조
- 사용 예제는 [examples.md](examples.md) 참조
```

<Tip>`SKILL.md`를 500줄 이하로 유지합니다. 상세한 참조 자료를 별도 파일로 이동합니다.</Tip>

### 기술을 호출하는 사람 제어

기본적으로 `disable-model-invocation: true`가 설정되지 않은 모든 기술을 사용자와 Claude 모두 호출할 수 있습니다. `/skill-name`을 입력하여 직접 호출할 수 있고, Claude는 대화와 관련이 있을 때 자동으로 로드할 수 있습니다. 두 개의 프론트매터 필드를 사용하면 이를 제한할 수 있습니다:

* **`disable-model-invocation: true`**: 사용자만 기술을 호출할 수 있습니다. 부작용이 있거나 타이밍을 제어하려는 워크플로우(예: `/commit`, `/deploy` 또는 `/send-slack-message`)에 사용합니다. 코드가 준비된 것처럼 보인다고 해서 Claude가 배포하기를 원하지 않습니다.

* **`user-invocable: false`**: Claude만 기술을 호출할 수 있습니다. 실행 가능한 명령어가 아닌 배경 지식에 사용합니다. `legacy-system-context` 기술은 오래된 시스템이 어떻게 작동하는지 설명합니다. Claude는 관련이 있을 때 이를 알아야 하지만 `/legacy-system-context`는 사용자가 취할 의미 있는 작업이 아닙니다.

이 예제는 사용자만 트리거할 수 있는 배포 기술을 만듭니다. `disable-model-invocation: true` 필드는 Claude가 자동으로 실행하는 것을 방지합니다:

```yaml  theme={null}
---
name: deploy
description: 애플리케이션을 프로덕션에 배포합니다.
disable-model-invocation: true
---

$ARGUMENTS를 프로덕션에 배포합니다:

1. 테스트 스위트 실행
2. 애플리케이션 빌드
3. 배포 대상으로 푸시
4. 배포 성공 확인
```

두 필드가 호출 및 컨텍스트 로딩에 미치는 영향은 다음과 같습니다:

| 프론트매터                            | 사용자가 호출 가능 | Claude가 호출 가능 | 컨텍스트에 로드되는 시기                     |
| :------------------------------- | :--------- | :------------ | :-------------------------------- |
| (기본값)                            | 예          | 예             | 설명은 항상 컨텍스트에 있고, 호출 시 전체 기술 로드    |
| `disable-model-invocation: true` | 예          | 아니요           | 설명은 컨텍스트에 없고, 사용자가 호출할 때 전체 기술 로드 |
| `user-invocable: false`          | 아니요        | 예             | 설명은 항상 컨텍스트에 있고, 호출 시 전체 기술 로드    |

<Note>
  일반 세션에서 기술 설명은 Claude가 사용 가능한 것을 알 수 있도록 컨텍스트에 로드되지만 전체 기술 콘텐츠는 호출할 때만 로드됩니다. [미리 로드된 기술이 있는 서브에이전트](/ko/sub-agents#preload-skills-into-subagents)는 다르게 작동합니다: 전체 기술 콘텐츠는 시작 시 주입됩니다.
</Note>

### 도구 액세스 제한

`allowed-tools` 필드를 사용하여 기술이 활성화되었을 때 Claude가 사용할 수 있는 도구를 제한합니다. 이 기술은 Claude가 파일을 탐색할 수 있지만 수정할 수 없는 읽기 전용 모드를 만듭니다:

```yaml  theme={null}
---
name: safe-reader
description: 변경하지 않고 파일 읽기
allowed-tools: Read, Grep, Glob
---
```

### 기술에 인수 전달

사용자와 Claude 모두 기술을 호출할 때 인수를 전달할 수 있습니다. 인수는 `$ARGUMENTS` 자리 표시자를 통해 사용할 수 있습니다.

이 기술은 번호로 GitHub 문제를 수정합니다. `$ARGUMENTS` 자리 표시자는 기술 이름 뒤에 오는 모든 것으로 대체됩니다:

```yaml  theme={null}
---
name: fix-issue
description: GitHub 문제 수정
disable-model-invocation: true
---

GitHub 문제 $ARGUMENTS를 코딩 표준에 따라 수정합니다.

1. 문제 설명 읽기
2. 요구 사항 이해
3. 수정 구현
4. 테스트 작성
5. 커밋 생성
```

`/fix-issue 123`을 실행하면 Claude는 "GitHub 문제 123을 코딩 표준에 따라 수정합니다..."를 받습니다.

인수를 사용하여 기술을 호출하지만 기술에 `$ARGUMENTS`가 포함되지 않으면 Claude Code는 `ARGUMENTS: <your input>`을 기술 콘텐츠 끝에 추가하므로 Claude는 여전히 입력한 내용을 봅니다.

## 고급 패턴

### 동적 컨텍스트 주입

`!`command\`\` 구문은 기술 콘텐츠를 Claude에게 보내기 전에 셸 명령어를 실행합니다. 명령어 출력이 자리 표시자를 대체하므로 Claude는 명령어 자체가 아닌 실제 데이터를 받습니다.

이 기술은 GitHub CLI를 사용하여 라이브 PR 데이터를 가져와 풀 요청을 요약합니다. `!`gh pr diff\`\` 및 기타 명령어가 먼저 실행되고 출력이 프롬프트에 삽입됩니다:

```yaml  theme={null}
---
name: pr-summary
description: 풀 요청의 변경 사항 요약
context: fork
agent: Explore
allowed-tools: Bash(gh:*)
---

## 풀 요청 컨텍스트
- PR diff: !`gh pr diff`
- PR 댓글: !`gh pr view --comments`
- 변경된 파일: !`gh pr diff --name-only`

## 작업
이 풀 요청을 요약합니다...
```

이 기술이 실행될 때:

1. 각 `!`command\`\`가 즉시 실행됩니다(Claude가 보기 전에).
2. 출력이 기술 콘텐츠의 자리 표시자를 대체합니다.
3. Claude는 실제 PR 데이터가 포함된 완전히 렌더링된 프롬프트를 받습니다.

이는 전처리이며 Claude가 실행하는 것이 아닙니다. Claude는 최종 결과만 봅니다.

<Tip>
  기술에서 [확장 사고](/ko/common-workflows#use-extended-thinking-thinking-mode)를 활성화하려면 기술 콘텐츠의 어디든 "ultrathink"라는 단어를 포함합니다.
</Tip>

### 서브에이전트에서 기술 실행

기술을 격리된 상태에서 실행하려면 프론트매터에 `context: fork`를 추가합니다. 기술 콘텐츠는 서브에이전트를 구동하는 프롬프트가 됩니다. 대화 기록에 액세스할 수 없습니다.

<Warning>
  `context: fork`는 명시적 지침이 있는 기술에만 의미가 있습니다. 기술에 작업 없이 "이러한 API 규칙 사용"과 같은 지침이 포함되어 있으면 서브에이전트는 지침을 받지만 실행 가능한 프롬프트가 없으므로 의미 있는 출력 없이 반환됩니다.
</Warning>

기술과 [서브에이전트](/ko/sub-agents)는 두 방향으로 함께 작동합니다:

| 접근 방식                  | 시스템 프롬프트                       | 작업             | 또한 로드                 |
| :--------------------- | :----------------------------- | :------------- | :-------------------- |
| `context: fork`가 있는 기술 | 에이전트 유형(`Explore`, `Plan` 등)에서 | SKILL.md 콘텐츠   | CLAUDE.md             |
| `skills` 필드가 있는 서브에이전트 | 서브에이전트의 마크다운 본문                | Claude의 위임 메시지 | 미리 로드된 기술 + CLAUDE.md |

`context: fork`를 사용하면 기술에 작업을 작성하고 실행할 에이전트 유형을 선택합니다. 역(사용자 정의 서브에이전트를 정의하여 기술을 참조 자료로 사용)은 [서브에이전트](/ko/sub-agents#preload-skills-into-subagents)를 참조하세요.

#### 예제: Explore 에이전트를 사용한 연구 기술

이 기술은 포크된 Explore 에이전트에서 연구를 실행합니다. 기술 콘텐츠는 작업이 되고 에이전트는 코드베이스 탐색에 최적화된 읽기 전용 도구를 제공합니다:

```yaml  theme={null}
---
name: deep-research
description: 주제를 철저히 연구합니다.
context: fork
agent: Explore
---

$ARGUMENTS를 철저히 연구합니다:

1. Glob 및 Grep을 사용하여 관련 파일 찾기
2. 코드 읽기 및 분석
3. 특정 파일 참조를 사용하여 결과 요약
```

이 기술이 실행될 때:

1. 새로운 격리된 컨텍스트가 생성됩니다.
2. 서브에이전트는 기술 콘텐츠를 프롬프트로 받습니다("\$ARGUMENTS를 철저히 연구합니다...").
3. `agent` 필드는 실행 환경(모델, 도구 및 권한)을 결정합니다.
4. 결과가 요약되어 주요 대화로 반환됩니다.

`agent` 필드는 사용할 서브에이전트 구성을 지정합니다. 옵션에는 기본 제공 에이전트(`Explore`, `Plan`, `general-purpose`) 또는 `.claude/agents/`의 모든 사용자 정의 서브에이전트가 포함됩니다. 생략하면 `general-purpose`를 사용합니다.

### Claude의 기술 액세스 제한

기본적으로 Claude는 `disable-model-invocation: true`가 설정되지 않은 모든 기술을 호출할 수 있습니다. `/compact` 및 `/init`과 같은 기본 제공 명령어는 Skill 도구를 통해 사용할 수 없습니다.

Claude가 호출할 수 있는 기술을 제어하는 세 가지 방법:

**모든 기술 비활성화** - `/permissions`에서 Skill 도구 거부:

```
# 거부 규칙에 추가:
Skill
```

**특정 기술 허용 또는 거부** - [권한 규칙](/ko/iam) 사용:

```
# 특정 기술만 허용
Skill(commit)
Skill(review-pr:*)

# 특정 기술 거부
Skill(deploy:*)
```

권한 구문: 정확한 일치는 `Skill(name)`, 모든 인수를 사용한 접두사 일치는 `Skill(name:*)`.

**개별 기술 숨기기** - 프론트매터에 `disable-model-invocation: true` 추가. 이렇게 하면 기술이 Claude의 컨텍스트에서 완전히 제거됩니다.

<Note>
  `user-invocable` 필드는 메뉴 표시 여부만 제어하고 Skill 도구 액세스는 제어하지 않습니다. 프로그래밍 방식 호출을 차단하려면 `disable-model-invocation: true`를 사용합니다.
</Note>

## 기술 공유

기술은 대상에 따라 다양한 범위에서 배포할 수 있습니다:

* **프로젝트 기술**: `.claude/skills/`를 버전 제어에 커밋
* **플러그인**: [플러그인](/ko/plugins)에서 `skills/` 디렉토리 만들기
* **관리**: [관리 설정](/ko/iam#managed-settings)을 통해 조직 전체 배포

### 시각적 출력 생성

기술은 모든 언어의 스크립트를 번들로 제공하고 실행할 수 있으므로 Claude에게 단일 프롬프트로 가능한 것 이상의 기능을 제공합니다. 강력한 패턴 중 하나는 시각적 출력을 생성하는 것입니다: 브라우저에서 열리는 대화형 HTML 파일로 데이터 탐색, 디버깅 또는 보고서 작성에 사용합니다.

이 예제는 코드베이스 탐색기를 만듭니다: 디렉토리를 확장 및 축소할 수 있는 대화형 트리 보기로 한눈에 파일 크기를 보고 색상으로 파일 유형을 식별합니다.

Skill 디렉토리 만들기:

```bash  theme={null}
mkdir -p ~/.claude/skills/codebase-visualizer/scripts
```

`~/.claude/skills/codebase-visualizer/SKILL.md` 만들기. 설명은 Claude에게 이 Skill을 활성화할 시기를 알려주고 지침은 Claude에게 번들 스크립트를 실행하도록 알려줍니다:

````yaml  theme={null}
---
name: codebase-visualizer
description: 코드베이스의 대화형 축소 가능한 트리 시각화를 생성합니다. 새 리포지토리 탐색, 프로젝트 구조 이해 또는 큰 파일 식별 시 사용합니다.
allowed-tools: Bash(python:*)
---

# 코드베이스 시각화기

프로젝트의 파일 구조를 축소 가능한 디렉토리로 보여주는 대화형 HTML 트리 보기를 생성합니다.

## 사용법

프로젝트 루트에서 시각화 스크립트를 실행합니다:

```bash
python ~/.claude/skills/codebase-visualizer/scripts/visualize.py .
```

이렇게 하면 현재 디렉토리에 `codebase-map.html`이 생성되고 기본 브라우저에서 열립니다.

## 시각화가 표시하는 것

- **축소 가능한 디렉토리**: 폴더를 클릭하여 확장/축소
- **파일 크기**: 각 파일 옆에 표시됨
- **색상**: 파일 유형별로 다른 색상
- **디렉토리 합계**: 각 폴더의 집계 크기 표시
````

`~/.claude/skills/codebase-visualizer/scripts/visualize.py` 만들기. 이 스크립트는 디렉토리 트리를 스캔하고 다음을 포함하는 자체 포함 HTML 파일을 생성합니다:

* 파일 수, 디렉토리 수, 총 크기 및 파일 유형 수를 보여주는 **요약 사이드바**
* 파일 유형별로 코드베이스를 분석하는 **막대 그래프**(크기 기준 상위 8개)
* 디렉토리를 확장 및 축소할 수 있는 **축소 가능한 트리**로 색상으로 코딩된 파일 유형 표시기 포함

스크립트는 Python이 필요하지만 기본 제공 라이브러리만 사용하므로 설치할 패키지가 없습니다:

```python expandable theme={null}
#!/usr/bin/env python3
"""코드베이스의 대화형 축소 가능한 트리 시각화를 생성합니다."""

import json
import sys
import webbrowser
from pathlib import Path
from collections import Counter

IGNORE = {'.git', 'node_modules', '__pycache__', '.venv', 'venv', 'dist', 'build'}

def scan(path: Path, stats: dict) -> dict:
    result = {"name": path.name, "children": [], "size": 0}
    try:
        for item in sorted(path.iterdir()):
            if item.name in IGNORE or item.name.startswith('.'):
                continue
            if item.is_file():
                size = item.stat().st_size
                ext = item.suffix.lower() or '(no ext)'
                result["children"].append({"name": item.name, "size": size, "ext": ext})
                result["size"] += size
                stats["files"] += 1
                stats["extensions"][ext] += 1
                stats["ext_sizes"][ext] += size
            elif item.is_dir():
                stats["dirs"] += 1
                child = scan(item, stats)
                if child["children"]:
                    result["children"].append(child)
                    result["size"] += child["size"]
    except PermissionError:
        pass
    return result

def generate_html(data: dict, stats: dict, output: Path) -> None:
    ext_sizes = stats["ext_sizes"]
    total_size = sum(ext_sizes.values()) or 1
    sorted_exts = sorted(ext_sizes.items(), key=lambda x: -x[1])[:8]
    colors = {
        '.js': '#f7df1e', '.ts': '#3178c6', '.py': '#3776ab', '.go': '#00add8',
        '.rs': '#dea584', '.rb': '#cc342d', '.css': '#264de4', '.html': '#e34c26',
        '.json': '#6b7280', '.md': '#083fa1', '.yaml': '#cb171e', '.yml': '#cb171e',
        '.mdx': '#083fa1', '.tsx': '#3178c6', '.jsx': '#61dafb', '.sh': '#4eaa25',
    }
    lang_bars = "".join(
        f'<div class="bar-row"><span class="bar-label">{ext}</span>'
        f'<div class="bar" style="width:{(size/total_size)*100}%;background:{colors.get(ext,"#6b7280")}"></div>'
        f'<span class="bar-pct">{(size/total_size)*100:.1f}%</span></div>'
        for ext, size in sorted_exts
    )
    def fmt(b):
        if b < 1024: return f"{b} B"
        if b < 1048576: return f"{b/1024:.1f} KB"
        return f"{b/1048576:.1f} MB"

    html = f'''<!DOCTYPE html>
<html><head>
  <meta charset="utf-8"><title>Codebase Explorer</title>
  <style>
    body {{ font: 14px/1.5 system-ui, sans-serif; margin: 0; background: #1a1a2e; color: #eee; }}
    .container {{ display: flex; height: 100vh; }}
    .sidebar {{ width: 280px; background: #252542; padding: 20px; border-right: 1px solid #3d3d5c; overflow-y: auto; flex-shrink: 0; }}
    .main {{ flex: 1; padding: 20px; overflow-y: auto; }}
    h1 {{ margin: 0 0 10px 0; font-size: 18px; }}
    h2 {{ margin: 20px 0 10px 0; font-size: 14px; color: #888; text-transform: uppercase; }}
    .stat {{ display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #3d3d5c; }}
    .stat-value {{ font-weight: bold; }}
    .bar-row {{ display: flex; align-items: center; margin: 6px 0; }}
    .bar-label {{ width: 55px; font-size: 12px; color: #aaa; }}
    .bar {{ height: 18px; border-radius: 3px; }}
    .bar-pct {{ margin-left: 8px; font-size: 12px; color: #666; }}
    .tree {{ list-style: none; padding-left: 20px; }}
    details {{ cursor: pointer; }}
    summary {{ padding: 4px 8px; border-radius: 4px; }}
    summary:hover {{ background: #2d2d44; }}
    .folder {{ color: #ffd700; }}
    .file {{ display: flex; align-items: center; padding: 4px 8px; border-radius: 4px; }}
    .file:hover {{ background: #2d2d44; }}
    .size {{ color: #888; margin-left: auto; font-size: 12px; }}
    .dot {{ width: 8px; height: 8px; border-radius: 50%; margin-right: 8px; }}
  </style>
</head><body>
  <div class="container">
    <div class="sidebar">
      <h1>📊 요약</h1>
      <div class="stat"><span>파일</span><span class="stat-value">{stats["files"]:,}</span></div>
      <div class="stat"><span>디렉토리</span><span class="stat-value">{stats["dirs"]:,}</span></div>
      <div class="stat"><span>총 크기</span><span class="stat-value">{fmt(data["size"])}</span></div>
      <div class="stat"><span>파일 유형</span><span class="stat-value">{len(stats["extensions"])}</span></div>
      <h2>파일 유형별</h2>
      {lang_bars}
    </div>
    <div class="main">
      <h1>📁 {data["name"]}</h1>
      <ul class="tree" id="root"></ul>
    </div>
  </div>
  <script>
    const data = {json.dumps(data)};
    const colors = {json.dumps(colors)};
    function fmt(b) {{ if (b < 1024) return b + ' B'; if (b < 1048576) return (b/1024).toFixed(1) + ' KB'; return (b/1048576).toFixed(1) + ' MB'; }}
    function render(node, parent) {{
      if (node.children) {{
        const det = document.createElement('details');
        det.open = parent === document.getElementById('root');
        det.innerHTML = `<summary><span class="folder">📁 ${{node.name}}</span><span class="size">${{fmt(node.size)}}</span></summary>`;
        const ul = document.createElement('ul'); ul.className = 'tree';
        node.children.sort((a,b) => (b.children?1:0)-(a.children?1:0) || a.name.localeCompare(b.name));
        node.children.forEach(c => render(c, ul));
        det.appendChild(ul);
        const li = document.createElement('li'); li.appendChild(det); parent.appendChild(li);
      }} else {{
        const li = document.createElement('li'); li.className = 'file';
        li.innerHTML = `<span class="dot" style="background:${{colors[node.ext]||'#6b7280'}}"></span>${{node.name}}<span class="size">${{fmt(node.size)}}</span>`;
        parent.appendChild(li);
      }}
    }}
    data.children.forEach(c => render(c, document.getElementById('root')));
  </script>
</body></html>'''
    output.write_text(html)

if __name__ == '__main__':
    target = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
    stats = {"files": 0, "dirs": 0, "extensions": Counter(), "ext_sizes": Counter()}
    data = scan(target, stats)
    out = Path('codebase-map.html')
    generate_html(data, stats, out)
    print(f'Generated {out.absolute()}')
    webbrowser.open(f'file://{out.absolute()}')
```

테스트하려면 모든 프로젝트에서 Claude Code를 열고 "이 코드베이스를 시각화합니다."라고 요청합니다. Claude는 스크립트를 실행하고 `codebase-map.html`을 생성한 후 브라우저에서 엽니다.

이 패턴은 모든 시각적 출력에 작동합니다: 종속성 그래프, 테스트 커버리지 보고서, API 문서 또는 데이터베이스 스키마 시각화입니다. 번들 스크립트가 무거운 작업을 수행하는 동안 Claude는 오케스트레이션을 처리합니다.

## 문제 해결

### 기술이 트리거되지 않음

Claude가 예상대로 기술을 사용하지 않으면:

1. 설명에 사용자가 자연스럽게 말할 키워드가 포함되어 있는지 확인합니다.
2. 기술이 `사용 가능한 기술은 무엇입니까?`에 나타나는지 확인합니다.
3. 설명과 더 가깝게 일치하도록 요청을 다시 표현해 봅니다.
4. 기술이 사용자 호출 가능하면 `/skill-name`으로 직접 호출해 봅니다.

### 기술이 너무 자주 트리거됨

Claude가 원하지 않을 때 기술을 사용하면:

1. 설명을 더 구체적으로 만듭니다.
2. 수동 호출만 원하면 `disable-model-invocation: true`를 추가합니다.

### Claude가 모든 기술을 보지 못함

기술 설명은 Claude가 사용 가능한 것을 알 수 있도록 컨텍스트에 로드됩니다. 많은 기술이 있으면 문자 예산(기본값 15,000자)을 초과할 수 있습니다. `/context`를 실행하여 제외된 기술에 대한 경고를 확인합니다.

제한을 늘리려면 `SLASH_COMMAND_TOOL_CHAR_BUDGET` 환경 변수를 설정합니다.

## 관련 리소스

* **[서브에이전트](/ko/sub-agents)**: 특화된 에이전트에 작업 위임
* **[플러그인](/ko/plugins)**: 다른 확장과 함께 기술 패키징 및 배포
* **[훅](/ko/hooks)**: 도구 이벤트 주변의 워크플로우 자동화
* **[메모리](/ko/memory)**: 지속적인 컨텍스트를 위한 CLAUDE.md 파일 관리
* **[대화형 모드](/ko/interactive-mode#built-in-commands)**: 기본 제공 명령어 및 바로 가기
* **[권한](/ko/iam)**: 도구 및 기술 액세스 제어
