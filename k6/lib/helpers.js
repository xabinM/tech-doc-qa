import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 부하 테스트용 질문 풀 - 다양한 질문으로 캐시 효과 차단
export const QUESTIONS = [
  'Spring Bean 생명주기를 단계별로 설명해주세요',
  'JPA N+1 문제란 무엇이고 어떻게 해결하나요?',
  '@Transactional의 propagation 옵션 차이점은 무엇인가요?',
  'Spring Security 필터 체인 구조를 설명해주세요',
  'Spring AOP의 Proxy 동작 원리는 무엇인가요?',
  'HikariCP 커넥션 풀 설정 방법을 알려주세요',
  'Spring WebFlux와 Spring MVC의 차이점은 무엇인가요?',
  'Lazy Loading과 Eager Loading의 차이점은 무엇인가요?',
  'Spring Boot 자동 설정 원리를 설명해주세요',
  'Redis를 캐시로 사용하는 방법을 알려주세요',
];

const JSON_HEADERS = { 'Content-Type': 'application/json' };

/**
 * 테스트 유저를 생성하고 Access Token을 반환한다.
 * 이미 존재하는 경우 (409) 로그인만 수행한다.
 */
export function ensureUserAndLogin(index) {
  const email = `loadtest_user_${index}@perf.test`;
  const password = 'LoadTest123!';

  // 회원가입 - 이미 존재하면 409 (무시)
  http.post(
    `${BASE_URL}/api/v1/auth/signup`,
    JSON.stringify({ email, password }),
    { headers: JSON_HEADERS }
  );

  // 로그인
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: JSON_HEADERS }
  );

  const ok = check(loginRes, {
    [`유저${index} 로그인 성공`]: (r) => r.status === 200,
  });

  if (!ok) {
    console.error(`유저 ${index} 로그인 실패: status=${loginRes.status} body=${loginRes.body}`);
    return null;
  }

  return loginRes.json('data.accessToken');
}

/**
 * 질문 API를 호출하고 응답을 반환한다.
 * @param {string} token - Access Token
 * @param {string} question - 질문 텍스트
 * @param {number|null} sessionId - 세션 ID (없으면 새 세션 생성)
 */
export function queryRequest(token, question, sessionId = null, extraTags = {}) {
  return http.post(
    `${BASE_URL}/api/v1/query`,
    JSON.stringify({ question, sessionId }),
    {
      headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` },
      tags: { endpoint: 'query', ...extraTags },
    }
  );
}

/**
 * 이력 조회 API를 호출한다.
 */
export function historyRequest(token) {
  return http.get(
    `${BASE_URL}/api/v1/query/history`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { endpoint: 'history' },
    }
  );
}

/**
 * 질문 응답에 대한 기본 체크
 */
export function checkQueryResponse(res) {
  return check(res, {
    '상태코드 200': (r) => r.status === 200,
    '응답 success true': (r) => {
      try { return r.json('success') === true; } catch { return false; }
    },
    '답변 텍스트 존재': (r) => {
      try { return r.json('data.answer').length > 0; } catch { return false; }
    },
  });
}
