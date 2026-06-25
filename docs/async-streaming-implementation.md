# 비동기 토큰 스트리밍 RAG 파이프라인 — 구현 및 이슈 해결 기록

- 상태: **구현 완료 · 라이브 검증 완료**
- 브랜치: `feat/backend/async-streaming`
- 영향 범위: `backend`(Spring) · `rag-server`(FastAPI) · `frontend`(Next.js)
- 관련 설계 문서: [async-streaming-design.md](./async-streaming-design.md)

질의 처리를 **동기 요청-응답**에서 **메시지 큐(Redis Streams) 기반 비동기 + 토큰 스트리밍(ChatGPT식 타자기 효과) + 재연결/재생**으로 전환했다. 추가 미들웨어 없이 **Redis Streams 단독**으로 잡 큐와 토큰 전달을 모두 처리한다.

---

## 1. 동기 → 비동기 전환 배경

기존 흐름은 동기 블로킹이었다 — 브라우저가 RAG/LLM 답변이 완성될 때까지 HTTP 커넥션을 점유하고 대기했다. 이를 다음 목적으로 전환했다.

- **토큰 스트리밍 UX**: 답변을 토큰 단위로 실시간 표시(타자기 효과)
- **요청 스레드 비점유**: LLM 생성 시간만큼 톰캣 스레드가 묶이지 않음
- **이력 저장 내구성**: 인메모리 `@Async`(at-most-once, 크래시 시 유실)를 워커 작업으로 흡수 → at-least-once
- **재연결/재생**: 연결이 끊겨도 마지막 수신 지점부터 이어받기

### 기술 선택 — 왜 Redis Streams 단독인가
- 이미 Redis를 Rate Limit·캐시·Refresh Token에 운영 중 → **인프라 추가 0**
- Kafka는 "특정 브라우저 1명에게 실시간 타겟 전달"에 부적합(파티션/컨슈머그룹 모델). 마지막 전달 hop은 Redis가 정석이므로 잡 큐까지 Redis로 통일하는 것이 가장 단순하면서 강력하다.
- Redis Stream 1개 기술로 **잡 큐 + 토큰 전달 + 재연결**을 모두 충족.

---

## 2. 전체 아키텍처

```
브라우저 ChatPage (EventSource)
  └─ POST /api/query (Next BFF) ─→ POST /api/v1/query (backend)
        ├─ 캐시 히트 → 200 {answer}  (즉시)
        └─ 캐시 미스 → 202 {jobId} + query:jobs 스트림에 XADD
  └─ GET /api/query/{jobId}/stream (Next BFF SSE 프록시)
        └─ GET /api/v1/query/{jobId}/stream (backend SSE relay)
              └─ answer:{jobId} 스트림 XREAD BLOCK

[워커]  RedisStreamJobConsumer (Consumer Group "workers")
   └─ query:jobs 소비 → QueryJobProcessor
        ├─ 캐시 재확인 → 미스 시 RagPort.askStream()
        │     └─ rag-server POST /ask/stream (Groq stream=True)
        ├─ 토큰 도착마다 answer:{jobId} XADD (+ 전체 누적)
        ├─ 완료 시 캐시 적재 + 이력(query_logs) 저장
        └─ XACK
```

### Redis Stream 2개 역할
| 스트림 | 역할 | 비고 |
|--------|------|------|
| `query:jobs` | 잡 큐 (Consumer Group `workers`) | XREADGROUP 소비, XACK, 미처리는 PEL 잔류 |
| `answer:{jobId}` | per-job 토큰 전달 | XADD(token/done/error), TTL 1h, SSE가 XREAD BLOCK으로 relay |

### 멀티 인스턴스 라우팅
SSE를 보유한 백엔드 인스턴스가 누가 `answer:{jobId}`에 XADD했든 XREAD만 하면 되므로 인스턴스 간 직접 통신이 불필요하다. SSE 이벤트 `id`가 Redis 엔트리 ID이므로, 재연결 시 `Last-Event-ID`로 그 지점부터 재생된다.

---

## 3. 구현 단계 (커밋 기준)

| Phase | 내용 | 커밋 |
|-------|------|------|
| 설계 | 설계 문서 | `98f127d` |
| 1 | 잡 큐 발행 인프라 (`QueryJob`·`QueryJobQueue`·`RedisStreamQueryJobQueue`) | `652ca66` |
| 1 | `POST /query` 비동기 제출 전환 (202+jobId, 캐시 히트 즉시반환, 멱등성 A안) | `cde2b50` |
| 1 | 워커(`RedisStreamJobConsumer`·`QueryJobProcessor`·`AnswerStream`) + 이력 흡수 | `c63c103` |
| 1 | SSE 엔드포인트(`QueryStreamController`·`AnswerSseRelay`·`AnswerStreamReader`) | `63fd20f` |
| 0 | rag-server 토큰 스트리밍 `POST /ask/stream` (Groq stream=True) | `7517f3a` |
| 2 | 워커 블로킹 `ask()` → `askStream()` SSE 중계 (진짜 타자기 효과) | `1f88aed` |
| 4 | 프론트 스트리밍 UI (EventSource + BFF SSE 프록시) | `8625f07` |

