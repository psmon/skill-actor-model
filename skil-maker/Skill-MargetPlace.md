> ## Documentation Index
> Fetch the complete documentation index at: https://code.claude.com/docs/llms.txt
> Use this file to discover all available pages before exploring further.

# 플러그인 마켓플레이스 생성 및 배포

> Claude Code 확장 프로그램을 배포하기 위한 플러그인 마켓플레이스를 구축하고 호스팅합니다.

플러그인 마켓플레이스는 다른 사용자에게 플러그인을 배포할 수 있는 카탈로그입니다. 마켓플레이스는 중앙 집중식 검색, 버전 추적, 자동 업데이트 및 여러 소스 유형(Git 저장소, 로컬 경로 등)을 지원합니다. 이 가이드에서는 팀이나 커뮤니티와 플러그인을 공유하기 위해 자신의 마켓플레이스를 만드는 방법을 보여줍니다.

기존 마켓플레이스에서 플러그인을 설치하려고 하시나요? [미리 빌드된 플러그인 검색 및 설치](/ko/discover-plugins)를 참조하세요.

## 개요

마켓플레이스를 생성하고 배포하려면 다음을 수행해야 합니다.

1. **플러그인 생성**: 명령어, 에이전트, hooks, MCP servers 또는 LSP servers를 사용하여 하나 이상의 플러그인을 빌드합니다. 이 가이드에서는 배포할 플러그인이 이미 있다고 가정합니다. 플러그인을 만드는 방법에 대한 자세한 내용은 [플러그인 생성](/ko/plugins)을 참조하세요.
2. **마켓플레이스 파일 생성**: 플러그인을 나열하고 찾을 위치를 정의하는 `marketplace.json`을 정의합니다([마켓플레이스 파일 생성](#create-the-marketplace-file) 참조).
3. **마켓플레이스 호스팅**: GitHub, GitLab 또는 다른 Git 호스트에 푸시합니다([마켓플레이스 호스팅 및 배포](#host-and-distribute-marketplaces) 참조).
4. **사용자와 공유**: 사용자가 `/plugin marketplace add`를 사용하여 마켓플레이스를 추가하고 개별 플러그인을 설치합니다([플러그인 검색 및 설치](/ko/discover-plugins) 참조).

마켓플레이스가 라이브 상태가 되면 저장소에 변경 사항을 푸시하여 업데이트할 수 있습니다. 사용자는 `/plugin marketplace update`를 사용하여 로컬 복사본을 새로 고칩니다.

## 연습: 로컬 마켓플레이스 생성

이 예제에서는 하나의 플러그인이 있는 마켓플레이스를 생성합니다. 코드 검토를 위한 `/review` skill입니다. 디렉터리 구조를 생성하고, skill을 추가하고, 플러그인 매니페스트와 마켓플레이스 카탈로그를 생성한 다음, 설치하고 테스트합니다.

<Steps>
  <Step title="디렉터리 구조 생성">
    ```bash  theme={null}
    mkdir -p my-marketplace/.claude-plugin
    mkdir -p my-marketplace/plugins/review-plugin/.claude-plugin
    mkdir -p my-marketplace/plugins/review-plugin/skills/review
    ```
  </Step>

  <Step title="skill 생성">
    `/review` skill이 수행하는 작업을 정의하는 `SKILL.md` 파일을 생성합니다.

    ```markdown my-marketplace/plugins/review-plugin/skills/review/SKILL.md theme={null}
    ---
    description: Review code for bugs, security, and performance
    disable-model-invocation: true
    ---

    Review the code I've selected or the recent changes for:
    - Potential bugs or edge cases
    - Security concerns
    - Performance issues
    - Readability improvements

    Be concise and actionable.
    ```
  </Step>

  <Step title="플러그인 매니페스트 생성">
    플러그인을 설명하는 `plugin.json` 파일을 생성합니다. 매니페스트는 `.claude-plugin/` 디렉터리에 있습니다.

    ```json my-marketplace/plugins/review-plugin/.claude-plugin/plugin.json theme={null}
    {
      "name": "review-plugin",
      "description": "Adds a /review skill for quick code reviews",
      "version": "1.0.0"
    }
    ```
  </Step>

  <Step title="마켓플레이스 파일 생성">
    플러그인을 나열하는 마켓플레이스 카탈로그를 생성합니다.

    ```json my-marketplace/.claude-plugin/marketplace.json theme={null}
    {
      "name": "my-plugins",
      "owner": {
        "name": "Your Name"
      },
      "plugins": [
        {
          "name": "review-plugin",
          "source": "./plugins/review-plugin",
          "description": "Adds a /review skill for quick code reviews"
        }
      ]
    }
    ```
  </Step>

  <Step title="추가 및 설치">
    마켓플레이스를 추가하고 플러그인을 설치합니다.

    ```shell  theme={null}
    /plugin marketplace add ./my-marketplace
    /plugin install review-plugin@my-plugins
    ```
  </Step>

  <Step title="시도해보기">
    편집기에서 일부 코드를 선택하고 새 명령어를 실행합니다.

    ```shell  theme={null}
    /review
    ```
  </Step>
</Steps>

플러그인이 수행할 수 있는 작업(hooks, 에이전트, MCP servers 및 LSP servers 포함)에 대해 자세히 알아보려면 [플러그인](/ko/plugins)을 참조하세요.

<Note>
  **플러그인 설치 방법**: 사용자가 플러그인을 설치하면 Claude Code는 플러그인 디렉터리를 캐시 위치에 복사합니다. 이는 플러그인이 `../shared-utils`와 같은 경로를 사용하여 디렉터리 외부의 파일을 참조할 수 없음을 의미합니다. 왜냐하면 해당 파일이 복사되지 않기 때문입니다.

플러그인 간에 파일을 공유해야 하는 경우 symlinks를 사용하거나(복사 중에 따라갑니다) 마켓플레이스를 재구성하여 공유 디렉터리가 플러그인 소스 경로 내에 있도록 합니다. 자세한 내용은 [플러그인 캐싱 및 파일 해석](/ko/plugins-reference#plugin-caching-and-file-resolution)을 참조하세요.
</Note>

## 마켓플레이스 파일 생성

저장소 루트에 `.claude-plugin/marketplace.json`을 생성합니다. 이 파일은 마켓플레이스의 이름, 소유자 정보 및 소스가 있는 플러그인 목록을 정의합니다.

각 플러그인 항목에는 최소한 `name`과 `source`(가져올 위치)가 필요합니다. 사용 가능한 모든 필드는 아래의 [전체 스키마](#marketplace-schema)를 참조하세요.

```json  theme={null}
{
  "name": "company-tools",
  "owner": {
    "name": "DevTools Team",
    "email": "devtools@example.com"
  },
  "plugins": [
    {
      "name": "code-formatter",
      "source": "./plugins/formatter",
      "description": "Automatic code formatting on save",
      "version": "2.1.0",
      "author": {
        "name": "DevTools Team"
      }
    },
    {
      "name": "deployment-tools",
      "source": {
        "source": "github",
        "repo": "company/deploy-plugin"
      },
      "description": "Deployment automation tools"
    }
  ]
}
```

## 마켓플레이스 스키마

### 필수 필드

| 필드        | 유형     | 설명                                                                                                                  | 예제             |
| :-------- | :----- | :------------------------------------------------------------------------------------------------------------------ | :------------- |
| `name`    | string | 마켓플레이스 식별자(kebab-case, 공백 없음). 이는 공개 대면입니다. 사용자는 플러그인을 설치할 때 이를 봅니다(예: `/plugin install my-tool@your-marketplace`). | `"acme-tools"` |
| `owner`   | object | 마켓플레이스 유지 관리자 정보([아래 필드 참조](#owner-fields))                                                                         |                |
| `plugins` | array  | 사용 가능한 플러그인 목록                                                                                                      | 아래 참조          |

<Note>
  **예약된 이름**: 다음 마켓플레이스 이름은 공식 Anthropic 사용을 위해 예약되어 있으며 타사 마켓플레이스에서 사용할 수 없습니다. `claude-code-marketplace`, `claude-code-plugins`, `claude-plugins-official`, `anthropic-marketplace`, `anthropic-plugins`, `agent-skills`, `life-sciences`. 공식 마켓플레이스를 사칭하는 이름(예: `official-claude-plugins` 또는 `anthropic-tools-v2`)도 차단됩니다.
</Note>

### 소유자 필드

| 필드      | 유형     | 필수  | 설명              |
| :------ | :----- | :-- | :-------------- |
| `name`  | string | 예   | 유지 관리자 또는 팀의 이름 |
| `email` | string | 아니오 | 유지 관리자의 연락처 이메일 |

### 선택적 메타데이터

| 필드                     | 유형     | 설명                                                                                                                            |
| :--------------------- | :----- | :---------------------------------------------------------------------------------------------------------------------------- |
| `metadata.description` | string | 간단한 마켓플레이스 설명                                                                                                                 |
| `metadata.version`     | string | 마켓플레이스 버전                                                                                                                     |
| `metadata.pluginRoot`  | string | 상대 플러그인 소스 경로에 앞에 붙는 기본 디렉터리(예: `"./plugins"`를 사용하면 `"source": "./plugins/formatter"` 대신 `"source": "formatter"`를 작성할 수 있습니다) |

## 플러그인 항목

`plugins` 배열의 각 플러그인 항목은 플러그인과 찾을 위치를 설명합니다. [플러그인 매니페스트 스키마](/ko/plugins-reference#plugin-manifest-schema)의 모든 필드(예: `description`, `version`, `author`, `commands`, `hooks` 등)와 이러한 마켓플레이스 특정 필드를 포함할 수 있습니다. `source`, `category`, `tags` 및 `strict`.

### 필수 필드

| 필드       | 유형             | 설명                                                                                                       |
| :------- | :------------- | :------------------------------------------------------------------------------------------------------- |
| `name`   | string         | 플러그인 식별자(kebab-case, 공백 없음). 이는 공개 대면입니다. 사용자는 설치할 때 이를 봅니다(예: `/plugin install my-plugin@marketplace`). |
| `source` | string\|object | 플러그인을 가져올 위치([아래 플러그인 소스](#plugin-sources) 참조)                                                           |

### 선택적 플러그인 필드

**표준 메타데이터 필드:**

| 필드            | 유형      | 설명                                                                                                                   |
| :------------ | :------ | :------------------------------------------------------------------------------------------------------------------- |
| `description` | string  | 간단한 플러그인 설명                                                                                                          |
| `version`     | string  | 플러그인 버전                                                                                                              |
| `author`      | object  | 플러그인 작성자 정보(`name` 필수, `email` 선택)                                                                                   |
| `homepage`    | string  | 플러그인 홈페이지 또는 문서 URL                                                                                                  |
| `repository`  | string  | 소스 코드 저장소 URL                                                                                                        |
| `license`     | string  | SPDX 라이선스 식별자(예: MIT, Apache-2.0)                                                                                    |
| `keywords`    | array   | 플러그인 검색 및 분류를 위한 태그                                                                                                  |
| `category`    | string  | 조직을 위한 플러그인 카테고리                                                                                                     |
| `tags`        | array   | 검색 가능성을 위한 태그                                                                                                        |
| `strict`      | boolean | true(기본값)일 때 마켓플레이스 구성 요소 필드가 plugin.json과 병합됩니다. false일 때 마켓플레이스 항목이 플러그인을 완전히 정의하고 plugin.json도 구성 요소를 선언하면 안 됩니다. |

**구성 요소 구성 필드:**

| 필드           | 유형             | 설명                             |
| :----------- | :------------- | :----------------------------- |
| `commands`   | string\|array  | 명령어 파일 또는 디렉터리의 사용자 정의 경로      |
| `agents`     | string\|array  | 에이전트 파일의 사용자 정의 경로             |
| `hooks`      | string\|object | 사용자 정의 hooks 구성 또는 hooks 파일 경로 |
| `mcpServers` | string\|object | MCP server 구성 또는 MCP 구성 파일 경로  |
| `lspServers` | string\|object | LSP server 구성 또는 LSP 구성 파일 경로  |

## 플러그인 소스

### 상대 경로

동일한 저장소의 플러그인의 경우:

```json  theme={null}
{
  "name": "my-plugin",
  "source": "./plugins/my-plugin"
}
```

<Note>
  상대 경로는 사용자가 Git(GitHub, GitLab 또는 Git URL)을 통해 마켓플레이스를 추가할 때만 작동합니다. 사용자가 `marketplace.json` 파일에 대한 직접 URL을 통해 마켓플레이스를 추가하면 상대 경로가 올바르게 해석되지 않습니다. URL 기반 배포의 경우 GitHub, npm 또는 Git URL 소스를 대신 사용하세요. 자세한 내용은 [문제 해결](#plugins-with-relative-paths-fail-in-url-based-marketplaces)을 참조하세요.
</Note>

### GitHub 저장소

```json  theme={null}
{
  "name": "github-plugin",
  "source": {
    "source": "github",
    "repo": "owner/plugin-repo"
  }
}
```

특정 분기, 태그 또는 커밋에 고정할 수 있습니다.

```json  theme={null}
{
  "name": "github-plugin",
  "source": {
    "source": "github",
    "repo": "owner/plugin-repo",
    "ref": "v2.0.0",
    "sha": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0"
  }
}
```

| 필드     | 유형     | 설명                                |
| :----- | :----- | :-------------------------------- |
| `repo` | string | 필수. `owner/repo` 형식의 GitHub 저장소   |
| `ref`  | string | 선택. Git 분기 또는 태그(저장소 기본 분기로 기본값)  |
| `sha`  | string | 선택. 정확한 버전에 고정할 전체 40자 Git 커밋 SHA |

### Git 저장소

```json  theme={null}
{
  "name": "git-plugin",
  "source": {
    "source": "url",
    "url": "https://gitlab.com/team/plugin.git"
  }
}
```

특정 분기, 태그 또는 커밋에 고정할 수 있습니다.

```json  theme={null}
{
  "name": "git-plugin",
  "source": {
    "source": "url",
    "url": "https://gitlab.com/team/plugin.git",
    "ref": "main",
    "sha": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0"
  }
}
```

| 필드    | 유형     | 설명                                 |
| :---- | :----- | :--------------------------------- |
| `url` | string | 필수. 전체 Git 저장소 URL(`.git`으로 끝나야 함) |
| `ref` | string | 선택. Git 분기 또는 태그(저장소 기본 분기로 기본값)   |
| `sha` | string | 선택. 정확한 버전에 고정할 전체 40자 Git 커밋 SHA  |

### 고급 플러그인 항목

이 예제는 명령어, 에이전트, hooks 및 MCP servers에 대한 사용자 정의 경로를 포함하여 많은 선택적 필드를 사용하는 플러그인 항목을 보여줍니다.

```json  theme={null}
{
  "name": "enterprise-tools",
  "source": {
    "source": "github",
    "repo": "company/enterprise-plugin"
  },
  "description": "Enterprise workflow automation tools",
  "version": "2.1.0",
  "author": {
    "name": "Enterprise Team",
    "email": "enterprise@example.com"
  },
  "homepage": "https://docs.example.com/plugins/enterprise-tools",
  "repository": "https://github.com/company/enterprise-plugin",
  "license": "MIT",
  "keywords": ["enterprise", "workflow", "automation"],
  "category": "productivity",
  "commands": [
    "./commands/core/",
    "./commands/enterprise/",
    "./commands/experimental/preview.md"
  ],
  "agents": ["./agents/security-reviewer.md", "./agents/compliance-checker.md"],
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PLUGIN_ROOT}/scripts/validate.sh"
          }
        ]
      }
    ]
  },
  "mcpServers": {
    "enterprise-db": {
      "command": "${CLAUDE_PLUGIN_ROOT}/servers/db-server",
      "args": ["--config", "${CLAUDE_PLUGIN_ROOT}/config.json"]
    }
  },
  "strict": false
}
```

주목할 주요 사항:

* **`commands` 및 `agents`**: 여러 디렉터리 또는 개별 파일을 지정할 수 있습니다. 경로는 플러그인 루트에 상대적입니다.
* **`${CLAUDE_PLUGIN_ROOT}`**: hooks 및 MCP server 구성에서 이 변수를 사용하여 플러그인의 설치 디렉터리 내의 파일을 참조합니다. 플러그인이 설치될 때 캐시 위치에 복사되기 때문에 필요합니다.
* **`strict: false`**: false로 설정되어 있으므로 플러그인은 자신의 `plugin.json`이 필요하지 않습니다. 마켓플레이스 항목이 모든 것을 정의합니다.

## 마켓플레이스 호스팅 및 배포

### GitHub에서 호스팅(권장)

GitHub는 가장 쉬운 배포 방법을 제공합니다.

1. **저장소 생성**: 마켓플레이스를 위한 새 저장소 설정
2. **마켓플레이스 파일 추가**: 플러그인 정의가 있는 `.claude-plugin/marketplace.json` 생성
3. **팀과 공유**: 사용자가 `/plugin marketplace add owner/repo`를 사용하여 마켓플레이스를 추가합니다.

**이점**: 기본 제공 버전 제어, 문제 추적 및 팀 협업 기능.

### 다른 Git 서비스에서 호스팅

GitLab, Bitbucket 및 자체 호스팅 서버와 같은 모든 Git 호스팅 서비스가 작동합니다. 사용자는 전체 저장소 URL을 사용하여 추가합니다.

```shell  theme={null}
/plugin marketplace add https://gitlab.com/company/plugins.git
```

### 비공개 저장소

Claude Code는 비공개 저장소에서 플러그인 설치를 지원합니다. 수동 설치 및 업데이트의 경우 Claude Code는 기존 Git 자격 증명 도우미를 사용합니다. 터미널에서 비공개 저장소에 대해 `git clone`이 작동하면 Claude Code에서도 작동합니다. 일반적인 자격 증명 도우미에는 GitHub의 `gh auth login`, macOS Keychain 및 `git-credential-store`가 포함됩니다.

백그라운드 자동 업데이트는 시작 시 자격 증명 도우미 없이 실행됩니다. 대화형 프롬프트가 Claude Code 시작을 차단하기 때문입니다. 비공개 마켓플레이스에 대해 자동 업데이트를 활성화하려면 환경에서 적절한 인증 토큰을 설정하세요.

| 공급자       | 환경 변수                        | 참고                         |
| :-------- | :--------------------------- | :------------------------- |
| GitHub    | `GITHUB_TOKEN` 또는 `GH_TOKEN` | 개인 액세스 토큰 또는 GitHub App 토큰 |
| GitLab    | `GITLAB_TOKEN` 또는 `GL_TOKEN` | 개인 액세스 토큰 또는 프로젝트 토큰       |
| Bitbucket | `BITBUCKET_TOKEN`            | 앱 비밀번호 또는 저장소 액세스 토큰       |

셸 구성(예: `.bashrc`, `.zshrc`)에서 토큰을 설정하거나 Claude Code를 실행할 때 전달하세요.

```bash  theme={null}
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
```

<Note>
  CI/CD 환경의 경우 토큰을 비밀 환경 변수로 구성하세요. GitHub Actions는 동일한 조직의 저장소에 대해 자동으로 `GITHUB_TOKEN`을 제공합니다.
</Note>

### 배포 전에 로컬에서 테스트

배포하기 전에 마켓플레이스를 로컬에서 테스트하세요.

```shell  theme={null}
/plugin marketplace add ./my-local-marketplace
/plugin install test-plugin@my-local-marketplace
```

추가 명령어의 전체 범위(GitHub, Git URL, 로컬 경로, 원격 URL)는 [마켓플레이스 추가](/ko/discover-plugins#add-marketplaces)를 참조하세요.

### 팀을 위해 마켓플레이스 필요

저장소를 구성하여 팀 구성원이 프로젝트 폴더를 신뢰할 때 마켓플레이스를 자동으로 설치하도록 프롬프트됩니다. `.claude/settings.json`에 마켓플레이스를 추가하세요.

```json  theme={null}
{
  "extraKnownMarketplaces": {
    "company-tools": {
      "source": {
        "source": "github",
        "repo": "your-org/claude-plugins"
      }
    }
  }
}
```

기본적으로 활성화해야 하는 플러그인을 지정할 수도 있습니다.

```json  theme={null}
{
  "enabledPlugins": {
    "code-formatter@company-tools": true,
    "deployment-tools@company-tools": true
  }
}
```

전체 구성 옵션은 [플러그인 설정](/ko/settings#plugin-settings)을 참조하세요.

### 관리되는 마켓플레이스 제한

플러그인 소스에 대한 엄격한 제어가 필요한 조직의 경우 관리자는 관리되는 설정에서 [`strictKnownMarketplaces`](/ko/settings#strictknownmarketplaces) 설정을 사용하여 사용자가 추가할 수 있는 플러그인 마켓플레이스를 제한할 수 있습니다.

`strictKnownMarketplaces`가 관리되는 설정에서 구성되면 제한 동작은 값에 따라 달라집니다.

| 값            | 동작                                       |
| ------------ | ---------------------------------------- |
| 정의되지 않음(기본값) | 제한 없음. 사용자는 모든 마켓플레이스를 추가할 수 있습니다.       |
| 빈 배열 `[]`    | 완전한 잠금. 사용자는 새 마켓플레이스를 추가할 수 없습니다.       |
| 소스 목록        | 사용자는 허용 목록과 정확히 일치하는 마켓플레이스만 추가할 수 있습니다. |

#### 일반적인 구성

모든 마켓플레이스 추가 비활성화:

```json  theme={null}
{
  "strictKnownMarketplaces": []
}
```

특정 마켓플레이스만 허용:

```json  theme={null}
{
  "strictKnownMarketplaces": [
    {
      "source": "github",
      "repo": "acme-corp/approved-plugins"
    },
    {
      "source": "github",
      "repo": "acme-corp/security-tools",
      "ref": "v2.0"
    },
    {
      "source": "url",
      "url": "https://plugins.example.com/marketplace.json"
    }
  ]
}
```

정규식 패턴 일치를 사용하여 내부 Git 서버의 모든 마켓플레이스 허용:

```json  theme={null}
{
  "strictKnownMarketplaces": [
    {
      "source": "hostPattern",
      "hostPattern": "^github\\.example\\.com$"
    }
  ]
}
```

#### 제한 작동 방식

제한은 플러그인 설치 프로세스 초기에 검증되며 네트워크 요청이나 파일 시스템 작업이 발생하기 전입니다. 이는 무단 마켓플레이스 액세스 시도를 방지합니다.

허용 목록은 대부분의 소스 유형에 대해 정확한 일치를 사용합니다. 마켓플레이스가 허용되려면 지정된 모든 필드가 정확히 일치해야 합니다.

* GitHub 소스의 경우: `repo`는 필수이며, 허용 목록에 지정된 경우 `ref` 또는 `path`도 일치해야 합니다.
* URL 소스의 경우: 전체 URL이 정확히 일치해야 합니다.
* `hostPattern` 소스의 경우: 마켓플레이스 호스트가 정규식 패턴과 일치합니다.

`strictKnownMarketplaces`는 [관리되는 설정](/ko/settings#settings-files)에서 설정되므로 개별 사용자 및 프로젝트 구성은 이러한 제한을 재정의할 수 없습니다.

지원되는 모든 소스 유형 및 `extraKnownMarketplaces`와의 비교를 포함한 전체 구성 세부 정보는 [strictKnownMarketplaces 참조](/ko/settings#strictknownmarketplaces)를 참조하세요.

## 검증 및 테스트

배포하기 전에 마켓플레이스를 테스트하세요.

마켓플레이스 JSON 구문을 검증하세요.

```bash  theme={null}
claude plugin validate .
```

또는 Claude Code 내에서:

```shell  theme={null}
/plugin validate .
```

테스트를 위해 마켓플레이스를 추가하세요.

```shell  theme={null}
/plugin marketplace add ./path/to/marketplace
```

모든 것이 작동하는지 확인하기 위해 테스트 플러그인을 설치하세요.

```shell  theme={null}
/plugin install test-plugin@marketplace-name
```

완전한 플러그인 테스트 워크플로우는 [플러그인을 로컬에서 테스트](/ko/plugins#test-your-plugins-locally)를 참조하세요. 기술적 문제 해결은 [플러그인 참조](/ko/plugins-reference)를 참조하세요.

## 문제 해결

### 마켓플레이스가 로드되지 않음

**증상**: 마켓플레이스를 추가할 수 없거나 마켓플레이스에서 플러그인을 볼 수 없음

**해결책**:

* 마켓플레이스 URL에 액세스할 수 있는지 확인하세요.
* `.claude-plugin/marketplace.json`이 지정된 경로에 있는지 확인하세요.
* `claude plugin validate` 또는 `/plugin validate`를 사용하여 JSON 구문이 유효한지 확인하세요.
* 비공개 저장소의 경우 액세스 권한이 있는지 확인하세요.

### 마켓플레이스 검증 오류

마켓플레이스 디렉터리에서 `claude plugin validate .` 또는 `/plugin validate .`를 실행하여 문제를 확인하세요. 일반적인 오류:

| 오류                                                | 원인                | 해결책                                            |
| :------------------------------------------------ | :---------------- | :--------------------------------------------- |
| `File not found: .claude-plugin/marketplace.json` | 매니페스트 누락          | 필수 필드가 있는 `.claude-plugin/marketplace.json` 생성 |
| `Invalid JSON syntax: Unexpected token...`        | JSON 구문 오류        | 누락된 쉼표, 추가 쉼표 또는 따옴표 없는 문자열 확인                 |
| `Duplicate plugin name "x" found in marketplace`  | 두 플러그인이 동일한 이름 공유 | 각 플러그인에 고유한 `name` 값 지정                        |
| `plugins[0].source: Path traversal not allowed`   | 소스 경로에 `..` 포함    | `..` 없이 마켓플레이스 루트에 상대적인 경로 사용                  |

**경고**(차단하지 않음):

* `Marketplace has no plugins defined`: `plugins` 배열에 최소한 하나의 플러그인 추가
* `No marketplace description provided`: 사용자가 마켓플레이스를 이해하도록 돕기 위해 `metadata.description` 추가
* `Plugin "x" uses npm source which is not yet fully implemented`: 대신 `github` 또는 로컬 경로 소스 사용

### 플러그인 설치 실패

**증상**: 마켓플레이스는 나타나지만 플러그인 설치 실패

**해결책**:

* 플러그인 소스 URL에 액세스할 수 있는지 확인하세요.
* 플러그인 디렉터리에 필수 파일이 포함되어 있는지 확인하세요.
* GitHub 소스의 경우 저장소가 공개이거나 액세스 권한이 있는지 확인하세요.
* 플러그인 소스를 수동으로 복제/다운로드하여 테스트하세요.

### 비공개 저장소 인증 실패

**증상**: 비공개 저장소에서 플러그인을 설치할 때 인증 오류

**해결책**:

수동 설치 및 업데이트의 경우:

* Git 공급자로 인증되었는지 확인하세요(예: GitHub의 경우 `gh auth status` 실행).
* 자격 증명 도우미가 올바르게 구성되었는지 확인하세요. `git config --global credential.helper`
* 저장소를 수동으로 복제하여 자격 증명이 작동하는지 확인하세요.

백그라운드 자동 업데이트의 경우:

* 환경에서 적절한 토큰을 설정했는지 확인하세요. `echo $GITHUB_TOKEN`
* 토큰에 필수 권한이 있는지 확인하세요(저장소에 대한 읽기 액세스).
* GitHub의 경우 토큰에 비공개 저장소에 대한 `repo` 범위가 있는지 확인하세요.
* GitLab의 경우 토큰에 최소한 `read_repository` 범위가 있는지 확인하세요.
* 토큰이 만료되지 않았는지 확인하세요.

### 상대 경로가 있는 플러그인이 URL 기반 마켓플레이스에서 실패

**증상**: URL을 통해 마켓플레이스를 추가했습니다(예: `https://example.com/marketplace.json`). 하지만 `"./plugins/my-plugin"`과 같은 상대 경로 소스가 있는 플러그인이 "경로를 찾을 수 없음" 오류로 설치 실패합니다.

**원인**: URL 기반 마켓플레이스는 `marketplace.json` 파일 자체만 다운로드합니다. 서버에서 플러그인 파일을 다운로드하지 않습니다. 마켓플레이스 항목의 상대 경로는 다운로드되지 않은 원격 서버의 파일을 참조합니다.

**해결책**:

* **외부 소스 사용**: 플러그인 항목을 상대 경로 대신 GitHub, npm 또는 Git URL 소스를 사용하도록 변경하세요.
  ```json  theme={null}
  { "name": "my-plugin", "source": { "source": "github", "repo": "owner/repo" } }
  ```
* **Git 기반 마켓플레이스 사용**: Git 저장소에서 마켓플레이스를 호스팅하고 Git URL을 사용하여 추가하세요. Git 기반 마켓플레이스는 전체 저장소를 복제하여 상대 경로가 올바르게 작동합니다.

### 설치 후 파일을 찾을 수 없음

**증상**: 플러그인이 설치되지만 파일 참조가 실패합니다. 특히 플러그인 디렉터리 외부의 파일

**원인**: 플러그인은 제자리에 사용되지 않고 캐시 디렉터리에 복사됩니다. 플러그인 디렉터리 외부의 파일을 참조하는 경로(예: `../shared-utils`)는 해당 파일이 복사되지 않기 때문에 작동하지 않습니다.

**해결책**: symlinks 및 디렉터리 재구성을 포함한 해결 방법은 [플러그인 캐싱 및 파일 해석](/ko/plugins-reference#plugin-caching-and-file-resolution)을 참조하세요.

추가 디버깅 도구 및 일반적인 문제는 [디버깅 및 개발 도구](/ko/plugins-reference#debugging-and-development-tools)를 참조하세요.

## 참고 항목

* [미리 빌드된 플러그인 검색 및 설치](/ko/discover-plugins) - 기존 마켓플레이스에서 플러그인 설치
* [플러그인](/ko/plugins) - 자신의 플러그인 생성
* [플러그인 참조](/ko/plugins-reference) - 완전한 기술 사양 및 스키마
* [플러그인 설정](/ko/settings#plugin-settings) - 플러그인 구성 옵션
* [strictKnownMarketplaces 참조](/ko/settings#strictknownmarketplaces) - 관리되는 마켓플레이스 제한