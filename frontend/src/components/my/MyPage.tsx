'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod/v4';
import { useQuery, useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { useAuthStore } from '@/store/auth';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

type Profile = { email: string; createdAt: string };

async function fetchProfile(): Promise<Profile> {
  const res = await fetch('/api/users/me');
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.message ?? '프로필 조회 실패');
  return json.data;
}

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, '현재 비밀번호를 입력해주세요'),
    newPassword: z.string().min(8, '비밀번호는 8자 이상이어야 합니다'),
    confirmPassword: z.string().min(1, '비밀번호 확인을 입력해주세요'),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    message: '새 비밀번호가 일치하지 않습니다',
    path: ['confirmPassword'],
  });

const deleteSchema = z.object({
  password: z.string().min(1, '비밀번호를 입력해주세요'),
});

type PasswordForm = z.infer<typeof passwordSchema>;
type DeleteForm = z.infer<typeof deleteSchema>;

export function MyPage() {
  const router = useRouter();
  const hydrate = useAuthStore((s) => s.hydrate);
  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [showDeleteForm, setShowDeleteForm] = useState(false);

  const { data: profile, isLoading, isError } = useQuery({
    queryKey: ['profile'],
    queryFn: fetchProfile,
  });

  const pwForm = useForm<PasswordForm>({ resolver: zodResolver(passwordSchema) });
  const delForm = useForm<DeleteForm>({ resolver: zodResolver(deleteSchema) });

  const changePwMutation = useMutation({
    mutationFn: async (data: PasswordForm) => {
      const res = await fetch('/api/users/me', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentPassword: data.currentPassword, newPassword: data.newPassword }),
      });
      const json = await res.json();
      if (!json.success) throw new Error(json.error?.message ?? '비밀번호 변경 실패');
    },
    onSuccess: () => {
      toast.success('비밀번호가 변경되었습니다');
      pwForm.reset();
      setShowPasswordForm(false);
    },
    onError: (e) => toast.error(e.message),
  });

  const deleteAccountMutation = useMutation({
    mutationFn: async (data: DeleteForm) => {
      const res = await fetch('/api/users/me', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password: data.password }),
      });
      const json = await res.json();
      if (!json.success) throw new Error(json.error?.message ?? '회원 탈퇴 실패');
    },
    onSuccess: () => {
      hydrate();
      router.push('/login');
    },
    onError: (e) => toast.error(e.message),
  });

  return (
    <div className="max-w-xl mx-auto px-4 py-12 space-y-6">
      <h1 className="text-2xl font-semibold">내 정보</h1>

      {/* 계정 정보 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">계정 정보</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {isLoading && <p className="text-sm text-muted-foreground">불러오는 중...</p>}
          {isError && <p className="text-sm text-destructive">정보를 불러오지 못했습니다</p>}
          {profile && (
            <>
              <div className="flex flex-col gap-1">
                <p className="text-xs text-muted-foreground">이메일</p>
                <p className="text-sm font-medium">{profile.email}</p>
              </div>
              <div className="flex flex-col gap-1">
                <p className="text-xs text-muted-foreground">가입일</p>
                <p className="text-sm font-medium">
                  {new Date(profile.createdAt).toLocaleDateString('ko-KR', {
                    year: 'numeric', month: 'long', day: 'numeric',
                  })}
                </p>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      {/* 비밀번호 변경 */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-base">비밀번호 변경</CardTitle>
          <Button variant="ghost" size="sm" onClick={() => setShowPasswordForm((v) => !v)}>
            {showPasswordForm ? '취소' : '변경'}
          </Button>
        </CardHeader>
        {showPasswordForm && (
          <CardContent>
            <form
              onSubmit={pwForm.handleSubmit((v) => changePwMutation.mutate(v))}
              className="space-y-3"
            >
              <div className="space-y-1.5">
                <Label htmlFor="currentPassword">현재 비밀번호</Label>
                <Input
                  id="currentPassword"
                  type="password"
                  aria-invalid={!!pwForm.formState.errors.currentPassword}
                  {...pwForm.register('currentPassword')}
                />
                {pwForm.formState.errors.currentPassword && (
                  <p className="text-sm text-destructive">{pwForm.formState.errors.currentPassword.message}</p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="newPassword">새 비밀번호</Label>
                <Input
                  id="newPassword"
                  type="password"
                  aria-invalid={!!pwForm.formState.errors.newPassword}
                  {...pwForm.register('newPassword')}
                />
                {pwForm.formState.errors.newPassword && (
                  <p className="text-sm text-destructive">{pwForm.formState.errors.newPassword.message}</p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="confirmPassword">새 비밀번호 확인</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  aria-invalid={!!pwForm.formState.errors.confirmPassword}
                  {...pwForm.register('confirmPassword')}
                />
                {pwForm.formState.errors.confirmPassword && (
                  <p className="text-sm text-destructive">{pwForm.formState.errors.confirmPassword.message}</p>
                )}
              </div>
              <Button type="submit" disabled={changePwMutation.isPending} className="w-full">
                {changePwMutation.isPending ? '변경 중...' : '비밀번호 변경'}
              </Button>
            </form>
          </CardContent>
        )}
      </Card>

      {/* 위험 구역 */}
      <Card className="border-destructive/30">
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <div>
            <CardTitle className="text-base text-destructive">위험 구역</CardTitle>
            <p className="text-xs text-muted-foreground mt-1">계정 삭제 시 모든 데이터가 영구적으로 삭제됩니다</p>
          </div>
          <Button
            variant="destructive"
            size="sm"
            onClick={() => setShowDeleteForm((v) => !v)}
          >
            {showDeleteForm ? '취소' : '회원 탈퇴'}
          </Button>
        </CardHeader>
        {showDeleteForm && (
          <CardContent>
            <form
              onSubmit={delForm.handleSubmit((v) => deleteAccountMutation.mutate(v))}
              className="space-y-3"
            >
              <div className="space-y-1.5">
                <Label htmlFor="deletePassword">비밀번호 확인</Label>
                <Input
                  id="deletePassword"
                  type="password"
                  placeholder="계정 삭제를 위해 비밀번호를 입력하세요"
                  aria-invalid={!!delForm.formState.errors.password}
                  {...delForm.register('password')}
                />
                {delForm.formState.errors.password && (
                  <p className="text-sm text-destructive">{delForm.formState.errors.password.message}</p>
                )}
              </div>
              <Button
                type="submit"
                variant="destructive"
                disabled={deleteAccountMutation.isPending}
                className="w-full"
              >
                {deleteAccountMutation.isPending ? '처리 중...' : '계정 영구 삭제'}
              </Button>
            </form>
          </CardContent>
        )}
      </Card>
    </div>
  );
}
