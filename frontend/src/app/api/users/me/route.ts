import { NextResponse } from 'next/server';
import { backendFetch, ApiError } from '@/lib/api/backend';
import { withAuth } from '@/lib/api/withAuth';
import { clearAuthCookies } from '@/lib/auth/cookies';

function errorResponse(e: unknown) {
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

export async function GET() {
  try {
    const data = await withAuth((token) =>
      backendFetch('/api/v1/users/me', { token })
    );
    return NextResponse.json({ success: true, data });
  } catch (e) {
    return errorResponse(e);
  }
}

export async function PUT(request: Request) {
  const body = await request.json();
  try {
    await withAuth((token) =>
      backendFetch('/api/v1/users/me/password', { method: 'PUT', body: JSON.stringify(body), token })
    );
    return NextResponse.json({ success: true });
  } catch (e) {
    return errorResponse(e);
  }
}

export async function DELETE(request: Request) {
  const body = await request.json();
  try {
    await withAuth((token) =>
      backendFetch('/api/v1/users/me', { method: 'DELETE', body: JSON.stringify(body), token })
    );
    await clearAuthCookies();
    return NextResponse.json({ success: true });
  } catch (e) {
    return errorResponse(e);
  }
}
