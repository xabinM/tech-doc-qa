## 브랜치 체크 및 Pull

현재 브랜치를 확인하고 원격과 동기화한 뒤, 변경된 파일과 적합한지 검토한다.

**절차:**
1. `git branch --show-current` 로 현재 브랜치 확인
2. `git pull` 로 원격 최신 상태 반영 (충돌 발생 시 사용자에게 보고 후 중단)
3. 변경된 파일과 브랜치 적합성 검토

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

**새 브랜치 생성이 필요하면:**
1. 현재 작업 변경사항을 stash 또는 임시 커밋으로 보존
2. `git checkout develop`
3. `git pull origin develop` (원격 최신 상태 반영)
4. `git checkout -b {new-branch}` (develop 기반으로 브랜치 생성)
5. 보존한 변경사항 복원 후 커밋 진행

---

## 커밋 분리

변경된 파일을 git diff로 분석한 뒤, 커밋 단위를 먼저 결정한다.

**하나의 커밋 = 하나의 관심사 (변경 이유가 하나여야 함)**

- 같은 파일이라도 목적이 다르면 커밋을 분리한다
  - 예: `index_docs.py`에 bulk 전환 + 문서 확장 + 임베딩이 섞였다면 3개로 분리
- 의존성 추가(`requirements.txt`)는 해당 기능 커밋에 포함한다 (별도 커밋 금지)
- 인프라 설정 변경(ES 매핑 등)은 그것을 필요로 하는 기능 커밋에 포함한다
- 여러 커밋으로 나눠야 한다면, 커밋 계획을 먼저 사용자에게 제시한 뒤 진행한다

**prefix 선택 기준:**
- `feat`: 새 기능 추가
- `refactor`: 동작 변경 없는 코드 개선 (bulk 전환, 구조 정리 등)
- `fix`: 버그 수정
- `chore`: 설정·의존성 변경, 스크립트 수정
- `test`: 테스트 추가·수정
- `docs`: 문서만 변경

## 커밋 실행

커밋 단위가 결정되면 커밋 메시지 초안을 사용자에게 먼저 보여주고 확인 후 실행한다.

**규칙:**

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
