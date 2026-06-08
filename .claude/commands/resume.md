# /resume

`work-log-analyzer` 에이전트를 스폰해 이력서 포인트를 생성한다.
메인 컨텍스트 오염을 방지하기 위해 파일 읽기와 가공은 에이전트가 담당한다.

## 실행 절차

1. `work-log-analyzer` 에이전트를 모드 2(이력서 포인트 생성)로 스폰한다
2. 에이전트가 `.claude/work-log/issues/` 하위 모든 파일을 읽는다
2. 아래 조건을 모두 만족하는 항목만 필터한다:
   - `resume_worthy: true`
   - `status: resolved` 또는 `status: confirmed`
3. 항목별로 이력서 bullet point를 생성한다:
   - 형식: "[기술/상황]에서 [문제/관찰]을 [조치]해 [수치] 달성"
   - 수치가 없으면 bullet point 생성하지 않고 "수치 미확보 — 재측정 필요" 표시
4. 카테고리별로 분류해서 출력한다:
   - 성능 개선
   - 안정성 / 장애 대응
   - 관측 가능성
   - 아키텍처 설계
5. `.claude/work-log/RESUME-POINTS.md` 에 저장한다

## 출력 형식 예시

```
## 성능 개선
- L1(Caffeine)/L2(Redis) 이중 캐시 도입으로 동일 질문 응답시간 3,200ms → 50ms 단축
- HikariCP 커넥션 풀 pending 454 → 0 개선 (설정 조정 및 트랜잭션 범위 축소)

## 안정성 / 장애 대응
- Resilience4j Circuit Breaker 설정 수정으로 외부 LLM 장애 시 즉시 fallback 동작 확인

## 관측 가능성
- Prometheus + Grafana 기반 모니터링 구축, k6 부하 테스트로 병목 지점 식별

## 아키텍처 설계
- Java 21 가상 스레드 적용, 300 VU 부하에서 JVM 스레드 41개 유지 (플랫폼 스레드 대비 ~80% 절감)
```
