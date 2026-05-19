import { NextResponse } from 'next/server';
import { backendFetch, ApiError } from '@/lib/api/backend';
import { withAuth } from '@/lib/api/withAuth';

export async function GET() {
  try {
    const data = await withAuth((token) =>
      backendFetch('/api/v1/users/me', { token })
    );
    return NextResponse.json({ success: true, data });
  } catch (e) {
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