### 핵심 설계 결정
- **멱등성 A안**: `Idempotency-Key` 레코드가 동기 완료 시 `answer`, 비동기 발행 시 `jobId`를 저장. 중복 요청은 같은 jobId로 **동일 답변 스트림에 재구독** → 멱등성과 재연결을 jobId로 통합.
- **트랜잭션 미개방**: 워커는 RAG 호출 중 DB 트랜잭션을 열지 않는다(커넥션 풀 고갈 방지 규칙 준수). history 조회·이력 저장은 각각 짧은 독립 트랜잭션.
- **이력 저장 흡수**: 캐시 미스 경로의 이력 저장이 워커로 흡수됨(jobId 멱등 키). 캐시 히트 경로는 기존 `QueryCompletedEvent` 유지.
- **헥사고날 경계**: `RagPort.askStream`은 Reactor 타입 대신 `Consumer<String>` 콜백을 노출해 application 계층을 기술 중립으로 유지.

---

## 4. 통합 검증 중 발견·해결한 이슈

docker compose로 실제 인프라(Postgres·Redis·ES)를 띄우고 backend·rag-server·frontend를 구동해 라이브 검증하던 중 **유닛/컴파일로는 드러나지 않는 결함 4건**을 발견·수정했다.

### ISSUE-1 · 워커 컨슈머 단일 스레드 병목 → 동시성 (`d726b3a` → `584297a`)
- **상태(Before)**: `StreamMessageListenerContainer`가 기본 단일 스레드로 잡을 순차 처리. 워커가 블로킹 RAG 스트리밍 동안 스레드를 점유해 처리량 병목.
- **1차 시도(`d726b3a`)**: concurrency 만큼 consumer를 Consumer Group에 등록(4개).
- **회귀 발견**: 라이브 테스트에서 `RedisCommandTimeoutException: XREADGROUP timed out after 1 minute` 발생, 잡이 **간헐적으로 처리되지 않음**.
- **원인**: Lettuce는 기본적으로 단일 네이티브 커넥션을 공유한다. 4개 consumer가 각자 블로킹 XREADGROUP을 같은 커넥션에 걸어 **커넥션 경합** → 타임아웃.
- **최종 수정(`584297a`)**: **단일 consumer가 블로킹 읽기를 전담**(공유 커넥션의 블로킹 읽기를 1개로 제한)하고, 실제 처리는 **별도 스레드풀로 분리**해 병렬성 유지. executor 포화 시 `CallerRunsPolicy`로 폴링을 늦춰 백프레셔.
- **결과(After)**: 잡 처리 안정화, 처리 병렬성 유지. 라이브에서 227토큰 스트리밍 + done 확인.

### ISSUE-2 · Netty 이벤트 루프 블로킹 (`785b44c`)
- **원인**: WebClient의 `bodyToFlux().doOnNext()`는 Reactor Netty 이벤트 루프 스레드에서 실행되는데, 그 안에서 블로킹 Redis XADD(answer 스트림 발행)를 호출 → 이벤트 루프 블로킹 → 동시 요청 I/O 지연.
- **수정**: `publishOn(Schedulers.boundedElastic())`로 토큰 처리를 별도 스케줄러로 이동.

### ISSUE-3 · SSE jobId 소유권 미검증 (IDOR) (`16416a2`)
- **원인**: 답변 스트림 구독 시 jobId가 인증 사용자 소유인지 검증하지 않아, 다른 사용자의 답변 스트림에 접근 가능(추측 불가능한 UUID로 완화되나 인가 누락).
- **수정**: `JobOwnershipStore`(Redis `job:owner:{jobId}=userId`, TTL 1h) 도입. 제출 시 등록, 구독 시 검증. 불일치 시 **SSE error 이벤트 반환**(4xx 대신 — EventSource 무한 재연결 루프 방지).
- **검증**: 타 사용자 토큰으로 접근 → `event:error / 접근 권한이 없습니다` 확인.

### ISSUE-4 · 스트리밍 답변 공백·개행 유실 (`7dfaf0c`)
사용자 리포트: "글은 순차적으로 나오는데 띄어쓰기·줄바꿈이 정리되지 않음."

- **증상 1 (공백)**: 토큰의 선행 공백이 사라져 단어가 붙음.
  - **원인**: SSE 규격상 수신측(EventSource)이 `data:` 값의 선행 공백 1개를 제거한다. Spring `SseEmitter`는 `data:` 뒤에 보정 공백을 넣지 않아 토큰 선행 공백이 유실.
- **증상 2 (개행)**: 마크다운 헤더/리스트가 한 줄로 붙어 렌더링됨.
  - **원인**: 토큰 내 개행이 SSE 멀티라인 `data`로 분해되며 **rag→backend 디코딩에서 유실**(rag-server는 개행을 보냈으나 backend가 받지 못함을 빈 data 라인 카운트로 확인).
