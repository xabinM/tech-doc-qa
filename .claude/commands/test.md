$ARGUMENTS 에 해당하는 테스트를 실행하고 결과를 요약한다.

**인자가 없으면:** `backend/` 전체 테스트 실행
**인자가 있으면:** 서비스 또는 클래스 지정 (예: `rag`, `AuthService`, `query`)

---

## backend 테스트

**실행 방법:**
- 전체: `cd backend && ./gradlew test`
- 특정 클래스: `cd backend && ./gradlew test --tests "*{인자}*"`

---

## rag-server 테스트

pytest가 WSL 환경에서 실행된다. Python 3.14 + pytest 9.0.3 가상환경 경로:
`/home/vit/ragtest-venv/bin/pytest`

**실행 방법:**
- 전체: `wsl -e bash -c "cd /mnt/c/root_project/rag-server && /home/vit/ragtest-venv/bin/pytest tests/ -v"`
- 특정 파일: `wsl -e bash -c "cd /mnt/c/root_project/rag-server && /home/vit/ragtest-venv/bin/pytest tests/test_{인자}*.py -v"`

---

## 결과 요약 형식

- 통과 / 실패 / 스킵 수
- 실패한 테스트가 있으면 실패 원인과 해당 파일 경로 출력
- backend 실패 원인이 헥사고날 아키텍처 규칙 위반이면 명시적으로 지적
