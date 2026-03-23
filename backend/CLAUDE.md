# Backend Overview
Spring Boot 3.5.x (Java 21) 기반 API 서버.
인증, 질의 처리, 검색 이력 관리를 담당한다.
RAG 처리는 Python FastAPI 서버에 위임한다.

# Current Status
- [ ] auth 도메인 구현
- [ ] query 도메인 구현
- [ ] RAG 서버 연동
- [ ] 테스트 코드

# Environment
- Spring Boot: 3.5.x
- Java: 21
- Build: Gradle (Groovy DSL)
- DB: PostgreSQL
- Cache: Redis
- ORM: Spring Data JPA

# Architecture
헥사고날 아키텍처 적용

# Package Structure
com.example.backend
  interfaces/        ← Controller, Request/Response DTO
  application/       ← Service, UseCase (비즈니스 로직 조율)
  domain/            ← Entity, Repository 인터페이스, 도메인 규칙
  infrastructure/    ← JPA 구현체, Redis, WebClient 등 외부 기술
  common/            ← 예외, 공통 응답 포맷, 설정

# Domain Structure
auth/      회원가입, 로그인, JWT 발급
query/     질의 처리, RAG 서버 연동, 검색 이력

# Naming Convention
- Controller  : {Domain}Controller
- UseCase     : {Action}{Domain}UseCase (예: SignupUseCase, QueryUseCase)
- Service     : {Domain}Service
- Repository  : {Domain}Repository (인터페이스), {Domain}JpaRepository (구현체)
- DTO         : {Domain}{Action}Request / {Domain}{Action}Response
- Entity      : {Domain} (예: User, QueryLog)

# Conventions
- 계층 간 의존성 방향: interfaces → application → domain ← infrastructure
- domain 계층은 외부 기술에 의존하지 않는다 (순수 Java)
- Repository 인터페이스는 domain에, 구현체는 infrastructure에
- 응답은 공통 응답 포맷 (ApiResponse<T>) 으로 통일
- 예외는 GlobalExceptionHandler에서 일괄 처리
- DTO는 interfaces 계층에만 존재, domain 객체를 외부에 직접 노출하지 않는다

# Key Classes
- ApiResponse<T>     : 공통 응답 래퍼
- CustomException    : 비즈니스 예외 기반 클래스
- ErrorCode          : 에러 코드 enum
- JwtProvider        : JWT 생성/검증
- RagClient          : Python FastAPI 호출 (WebClient)

# API Response Format
성공
{
  "success": true,
  "data": {},
  "error": null
}

실패
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH_001",
    "message": "이메일 또는 비밀번호가 올바르지 않습니다"
  }
}

# Do Not
- domain 계층에 @Entity 외 Spring 어노테이션 사용 금지
- 환경변수 하드코딩 금지
- @Transactional을 interfaces 계층에 사용 금지
- RAG 서버 URL 하드코딩 금지

# Claude Code Guidelines

## 항상 체크할 것
작업 전후로 아래 세 가지를 반드시 확인한다.

### 1. 누락/오류 가능성 체크
- 요청한 기능 외에 놓치고 있는 예외 케이스나 엣지 케이스가 있으면 먼저 언급한다
- 보안 취약점 (인증 누락, SQL Injection, 민감 정보 노출 등) 발견 시 즉시 지적한다
- 트랜잭션 경계, 커넥션 풀 고갈 가능성 등 성능 리스크가 있으면 언급한다

### 2. 코드 스타일
- 헥사고날 아키텍처 의존성 방향을 위반하지 않는다
  (interfaces → application → domain ← infrastructure)
- domain 계층에 외부 기술 의존성이 생기면 반드시 지적한다
- DTO는 interfaces 계층에만 위치한다
- 공통 응답은 ApiResponse<T> 로 통일한다
- 예외는 CustomException + ErrorCode + GlobalExceptionHandler 패턴을 따른다
- MVP 구조: Controller → UseCase → Service → Repository 순서로 구성한다

### 3. 테스트 코드
- 모든 기능 구현 후 테스트 코드를 함께 작성한다
- 단위 테스트: Service, Domain 로직 (Mockito 사용)
- 통합 테스트: Controller 레벨 (MockMvc 사용)
- 테스트 커버리지: 핵심 비즈니스 로직은 반드시 커버한다
- 외부 의존성 (RAG 서버, Redis 등) 은 Mock 처리한다