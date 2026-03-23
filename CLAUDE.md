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
- 검색 이력 저장은 @Async + @TransactionalEventListener(AFTER_COMMIT)
- WebClient 타임아웃 설정 필수 (LLM 응답 지연 대비)
- Circuit Breaker (Resilience4j) - RAG 서버 장애 전파 방지
- Graceful Shutdown 활성화
- Rate Limiting - Redis 카운터 기반 사용자당 일일 요청 제한

# Common Conventions
- 언어: 모든 주석, 커밋 메시지, PR 설명은 한국어
- 커밋 메시지: feat/fix/docs/refactor/chore prefix 사용
- 환경변수: 민감 정보는 반드시 환경변수로 분리, 하드코딩 금지
- HTTP 상태코드: 표준 준수