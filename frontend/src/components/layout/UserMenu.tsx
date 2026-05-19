'use client';

import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { LogOut, UserRound } from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { useAuthStore } from '@/store/auth';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

async function requestLogout() {
  const res = await fetch('/api/auth/logout', { method: 'POST' });
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.message ?? '로그아웃에 실패했습니다');
}

export function UserMenu() {
  const router = useRouter();
  const hydrate = useAuthStore((s) => s.hydrate);

  const mutation = useMutation({
    mutationFn: requestLogout,
    onSuccess: () => {
      hydrate();
      router.push('/login');
    },
    onError: (e) => toast.error(e.message),
  });

  return (
    <div className="flex items-center gap-0.5">
      <Link
        href="/my"
        className={cn(buttonVariants({ variant: 'ghost', size: 'icon' }))}
        aria-label="내 정보"
      >
        <UserRound className="size-4" />
      </Link>
      <Button
        variant="ghost"
        size="icon"
        onClick={() => mutation.mutate()}
        disabled={mutation.isPending}
        aria-label="로그아웃"
      >
        <LogOut className="size-4" />
      </Button>
    </div>
  );
}
