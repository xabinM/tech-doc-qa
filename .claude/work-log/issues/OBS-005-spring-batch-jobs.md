---
id: OBS-005
title: Spring Batch 데이터 정리 / 일별 통계 집계 Job 구현
type: observation
status: confirmed
severity: n/a
discovered: 2026-06-04
resolved: ~
tags: [spring-batch, cleanup, stats, scheduler, metrics, postgresql]
resume_worthy: true
---

## 개요
서비스 운영에 필요한 2가지 Spring Batch Job을 구현했다.
`staleDataCleanupJob`은 오래된 query_logs·chat_sessions를 청크 단위로 삭제하고,
`dailyStatsJob`은 전날 사용량을 user_id별로 집계해 query_stats 테이블에 UPSERT한다.
두 Job 모두 BatchMetricsListener를 통해 실행 시간과 처리 건수를 Prometheus로 노출한다.

## 발견 배경
query_logs가 무한 누적되면 테이블이 비대해져 조회 성능이 저하되고,
일별 사용량 통계를 실시간 집계하면 쿼리 부하가 크다. 배치로 분리해 운영 안정성을 확보했다.

## 적용한 변경

### staleDataCleanupJob (`StaleDataCleanupJobConfig.java`)
보존기간이 지난 레코드를 2개 Step으로 순차 삭제한다.

**Step 1 — cleanupQueryLogsStep**
- 대상: `query_logs.created_at < NOW() - INTERVAL '90 days'`
- Reader: `JdbcCursorItemReader` — ID만 SELECT (메모리 절약)
- Writer: `JdbcBatchItemWriter` — `DELETE FROM query_logs WHERE id = ?`
- 청크: 100건, 재시도: 3회 (`DataIntegrityViolationException` 제외)

**Step 2 — cleanupChatSessionsStep** (Step 1 완료 후 실행)
- 대상: `chat_sessions.created_at < NOW() - INTERVAL '30 days'` AND `query_logs`가 남아있지 않은 세션
  - Step 1에서 오래된 query_logs를 먼저 삭제했으므로 남은 query_logs 없음 = 진짜 비활성 세션
- 청크: 100건, 재시도: 3회

**청크 기반 처리 이유**
단순 `DELETE ... WHERE`는 대용량 테이블에서 장시간 테이블 락을 유발한다.
ID를 먼저 읽어 청크 단위로 삭제하면 락 점유 시간이 분산된다.

### dailyStatsJob (`DailyStatsJobConfig.java`)
전날 query_logs를 user_id별로 집계해 query_stats에 UPSERT한다.

- Reader: `JdbcCursorItemReader` — `CURRENT_DATE - 1` 기준 user_id별 count 집계 SELECT
- Writer: `JdbcBatchItemWriter` — `INSERT ... ON CONFLICT DO UPDATE` (멱등성 보장)
- 청크: 500건 (집계 결과는 query_logs보다 훨씬 적으므로 청크 크게 설정)
- 멱등성: 동일 날짜 재실행 시 ON CONFLICT로 덮어씀 → 오류 후 재실행 안전

**Flyway 마이그레이션**
- `V6__create_query_stats.sql`: `query_stats(id, user_id, stat_date, query_count)` 테이블 생성
  - `UNIQUE(user_id, stat_date)` 제약으로 ON CONFLICT 기준 확보

### BatchScheduler (`BatchScheduler.java`)
- `staleDataCleanupJob`: 매일 새벽 2시 실행
- `dailyStatsJob`: 매일 새벽 1시 실행 (전날 데이터 완성 후)

### BatchMetricsListener (`BatchMetricsListener.java`)
- Job 실행 시간: `batch.job.duration{jobName}` (Timer)
- 처리 건수: `batch.job.write.count{jobName}` (Counter)
- Grafana에서 배치 실행 이력 타임라인으로 확인 가능

### BatchController (`BatchController.java`)
- `POST /api/v1/batch/cleanup` — 수동 정리 Job 트리거 (관리자 전용)
- `POST /api/v1/batch/stats` — 수동 통계 집계 트리거 (관리자 전용)

## 결과 (After)
- query_logs 90일, chat_sessions 30일 보존 정책 자동 적용
- 일별 user_id별 쿼리 횟수 통계 자동 집계
- Prometheus에서 배치 실행 시간·처리 건수 모니터링 가능

## 관련 파일 및 코드
- `backend/src/main/java/com/example/backend/infrastructure/batch/StaleDataCleanupJobConfig.java`
- `backend/src/main/java/com/example/backend/infrastructure/batch/DailyStatsJobConfig.java`
- `backend/src/main/java/com/example/backend/infrastructure/batch/BatchScheduler.java`
- `backend/src/main/java/com/example/backend/infrastructure/batch/BatchMetricsListener.java`
- `backend/src/main/java/com/example/backend/infrastructure/batch/BatchConfig.java`
- `backend/src/main/java/com/example/backend/interfaces/batch/BatchController.java`
- `backend/src/main/resources/db/migration/V6__create_query_stats.sql`

```java
// 청크 기반 삭제 — 테이블 락 점유 시간 분산
new StepBuilder("cleanupQueryLogsStep", jobRepository)
    .<Long, Long>chunk(CHUNK_SIZE, txManager)  // CHUNK_SIZE = 100
    .reader(oldQueryLogIdReader())
    .writer(queryLogDeleteWriter())
    .faultTolerant().retryLimit(3).retry(Exception.class)
    .noRetry(DataIntegrityViolationException.class)
    .build();
```

## 이력서 포인트
Spring Batch로 query_logs 90일·chat_sessions 30일 보존 정책을 청크(100건) 기반 삭제 Job으로 구현해 대용량 삭제 시 테이블 락을 분산하고, ON CONFLICT UPSERT 기반 멱등성 일별 통계 집계 Job을 추가해 배치 실행 지표를 Prometheus로 노출했다.
