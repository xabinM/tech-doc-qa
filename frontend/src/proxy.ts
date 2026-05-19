import { NextRequest, NextResponse } from 'next/server';

const PROTECTED = ['/query'];
const AUTH_ONLY = ['/login', '/signup'];

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const hasRefreshToken = request.cookies.has('refresh_token');

  if (PROTECTED.some((p) => pathname.startsWith(p)) && !hasRefreshToken) {
    return NextResponse.redirect(new URL('/login', request.url));
  }

  if (AUTH_ONLY.some((p) => pathname.startsWith(p)) && hasRefreshToken) {
    return NextResponse.redirect(new URL('/query', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
};
