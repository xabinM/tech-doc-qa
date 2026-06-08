# /log-issue

작업 중 발견한 이슈나 검증된 관찰 사항을 `.claude/work-log/issues/` 에 기록한다.

## 실행 절차

반드시 `.claude/rules/work-log.md` 의 규칙을 따른다.

1. 사용자의 이슈 설명을 듣는다
2. 아래를 판단해서 사용자에게 먼저 제시한다:
   - type: issue(문제) / observation(긍정적 확인)
   - severity: critical / high / medium / low / n/a
   - 파일명 제안 (예: ISSUE-004-groq-api-bottleneck.md)
   - resume_worthy: true / false — 판단 근거도 함께 설명
3. 이미지가 있으면 저장 경로와 파일명을 명시해서 먼저 요청한다
   - 경로: `.claude/work-log/images/{파일ID}-{설명}.png`
   - 사용자 저장 확인 후 Read로 검증
4. 파일을 작성한다
5. 완료 보고: "ISSUE-NNN / OBS-NNN 생성 완료"

## 이슈 번호 부여 규칙

- 기존 파일 목록을 읽어 다음 번호 자동 부여
- ISSUE와 OBS는 번호 체계를 분리하지 않고 통합 관리
  - 예: ISSUE-001, OBS-002, ISSUE-003 ...
