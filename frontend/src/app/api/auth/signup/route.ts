import { NextResponse } from 'next/server';
import { backendFetch, ApiError } from '@/lib/api/backend';

export async function POST(request: Request) {
  const body = await request.json();

  try {
    await backendFetch('/api/v1/auth/signup', {
      method: 'POST',
      body: JSON.stringify(body),
    });
    return NextResponse.json({ success: true });
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
