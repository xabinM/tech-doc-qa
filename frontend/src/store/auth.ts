import { create } from 'zustand';

interface AuthState {
  isAuthenticated: boolean;
  hydrate: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  isAuthenticated: false,
  hydrate: () => {
    if (typeof document === 'undefined') return;
    const loggedIn = document.cookie
      .split(';')
      .some((c) => c.trim().startsWith('logged_in=1'));
    set({ isAuthenticated: loggedIn });
  },
}));
