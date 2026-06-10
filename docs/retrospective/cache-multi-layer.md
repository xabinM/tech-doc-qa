# 기술 회고 — RAG 응답 캐싱: 왜 캐시했고, 그 캐시는 올바른가

> 기술 문서 Q&A 서비스의 `POST /api/v1/query` 경로에 LLM(RAG) 응답 캐시를 도입한 과정을,
> "**왜 캐시가 필요했는가**"에서 시작해 "**그렇게 캐시한 것이 정말 올바른가**"까지 스스로 감사한 회고.
>
> 이 글의 핵심은 캐시를 *추가했다는 사실*이 아니라, 비결정적·맥락 의존적인 LLM 출력을
> 캐시하는 것이 타당한지 따지고, 내가 구현한 캐시가 멀티턴 환경에서 정합성이 깨지는 지점을
> 직접 찾아낸 과정에 있다.
>
> 관련 작업 로그: `OBS-004`(구현), `ISSUE-003`(선행 부하 테스트)

---

## 1. 왜 캐시인가 — 캐시를 쓰기로 한 이유

### 1-1. LLM 호출은 비싸다 — 세 가지 의미에서

`POST /api/v1/query` 요청 1건은 다음 경로를 탄다.

```
Client → Backend → (ES 검색 + LLM 생성) RAG Server → LLM API
```

이 경로의 비용은 세 축으로 나뉜다.

| 비용 축 | 내용 |
|---------|------|
| **지연(latency)** | LLM 생성에 수 초. 부하 테스트에선 `MOCK_DELAY_SECONDS=5`로 5초를 가정했다. |
| **금전(cost)** | LLM API는 토큰 단위 과금. 같은 답을 다시 만들면 그대로 다시 과금된다. |
| **처리량(throughput)** | 요청이 5초간 처리 스레드/커넥션을 점유 → 동시 처리 가능 수가 외부 I/O에 묶인다. |

### 1-2. 선행 경험 — 외부 I/O가 처리량의 천장이었다 (ISSUE-003)

k6 ramp-up(최대 VU 300, LLM 5초) 부하 테스트에서:

| 지표 | 값 |
|------|-----|
| HTTP p95 | 2,755ms |
| 에러율 | 10.6% (임계값 5% 초과 → 실패) |
| RPS | 109.8 |

HikariCP 풀 사이즈 조정(30→100)으로 커넥션 고갈에 의한 에러는 0.05%까지 잡았다.
하지만 그건 "5초를 기다릴 수 있는 동시 요청 수"를 늘린 것일 뿐, **5초라는 응답 시간 자체와
반복되는 LLM 호출 비용은 그대로**였다. 처리량의 천장은 여전히 외부 I/O였다.

### 1-3. 캐시가 답이 되는 도메인 특성 — 질문 분포의 머리가 두껍다

기술 문서 Q&A의 질문 분포는 균등하지 않다. "Spring이란?", "@Transactional 동작 원리",
"AOP가 뭔가요" 같은 **대표 질문이 트래픽의 큰 비중**을 차지한다(head-heavy).

> 같은 질문에 대해 매번 5초짜리 LLM 호출을 반복하는 것 — 이것이 가장 큰 낭비였다.
> 이 반복을 제거하면 latency·cost·throughput 세 비용을 한 번에 줄일 수 있다.

### 1-4. 캐시가 아닌 대안은 왜 부족한가

캐시를 도입하기 전에 다른 선택지를 먼저 검토했다.

| 대안 | 한계 |
|------|------|
| 스케일 아웃(인스턴스 증설) | latency·cost는 그대로. 반복 호출 낭비를 돈으로 가린다. |
| 타임아웃·풀 튜닝 | 이미 했다(ISSUE-003). 실패를 줄일 뿐 반복 호출을 없애지 못한다. |
| LLM 모델 경량화 | 품질 저하 트레이드오프. 반복 호출 문제 자체는 남는다. |
| **응답 캐시** | **반복 질문의 LLM 호출 자체를 제거** → 세 비용 동시 절감. |

→ "반복되는 동일 작업의 결과를 재사용한다"는 캐시의 본질이 이 문제의 정중앙에 있었다.

---

## 2. 그런데 — LLM 응답을 캐시해도 되는가?

