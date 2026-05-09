## 브랜치 체크

현재 브랜치를 확인하고 변경된 파일과 적합한지 검토한다.

**브랜치 네이밍 규칙:** `{type}/{service}/{feature}`
- type: feat / fix / refactor / chore
- service: backend / rag / infra
- 예시: feat/backend-query, fix/backend-jwt, chore/ci-setup

**브랜치 추론 기준 (변경된 파일 경로 기준):**
- `backend/` 하위 auth 관련 → `feat/backend-auth` 계열
- `backend/` 하위 query 관련 → `feat/backend-query` 계열
- `rag-server/` 하위 → `feat/rag-{feature}` 계열
- `elasticsearch/` 또는 인프라 설정 → `chore/infra-{feature}` 계열
- 여러 서비스에 걸친 변경 → 경고 후 사용자에게 확인

**보호 브랜치 처리:**
- 현재 브랜치가 `main` 또는 `develop`이면 커밋을 중단하고 새 브랜치 생성을 제안한다

**브랜치가 변경 내용과 맞지 않으면:**
- 적합한 브랜치 이름을 제안하고 사용자에게 전환 여부를 확인한 뒤 진행한다

---

## 커밋 분리

변경된 파일을 서비스/관심사별로 그룹화하고, 그룹마다 별도 커밋으로 나눠 순서대로 실행한다.

**그룹화 기준 (브랜치 추론 기준과 동일):**
- `backend/` auth 관련 파일 → 1개 커밋
- `backend/` query 관련 파일 → 1개 커밋
- `rag-server/` 파일 → 1개 커밋
- `elasticsearch/`, `docker-compose.yml`, `.env.example` 등 인프라 파일 → 1개 커밋
- `.claude/` 파일 → 1개 커밋
- 위 기준으로 묶이지 않는 파일은 가장 유사한 그룹에 포함하거나 별도 그룹으로 분리

**절차:**
1. 전체 변경 파일을 분석해 그룹과 커밋 순서를 사용자에게 먼저 제시한다
2. 사용자 확인 후 그룹 순서대로 커밋을 실행한다
3. 단일 서비스만 변경된 경우에는 분리 없이 바로 커밋한다

---

## 커밋 메시지 규칙

- prefix는 feat/fix/refactor/test/chore/docs 중 적절한 것을 선택한다
- 제목은 50자 이내로 간결하게 작성한다
- 변경 목적(why)을 중심으로 작성한다
- 커밋 메시지에 Co-Authored-By 라인을 절대 포함하지 않는다
