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

# Rules

## 테스트 규칙
- 외부 의존성(RAG 서버, Redis, PostgreSQL)은 반드시 Mock 처리
- Service 단위 테스트: `@ExtendWith(MockitoExtension.class)`
- Controller 통합 테스트: `@WebMvcTest` + `@MockBean`
- 테스트 메서드명: 한국어, `{상황}_{기대결과}` 형식 (예: `존재하지않는이메일로로그인_예외발생`)
- Happy path + Edge case(빈값, 경계값, 권한없음, 존재하지않는리소스) 반드시 포함
- `assertThrows`로 예외 타입과 `ErrorCode` 함께 검증
- 테스트 데이터는 메서드 내 지역변수로 선언, 필드 공유 최소화

## 트랜잭션 / DB 규칙
- RAG 서버 호출 전 `@Transactional` 범위 반드시 종료 (커넥션 풀 고갈 방지)
- `@Transactional`은 Service 계층에만, Controller 금지
- JPA 연관관계에 fetch 전략 명시 (`LAZY` 기본, 필요 시 `EAGER` 명시적 선언)
- Flyway 마이그레이션 파일 수정 절대 금지 — 항상 새 버전(V{n+1}) 파일 추가
- N+1 발생 가능한 연관관계 조회는 fetch join 또는 `@EntityGraph` 사용

## 보안 규칙
- JWT secret, DB password, Redis password 환경변수 필수, 하드코딩 금지
- 새 엔드포인트 추가 시 `SecurityConfig`에 `permitAll` 또는 `authenticated` 명시적 선언
- Controller DTO에 `@Valid` 적용, `@NotBlank`/`@Email`/`@Size` 등 제약조건 명시
- 에러 응답에 스택트레이스, 내부 경로, 비밀번호 절대 포함 금지
- 로그에 JWT 토큰, 비밀번호 출력 금지

## 비동기 / 이벤트 규칙
- `@Async` 메서드는 반드시 예외 catch 또는 `AsyncUncaughtExceptionHandler` 등록
- `@Async` 메서드를 같은 클래스 내에서 호출 금지 (프록시 우회로 비동기 미적용)
- 트랜잭션 커밋 이후 이벤트 처리가 필요하면 `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용
- `@Async` + `@EventListener` 조합 시 트랜잭션 전파 없음을 인지하고 설계
- 커스텀 스레드풀(`ThreadPoolTaskExecutor`) 설정 권장, 기본 스레드풀 사용 시 명시

## API 응답 규칙
- 모든 응답은 `ApiResponse<T>` 래핑
- 성공: 200(조회/수정), 201(생성), 204(삭제)
- 에러: 400(입력오류), 401(인증없음), 403(권한없음), 404(리소스없음), 409(중복), 429(Rate Limit), 503(RAG 서버 장애)
- 에러는 `CustomException` + `ErrorCode`, `GlobalExceptionHandler`에서 일괄 처리
- 페이지네이션은 Cursor 기반, offset 기반 금지

## WebClient / 외부 호출 규칙
- WebClient `connectTimeout`, `readTimeout`, `writeTimeout` 설정 필수
- RAG 서버는 `RagPort` 인터페이스를 통해서만 호출, 직접 호출 금지
- Circuit Breaker 없이 외부 서비스 직접 호출 금지
- 외부 호출 실패 시 적절한 fallback 또는 명확한 에러 코드 반환

## 코드 스타일 규칙
- 생성자 주입: `@RequiredArgsConstructor` 사용, `@Autowired` 금지
- 매직 넘버/문자열은 상수 또는 enum으로 분리
- 메서드 길이 30줄 초과 시 분리 검토
- 패키지 구조 고정: `interfaces` / `application` / `domain` / `infrastructure` / `common`
