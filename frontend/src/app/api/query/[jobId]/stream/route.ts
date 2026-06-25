import {
  getAccessToken,
  getRefreshToken,
  setAuthCookies,
  clearAuthCookies,
} from '@/lib/auth/cookies';
import { backendFetch, type TokenData } from '@/lib/api/backend';

const BACKEND_URL = process.env.BACKEND_URL;
if (!BACKEND_URL) throw new Error('BACKEND_URL 환경변수가 설정되지 않았습니다');

export const dynamic = 'force-dynamic';

/**
 * 답변 스트림 SSE 프록시.
 *
 * 브라우저 EventSource → (이 Route Handler) → 백엔드 GET /api/v1/query/{jobId}/stream
 * - 쿠키의 AT를 Authorization 헤더로 주입 (EventSource는 커스텀 헤더 불가하므로 BFF가 대신)
 * - 401이면 refresh 후 1회 재시도
 * - 재연결 시 EventSource가 보낸 Last-Event-ID를 그대로 전달 → 백엔드에서 재생(resume)
 * - 백엔드 SSE 본문 스트림을 그대로 패스스루
 */
export async function GET(
  request: Request,
  { params }: { params: Promise<{ jobId: string }> }
) {
  const { jobId } = await params;
  const lastEventId = request.headers.get('Last-Event-ID') ?? undefined;

  const token = await getAccessToken();
  let backendRes = await fetchStream(jobId, token, lastEventId);

  if (backendRes.status === 401) {
    const refreshed = await refreshAccessToken();
    if (!refreshed) {
      return sseError('인증이 필요합니다');
    }
    backendRes = await fetchStream(jobId, refreshed, lastEventId);
  }

  if (!backendRes.ok || !backendRes.body) {
    return sseError('스트림 연결에 실패했습니다');
  }

  return new Response(backendRes.body, {
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
    },
  });
}

function fetchStream(jobId: string, token: string | undefined, lastEventId?: string) {
  return fetch(`${BACKEND_URL}/api/v1/query/${encodeURIComponent(jobId)}/stream`, {
    headers: {
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(lastEventId ? { 'Last-Event-ID': lastEventId } : {}),
    },
    cache: 'no-store',
  });
}

async function refreshAccessToken(): Promise<string | undefined> {
  const refreshToken = await getRefreshToken();
  if (!refreshToken) return undefined;
  try {
    const tokens = await backendFetch<TokenData>('/api/v1/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    });
    await setAuthCookies(tokens.accessToken, tokens.refreshToken);
    return tokens.accessToken;
  } catch {
    await clearAuthCookies();
    return undefined;
  }
}

/** 오류를 SSE error 이벤트로 내려 클라이언트가 일관되게 처리하도록 한다. */
function sseError(message: string) {
  const body = `event: error\ndata: ${message}\n\n`;
  return new Response(body, {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache',
    },
  });
}
