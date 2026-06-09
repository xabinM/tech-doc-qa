# Work Log 작성 규칙

## 1. 파일 위치 및 네이밍

- 이슈 파일: `.claude/work-log/issues/`
- 이미지: `.claude/work-log/images/`
- 이력서 포인트 출력: `.claude/work-log/RESUME-POINTS.md`

네이밍 규칙:
- 문제/버그/미해결: `ISSUE-{NNN}-{kebab-case}.md`
- 긍정적 확인/검증: `OBS-{NNN}-{kebab-case}.md`
- 이미지: `{파일ID}-{before|after|evidence}-{설명}.png`
  - 예: `ISSUE-001-before-db-pool.png`, `OBS-007-evidence-jvm-threads.png`

## 2. 이미지 처리 절차

이미지는 **선(先) 첨부**와 **후(後) 첨부** 두 가지 패턴을 모두 허용한다.

### 선(先) 첨부 — 이미지가 이미 준비된 경우
1. 저장할 경로와 파일명을 사용자에게 명시적으로 요청
2. 사용자가 저장 완료 확인
3. Read 도구로 이미지 파일 존재 확인
4. 확인 후 이슈 파일 작성

### 후(後) 첨부 — 이미지가 아직 없는 경우 (기본 패턴)
1. `images` 섹션에 `미첨부 — 추후 추가 예정` 명시하고 파일을 먼저 작성
2. 이미지 준비 후 사용자가 저장 경로와 파일명을 알려주면:
   - Read 도구로 이미지 존재 확인
   - 이슈 파일의 해당 섹션에 `![설명](../images/{파일명})` 추가
   - `미첨부 — 추후 추가 예정` 문구 제거

## 3. 파일 템플릿

### ISSUE 템플릿 (문제/버그/미해결)

```
---
id: ISSUE-001
title: 한국어 제목
type: issue
status: open | investigating | resolved
severity: critical | high | medium | low
discovered: YYYY-MM-DD
resolved: YYYY-MM-DD | ~
tags: [태그1, 태그2]
resume_worthy: true | false
---

## 개요
이 이슈가 무엇인지 2~4줄로 설명.
왜 문제가 되는지, 어떤 영향이 있는지 포함.

## 발견 배경
어떤 상황(테스트/개발/운영)에서 발견했는지 1~3줄.
어떤 작업 중에 어떤 계기로 발견했는지 구체적으로.

## 테스트 환경
부하 테스트 또는 특정 조건에서 발견된 경우 작성. 일반 개발 중 발견 시 생략 가능.
- 시나리오: ramp-up / spike / soak / 수동 테스트 등
- 최대 VU(가상 사용자): N명
- 테스트 시간: N분
- 총 요청 수: N건
- 평균 RPS: N req/s
- 에러율: N%
- 기타 조건: (예: Rate Limit 비활성화, Mock 미사용 등)

## 상태 (Before)
- 구체적 수치, 로그, 증상
- ![증거](../images/ISSUE-001-evidence.png)

## 원인 분석
왜 이 문제가 발생했는지 기술적으로 설명.
미파악 시 "원인 미파악 — 추가 조사 필요" 명시.

## 개선 방향
미해결 상태에서도 반드시 작성.
- 가능한 해결 방법 나열
- 우선순위 또는 트레이드오프 명시
- 해결 후 이 섹션을 "## 적용한 변경"으로 교체

## 적용한 변경
resolved 상태일 때 작성. 미해결 시 섹션 생략.
- 변경한 파일, 설정, 코드 (구체적으로)

## 결과 (After)
resolved 상태일 때 작성. 미해결 시 섹션 생략.
- 수치 비교: Before X → After Y
- ![결과](../images/ISSUE-001-after.png)

## 관련 파일 및 코드
- 파일: `경로/파일명.java:라인번호`
- 발견 시점 핵심 코드:
```java
// 관련 코드 스냅샷
```

## 이력서 포인트
resume_worthy: true인 경우만 작성.
아래 형식으로 1~2줄:
"[상황]에서 [문제]를 발견, [변경]을 적용해 [수치] 개선"
```

---

### OBS 템플릿 (긍정적 구현/검증)

ISSUE와 달리 원인 분석·개선 방향 섹션이 없다.
직접 설계·구현한 것과 실측 수치에 집중한다.

