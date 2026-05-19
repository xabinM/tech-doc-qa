'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod/v4';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';

const schema = z.object({
  question: z.string().min(1, '질문을 입력해주세요'),
});

type FormValues = z.infer<typeof schema>;
type QueryResult = { answer: string };

async function submitQuery(question: string): Promise<QueryResult> {
  const res = await fetch('/api/query', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  });
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.message ?? '오류가 발생했습니다');
  return json.data;
}

export function QueryForm() {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: (values: FormValues) => submitQuery(values.question),
    onError: (e) => toast.error(e.message),
  });

  return (
    <div className="flex flex-col gap-6">
      <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="flex flex-col gap-3">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="question">질문</Label>
          <textarea
            id="question"
            rows={4}
            placeholder="Spring이나 Java에 대해 질문하세요"
            aria-invalid={!!errors.question}
            className="border-input bg-background placeholder:text-muted-foreground focus-visible:ring-ring flex min-h-[80px] w-full rounded-md border px-3 py-2 text-sm shadow-sm focus-visible:ring-1 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50 aria-[invalid=true]:border-destructive"
            {...register('question')}
          />
          {errors.question && (
            <p className="text-sm text-destructive">{errors.question.message}</p>
          )}
        </div>
        <Button type="submit" disabled={mutation.isPending} className="self-end">
          {mutation.isPending ? '답변 생성 중...' : '질문하기'}
        </Button>
      </form>

      {mutation.data && (
        <div className="bg-muted rounded-lg p-4">
          <p className="text-muted-foreground mb-2 text-sm font-medium">답변</p>
          <p className="text-sm whitespace-pre-wrap">{mutation.data.answer}</p>
        </div>
      )}

      {mutation.isPending && (
        <div className="bg-muted animate-pulse rounded-lg p-4">
          <p className="text-muted-foreground text-sm">답변을 생성하고 있습니다...</p>
        </div>
      )}
    </div>
  );
}
