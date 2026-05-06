#!/bin/bash
# PostToolUse 훅 — 툴 실행 결과에서 빌드/테스트 실패 감지

OUTPUT="$CLAUDE_TOOL_OUTPUT"

if echo "$OUTPUT" | grep -qiE "(BUILD FAILED|FAILURE:|Exception in)"; then
  echo "[hook] 빌드/테스트 실패 감지됨. 원인을 분석하세요."
fi
