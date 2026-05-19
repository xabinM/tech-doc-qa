import { HydrateAuth } from '@/components/auth/HydrateAuth';
import { AppHeader } from '@/components/layout/AppHeader';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen flex-col">
      <HydrateAuth />
      <AppHeader />
      <div className="flex-1 overflow-hidden min-h-0">
        {children}
      </div>
    </div>
  );
}
