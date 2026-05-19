import { NextResponse } from 'next/server';
import { backendFetch, ApiError } from '@/lib/api/backend';
import { withAuth } from '@/lib/api/withAuth';

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const params = new URLSearchParams();
  const cursorId = searchParams.get('cursorId');
  if (cursorId) params.set('cursorId', cursorId);
  params.set('size', searchParams.get('size') ?? '20');

  try {
    const data = await withAuth((token) =>
      backendFetch(`/api/v1/sessions?${params}`, { token })
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
