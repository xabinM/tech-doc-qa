import { NextResponse } from 'next/server';
import { backendFetch, ApiError } from '@/lib/api/backend';
import { clearAuthCookies, getAccessToken } from '@/lib/auth/cookies';

export async function POST() {
  const token = await getAccessToken();

  try {
    await backendFetch('/api/v1/auth/logout', { method: 'POST', token });
  } catch (e) {
    if (!(e instanceof ApiError)) {
      console.error('로그아웃 백엔드 호출 실패:', e instanceof Error ? e.message : e);
    }
    // 백엔드 실패와 무관하게 쿠키는 항상 삭제
  } finally {
    await clearAuthCookies();
  }

  return NextResponse.json({ success: true });
}
