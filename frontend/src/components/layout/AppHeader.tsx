import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { UserMenu } from '@/components/layout/UserMenu';

export function AppHeader() {
  return (
    <header className="shrink-0 border-b px-5 h-12 flex items-center justify-between">
      <span className="text-sm font-semibold">기술 문서 Q&amp;A</span>
      <div className="flex items-center gap-1">
        <ThemeToggle />
        <UserMenu />
      </div>
    </header>
  );
}