캐시는 **"같은 입력 → 같은 출력"이 성립할 때** 안전하다. LLM 응답이 이 전제를 만족하는지
도입 전에 따져야 했다. 이 검토가 이 회고에서 가장 중요한 부분이고, 결과적으로 4절의
자가 감사로 이어진다.

| 캐시 안전성 질문 | 이 도메인에서의 답 | 함의 |
|------------------|-------------------|------|
| 출력이 **결정적**인가? | 아니오 — LLM은 같은 입력에도 다른 답을 낼 수 있다 | 첫 응답이 고정된다는 것을 수용해야 함 |
| 입력이 **질문 텍스트만**인가? | **아니오 — 답변 = f(질문, 대화이력)** | 키 설계의 핵심 쟁점 (4-1) |
| 원본(문서)이 **불변**인가? | 아니오 — 문서 재색인 시 정답이 바뀜 | 무효화 전략 필요 (4-4) |
| **사용자별로 달라야** 하는가? | 공개 문서 기반이라 원칙상 무관, 단 대화이력이 끼면 달라짐 | 스코프 설계 쟁점 (4-1) |

결론은 "**조건부로 캐시 가능**"이었다. LLM의 비결정성은 "첫 답을 TTL 동안 고정"으로 수용하기로 했다.
하지만 **두 번째 행 — 입력이 질문 텍스트만이 아니라는 사실**이 캐시 키 설계의 함정이 됐고,
구현 시점에는 이를 놓쳤다(4-1에서 상술).

---

## 3. 구현과 설계 결정 — 트레이드오프

### 결정 1. L1(로컬) + L2(분산) 이중 캐시

| 후보 | 장점 | 단점 | 채택 |
|------|------|------|------|
| L1만 (Caffeine) | in-process, 네트워크 0 | 인스턴스마다 독립·재시작 시 소실 | ✗ |
| L2만 (Redis) | 인스턴스 간 공유·영속 | 히트마다 네트워크 왕복 | ✗ |
| **L1 + L2** | 빈번한 히트는 in-process, 공유는 Redis | 2단 일관성 비용 | ✔ |

- 읽기: L1 → L2 → loader(RAG). 쓰기: L2 → L1 write-through.
- L2 히트 시 L1로 승격해 다음 요청부터 in-process 처리.

```java
public String getOrCompute(String cacheKey, Supplier<String> loader) {
    String l1 = l1Cache.getIfPresent(cacheKey);
    if (l1 != null) { l1HitCounter.increment(); return l1; }

    String l2 = redisTemplate.opsForValue().get(L2_PREFIX + cacheKey);
    if (l2 != null) { l2HitCounter.increment(); l1Cache.put(cacheKey, l2); return l2; }

    missCounter.increment();
    return computeWithLock(cacheKey, loader);
}
```

**TTL 트레이드오프**: L1 5분(짧게 — stale·메모리 최소화, 원본은 L2), L2 60분(길게 — LLM 비용 절감 기간).

### 결정 2. 캐시 키 — 정규화 후 SHA-256

질문을 `trim → 연속 공백 단일화 → 소문자화` 후 SHA-256.
"Spring이란? "과 "spring이란?"이 같은 키를 공유하게 했다.
**※ 이 결정이 4-1에서 다룰 정합성 결함의 진원지다 — 키에 질문만 넣었다.**

### 결정 3. Cache Stampede 방지 — Redis SET NX 분산 락

캐시 미스가 동시에 N개 터지면 N개가 전부 LLM을 호출하는 thundering herd가 발생한다.
1개 스레드만 호출하도록 분산 락으로 직렬화했다.

```
미스 → SETNX(lock, TTL 30s)
  ├─ 획득: double-check 후 loader 실행 → 캐시 저장 → 락 해제
  └─ 실패: 200ms 폴링 최대 15회 → 캐시 채워지면 반환
```

- **in-process 락이 아닌 분산 락**: 다중 인스턴스에서도 전역 1회 호출이어야 하므로.
- **폴링 vs pub/sub**: pub/sub가 우수하나 복잡. 단순 폴링을 택하되, **가상 스레드에서
  `Thread.sleep()`이 park(carrier thread 비점유)** 로 동작해 폴링 비용이 낮다고 판단.
- **double-check**: 락 획득 직후 L2를 재확인(다른 노드가 채웠을 수 있음).

### 결정 4. 전 계층 Prometheus 메트릭

