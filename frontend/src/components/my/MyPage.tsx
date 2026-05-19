'use client';

import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

type Profile = { email: string; createdAt: string };

async function fetchProfile(): Promise<Profile> {
  const res = await fetch('/api/users/me');
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.message ?? '프로필 조회 실패');
  return json.data;
}

export function MyPage() {
  const { data: profile, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: fetchProfile,
  });

  return (
    <div className="max-w-xl mx-auto px-4 py-12">
      <h1 className="text-2xl font-semibold mb-8">내 정보</h1>
      <Card>
        <CardHeader>
          <CardTitle className="text-base">계정 정보</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {isLoading ? (
            <p className="text-sm text-muted-foreground">불러오는 중...</p>
          ) : (
            <>
              <div className="flex flex-col gap-1">
                <p className="text-xs text-muted-foreground">이메일</p>
                <p className="text-sm font-medium">{profile?.email}</p>
              </div>
              <div className="flex flex-col gap-1">
                <p className="text-xs text-muted-foreground">가입일</p>
                <p className="text-sm font-medium">
                  {profile?.createdAt
                    ? new Date(profile.createdAt).toLocaleDateString('ko-KR', {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                      })
                    : '-'}
                </p>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
