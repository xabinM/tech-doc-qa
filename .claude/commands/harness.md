# Claude Harness 관리

Claude가 읽는 모든 MD 파일(CLAUDE.md, rules, agents, commands)을 관리한다.

**인자 없이 실행하면:** 현재 harness 전체 현황을 점검한다.
**인자가 있으면:** 해당 작업을 수행한다.

---

## 인자 목록

| 인자 | 설명 |
|------|------|
| `audit` | 전체 harness 점검 (누락, 중복, 깨진 참조 등) |
| `add rule {이름}` | 새 규칙 파일 생성 후 관련 CLAUDE.md에 @import 추가 |
| `add agent {이름}` | 새 에이전트 파일 생성 (description 포함) |
| `add command {이름}` | 새 커맨드 파일 생성 |
| `update {파일경로}` | 기존 harness 파일을 최신 프로젝트 상태에 맞게 업데이트 |
| `sync` | 코드베이스를 분석해 harness가 현실과 맞는지 확인하고 업데이트 제안 |

---

## 인자 없이 실행 시 — Harness Audit

다음 항목을 순서대로 점검하고 결과를 표로 출력한다.

### 1. 파일 현황 파악
아래 경로를 읽어 현재 harness 구성 목록화:
- `CLAUDE.md` (루트)
- `backend/CLAUDE.md`
- `.claude/rules/*.md`
- `.claude/agents/*.md`
- `.claude/commands/*.md`
- `.claude/settings.local.json`

### 2. @import 참조 검증
CLAUDE.md 파일의 `@경로` 참조가 실제 파일과 일치하는지 확인.
깨진 참조가 있으면 경로와 함께 명시.

### 3. Agent description 품질 검사
각 agent의 `description` 필드가 "언제 사용하는지" 명확히 설명하는지 확인.
모호한 description은 개선 제안.

### 4. 누락 항목 탐지
최근 코드 변경사항을 보고 harness에 반영되지 않은 내용 탐지:
- 새 도메인/기능이 추가됐는데 관련 rule이 없는 경우
- 새 외부 의존성이 추가됐는데 agent/rule이 없는 경우

### 5. 출력 형식
```
## Harness 현황
- CLAUDE.md: [루트, backend]
- Rules: [목록]
- Agents: [목록]
- Commands: [목록]

## 문제 항목
- [경로] 문제 설명

## 업데이트 제안
- 제안 내용
```

---

## `sync` 실행 시
1. `git diff main...HEAD` 로 최근 변경사항 파악
2. 변경된 기능/의존성과 현재 harness 비교
3. 추가/수정이 필요한 rules, agents 목록 제시
4. 사용자 확인 후 실행
