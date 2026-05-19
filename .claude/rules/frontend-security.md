# 프론트엔드 보안 규칙

## 토큰 저장
- `access_token`, `refresh_token` 을 `localStorage`, `sessionStorage`, JS 변수, Zustand store에 저장 금지
- 토큰은 httpOnly 쿠키에만 저장 — `src/lib/auth/cookies.ts`의 `setAuthCookies()` 를 통해서만 설정
- `src/lib/auth/cookies.ts` 는 Route Handler(서버)에서만 import — 클라이언트 컴포넌트에서 import 금지

## 환경변수
- `BACKEND_URL` 은 서버 전용 — `NEXT_PUBLIC_BACKEND_URL` 로 이름 변경 금지
- 브라우저에 노출되어도 무방한 값만 `NEXT_PUBLIC_` prefix 사용
- 민감 정보(API 키, DB 패스워드 등) `.env.local` 에만 저장, `.env` 커밋 금지

## 인증/인가
- `logged_in` 쿠키는 UI 힌트 전용 — 실제 접근 제어 판단에 사용 금지
- 라우트 보호는 `proxy.ts` 의 `refresh_token` 쿠키 확인이 기준
- Route Handler에서 인증이 필요한 작업 시 `getAccessToken()` 으로 AT 검증 — 클라이언트가 보낸 헤더 신뢰 금지

## 에러 응답
- Route Handler 에러 응답에 스택트레이스, 내부 파일 경로 포함 금지
- `ApiError` 외 예상치 못한 에러는 `{ code: 'NETWORK_ERROR', message: '서버에 연결할 수 없습니다' }` 로 일반화
- 콘솔 로그에 토큰 값, 사용자 비밀번호 출력 금지
