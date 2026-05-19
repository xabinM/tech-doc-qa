import { NextResponse } from 'next/server';
import { backendFetch, ApiError, type TokenData } from '@/lib/api/backend';
import { setAuthCookies, clearAuthCookies, getRefreshToken } from '@/lib/auth/cookies';

export async function POST() {
  const refreshToken = await getRefreshToken();

  if (!refreshToken) {
    return NextResponse.json(
      { success: false, error: { code: 'AUTH_TOKEN_MISSING', message: '인증이 필요합니다' } },
      { status: 401 }
    );
  }

  try {
    const data = await backendFetch<TokenData>('/api/v1/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    });
    await setAuthCookies(data.accessToken, data.refreshToken);
    return NextResponse.json({ success: true });
  } catch (e) {
    await clearAuthCookies();
    if (e instanceof ApiError) {
      return NextResponse.json(
        { success: false, error: { code: e.code, message: e.message } },
        { status: e.status }
      );
    }
    return NextResponse.json(
      { success: false, error: { code: 'NETWORK_ERROR', message: '서버에 연결할 수 없습니다' } },
      { status: 503 }
    );
  }
}
