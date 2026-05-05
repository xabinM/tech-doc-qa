# Claude Behavioral Guidelines

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan and get confirmation before starting.

## 5. Destructive Action Rules

**아래 행동은 반드시 사용자에게 명시적으로 확인을 받은 후에만 실행한다. 자율적으로 진행 금지.**

### 절대 자율 실행 금지 (항상 멈추고 확인)
- **Flyway 마이그레이션 파일 수정 또는 삭제** — 운영 DB 파괴로 이어짐
- **`.env` 또는 민감 정보 파일 git add/commit** — 비밀 정보 유출
- **`git push --force`** — 공유 브랜치 이력 파괴
- **`git reset --hard`** — 커밋되지 않은 작업 영구 소멸
- **`git clean -f`** — untracked 파일 전부 삭제
- **`docker-compose down -v`** — DB 볼륨 삭제, 데이터 전체 소멸
- **`DROP TABLE` / `TRUNCATE`** — 테이블 데이터 삭제
- **`rm -rf`** — 디렉토리 재귀 삭제
- **harness 파일 삭제** (`.claude/rules/`, `.claude/agents/`, `.claude/commands/`) — Claude 동작 기반 파괴

### 확인 절차
위 행동이 필요하다고 판단되면:
1. 어떤 행동을 왜 하려는지 설명
2. 되돌릴 수 없음을 명시
3. 사용자의 명시적 "진행해" 또는 "yes" 확인 후 실행
