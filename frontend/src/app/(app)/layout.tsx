import { HydrateAuth } from '@/components/auth/HydrateAuth';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <HydrateAuth />
      {children}
    </>
  );
}
