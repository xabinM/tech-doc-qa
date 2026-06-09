/**
 * Phase 2 - 시나리오 1: 점진적 부하 증가 (Ramp-up)
 *
 * 목적: Tomcat 플랫폼 스레드 포화 지점 탐색 및 기록
 *
 * 실행 방법:
 *   # 부하 테스트 프로파일로 백엔드 기동
 *   cd backend && ./gradlew bootRun --args='--spring.profiles.active=local,loadtest'
 *
 *   # RAG 서버 기동 (MOCK_DELAY_SECONDS=5 로 LLM 지연 시뮬레이션)
 *   cd rag-server && MOCK_DELAY_SECONDS=5 uvicorn main:app --port 8000
 *
 *   # k6 실행
 *   k6 run k6/scenarios/01-ramp.js
 *   k6 run --env BASE_URL=http://localhost:8080 k6/scenarios/01-ramp.js
 *
 * Grafana에서 확인할 핵심 지표:
 *   - JVM 스레드 수: VU 200 근처에서 200개 포화 → 이후 큐 적체
 *   - HTTP P99: VU 200 초과 시 급등
 *   - DB 커넥션 풀: active 증가 추이
 */
import { sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import {
  ensureUserAndLogin,
  queryRequest,
  checkQueryResponse,
  QUESTIONS,
} from '../lib/helpers.js';

// 커스텀 메트릭
const queryErrorRate = new Rate('query_error_rate');
const queryDuration = new Trend('query_duration_ms', true);

const TEST_USER_COUNT = 20;

export const options = {
  stages: [
    { duration: '30s', target: 10  },  // 워밍업
    { duration: '1m',  target: 50  },  // 적정 부하
    { duration: '1m',  target: 100 },  // 높은 부하
    { duration: '1m',  target: 200 },  // Tomcat 스레드 포화 임계 (200개)
    { duration: '1m',  target: 300 },  // 임계 초과 - 스레드 고갈 관찰
    { duration: '30s', target: 0   },  // 램프다운
  ],
  thresholds: {
    // 에러율 5% 미만 (포화 구간에서 일부 허용)
    http_req_failed: ['rate<0.05'],
    // 전체 P95 30초 이내
    http_req_duration: ['p(95)<30000'],
    // /api/v1/query P99 45초 이내
    'http_req_duration{endpoint:query}': ['p(99)<45000'],
    // 커스텀: query 에러율
    query_error_rate: ['rate<0.05'],
  },
};

export function setup() {
  console.log(`테스트 유저 ${TEST_USER_COUNT}명 초기화 중...`);
  const tokens = [];
  for (let i = 0; i < TEST_USER_COUNT; i++) {
    const token = ensureUserAndLogin(i);
    if (token) tokens.push(token);
  }
  console.log(`초기화 완료: ${tokens.length}명 토큰 확보`);
  return { tokens };
}

export default function (data) {
  const token = data.tokens[__VU % data.tokens.length];
  const question = QUESTIONS[Math.floor(Math.random() * QUESTIONS.length)];

  const res = queryRequest(token, question);
  const passed = checkQueryResponse(res);

  queryErrorRate.add(!passed);
  queryDuration.add(res.timings.duration);

  // 요청 실패 시 짧은 대기 (폭주 방지)
  if (!passed) sleep(1);
}

export function handleSummary(data) {
  const summary = {
    timestamp: new Date().toISOString(),
    scenario: 'ramp-up',
    metrics: {
      http_req_duration_p50:  data.metrics.http_req_duration?.values?.['p(50)'],
      http_req_duration_p95:  data.metrics.http_req_duration?.values?.['p(95)'],
      http_req_duration_p99:  data.metrics.http_req_duration?.values?.['p(99)'],
      http_req_failed_rate:   data.metrics.http_req_failed?.values?.rate,
      http_reqs_total:        data.metrics.http_reqs?.values?.count,
      http_reqs_rate:         data.metrics.http_reqs?.values?.rate,
      query_duration_p95:     data.metrics.query_duration_ms?.values?.['p(95)'],
      query_duration_p99:     data.metrics.query_duration_ms?.values?.['p(99)'],
    },
  };

  return {
    'k6/results/ramp-summary.json': JSON.stringify(summary, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

// k6 내장 텍스트 요약 (없을 경우 fallback)
function textSummary(data, opts) {
  return `
=== Ramp-up 시나리오 결과 ===
요청 총계:    ${data.metrics.http_reqs?.values?.count ?? '-'}
초당 요청:    ${(data.metrics.http_reqs?.values?.rate ?? 0).toFixed(2)} req/s
에러율:       ${((data.metrics.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)}%
P50 응답시간: ${(data.metrics.http_req_duration?.values?.['p(50)'] ?? 0).toFixed(0)} ms
P95 응답시간: ${(data.metrics.http_req_duration?.values?.['p(95)'] ?? 0).toFixed(0)} ms
P99 응답시간: ${(data.metrics.http_req_duration?.values?.['p(99)'] ?? 0).toFixed(0)} ms
`;
}
