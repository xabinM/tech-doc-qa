# 비동기 / 이벤트 규칙

- `@Async` 메서드는 반드시 예외 catch 또는 `AsyncUncaughtExceptionHandler` 등록
- `@Async` 메서드를 같은 클래스 내에서 호출 금지 (프록시 우회로 비동기 미적용)
- 이벤트 리스너 작성 시 발행 시점에 활성 트랜잭션이 있는지 명시적으로 판단한다 — 트랜잭션 커밋 이후 실행이 필요한 경우에만 `@TransactionalEventListener`, 트랜잭션이 없는 컨텍스트라면 `@EventListener`
- `@Async` + `@EventListener` 조합 시 트랜잭션 전파 없음을 인지하고 설계
- 커스텀 스레드풀 설정 권장, 기본 스레드풀 사용 시 명시
