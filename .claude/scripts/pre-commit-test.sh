#!/usr/bin/env bash
# 커밋 전 자동 테스트 실행 스크립트
# staged 파일 기준으로 서비스별 테스트 실행

STAGED=$(git diff --cached --name-only 2>/dev/null)
FAILED=0

if echo "$STAGED" | grep -q "^rag-server/"; then
  echo "[pre-commit] rag-server 파일 감지 → pytest 실행"
  wsl -e bash -c "cd /mnt/c/root_project/rag-server && /home/vit/ragtest-venv/bin/pytest tests/ -q" || FAILED=1
fi

if echo "$STAGED" | grep -q "^backend/"; then
  echo "[pre-commit] backend 파일 감지 → gradle test 실행"
  cd "$(git rev-parse --show-toplevel)/backend" && ./gradlew test -q || FAILED=1
fi

if echo "$STAGED" | grep -q "^frontend/"; then
  echo "[pre-commit] frontend 파일 감지 → TypeScript 타입 체크"
  FRONTEND_DIR="$(git rev-parse --show-toplevel)/frontend"
  # Node.js가 PATH에 없을 경우 Windows 기본 경로 보완
  export PATH="$PATH:/c/Program Files/nodejs"
  if command -v npx &>/dev/null; then
    cd "$FRONTEND_DIR" && npx tsc --noEmit || FAILED=1
  else
    echo "[pre-commit] npx를 찾을 수 없습니다 — TypeScript 체크 건너뜀 (Node.js 설치 확인)"
  fi
fi

if [ $FAILED -ne 0 ]; then
  printf '{"continue": false, "stopReason": "검사 실패 — 커밋이 중단됐습니다. 위 오류를 확인하세요."}'
  exit 1
fi
