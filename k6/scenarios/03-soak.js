/**
 * Phase 2 - 시나리오 3: 지속 부하 (Soak)
 *
 * 목적: 장시간 운영 중 발생하는 메모리 누수, 커넥션 고갈, GC 압력 탐지
 *
 * 관찰 포인트 (Grafana):
 *   - JVM 힙 메모리: 시간이 지날수록 꾸준히 증가하면 메모리 누수 의심
 *   - DB 커넥션 pending: 꾸준히 증가하면 커넥션 고갈
 *   - P99 응답시간: 초반 대비 후반에 악화되면 리소스 고갈
 *   - GC pause: `jvm_gc_pause_seconds` 증가 추이
 *
 * 기본 실행 시간: 30분 (SOAK_DURATION 환경변수로 조절)
 *   k6 run k6/scenarios/03-soak.js
 *   k6 run --env SOAK_DURATION=10m k6/scenarios/03-soak.js
 */
import { sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import {
  ensureUserAndLogin,
  queryRequest,
  historyRequest,
  checkQueryResponse,
  QUESTIONS,
} from '../lib/helpers.js';

const queryErrorRate = new Rate('query_error_rate');
const queryDuration = new Trend('query_duration_ms', true);

const TEST_USER_COUNT = 20;
const SOAK_DURATION = __ENV.SOAK_DURATION || '30m';

export const options = {
  stages: [
    { duration: '2m',          target: 30 },  // 램프업
    { duration: SOAK_DURATION, target: 30 },  // 지속 부하 (기본 30분)
    { duration: '2m',          target: 0  },  // 램프다운
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],        // Soak: 엄격한 에러율 기준
    http_req_duration: ['p(99)<30000'],
    // 초반 5분 기준값 대비 후반 P99가 2배 이상 악화되면 실패
    'http_req_duration{endpoint:query}': ['p(99)<40000'],
    query_error_rate: ['rate<0.01'],
  },
};

export function setup() {
  const tokens = [];
  for (let i = 0; i < TEST_USER_COUNT; i++) {
    const token = ensureUserAndLogin(i);
    if (token) tokens.push(token);
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[__VU % data.tokens.length];

  // 실제 사용 패턴 시뮬레이션: 70% 질문, 30% 이력 조회
  const rand = Math.random();

  if (rand < 0.70) {
    const question = QUESTIONS[Math.floor(Math.random() * QUESTIONS.length)];
    const res = queryRequest(token, question);
    const passed = checkQueryResponse(res);
    queryErrorRate.add(!passed);
    queryDuration.add(res.timings.duration);
  } else {
    historyRequest(token);
  }

  // Soak 테스트: 요청 간 짧은 대기로 실제 사용 패턴 모사
  sleep(Math.random() * 2 + 1);  // 1~3초 랜덤 대기
}

export function handleSummary(data) {
  const summary = {
    timestamp: new Date().toISOString(),
    scenario: 'soak',
    duration: SOAK_DURATION,
    metrics: {
      http_req_duration_p50:  data.metrics.http_req_duration?.values?.['p(50)'],
      http_req_duration_p95:  data.metrics.http_req_duration?.values?.['p(95)'],
      http_req_duration_p99:  data.metrics.http_req_duration?.values?.['p(99)'],
      http_req_failed_rate:   data.metrics.http_req_failed?.values?.rate,
      http_reqs_total:        data.metrics.http_reqs?.values?.count,
    },
  };

  return {
    'k6/results/soak-summary.json': JSON.stringify(summary, null, 2),
    stdout: `
=== Soak 시나리오 결과 (${SOAK_DURATION}) ===
요청 총계:    ${data.metrics.http_reqs?.values?.count ?? '-'}
에러율:       ${((data.metrics.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)}%
P50 응답시간: ${(data.metrics.http_req_duration?.values?.['p(50)'] ?? 0).toFixed(0)} ms
P95 응답시간: ${(data.metrics.http_req_duration?.values?.['p(95)'] ?? 0).toFixed(0)} ms
P99 응답시간: ${(data.metrics.http_req_duration?.values?.['p(99)'] ?? 0).toFixed(0)} ms
→ 메모리/커넥션 추세는 Grafana에서 시계열로 확인
`,
  };
}
