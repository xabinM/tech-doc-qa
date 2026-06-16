# 로깅 기능 — 이슈 및 수정 기록

액세스 로그 + 애플리케이션 로그(JSON, 요청 단위 추적 ID) 구현 중 발견한 현업 기준 문제와 수정 사항.

대상 브랜치: `feat/backend-request-logging`

---

## 적용한 수정

### #1 `@Async` 스레드에 MDC(요청 ID) 미전파 — 심각도: 높음
- **문제상황:** MDC는 thread-local이라 `@Async` 스레드로 복사되지 않는다. 검색 이력 저장(`@Async + @EventListener`) 등 비동기 작업 로그에 `requestId`가 빠져, "모든 로그에 요청 ID"가 동기 구간에서만 성립했다.
- **수정사항:** `AsyncConfig.getAsyncExecutor()`를 MDC 복사 데코레이터로 감쌈. 제출 시점의 MDC를 실행 스레드에 set, 작업 종료 시 clear.
- **파일:** `backend/.../common/config/AsyncConfig.java`

### #2 `X-Forwarded-For` 신뢰로 인한 IP 스푸핑 — 심각도: 높음
- **문제상황:** 필터가 `X-Forwarded-For` 첫 값을 무조건 신뢰해 `client_ip`를 기록했다. XFF는 클라이언트가 위조 가능하므로 신뢰 프록시 검증 없이는 스푸핑된다.
- **수정사항:** 수동 XFF 파싱 제거. `server.forward-headers-strategy: native`로 Tomcat `RemoteIpValve` 활성화 → 사설 IP 대역(내부 프록시)에서 온 XFF만 반영하고 외부 위조는 무시. 필터는 보정된 `getRemoteAddr()` 사용.
- **파일:** `backend/.../common/filter/RequestLoggingFilter.java`, `backend/src/main/resources/application.yaml`

### #6 신규 동작 테스트 부재 — 심각도: 중간
- **문제상황:** 인바운드 ID 재사용/sanitize, MDC 전파 등 신규 로직에 테스트가 없어 프로젝트 테스트 규칙을 위반했다.
- **수정사항:** `RequestLoggingFilterTest`(6), `AsyncConfigTest`(2) 추가. 전부 통과.
- **파일:** `backend/src/test/.../RequestLoggingFilterTest.java`, `backend/src/test/.../AsyncConfigTest.java`

---

## 보류 (전략 결정 필요)

### #3 인바운드 `X-Request-Id` 무조건 신뢰 — 심각도: 중간
- **문제상황:** 앱이 외부에 직접 노출되면 클라이언트가 임의 ID를 주입해 추적을 무력화하거나 타 요청 ID를 사칭할 수 있다. 정석은 신뢰 경계(ingress) 안쪽에서만 인바운드 ID를 신뢰하는 것.
- **방향:** 신뢰 경계 전제 명시, 또는 인바운드 ID는 traceId로 보존하되 서버가 자체 spanId를 별도 생성.

### #4 표준 분산 추적(Micrometer Tracing) 미사용 — 심각도: 중간
- **문제상황:** 커스텀 `X-Request-Id`는 Spring Boot 3 표준인 Micrometer Tracing / W3C `traceparent`와 호환되지 않고 spanId·부모관계 개념이 없다. APM(Tempo/Jaeger/Zipkin/Datadog) 연동 불가.
- **방향:** `micrometer-tracing-bridge-otel` 도입 시 traceId/spanId MDC 자동 주입 + WebClient 자동 전파. 다중 서버 본격 확장 시 정공법. (도입하면 #1 일부·#3·#5 자동 해소)

### #5 요청 ID 8자(32비트) 충돌 — 심각도: 중간
- **문제상황:** 생일 문제로 약 77,000건이면 50% 충돌. 대규모 트래픽에서 추적 ID가 겹친다. 표준 traceId는 128비트.
- **방향:** 전체 UUID 또는 #4(Tracing) 도입으로 128비트 ID 사용.

### RagClient 전파 테스트
- **문제상황:** rag-server 호출에 `X-Request-Id`가 실리는지 검증하는 테스트가 없다. WebClient 호출이라 `MockWebServer`(okhttp) 의존성이 필요.
- **방향:** 테스트 의존성 추가 후 작성.

---

## 참고 — 현재 동작
- 액세스 로그는 `http_method`/`uri`/`status`/`duration_ms`/`client_ip` 구조화 필드로 출력 (non-local 프로파일 JSON)
- `requestId`는 인바운드 `X-Request-Id` 재사용 또는 신규 생성, 응답 헤더로 반환, rag-server 호출에 전파
- rag-server(FastAPI)가 `X-Request-Id`를 수신해 자기 로그에 남기는 작업은 다음 단계 (end-to-end 추적 완성)
