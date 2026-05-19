@AGENTS.md

# Frontend Overview
Next.js 16 (App Router, Turbopack) 기반 Tech Doc Q&A 프론트엔드.
Spring Boot 백엔드와 BFF 패턴으로 통신하며, JWT 토큰을 httpOnly 쿠키로 관리한다.

# Current Status
- [x] 인증 (로그인 / 회원가입 / 로그아웃 / 토큰 갱신)
- [ ] 질문 입력 & 답변 표시
- [ ] 검색 이력 조회

# Environment
- Next.js: 16.x (App Router, Turbopack)
- React: 19
- TypeScript: 5
- UI: shadcn/ui v4 (@base-ui/react 기반) + Tailwind CSS v4
- 상태관리: Zustand v5
- 서버 상태: TanStack Query v5
- 폼: react-hook-form v7 + zod v4 (`import { z } from 'zod/v4'`)
- 패키지매니저: npm

# Architecture
BFF(Backend for Frontend) 패턴 적용.
브라우저는 Spring 백엔드를 직접 호출하지 않고, Next.js Route Handler를 경유한다.

```
브라우저
  → /api/auth/* (Next.js Route Handler)  ← BFF 레이어
      → http://localhost:8080/api/v1/*   ← Spring 백엔드
```

- 토큰은 httpOnly 쿠키에만 저장 (JS 코드에 절대 노출 금지)
- 인증 상태는 `logged_in` 비보안 쿠키로 클라이언트에 힌트 전달
- `proxy.ts`가 `refresh_token` 쿠키 존재로 라우트 보호

# Directory Structure
```
src/
  app/
    api/auth/          ← BFF Route Handlers (login, signup, logout, refresh)
    (auth)/            ← 인증 페이지 그룹 (로그인, 회원가입) — 카드 레이아웃
    (app)/             ← 보호된 페이지 그룹 (query 등) — 앱 레이아웃
  components/
    ui/                ← shadcn/ui 자동 생성 (수동 수정 금지)
    auth/              ← 인증 폼 컴포넌트
  lib/
    api/backend.ts     ← 서버용 fetch 래퍼, ApiError 클래스 (Route Handler 전용)
    auth/cookies.ts    ← httpOnly 쿠키 set/get/clear (Route Handler 전용)
  providers/           ← TanStack Query Provider
  store/               ← Zustand 스토어
  proxy.ts             ← 라우트 보호 (Next.js 16: middleware → proxy)
```

# Key Files
| 파일 | 역할 |
|------|------|
| `src/proxy.ts` | 라우트 보호. refresh_token 쿠키 기반. **middleware.ts 아님** |
| `src/lib/api/backend.ts` | 서버용 fetch 래퍼. `ApiError` 클래스 포함. 클라이언트에서 import 금지 |
| `src/lib/auth/cookies.ts` | httpOnly 쿠키 set/get/clear. Route Handler에서만 호출 |
| `src/store/auth.ts` | `isAuthenticated` 상태. `logged_in` 쿠키 읽어 초기화 |
| `src/app/api/auth/*/route.ts` | BFF 엔드포인트 4개 (login, signup, logout, refresh) |

# Key Design Decisions
- **httpOnly Cookie**: AT(15분) + RT(7일) httpOnly 쿠키 저장 → JS 접근 불가 → XSS 방어
- **logged_in 쿠키**: 비보안, JS가 읽어서 `isAuthenticated` 초기화에만 사용. 접근 제어 판단 금지
- **Route Handler = BFF**: 모든 백엔드 호출은 `src/app/api/`를 경유. 클라이언트 직접 호출 금지
- **Silent Refresh**: Route Handler에서 401 수신 시 refresh → 원래 요청 재시도 (향후 구현)
- **TanStack Query**: 서버 상태 조회는 `useQuery`, 변경은 `useMutation`

# Next.js 16 Breaking Changes
코드 작성 전 `node_modules/next/dist/docs/` 문서 확인 필수.
- `middleware.ts` → `proxy.ts` 파일명 변경, export 함수명도 `proxy`로 변경
- `cookies()` from `next/headers` 는 async — `await cookies()` 필수
- 작업 대상 기능의 관련 docs 파일을 먼저 읽을 것

# Zod v4 주의사항
- `import { z } from 'zod/v4'` 사용 (classic API)
- `z.string().email()` deprecated → `z.email()` 사용
- `z.string().min(n, 'message')` 는 그대로 사용 가능

# Rules

@../.claude/rules/behavioral-guidelines.md
@../.claude/rules/frontend-component.md
@../.claude/rules/frontend-api.md
@../.claude/rules/frontend-security.md
