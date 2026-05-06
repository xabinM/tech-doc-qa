# 비동기 / 이벤트 규칙

- `@Async` 메서드는 반드시 예외 catch 또는 `AsyncUncaughtExceptionHandler` 등록
- `@Async` 메서드를 같은 클래스 내에서 호출 금지 (프록시 우회로 비동기 미적용)
- 트랜잭션 커밋 이후 이벤트 처리가 필요하면 `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용
- `@Async` + `@EventListener` 조합 시 트랜잭션 전파 없음을 인지하고 설계
- 커스텀 스레드풀(`ThreadPoolTaskExecutor`) 설정 권장, 기본 스레드풀 사용 시 명시
