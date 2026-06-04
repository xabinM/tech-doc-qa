/**
 * Phase 2 - 시나리오 2: 스파이크 테스트
 *
 * 목적: 갑작스러운 트래픽 급증 시 서비스 회복력 측정
 *
 * 핵심 질문:
 *   - 스파이크 도달 시 에러율이 얼마나 되는가?
 *   - 스파이크 소멸 후 정상 응답으로 회복하는 시간은?
 *   - Circuit Breaker가 올바르게 개방/폐쇄되는가?
 *
 * 실행:
 *   k6 run k6/scenarios/02-spike.js
 */
import { sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import {
  ensureUserAndLogin,
  queryRequest,
  checkQueryResponse,
  historyRequest,
  QUESTIONS,
} from '../lib/helpers.js';

const queryErrorRate = new Rate('query_error_rate');
const queryDuration = new Trend('query_duration_ms', true);

const TEST_USER_COUNT = 30;  // 스파이크 VU에 대응해 유저 풀 확대

export const options = {
  stages: [
    { duration: '1m',  target: 20  },  // 정상 운영 상태
    { duration: '10s', target: 500 },  // 스파이크 급상승
    { duration: '2m',  target: 500 },  // 스파이크 지속
    { duration: '10s', target: 20  },  // 스파이크 해소
    { duration: '2m',  target: 20  },  // 회복 구간 - P99가 정상으로 돌아오는 시간 관찰
  ],
  thresholds: {
    // 스파이크 구간 에러 허용 (CB 개방으로 빠른 실패)
    http_req_failed: ['rate<0.30'],
    // 회복 구간 에러율은 엄격하게 (시나리오 태그 사용)
    'http_req_failed{phase:steady}': ['rate<0.02'],
    query_error_rate: ['rate<0.30'],
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
  const question = QUESTIONS[Math.floor(Math.random() * QUESTIONS.length)];

  // VU 인덱스로 스파이크/정상 구간 판단 (스파이크 단계: target=500, 정상: target=20)
  const isSpike = __VU > 100;
  const phase = isSpike ? 'spike' : 'steady';

  const res = queryRequest(token, question, null, { phase });

  const passed = checkQueryResponse(res);
  queryErrorRate.add(!passed);
  queryDuration.add(res.timings.duration);

  if (!passed && isSpike) {
    // 스파이크 구간 실패 시 즉시 재시도 않고 잠시 대기
    sleep(0.5);
  }
}

export function handleSummary(data) {
  const summary = {
    timestamp: new Date().toISOString(),
    scenario: 'spike',
    metrics: {
      http_req_duration_p99:  data.metrics.http_req_duration?.values?.['p(99)'],
      http_req_failed_rate:   data.metrics.http_req_failed?.values?.rate,
      http_reqs_total:        data.metrics.http_reqs?.values?.count,
    },
  };

  return {
    'k6/results/spike-summary.json': JSON.stringify(summary, null, 2),
    stdout: `
=== Spike 시나리오 결과 ===
요청 총계:    ${data.metrics.http_reqs?.values?.count ?? '-'}
에러율:       ${((data.metrics.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)}%
P99 응답시간: ${(data.metrics.http_req_duration?.values?.['p(99)'] ?? 0).toFixed(0)} ms
Fallback 횟수: Grafana > rag.fallback.total 확인
`,
  };
}
