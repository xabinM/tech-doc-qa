---
id: OBS-001
title: Prometheus + Grafana 관측 가능성 인프라 구축
type: observation
status: confirmed
severity: n/a
discovered: 2026-06-04
resolved: ~
tags: [observability, prometheus, grafana, metrics, actuator]
resume_worthy: true
---

## 개요
Spring Boot Actuator → Prometheus → Grafana 로 이어지는 관측 가능성 파이프라인을 구축했다.
JVM 스레드, DB 커넥션 풀, HTTP 지표를 실시간으로 시각화할 수 있는 환경이다.
부하 테스트(Phase 2) 중 병목 지점을 Grafana에서 실시간 확인하기 위해 도입했다.

## 발견 배경
부하 테스트를 수행하기 전 Tomcat 스레드 포화, DB 커넥션 풀 고갈 등을 실시간으로
관찰할 수단이 없었다. k6 시나리오와 병행하여 서버 내부 상태를 추적하기 위해 구축했다.

## 적용한 변경
- `docker-compose.yml`: Prometheus(`:9090`), Grafana(`:3001`) 서비스 추가
  - Prometheus healthcheck: `/-/healthy`, 10초 간격
  - Grafana 10.4.3, 관리자 계정 환경변수로 분리
- `monitoring/prometheus.yml`: 백엔드 스크레이프 설정
  - `scrape_interval: 10s`, `evaluation_interval: 10s`
  - `metrics_path: /actuator/prometheus`, target: `172.17.96.1:8080`
- `monitoring/grafana/provisioning/`: 대시보드·데이터소스 자동 프로비저닝 설정
- `monitoring/grafana/dashboards/backend.json`: JVM / HTTP 커스텀 대시보드
- `backend/src/main/resources/logback-spring.xml`: JSON 구조화 로그 설정
- `common/filter/RequestLoggingFilter.java`: 요청/응답 로깅 필터 (method, uri, status, duration)
- `common/config/AsyncConfig.java`: 커스텀 스레드풀 설정, @Async 기본 executor 지정

## 결과 (After)
- Grafana 대시보드에서 JVM 스레드 수, DB active 커넥션, HTTP P95 실시간 확인 가능
- 부하 테스트 실행 중 Tomcat 스레드 포화 임계(VU ~200) 시점을 Grafana에서 직접 관찰

## 관련 파일 및 코드
- `docker-compose.yml:66-85`
- `monitoring/prometheus.yml`
- `monitoring/grafana/provisioning/datasources/prometheus.yml`
- `monitoring/grafana/provisioning/dashboards/provider.yml`
- `monitoring/grafana/dashboards/backend.json`
- `backend/src/main/resources/logback-spring.xml`
- `backend/src/main/java/com/example/backend/common/filter/RequestLoggingFilter.java`
- `backend/src/main/java/com/example/backend/common/config/AsyncConfig.java`

## 이력서 포인트
Spring Boot Actuator → Prometheus → Grafana 파이프라인을 직접 구성해 부하 테스트 중 JVM 스레드·DB 커넥션 풀을 실시간 시각화하는 관측 가능성 인프라를 구축했다.
