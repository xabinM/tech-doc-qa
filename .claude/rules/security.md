# 보안 규칙

- JWT secret, DB password, Redis password 환경변수 필수, 하드코딩 금지
- 새 엔드포인트 추가 시 `SecurityConfig`에 `permitAll` 또는 `authenticated` 명시적 선언
- Controller DTO에 `@Valid` 적용, `@NotBlank`/`@Email`/`@Size` 등 제약조건 명시
- 에러 응답에 스택트레이스, 내부 경로, 비밀번호 절대 포함 금지
- 로그에 JWT 토큰, 비밀번호 출력 금지
