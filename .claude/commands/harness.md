# Claude Harness 관리

Claude가 읽는 모든 MD 파일(CLAUDE.md, rules, agents, commands)을 점검, 생성, 동기화한다.

**인자 없이 실행하면:** 전체 harness audit을 수행한다.
**인자가 있으면:** 해당 작업만 수행한다.

---

## 파일 크기 기준 (초과 시 분리 필요)

| 파일 유형 | 경고 기준 | 분리 필수 기준 |
|----------|----------|--------------|
| `CLAUDE.md` (루트/서비스) | 80줄 | 120줄 |
| `.claude/rules/*.md` | 50줄 | 80줄 |
| `.claude/agents/*.md` | 100줄 | 150줄 |
| `.claude/commands/*.md` | 80줄 | 120줄 |

**분리 기준 초과 시 처리:**
- 파일을 논리적 단위로 분할
- CLAUDE.md의 경우: 섹션별로 rules 파일로 추출 후 @import 교체
- rules 파일의 경우: 주제별로 파일 분리 (예: `security.md` → `security-jwt.md` + `security-input.md`)
- agents 파일의 경우: 역할이 너무 넓으면 전문화된 두 에이전트로 분리
- 분리 후 기존 파일은 삭제하고 @import 또는 신규 파일로 대체

---

## 인자 목록

| 인자 | 설명 |
|------|------|
| `audit` | 전체 harness 점검 (인자 없이 실행과 동일) |
| `add rule {이름}` | 새 규칙 파일 생성 후 관련 CLAUDE.md에 @import 추가 |
| `add agent {이름}` | 새 에이전트 파일 생성 |
| `add command {이름}` | 새 커맨드 파일 생성 |
| `update {파일경로}` | 특정 harness 파일을 최신 코드 상태에 맞게 업데이트 |
| `split {파일경로}` | 크기 초과 파일을 논리적 단위로 분리 |
| `sync` | git diff 기반으로 harness와 코드 간 불일치 탐지 후 업데이트 제안 |

---

## Audit 수행 절차

### Step 1. 파일 목록 수집
아래 경로의 파일을 모두 읽는다:
- `CLAUDE.md` (루트)
- `*/CLAUDE.md` (서비스별: backend, rag-server 등)
- `.claude/rules/*.md`
- `.claude/agents/*.md`
- `.claude/commands/*.md`
- `.claude/settings.local.json`

### Step 2. 파일 크기 점검
각 파일의 줄 수를 확인한다.
- 경고 기준 초과: `[WARN]` 표시
- 분리 필수 기준 초과: `[SPLIT]` 표시 + 분리 방법 제안

### Step 3. @import 참조 무결성 검사
CLAUDE.md 파일 내 `@경로` 참조를 모두 추출해 파일 존재 여부 확인.
- 존재하지 않는 경로: `[BROKEN]` 표시 + 실제 파일 경로 제안
- 참조됐지만 내용이 비어있는 파일: `[EMPTY]` 표시

### Step 4. Agent 품질 검사
각 agent의 frontmatter `description` 필드를 분석한다.

**좋은 description 기준:**
- "언제" 사용하는지 명확히 명시 (트리거 조건)
- 50자 이상, 200자 이하
- 다른 agent와 역할이 명확히 구분됨

**나쁜 description 패턴 (경고):**
- "도움을 준다", "관련 작업을 한다" 같은 모호한 표현
- 다른 agent와 description이 80% 이상 유사 (역할 중복)
- description 없음

### Step 5. Rules 품질 검사
각 rule 파일을 분석한다.

**좋은 rule 기준:**
- 각 항목이 구체적이고 실행 가능함 ("고려한다" X, "금지" / "필수" / "사용한다" O)
- 프로젝트 실제 코드와 일치 (존재하지 않는 클래스/패턴 참조 경고)
- 중복 항목 없음 (다른 rule 파일과 교차 비교)

**중복 탐지:**
- 두 파일 간 동일하거나 매우 유사한 규칙이 있으면 `[DUPLICATE]` 표시

### Step 6. 코드-Harness 불일치 탐지
아래 파일들을 읽어 harness에 반영되지 않은 항목 탐지:
- `backend/build.gradle`: 새 의존성 추가됐는데 관련 rule/agent 없는 경우
- `backend/src/` 패키지 구조: CLAUDE.md의 패키지 구조 설명과 불일치
- `docker-compose.yml`: 새 서비스 추가됐는데 agent/rule 없는 경우

### Step 7. 결과 출력

```
## Harness Audit 결과

### 파일 현황
| 파일 | 줄 수 | 상태 |
|------|------|------|
| CLAUDE.md | 45줄 | OK |
| backend/CLAUDE.md | 72줄 | OK |
| .claude/rules/security.md | 12줄 | OK |
| ... | ... | ... |

### 문제 항목
- [SPLIT] .claude/agents/xxx.md (152줄) — 분리 방법: ...
- [BROKEN] backend/CLAUDE.md:3 — @../missing-file.md 파일 없음
- [DUPLICATE] rules/security.md:4 ↔ rules/testing.md:7 — 동일 내용
- [WARN] .claude/agents/rag-builder.md — description 모호

### 코드-Harness 불일치
- build.gradle에 {의존성} 추가됐으나 관련 rule 없음 → 제안: ...

### 전체 점수
파일 수: {n}개 | 문제: {n}건 (SPLIT: n, BROKEN: n, WARN: n, DUPLICATE: n)
```

---

## 각 인자 수행 절차

@.claude/commands/harness-procedures.md