```
---
id: OBS-001
title: 한국어 제목
type: observation
status: confirmed
severity: n/a
discovered: YYYY-MM-DD
resolved: ~
tags: [태그1, 태그2]
resume_worthy: true | false
---

## 개요
무엇을 구현/확인했는지 2~4줄.
왜 도입했는지, 어떤 효과를 목표로 했는지 포함.

## 발견 배경
어떤 계기로 이 작업을 시작했는지 1~3줄.
선행 이슈나 요구사항과의 연결고리 명시.

## 테스트 환경
실측이 포함된 경우 작성. 구현 작업만인 경우 생략.

## 적용한 변경
- 구현한 내용을 구체적으로 기술 (파일, 설정, 설계 결정)
- 핵심 코드 스냅샷 포함

## 결과
- 실측 수치 또는 달성된 상태
- ![증거](../images/OBS-001-evidence.png)

## 관련 파일 및 코드
- 파일: `경로/파일명.java:라인번호`

## 이력서 포인트
resume_worthy: true인 경우만 작성.
아래 형식으로 1~2줄:
"[기술/상황]에서 [무엇]을 직접 설계해 [수치/효과] 달성"
```

## 4. status 정의

| status | 의미 |
|--------|------|
| open | 발견만 됨, 원인 미파악 |
| investigating | 원인 분석 중 |
| resolved | 수정 완료, After 수치 있음 |
| confirmed | OBS 타입 — 긍정적으로 실측 확인됨 |

## 5. resume_worthy 기준

아래 조건을 모두 만족해야 `true`:
- before/after 수치가 있거나 (ISSUE), 실측 수치가 있거나 (OBS)
- 내가 직접 설계하거나 코드로 구현한 것
- "어떻게 이 수치를 얻었나요?" 질문에 답할 수 있음

## 6. 코드 작업 시작 시 자동 이슈 스캔 규칙

코드 수정/구현 작업을 시작할 때 반드시 아래를 수행한다:

1. `.claude/work-log/issues/` 하위 파일 목록을 읽는다
2. 작업 대상 파일/기능과 관련된 이슈가 있으면 작업 전에 사용자에게 보고한다
   - 보고 형식: "관련 이슈 있음: [ISSUE-NNN] {제목} (status: {상태})"
3. `status: open | investigating` 인 이슈가 있으면 해당 이슈를 먼저 확인하도록 제안한다
4. 관련 이슈가 없으면 별도 언급 없이 작업을 진행한다

**태그 기반 연관 판단 기준:**
- 작업 파일이 `RagClient`, `WebClient` 관련 → tags에 `circuit-breaker`, `rag`, `groq` 포함된 이슈 확인
- 작업 파일이 캐시 관련 → tags에 `cache`, `redis`, `caffeine` 포함된 이슈 확인
- 작업 파일이 DB/쿼리/트랜잭션 관련 → tags에 `database`, `hikaricp`, `connection-pool` 포함된 이슈 확인
- 작업 파일이 배치 관련 → tags에 `batch`, `spring-batch` 포함된 이슈 확인

## 7. /log-issue 호출 시 내 행동 규칙

1. 사용자의 이슈 설명을 듣는다
   - 설명이 없으면 커밋 내용·코드·테스트 결과를 직접 분석해 이슈를 도출한다
2. type / severity / resume_worthy 판단 후 파일명 제안
   - ISSUE: `work-log.md` ISSUE 템플릿 사용
   - OBS: `work-log.md` OBS 템플릿 사용 (원인 분석·개선 방향 섹션 제외)
3. 이미지 필요 여부 확인
   - 준비된 이미지 있음: 저장 경로·파일명 명시 요청 → 확인 후 작성 (선 첨부)
   - 이미지 없음: 먼저 파일 작성, `미첨부 — 추후 추가 예정` 명시 (후 첨부)
4. 파일 작성 완료 후 "ISSUE-NNN / OBS-NNN 생성 완료" 보고

## 8. /resume 호출 시 내 행동 규칙

1. `.claude/work-log/issues/` 전체 파일 읽기
2. `resume_worthy: true` + `status: resolved | confirmed` 만 필터
3. 항목별 이력서 bullet point 생성
4. `.claude/work-log/RESUME-POINTS.md` 에 저장 후 내용 출력

## 9. /work-log-status 호출 시 내 행동 규칙

`work-log-analyzer` 에이전트를 모드 3으로 스폰해 전체 이슈 현황을 요약한다.
출력 형식:
```
전체: N개
- open: N개
- investigating: N개
- resolved: N개 (resume_worthy: N개)
- confirmed: N개 (resume_worthy: N개)

이력서 즉시 활용 가능: N개   ← resolved/confirmed + resume_worthy: true
수치 확보 후 활용 가능: N개  ← resume_worthy: true이나 status가 open/investigating
```
