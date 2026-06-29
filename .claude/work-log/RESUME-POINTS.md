# 이력서 포인트

생성일: 2026-06-08
기준: resume_worthy: true + status: resolved | confirmed

---

## 성능 개선

- k6 ramp 테스트(300 VU, LLM 5초 지연)에서 에러율 10.6% 발생 → Grafana로 HikariCP pending 454개 확인 → 가상 스레드 환경에서 `prepareSession()` + `@Async` 핸들러 경합으로 DB 커넥션 풀(30개)이 고갈됨을 진단 → pool size 100으로 조정해 에러율 10.6% → 0.05%(200배 개선), RPS 109.8 → 237.3(2.16배 향상), P95 2,755ms → 1,192ms(57% 단축) 달성 (ISSUE-003)

---

## 안정성 / 장애 대응

- 가상 스레드 환경에서 외부 I/O 대기(RAG 5초 + WebClient 30초 타임아웃) 중 DB 커넥션을 점유하는 패턴이 HikariCP 커넥션 풀 고갈을 유발함을 Grafana 메트릭(pending 454)으로 실측 진단하고 pool size 조정으로 에러율 0.05% 달성 (ISSUE-003)

- Redis State Machine 기반 멱등성 처리(PROCESSING → COMPLETED/FAILED)를 구현해 클라이언트 재시도에 의한 중복 LLM 호출을 차단하고, Resilience4j Circuit Breaker 상태(CLOSED/OPEN/HALF_OPEN)를 Micrometer로 자동 노출해 CB 트립 시점을 Grafana에서 실시간 확인 가능하게 했다 (OBS-004)

- Spring Batch로 query_logs 90일 · chat_sessions 30일 보존 정책을 청크(100건) 기반 삭제 Job으로 구현해 대용량 삭제 시 테이블 락 점유 시간을 분산하고, ON CONFLICT UPSERT 기반 멱등성 일별 통계 집계 Job을 추가했다 (OBS-005)
  - 수치 미확보 — 재측정 필요 (처리 건수 및 락 점유 시간 before/after 미측정)

---

## 관측 가능성

- Spring Boot Actuator → Prometheus → Grafana 파이프라인을 직접 구성해 부하 테스트 중 JVM 스레드 · DB 커넥션 풀을 실시간 시각화하는 관측 가능성 인프라를 구축하고, Tomcat 스레드 포화 임계(VU 200) 시점을 Grafana에서 직접 관찰했다 (OBS-001)
  - 수치 미확보 — 재측정 필요 (인프라 구축 자체가 목적이며 before/after 정량 수치 없음)

- LLM 5초 지연 시뮬레이션 조건에서 k6 ramp/spike/soak 3가지 시나리오를 직접 설계해 Tomcat 스레드 포화 임계(VU 200)와 p95 2,756ms · 에러율 10.6% · 109.8 RPS를 실측 확인했다 (OBS-002)

- Circuit Breaker 상태 · 배치 실행 시간·처리 건수 등 전 계층의 운영 지표를 Prometheus 커스텀 메트릭(Counter/Timer/Gauge)으로 노출해 Grafana 대시보드에서 단일 화면으로 모니터링 가능하게 했다 (OBS-004, OBS-005)
  - 수치 미확보 — 재측정 필요 (배치 실행 시간 실측값 미기록)

---

## 아키텍처 설계

- POST /api/v1/query 처리 경로에 멱등성 처리 → Circuit Breaker를 계층적으로 적용해 외부 LLM 의존도를 낮추는 방어적 설계를 구현하고, Idempotency-Key 기반 중복 요청을 같은 jobId로 동일 답변 스트림에 재구독시키도록 설계했다 (OBS-004)

- 외부 LLM API 비용·의존성을 차단하기 위해 MOCK_DELAY_SECONDS 환경변수 기반 mock RAG 서버를 구현하고, loadtest 프로파일로 Rate Limit을 격리한 완전 재현 가능한 부하 테스트 환경을 설계해 총 33,675건 → 71,953건 처리량 실측 비교를 달성했다 (OBS-002, ISSUE-003)

- 단순 `DELETE ... WHERE` 대신 ID를 먼저 SELECT 후 청크 단위 삭제하는 패턴으로 대용량 테이블의 락 점유 시간을 분산하고, chat_sessions 삭제를 query_logs 삭제 이후 Step으로 순서화해 데이터 정합성을 보장하는 배치 파이프라인을 설계했다 (OBS-005)
  - 수치 미확보 — 재측정 필요 (락 점유 시간 분산 효과 실측값 없음)