"빨라졌다"가 아니라 수치로 증명하기 위해 계측했다.
`cache.query.hit{level}`, `cache.query.miss`(실제 RAG 호출), `cache.query.stampede.blocked`,
`cache.query.l1.hit_rate`/`l1.size`(Gauge).

---

## 4. 자가 감사 — 이 캐시는 올바른가?

구현 후 "정말 이대로 맞는가"를 직접 점검했다. 결과적으로 **정합성 결함 2건을 포함**해
여러 한계를 찾았고, 이 중 영향이 큰 4건은 코드로 수정했다. 정직하게 기록한다.

| 항목 | 분류 | 상태 |
|------|------|------|
| 4-1 멀티턴 캐시 키 정합성 | 정합성·프라이버시 결함 | **수정 완료** |
| 4-2 분산 락 소유권 누락 | 정합성 결함 | **수정 완료** |
| 4-3 락 대기(3s) < RAG(5s) | 효율 결함 | **수정 완료** |
| 4-6 Redis 쓰기 실패 보호 | 견고성 | **수정 완료** |
| 4-4 캐시 무효화 부재 | 한계 | 미해결 (백로그) |
| 4-5 L1 다중 인스턴스 일관성 | 한계 | 미해결 (백로그) |
| 4-7 캐시 종단 부하 재측정 | 측정 공백 | 미해결 (최우선 과제) |

### 4-1. [정합성 결함·최우선] 멀티턴 서비스인데 캐시 키는 질문 텍스트만

가장 중요한 발견이다.

이 서비스는 **멀티턴 대화**다. `ChatSessionService`가 세션의 최근 20턴을 모아 RAG에 넘기고,
RAG는 그 이력을 컨텍스트로 답을 만든다. 즉 **답변 = f(질문, 대화이력)** 이다.

```java
// RagClient — 답변은 질문과 history 둘 다의 함수
public String ask(String question, List<ConversationTurn> history) { ... }

// 그러나 캐시 키는 질문만으로 만들어진다
String cacheKey = QueryCacheService.cacheKeyOf(question);   // history·sessionId·userId 없음
String answer = queryCacheService.getOrCompute(cacheKey,
        () -> ragPort.ask(question, ctx.history()));        // loader는 history를 쓰는데 키엔 없음
```

**무엇이 깨지는가**

- 대화 A에서 "그건 왜 그래?" → `hash("그건 왜 그래?")`로 답 캐시
- 다른 맥락의 대화 B에서 같은 "그건 왜 그래?" → **캐시 히트** → A의 맥락으로 만든 엉뚱한 답 반환
- 캐시가 **전역(유저 스코프 없음)** → A 유저의 대화 맥락 기반 답이 B 유저에게 노출
  → **정합성 결함이자 프라이버시 누출**

**근본 원인**: 2절 표의 두 번째 행("입력이 질문 텍스트만인가? → 아니오")을 구현 시점에 놓쳤다.
캐시는 *단일턴(stateless)* 질문을 암묵적으로 가정했는데, 제품은 *멀티턴(stateful)* 이다.
캐시 키와 캐시 함수의 입력 정의가 불일치한다.

**→ 적용한 수정** (`QueryService.java`)

여러 선택지 중 **"단일턴(대화 첫 질문)에서만 전역 캐시를 사용하고, 멀티턴은 캐시를 우회"** 를 택했다.
근거: 캐시 효과를 만드는 head 질문 대부분이 "새 대화의 첫 질문"이라, 이 분기만으로도
**캐시 이득은 대부분 보존하면서 정합성·프라이버시 결함을 제거**할 수 있다.
(키에 대화 맥락 전체를 넣는 방식은, 맥락이 조금만 달라도 키가 갈려 히트율이 0에 수렴하므로 택하지 않았다.)

```java
// 답변 = f(질문, 대화이력). 이력이 없을 때만 답이 f(질문)이 되어 전역 캐시가 안전하다.
private String answerFor(String question, ChatSessionService.SessionContext ctx) {
    if (!ctx.history().isEmpty()) {
        return ragPort.ask(question, ctx.history());          // 멀티턴: 캐시 우회
    }
    String cacheKey = QueryCacheService.cacheKeyOf(question); // 단일턴: 전역 캐시 안전
    return queryCacheService.getOrCompute(cacheKey, () -> ragPort.ask(question, ctx.history()));
}
```

