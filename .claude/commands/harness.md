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

@.claude/commands/harness-audit.md

---

## 각 인자 수행 절차

@.claude/commands/harness-procedures.md
