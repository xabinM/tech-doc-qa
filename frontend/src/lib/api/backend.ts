const BACKEND_URL = process.env.BACKEND_URL;
if (!BACKEND_URL) throw new Error('BACKEND_URL 환경변수가 설정되지 않았습니다');

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function backendFetch<T = void>(
  path: string,
  init?: RequestInit & { token?: string }
): Promise<T> {
  const { token, headers: extraHeaders, ...rest } = init ?? {};

  const res = await fetch(`${BACKEND_URL}${path}`, {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(extraHeaders as Record<string, string> ?? {}),
    },
    cache: 'no-store',
  });

  const body = await res.json();

  if (!body.success) {
    throw new ApiError(
      body.error?.code ?? 'UNKNOWN',
      body.error?.message ?? '알 수 없는 오류가 발생했습니다',
      res.status
    );
  }

  return (body.data ?? undefined) as T;
}

export interface TokenData {
  accessToken: string;
  refreshToken: string;
}
