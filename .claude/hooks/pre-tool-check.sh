#!/bin/bash
# PreToolUse 훅 — 치명적 명령 실행 전 차단
# exit 2 반환 시 Claude Code가 툴 실행을 막고 사용자에게 알림

CMD="$CLAUDE_TOOL_INPUT"
WARNINGS=""

# Flyway 마이그레이션 파일 수정/삭제
if echo "$CMD" | grep -qE "db/migration/V[0-9]"; then
  if echo "$CMD" | grep -qiE "(rm |del |sed -i|truncate|> )"; then
    WARNINGS="$WARNINGS\n[CRITICAL] Flyway 마이그레이션 파일 수정/삭제 감지 — 운영 DB 파괴 위험"
  fi
fi

# SQL 데이터 파괴 명령
if echo "$CMD" | grep -qiE "(DROP TABLE|DROP DATABASE|TRUNCATE)"; then
  WARNINGS="$WARNINGS\n[CRITICAL] SQL 데이터 파괴 명령 감지 (DROP/TRUNCATE)"
fi

# .env 파일 git staging
if echo "$CMD" | grep -qiE "git add" && echo "$CMD" | grep -qE "\.env"; then
  WARNINGS="$WARNINGS\n[CRITICAL] .env 파일 스테이징 감지 — 민감 정보 커밋 위험"
fi

# harness 파일 삭제
if echo "$CMD" | grep -qiE "\.claude/(rules|agents|commands)/.*\.md"; then
  if echo "$CMD" | grep -qiE "^rm "; then
    WARNINGS="$WARNINGS\n[CRITICAL] Harness 파일 삭제 감지 — 사용자 확인 필요"
  fi
fi

# 경고가 있으면 출력 후 차단
if [ -n "$WARNINGS" ]; then
  echo -e "$WARNINGS"
  exit 2
fi
