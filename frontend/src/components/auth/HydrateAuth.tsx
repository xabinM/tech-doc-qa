'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/auth';

function isLoggedIn() {
  return document.cookie.split(';').some((c) => c.trim().startsWith('logged_in=1'));
}

export function HydrateAuth() {
  const hydrate = useAuthStore((s) => s.hydrate);
  const router = useRouter();

  // 마운트(=클라이언트 사이드 back 네비게이션 포함) 시 인증 확인
  useEffect(() => {
    if (!isLoggedIn()) {
      router.replace('/login');
      return;
    }
    hydrate();
  }, [hydrate, router]);

  // bfcache 복원 시 인증 확인 (pageshow + persisted)
  useEffect(() => {
    const handlePageShow = (e: PageTransitionEvent) => {
      if (e.persisted && !isLoggedIn()) window.location.replace('/login');
    };
    window.addEventListener('pageshow', handlePageShow);
    return () => window.removeEventListener('pageshow', handlePageShow);
  }, []);

  return null;
}