- **수정**: **두 SSE hop(rag→backend, backend→browser) 모두 토큰을 JSON 문자열로 인코딩**해 공백·개행을 한 줄에 안전하게 전송하고, 수신측이 JSON 파싱.
  - rag-server: 토큰 `data`를 `json.dumps`로 인코딩
  - `RagClient`: `ObjectMapper`로 토큰 파싱
  - `AnswerSseRelay`: `data`를 JSON 인코딩
  - `ChatPage`: 토큰 `data`를 `JSON.parse`
- **검증**: 브라우저 조립 결과 == DB 저장 답변(무손실), 마크다운 번호목록·문단 개행 정상.

### 부수: 기존 테스트 결함 2건 (`7b16780`)
라이브 인프라로 전체 스위트를 돌리며 드러난 기존(본 작업 무관) 결함.
- `ChatSessionServiceTest`: 모킹이 불변 `List.of`를 반환해 `prepareSession`의 `Collections.reverse`에서 예외 → 가변 `ArrayList`로 교정 + 단언 순서 바로잡음.
- `AuthControllerTest`: 회원가입은 API 스펙상 201인데 200을 단언 → `isCreated()`로 교정.

---

## 5. 검증 결과

| 영역 | 결과 |
|------|------|
| rag-server | pytest **35 통과** (스트리밍 정상/오류/인증 + llm 토큰 순차) |
| backend (라이브 인프라) | 전체 스위트 **58 통과** |
| frontend | `tsc --noEmit` 통과 |
| 라이브 e2e | signup(201)→login→202(jobId)→SSE 토큰 스트리밍→done, 이력 저장·캐시 히트·IDOR 차단·공백/개행 보존 확인 |
| Redis 배선 | `query:jobs`의 `workers` Consumer Group 실제 생성, Flyway V1~V6 적용 확인 |

---

## 6. 구성요소 파일 맵

### backend
```
application/query/
  QueryJob.java                  잡 메시지 모델
  QueryService.java              제출(submit): RateLimit·캐시조회·잡발행·소유권등록
  QueryJobProcessor.java         워커 처리 로직: 캐시→RAG스트리밍→토큰발행→이력저장
  QueryCacheService.java         L1/L2 캐시 (getIfCached, put 추가)
  IdempotencyService.java        멱등성 A안 (answer/jobId 분기)
  port/
    QueryJobQueue.java           잡 발행 포트
    AnswerStream.java            답변 스트림 발행 포트 (token/done/error)
    AnswerStreamReader.java      답변 스트림 읽기 포트
    RagPort.java                 askStream(question, history, onToken)
    JobOwnershipStore.java       jobId 소유권 포트
infrastructure/query/
  RedisStreamQueryJobQueue.java  query:jobs XADD
  RedisStreamJobConsumer.java    단일 reader + executor 처리 (Consumer Group)
  RedisAnswerStream.java         answer:{jobId} XADD + TTL
  RedisAnswerStreamReader.java   answer:{jobId} XREAD BLOCK
  RedisJobOwnershipStore.java    job:owner:{jobId} 저장/검증
  RagClient.java                 WebClient SSE 소비 + CircuitBreaker (JSON 토큰 파싱)
interfaces/query/
  QueryController.java           POST /query (200/202)
  QueryStreamController.java     GET /query/{jobId}/stream (소유권 검증)
  AnswerSseRelay.java            answer 스트림 → SSE relay (JSON 인코딩)
  dto/QuerySubmitResponse.java   status=completed/accepted 통합 DTO
```

### rag-server
```
router/query.py    POST /ask/stream (StreamingResponse, 토큰 JSON 인코딩)
service/rag.py     ask_stream: ES검색 → (token...)+(done,sources)
client/llm.py      generate_answer_stream: Groq stream=True
```

### frontend
```
app/api/query/route.ts                 POST 프록시 (status=completed/accepted)
app/api/query/[jobId]/stream/route.ts  SSE 프록시 (AT 주입·401 refresh·Last-Event-ID)
components/query/ChatPage.tsx           EventSource 토큰 누적(타자기) + JSON.parse
```

---

## 7. 남은 개선 항목 (Phase 3 백로그)

- **DLQ + XAUTOCLAIM**: 파싱 실패 등 미처리(PEL) 메시지의 재claim/폐기. 현재는 PEL 잔류만.
- **스트리밍 Stampede 방지 재통합**: 스트리밍 전환으로 `getOrCompute`(분산 락) 미사용. 동일 질문 동시 미스 시 RAG 중복 호출 가능.
- **SSE 취소 강화**: `SseEmitter.onCompletion/onTimeout` 플래그로 클라이언트 종료 즉시 pump 중단(현재는 다음 send 실패로 감지).
- **블로킹 Redis 커넥션 격리**: SSE relay의 XREAD BLOCK이 공유 Lettuce 커넥션을 점유. 다수 SSE 동시 연결 시 전용 커넥션(`shareNativeConnection=false`) 검토.
- **메트릭**: 큐 깊이(`XLEN`)·대기 lag·생성 시간·활성 SSE 수.
- **폴링 폴백**: SSE 차단 환경용 `GET /query/{jobId}` 상태 폴링.
</content>
