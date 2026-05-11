# 트랜잭션 / DB 규칙

- 외부 서비스 호출 전 `@Transactional` 범위 반드시 종료 (커넥션 풀 고갈 방지)
- `@Transactional`은 Service 계층에만, Controller 금지
- JPA 연관관계에 fetch 전략 명시 (`LAZY` 기본, 필요 시 `EAGER` 명시적 선언)
- Flyway 마이그레이션 파일 수정 절대 금지 — 항상 새 버전(V{n+1}) 파일 추가
- N+1 발생 가능한 연관관계 조회는 fetch join 또는 `@EntityGraph` 사용
