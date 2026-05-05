# 테스트 규칙

- 외부 의존성(RAG 서버, Redis, PostgreSQL)은 반드시 Mock 처리
- Service 단위 테스트: `@ExtendWith(MockitoExtension.class)`
- Controller 통합 테스트: `@WebMvcTest` + `@MockBean`
- 테스트 메서드명: 한국어, `{상황}_{기대결과}` 형식 (예: `존재하지않는이메일로로그인_예외발생`)
- Happy path + Edge case(빈값, 경계값, 권한없음, 존재하지않는리소스) 반드시 포함
- `assertThrows`로 예외 타입과 `ErrorCode` 함께 검증
- 테스트 데이터는 메서드 내 지역변수로 선언, 필드 공유 최소화
