import { cookies } from 'next/headers';

const ACCESS = 'access_token';
const REFRESH = 'refresh_token';
const LOGGED_IN = 'logged_in';

const secure = process.env.NODE_ENV === 'production';

export async function setAuthCookies(accessToken: string, refreshToken: string) {
  const jar = await cookies();
  jar.set(ACCESS, accessToken, {
    httpOnly: true,
    secure,
    sameSite: 'strict',
    path: '/',
    maxAge: 60 * 15, // 15분
  });
  jar.set(REFRESH, refreshToken, {
    httpOnly: true,
    secure,
    sameSite: 'strict',
    path: '/',
    maxAge: 60 * 60 * 24 * 7, // 7일
  });
  // JS에서 읽을 수 있는 인증 상태 힌트 (보안 값 아님)
  jar.set(LOGGED_IN, '1', {
    httpOnly: false,
    secure,
    sameSite: 'strict',
    path: '/',
    maxAge: 60 * 60 * 24 * 7,
  });
}

export async function clearAuthCookies() {
  const jar = await cookies();
  jar.delete(ACCESS);
  jar.delete(REFRESH);
  jar.delete(LOGGED_IN);
}

export async function getAccessToken(): Promise<string | undefined> {
  return (await cookies()).get(ACCESS)?.value;
}

export async function getRefreshToken(): Promise<string | undefined> {
  return (await cookies()).get(REFRESH)?.value;
}
