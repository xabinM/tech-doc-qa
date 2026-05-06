# WebClient / 외부 호출 규칙

- WebClient `connectTimeout`, `readTimeout`, `writeTimeout` 설정 필수
- RAG 서버는 `RagPort` 인터페이스를 통해서만 호출, 직접 호출 금지
- Circuit Breaker 없이 외부 서비스 직접 호출 금지
- 외부 호출 실패 시 적절한 fallback 또는 명확한 에러 코드 반환
