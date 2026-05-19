# 프론트엔드 API 호출 규칙

## BFF 원칙 (필수)
- 브라우저에서 Spring 백엔드(`localhost:8080`) 직접 호출 금지
- 모든 백엔드 호출은 `src/app/api/` 하위의 Route Handler를 경유
- `src/lib/api/backend.ts`의 `backendFetch` 는 Route Handler(서버)에서만 사용 — 클라이언트 컴포넌트에서 import 금지

## Route Handler 작성
- 새 백엔드 API가 필요할 경우 `src/app/api/{domain}/route.ts` 생성
- 백엔드 에러(`ApiError`)는 Route Handler에서 잡아 클라이언트 친화적 응답으로 변환
- 인증이 필요한 Route Handler: `getAccessToken()` 으로 AT 읽어 `Authorization` 헤더 주입
- 401 수신 시: `getRefreshToken()` → `/api/v1/auth/refresh` 재발급 → `setAuthCookies()` → 원래 요청 재시도

## 클라이언트 데이터 페칭
- 서버 상태 조회: `useQuery` (TanStack Query v5)
- 서버 상태 변경: `useMutation` (TanStack Query v5)
- 로딩/에러 상태는 TanStack Query가 제공하는 `isLoading`, `isError` 사용 — 별도 state 중복 관리 금지
- `queryKey` 는 문자열 배열로 명시적으로 선언 (`['history', cursor]` 형태)

## 에러 처리
- `useMutation.onError`: `toast.error(error.message)` 로 사용자에게 표시
- Route Handler에서 throw된 에러는 `{ success: false, error: { code, message } }` 구조로 통일
- 네트워크 에러(fetch 실패): `{ code: 'NETWORK_ERROR', message: '서버에 연결할 수 없습니다' }` 반환