검증: `QueryServiceTest`에 단일턴(캐시 경유)·멀티턴(캐시 우회) 두 경로를 분리 검증하는 테스트 추가
(`query_multiTurn_bypassesCache`에서 `verify(queryCacheService, never()).getOrCompute(...)`로 우회를 단언).

### 4-2. [정합성 결함] 분산 락에 소유권 토큰이 없다

`computeWithLock`은 `SETNX`로 락(TTL 30s)을 잡고 `finally`에서 **키 이름만으로 delete** 한다.

```java
redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, SECONDS);
// ...
finally { redisTemplate.delete(lockKey); }   // 소유권 확인 없이 삭제
```

락 보유 스레드의 RAG 호출이 락 TTL(30s)을 넘기면 → 락 자동 만료 → 다른 스레드가 락 획득 →
**첫 스레드의 `finally`가 남의 락을 삭제**한다. 그 결과 또 다른 스레드가 동시에 loader를 실행 →
Stampede 방지가 의도대로 동작하지 않는다. 값(고유 토큰) 비교 후 삭제하는 Lua CAS / fencing token이
빠진 고전적 분산 락 결함이다.

**→ 적용한 수정** (`QueryCacheService.java`)

락 값에 요청별 UUID 토큰을 넣고, 해제는 **"값이 내 것일 때만 삭제"하는 Lua compare-and-delete** 로 바꿨다.
GET→비교→DEL을 Redis 단일 스크립트로 원자 실행해, 만료된 뒤 남이 잡은 락을 지우는 일을 막는다.

```java
private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of(
    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
    Long.class);

String lockToken = UUID.randomUUID().toString();
redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, lockTtlSeconds, SECONDS);
// ...
finally { redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockToken); }  // 내 락일 때만 삭제
```

(운영 규모가 커지면 Redisson 같은 검증된 분산 락 도입을 고려한다 — 현 단계에선 의존성 추가 없이 CAS로 해결.)

### 4-3. Stampede 락 대기(3초) < RAG 응답(5초)

락 대기는 `200ms × 15 = 최대 3초`인데 부하 테스트 RAG 응답은 5초다.
락을 잡은 스레드가 5초 걸리면 대기 스레드는 3초 만에 포기하고 **직접 loader를 실행**(`waitForCache` fallback)
→ 가장 느린 바로 그 상황에서 중복 RAG 호출이 새어 나간다. (4-2와 합쳐지면 누수가 더 커진다.)

**→ 적용한 수정** (`application.yaml`)

대기 상한을 RAG 응답 시간 + 여유로 끌어올렸다. `lock-max-retries: 15 → 40`
(200ms × 40 = **8초** ≥ RAG 5초 + 여유). 락 TTL 30s가 8s보다 충분히 길어 일관적이다.
폴링 자체를 락 해제 시그널(pub/sub)로 대체하는 것은 다음 단계의 개선으로 남긴다.

### 4-4. 캐시 무효화 메커니즘 부재

무효화가 오직 TTL뿐이다. 문서 재색인으로 정답이 바뀌거나 오답이 캐시되면 L1 5분 / L2 60분간 stale.
(2절 표 세 번째 행에서 예고한 한계.)

> **보완 방향**: 관리자 무효화 API, 문서 재색인 시 관련 캐시 evict 훅.

### 4-5. L1 다중 인스턴스 일관성 — 능동 동기화 없음

L2는 공유되지만 L1(Caffeine)은 인스턴스 로컬. 한 인스턴스에서 비워도 다른 인스턴스 L1은 그대로다.
현재는 "L1 TTL 5분"으로 완화할 뿐 Redis pub/sub 기반 L1 evict 브로드캐스트는 없다.

### 4-6. Redis(L2) 쓰기 실패 시 정상 경로 보호 부족

`computeWithLock` 정상 경로의 `putBoth()`에서 Redis 장애가 나면 예외가 전파되어
**이미 받아둔 RAG 답변까지 버려질 수 있다.** fallback 경로엔 try-catch가 있어 일관적이지 않다.

**→ 적용한 수정** (`QueryCacheService.java`)

캐시 저장을 `putBothSafely()`로 일원화해, 정상·fallback **양쪽 경로 모두** 저장 실패를 삼키고
요청은 정상 응답하도록 했다. 실패는 `cache.query.write.failed` 메트릭으로만 기록한다.
(L1은 `putBoth` 내부에서 먼저 채워지므로, L2(Redis) 장애만 흡수된다.)

