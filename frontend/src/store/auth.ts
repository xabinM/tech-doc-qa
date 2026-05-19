import { create } from 'zustand';

interface AuthState {
  isLoggedInHint: boolean; // UI 힌트 전용 — 접근 제어 판단에 사용 금지
  hydrate: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  isLoggedInHint: false,
  hydrate: () => {
    if (typeof document === 'undefined') return;
    const loggedIn = document.cookie
      .split(';')
      .some((c) => c.trim().startsWith('logged_in=1'));
    set({ isLoggedInHint: loggedIn });
  },
}));
