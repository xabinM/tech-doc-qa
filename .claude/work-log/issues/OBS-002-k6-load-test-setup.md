---
id: OBS-002
title: k6 부하 테스트 환경 구성 (ramp / spike / soak 3가지 시나리오)
type: observation
status: confirmed
severity: n/a
discovered: 2026-06-04
resolved: ~
tags: [load-test, k6, ramp, spike, soak, performance]
resume_worthy: true
---

## 개요
k6를 이용한 3단계 부하 테스트 환경을 직접 설계했다.
Tomcat 스레드 포화 지점 탐색(ramp), 순간 급증 대응(spike), 장시간 누수 탐지(soak) 시나리오로 구성된다.
RAG 서버의 LLM 지연을 시뮬레이션하기 위한 mock 엔드포인트도 함께 구현했다.

## 발견 배경
실제 LLM API를 부하 테스트에 사용하면 비용과 외부 의존성 문제가 생긴다.
`MOCK_DELAY_SECONDS` 환경변수로 지연 시간을 제어하는 mock 라우터를 RAG 서버에 추가해
완전히 격리된 부하 테스트 환경을 구성했다.

## 테스트 환경
- 시나리오: ramp-up (01), spike (02), soak (03)
- 테스트 유저 수: 20명 (사전 회원가입·로그인 후 토큰 재사용)
- LLM 응답 시뮬레이션: `MOCK_DELAY_SECONDS=5` (5초 고정 지연)
- Rate Limit: loadtest 프로파일에서 비활성화 (`application-loadtest.yaml`)

## 적용한 변경
**시나리오 구성 (`k6/scenarios/`)**
- `01-ramp.js`: 워밍업(10 VU) → 적정(50) → 높음(100) → 임계(200) → 초과(300) → 램프다운
  - 목적: Tomcat 플랫폼 스레드 포화 지점 탐색
- `02-spike.js`: 순간 VU 급증, RAG 서버 Circuit Breaker 반응 확인
- `03-soak.js`: 장시간 중간 부하, 메모리 누수·커넥션 고갈 탐지

**공통 헬퍼 (`k6/lib/helpers.js`)**
- `ensureUserAndLogin()`: 유저 생성 + 로그인 + 토큰 반환 (setup 단계에서 사전 초기화)
- `queryRequest()` / `checkQueryResponse()`: 쿼리 요청 및 응답 검증

**임계값 (ramp 기준)**
- 에러율: `rate < 0.05` (5% 미만)
- HTTP P95: `p(95) < 30000` (30초 이내)
- Query P99: `p(99) < 45000` (45초 이내)

**Mock RAG 서버 (`rag-server/router/mock.py`)**
- `GET /mock/health`, `POST /mock/query` (MOCK_DELAY_SECONDS 후 더미 응답 반환)

**부하 테스트 프로파일 (`application-loadtest.yaml`)**
- Rate Limit 비활성화, 짧은 DB 타임아웃 설정

## 결과 (After)
- ramp-up 1회차 실행 결과: 총 33,675건, 109.8 RPS, p95 2,756ms, 에러율 10.6%
  (→ ISSUE-003 참조)
- 완전 격리된 재현 가능한 부하 테스트 환경 확보

## 관련 파일 및 코드
- `k6/scenarios/01-ramp.js`
- `k6/scenarios/02-spike.js`
- `k6/scenarios/03-soak.js`
- `k6/lib/helpers.js`
- `rag-server/router/mock.py`
- `backend/src/main/resources/application-loadtest.yaml`
- `k6/results/ramp-summary.json`

## 이력서 포인트
LLM 5초 지연 시뮬레이션 환경에서 ramp/spike/soak k6 시나리오를 직접 설계해 Tomcat 스레드 포화 임계(VU 200)와 p95 2.75초·에러율 10.6%를 실측 확인했다.