```java
private void putBothSafely(String cacheKey, String value) {
    try { putBoth(cacheKey, value); }
    catch (Exception e) {
        cacheWriteFailedCounter.increment();
        log.warn("캐시 저장 실패 (요청은 정상 처리): key={}, error={}", cacheKey, e.getMessage());
    }
}
```

### 4-7. [측정 공백·최우선] 캐시 적용 후 종단 부하 테스트 미실시

5절 수치는 **캐시 자체 메트릭(히트율 등)** 실측이다.
캐시 적용 후 ISSUE-003 시나리오를 재실행한 **에러율·RPS·p95의 종단 개선 수치는 아직 없다.**

> **보완 방향(최우선)**: 캐시 ON/OFF로 동일 ramp 재측정해 처리량·지연 종단 효과를 숫자로 확정.

---

## 5. 결과

### 캐시 자체 메트릭 (Grafana 실측, 워밍업 후)

| 지표 | 값 |
|------|-----|
| L1 히트 | 최대 **200/s** |
| L2 히트 | 0/s (대부분 L1에서 처리) |
| RAG 실호출(미스) | 최대 40/s → 히트율 상승 후 급감 |
| Stampede 차단 | **13.2/s** |
| L1 히트율 | **58.2%** |

- 캐시 히트 시 RAG 응답 **5초 → 수 ms**(스레드 점유 시간 제거).
- 히트율 58.2% = 질문 절반 이상을 LLM 호출 없이 응답 → 그만큼 latency·cost 절감.
- 단, 4-2/4-3 결함으로 RAG가 느린 구간에선 일부 중복 호출이 새어 나갔을 수 있어
  Stampede 차단 수치는 "방지가 동작한 만큼"의 **하한**으로 해석해야 한다.

### 참고 — 선행 작업(ISSUE-003) 종단 수치

| 지표 | Before | After |
|------|--------|-------|
| 에러율 | 10.6% | 0.05% |
| RPS | 109.8 | 237.3 |
| p95 | 2,755ms | 1,192ms |

> 이는 **캐시가 아닌 커넥션 풀 조정**의 효과다. 캐시의 종단 효과는 위 자체 메트릭까지만 확정됐고,
> 부하 테스트 종단 재측정(4-7)은 다음 과제다. — 효과와 한계를 섞어 과장하지 않기 위해 구분해 둔다.

---

## 6. 회고 한 줄

> "반복 질문"이라는 도메인 특성에서 출발해 LLM 응답 캐시의 타당성을 따지고
> L1/L2 + 분산 락 Stampede 방지를 구현해 히트율 58.2%, 캐시 히트 시 5초→수ms를 실측했다.
> 더 중요하게는, 구현을 스스로 감사해 **멀티턴 환경에서 캐시 키가 대화 맥락을 누락해
> 정합성·프라이버시가 깨지는 결함**과 **분산 락 소유권 누락**을 직접 찾아내,
> 단일턴 한정 캐시 분기와 Lua compare-and-delete로 수정했다.
> "무엇을 캐시할 수 있는가"는 "어떻게 캐시하는가"보다 어려운 질문이라는 것을 배웠다.
>
> 다만 결함을 코드로 막은 것까지가 현재이고, **그 효과를 부하 테스트 종단 수치로 증명하는 일(4-7)** 은
> 아직 남아 있다 — 다음 과제다.

---

## 관련 파일

- `backend/.../application/query/QueryService.java` — **수정**: `answerFor()` 단일턴 한정 캐시 분기 (4-1)
- `backend/.../application/query/QueryCacheService.java` — **수정**: 락 소유권 토큰 + Lua CAS (4-2), `putBothSafely()` (4-6)
- `backend/.../resources/application.yaml` — **수정**: `cache.query.lock-max-retries` 15→40 (4-3)
- `backend/.../application/query/QueryServiceTest.java` — **수정**: 단일턴/멀티턴 경로 검증 테스트 추가·기존 깨진 테스트 복구
- `backend/.../application/query/ChatSessionService.java` — (참고) 멀티턴 history 구성
- `backend/.../infrastructure/query/RagClient.java` — (참고) `ask(question, history)`
- 작업 로그: `.claude/work-log/issues/OBS-004-*.md`, `ISSUE-003-*.md`

> 미해결 백로그: 4-4(캐시 무효화), 4-5(L1 다중 인스턴스 일관성), 4-7(캐시 종단 부하 재측정·최우선).
