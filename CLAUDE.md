# Project Overview
Spring/Java 기술 문서 기반 Q&A 서비스.
사용자가 질문하면 Elasticsearch에서 관련 문서 청크를 검색하고,
LLM이 해당 문서를 컨텍스트로 답변을 생성한다.
초기 도메인은 Spring/Java 공식 문서이며, 이후 다른 기술 문서로 확장 가능한 구조로 설계한다.

# Repository Structure
tech-doc-qa/
  backend/      Spring Boot (Java 21) - API 서버, 인증, 이력 관리
  rag-server/   Python FastAPI - ES 검색, LLM API 연동, 답변 생성
  frontend/     추후 추가
  elasticsearch/ 인덱스 매핑, 설정 파일
  docs/         설계 문서

# Architecture
- 서비스 간 통신 : REST (WebClient, 비동기)
- 인증 방식     : JWT (Access Token + Refresh Token)
- DB           : PostgreSQL
- Cache/Store  : Redis (Refresh Token, Rate Limiting)
- Search       : Elasticsearch
- LLM          : Claude API (추후 교체 가능한 구조)

# API Versioning
모든 API는 /api/v1/ prefix 사용

# Backend API Spec
POST   /api/v1/auth/signup        회원가입
POST   /api/v1/auth/login         로그인 → JWT 발급
POST   /api/v1/query              질문 → RAG 서버 위임 → 답변 반환
GET    /api/v1/query/history      내 검색 이력 조회 (Cursor 기반 페이지네이션)

# DB Schema
users       : id, email, password, created_at
query_logs  : id, user_id, question, answer, created_at

# Key Design Decisions
- RAG 서버 호출 전 트랜잭션 종료 → 응답 후 이력 저장 (커넥션 풀 고갈 방지)
- 검색 이력 저장은 @Async + @EventListener
- WebClient 타임아웃 설정 필수 (LLM 응답 지연 대비)
- Circuit Breaker (Resilience4j) - RAG 서버 장애 전파 방지
- Graceful Shutdown 활성화
- Rate Limiting - Redis 카운터 기반 사용자당 일일 요청 제한

# Common Conventions
- 언어: 모든 주석, 커밋 메시지, PR 설명은 한국어
- 커밋 메시지: feat/fix/docs/refactor/chore prefix 사용
- 환경변수: 민감 정보는 반드시 환경변수로 분리, 하드코딩 금지
- HTTP 상태코드: 표준 준수

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
