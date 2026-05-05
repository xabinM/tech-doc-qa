# Backend Overview
Spring Boot 3.5.x (Java 21) 기반 API 서버.
인증, 질의 처리, 검색 이력 관리를 담당한다.
RAG 처리는 Python FastAPI 서버에 위임한다.

# Current Status
- [x] auth 도메인 구현 (회원가입, 로그인, 토큰 갱신, 로그아웃)
- [x] 테스트 코드 (AuthService 단위 테스트, AuthController 통합 테스트)
- [ ] query 도메인 구현
- [ ] RAG 서버 연동

# Environment
- Spring Boot: 3.5.x
- Java: 21
- Build: Gradle (Groovy DSL)
- DB: PostgreSQL
- Cache: Redis
- ORM: Spring Data JPA

# Architecture
헥사고날 아키텍처 적용
계층 간 의존성 방향: interfaces → application → domain ← infrastructure

# Package Structure
com.example.backend
  interfaces/        ← Controller, Request/Response DTO
  application/       ← Service, Port 인터페이스
  domain/            ← Entity, Repository 인터페이스
  infrastructure/    ← JPA 구현체, Redis, WebClient 등 외부 기술
  common/            ← 예외, 공통 응답 포맷, 설정

# Domain Structure
auth/      회원가입, 로그인, JWT 발급
query/     질의 처리, RAG 서버 연동, 검색 이력

# Naming Convention
- Controller  : {Domain}Controller
- Service     : {Domain}Service
- Repository  : {Domain}Repository (인터페이스), {Domain}JpaRepository (구현체)
- Port        : {기능} (인터페이스, application/port 하위)
- DTO         : {Domain}{Action}Request / {Domain}{Action}Response
- Entity      : {Domain} (예: User, QueryLog)

# Rules
- domain 계층은 외부 기술에 의존하지 않는다 (순수 Java)
- domain 계층에 @Entity 외 Spring 어노테이션 사용 금지
- Repository 인터페이스는 domain에, 구현체는 infrastructure에
- Port 인터페이스는 application에, 구현체는 infrastructure에
- DTO는 interfaces 계층에만 존재, domain 객체를 외부에 직접 노출하지 않는다
- 응답은 ApiResponse<T> 로 통일
- 예외는 CustomException + ErrorCode + GlobalExceptionHandler 패턴
- @Transactional을 interfaces 계층에 사용 금지
- 환경변수 하드코딩 금지, RAG 서버 URL 하드코딩 금지

# Key Classes
- `ApiResponse<T>`      : 공통 응답 래퍼
- `CustomException`     : 비즈니스 예외 기반 클래스
- `ErrorCode`           : 에러 코드 enum
- `TokenManager`        : JWT 포트 인터페이스 (generateAccessToken, validateRefreshToken 등)
- `RefreshTokenStore`   : Refresh Token 저장소 포트 인터페이스
- `RagPort`             : RAG 서버 호출 포트 인터페이스
- `JwtProvider`         : TokenManager 구현체
- `RedisTokenRepository`: RefreshTokenStore 구현체
- `RagClient`           : RagPort 구현체 (stub, RAG 서버 연동 예정)

# Backend-Specific Checks
작업 시 아래를 반드시 확인한다.
- 보안 취약점 (인증 누락, SQL Injection, 민감 정보 노출 등) 발견 시 즉시 지적
- RAG 서버 호출 중 DB 트랜잭션이 열려있으면 안 됨 (커넥션 풀 고갈)
- 단위 테스트(Mockito) + 통합 테스트(MockMvc) 함께 작성
- 외부 의존성 (RAG 서버, Redis 등)은 Mock 처리
