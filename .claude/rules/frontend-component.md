# 프론트엔드 컴포넌트 규칙

## 서버/클라이언트 경계
- 페이지 컴포넌트는 서버 컴포넌트 기본 — `'use client'`는 상태(useState), 이벤트 핸들러, 브라우저 API가 필요한 컴포넌트에만 추가
- `'use client'` 범위를 최소화한다 — 페이지 전체에 달지 말고 상태가 필요한 하위 컴포넌트에만 적용
- `cookies()`, `headers()` from `next/headers` 는 서버 컴포넌트 / Route Handler에서만 사용

## shadcn/ui v4
- `src/components/ui/` 파일은 shadcn CLI 자동 생성 파일 — 직접 수정 금지
- 커스터마이징이 필요하면 `src/components/` 하위에 래퍼 컴포넌트 작성
- 새 컴포넌트 추가: `npx shadcn@latest add {component}` 명령 사용
- shadcn v4는 `@base-ui/react` 기반 — Radix UI (`@radix-ui/*`) 직접 import 금지
- `Form` 복합 컴포넌트(`FormField`, `FormControl` 등)는 미생성 — react-hook-form을 직접 연결한다

## 폼 패턴
- 폼 유효성 검증: react-hook-form + zod 조합 필수
- Server Action 방식(`action={...}`) 사용 금지 — `onSubmit + useMutation` 패턴으로 통일
- 에러 메시지: `<p className="text-sm text-destructive">{error.message}</p>` 패턴으로 통일
- `aria-invalid={!!errors.fieldName}` 을 Input에 직접 전달하여 에러 스타일 활성화

## 코드 스타일
- 컴포넌트 파일명: PascalCase (`LoginForm.tsx`)
- 페이지 파일명: 소문자 (`page.tsx`, Next.js 컨벤션)
- 타입은 `interface` 보다 `type` 선호 (Props, API 응답 등)
- `React.FC<Props>` 대신 함수 시그니처에 직접 타입 명시
