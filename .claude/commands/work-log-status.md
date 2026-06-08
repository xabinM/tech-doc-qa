# /work-log-status

`work-log-analyzer` 에이전트를 모드 3으로 스폰해 work-log 전체 현황을 요약한다.

## 실행 절차

1. `work-log-analyzer` 에이전트를 모드 3(이슈 현황 요약)으로 스폰한다
2. 에이전트가 `.claude/work-log/issues/` 전체 파일을 읽고 status별로 집계한다
3. 아래 형식으로 출력한다:

```
전체: N개
- open: N개
- investigating: N개
- resolved: N개 (resume_worthy: N개)
- confirmed: N개 (resume_worthy: N개)

이력서 즉시 활용 가능: N개   ← resolved/confirmed + resume_worthy: true
수치 확보 후 활용 가능: N개  ← resume_worthy: true이나 status가 open/investigating
```

4. open / investigating 이슈가 있으면 제목과 tags를 함께 나열해 현재 미해결 작업을 상기시킨다
