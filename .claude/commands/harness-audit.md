# Harness Audit 수행 절차

## Step 1. 파일 목록 수집
아래 경로의 파일을 모두 읽는다:
- `CLAUDE.md` (루트)
- `*/CLAUDE.md` (서비스별: backend, rag-server 등)
- `.claude/rules/*.md`
- `.claude/agents/*.md`
- `.claude/commands/*.md`
- `.claude/settings.local.json`

## Step 2. 파일 크기 점검
각 파일의 줄 수를 확인한다.
- 경고 기준 초과: `[WARN]` 표시
- 분리 필수 기준 초과: `[SPLIT]` 표시 + 분리 방법 제안

## Step 3. @import 참조 무결성 검사
CLAUDE.md 파일 내 `@경로` 참조를 모두 추출해 파일 존재 여부 확인.
- 존재하지 않는 경로: `[BROKEN]` 표시 + 실제 파일 경로 제안
- 참조됐지만 내용이 비어있는 파일: `[EMPTY]` 표시

## Step 4. Agent 품질 검사
각 agent의 frontmatter `description` 필드를 분석한다.

**좋은 description 기준:**
- "언제" 사용하는지 명확히 명시 (트리거 조건)
- 50자 이상, 200자 이하
- 다른 agent와 역할이 명확히 구분됨

**나쁜 description 패턴 (경고):**
- "도움을 준다", "관련 작업을 한다" 같은 모호한 표현
- 다른 agent와 description이 80% 이상 유사 (역할 중복)
- description 없음

## Step 5. Rules 품질 검사
각 rule 파일을 분석한다.

**좋은 rule 기준:**
- 각 항목이 구체적이고 실행 가능함 ("고려한다" X, "금지" / "필수" / "사용한다" O)
- 프로젝트 실제 코드와 일치 (존재하지 않는 클래스/패턴 참조 경고)
- 중복 항목 없음 (다른 rule 파일과 교차 비교)

**중복 탐지:**
- 두 파일 간 동일하거나 매우 유사한 규칙이 있으면 `[DUPLICATE]` 표시

**컨텍스트 오염 탐지 (`[CONTAMINATED]`):**
아래 패턴이 발견되면 경고한다.
- 원칙을 표현해야 할 항목에 특정 어노테이션/클래스명이 구문 그대로 박힌 경우
  (예: "트랜잭션 커밋 후 실행이 필요한지 판단한다" 가 아닌 `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용 형태)
- 일반 원칙이어야 할 항목에 특정 서비스명이 들어간 경우
  (예: "503(RAG 서버 장애)" — 의도적 프로젝트 컨벤션이 아니라면 "503(외부 서비스 장애)"이 맞음)
- 실제 코드 패턴과 다른 내용이 규칙으로 굳은 경우
  (예: 코드는 `@MockitoBean`을 쓰는데 규칙은 `@MockBean`으로 명시)

## Step 6. 코드-Harness 불일치 탐지
아래 파일들을 읽어 harness에 반영되지 않은 항목 탐지:
- `backend/build.gradle`: 새 의존성 추가됐는데 관련 rule/agent 없는 경우
- `backend/src/` 패키지 구조: CLAUDE.md의 패키지 구조 설명과 불일치
- `docker-compose.yml`: 새 서비스 추가됐는데 agent/rule 없는 경우

## Step 7. 결과 출력

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
- [CONTAMINATED] .claude/agents/xxx.md:25 — 설명
- [WARN] .claude/agents/rag-builder.md — description 모호

### 코드-Harness 불일치
- build.gradle에 {의존성} 추가됐으나 관련 rule 없음 → 제안: ...

### 전체 점수
파일 수: {n}개 | 문제: {n}건 (SPLIT: n, BROKEN: n, WARN: n, DUPLICATE: n, CONTAMINATED: n)
```
