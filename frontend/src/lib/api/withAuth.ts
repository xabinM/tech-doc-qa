import { backendFetch, ApiError, type TokenData } from './backend';
import {
  getAccessToken,
  getRefreshToken,
  setAuthCookies,
  clearAuthCookies,
} from '@/lib/auth/cookies';

export async function withAuth<T>(call: (token: string | undefined) => Promise<T>): Promise<T> {
  const token = await getAccessToken();

  try {
    return await call(token);
  } catch (e) {
    if (!(e instanceof ApiError) || e.status !== 401) throw e;
  }

  // AT 만료 — 토큰 갱신 후 재시도
  const refreshToken = await getRefreshToken();
  if (!refreshToken) {
    throw new ApiError('AUTH_TOKEN_MISSING', '인증이 필요합니다', 401);
  }

  let newTokens: TokenData;
  try {
    newTokens = await backendFetch<TokenData>('/api/v1/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    });
  } catch (e) {
    await clearAuthCookies();
    throw e;
  }

  await setAuthCookies(newTokens.accessToken, newTokens.refreshToken);
  return call(newTokens.accessToken);
}
