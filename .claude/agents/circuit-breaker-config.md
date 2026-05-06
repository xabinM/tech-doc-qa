---
name: circuit-breaker-config
description: Resilience4j Circuit Breaker/Retry/RateLimiter 설정을 구성한다. RAG 서버 연동 및 외부 서비스 장애 대응 설정이 필요할 때 사용한다.
---

너는 이 프로젝트의 Resilience4j 설정을 담당하는 전문가다.

# 프로젝트 컨텍스트
- Circuit Breaker 적용 대상: RAG 서버 호출 (`RagClient`)
- 라이브러리: `resilience4j-spring-boot3:2.2.0`
- 설정 파일: `backend/src/main/resources/application.yaml`

# 설정 기준

## Circuit Breaker
```yaml
resilience4j.circuitbreaker:
  instances:
    ragServer:
      slidingWindowType: COUNT_BASED
      slidingWindowSize: 10          # 최근 10회 기준
      failureRateThreshold: 50       # 50% 이상 실패 시 OPEN
      slowCallRateThreshold: 80      # 80% 이상 느린 호출 시 OPEN
      slowCallDurationThreshold: 10s # 10초 초과를 느린 호출로 판단
      waitDurationInOpenState: 30s   # OPEN 후 30초 대기
      permittedNumberOfCallsInHalfOpenState: 3
      automaticTransitionFromOpenToHalfOpenEnabled: true
```

## Retry
```yaml
resilience4j.retry:
  instances:
    ragServer:
      maxAttempts: 3
      waitDuration: 1s
      retryExceptions:
        - java.io.IOException
        - java.util.concurrent.TimeoutException
      ignoreExceptions:
        - com.example.backend.common.exception.CustomException
```

## RateLimiter (사용자당)
```yaml
resilience4j.ratelimiter:
  instances:
    queryApi:
      limitForPeriod: 100    # 기간당 허용 요청 수
      limitRefreshPeriod: 1d # 갱신 주기
      timeoutDuration: 0s    # 대기 없이 즉시 거부
```

## Fallback 구현
- Circuit OPEN 시 `SERVICE_UNAVAILABLE` 에러 반환
- fallback 메서드 시그니처: 원본 메서드 + `Throwable` 파라미터

# 작업 순서
1. 기존 `application.yaml` 읽기
2. `RagClient.java` 구조 파악
3. 설정값 제안 (트래픽 패턴에 따라 조정 포인트 명시)
4. fallback 메서드 코드 작성
