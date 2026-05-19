'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod/v4';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { MarkdownRenderer } from '@/components/query/MarkdownRenderer';

type Turn = { question: string; answer: string };
type Session = { id: number; title: string; createdAt: string };
type SessionsData = { items: Session[]; nextCursorId: number | null; hasNext: boolean };
type Message = { id: number; question: string; answer: string; createdAt: string };

const schema = z.object({ question: z.string().min(1, '질문을 입력해주세요') });
type FormValues = z.infer<typeof schema>;

async function postQuery(question: string, sessionId: number | null) {
  const res = await fetch('/api/query', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, sessionId }),
  });
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.message ?? '오류가 발생했습니다');
  return json.data as { answer: string; sessionId: number };
}

async function fetchSessions(): Promise<SessionsData> {
  const res = await fetch('/api/sessions');
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.message ?? '세션 목록 조회 실패');
  return json.data;
}

async function fetchSessionMessages(sessionId: number): Promise<Message[]> {
  const res = await fetch(`/api/sessions/${sessionId}/messages`);
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.message ?? '대화 내역 조회 실패');
  return json.data.messages;
}

async function deleteSession(sessionId: number) {
  const res = await fetch(`/api/sessions/${sessionId}`, { method: 'DELETE' });
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.message ?? '세션 삭제 실패');
}

export function ChatPage() {
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [pendingMessages, setPendingMessages] = useState<Turn[]>([]);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  const { data: sessionsData } = useQuery({
    queryKey: ['sessions'],
    queryFn: fetchSessions,
  });

  const { data: loadedMessages } = useQuery({
    queryKey: ['session-messages', currentSessionId],
    queryFn: () => fetchSessionMessages(currentSessionId!),
    enabled: currentSessionId !== null,
  });

  const displayMessages: Turn[] = [
    ...(loadedMessages?.map((m) => ({ question: m.question, answer: m.answer })) ?? []),
    ...pendingMessages,
  ];

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const queryMutation = useMutation({
    mutationFn: (values: FormValues) => postQuery(values.question, currentSessionId),
    onSuccess: (data, variables) => {
      if (currentSessionId === null) {
        setCurrentSessionId(data.sessionId);
        queryClient.invalidateQueries({ queryKey: ['sessions'] });
      }
      setPendingMessages((prev) => [...prev, { question: variables.question, answer: data.answer }]);
      reset();
    },
    onError: (e) => toast.error(e.message),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteSession,
    onSuccess: (_, sessionId) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      if (currentSessionId === sessionId) {
        setCurrentSessionId(null);
        setPendingMessages([]);
      }
      setConfirmDeleteId(null);
      toast.success('대화가 삭제되었습니다');
    },
    onError: (e) => toast.error(e.message),
  });

  const selectSession = useCallback((sessionId: number) => {
    setCurrentSessionId(sessionId);
    setPendingMessages([]);
    setConfirmDeleteId(null);
  }, []);

  const startNewChat = useCallback(() => {
    setCurrentSessionId(null);
    setPendingMessages([]);
    setConfirmDeleteId(null);
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [displayMessages.length]);

  const submitQuestion = handleSubmit((v) => queryMutation.mutate(v));

  return (
    <div className="flex h-full overflow-hidden">
      {/* 사이드바 */}
      <aside className="w-60 shrink-0 border-r flex flex-col bg-muted/30">
        <div className="p-3 border-b">
          <Button variant="outline" size="sm" className="w-full" onClick={startNewChat}>
            + 새 대화
          </Button>
        </div>
        <nav className="flex-1 overflow-y-auto p-2 space-y-0.5">
          {!sessionsData?.items.length && (
            <p className="px-3 py-2 text-xs text-muted-foreground">대화 내역이 없습니다</p>
          )}
          {sessionsData?.items.map((session) => (
            <div key={session.id} className="group relative">
              {confirmDeleteId === session.id ? (
                /* 삭제 확인 UI */
                <div className="flex items-center gap-1 px-2 py-1.5 rounded-md bg-destructive/10">
                  <span className="flex-1 text-xs text-destructive truncate">삭제할까요?</span>
                  <button
                    className="text-xs text-muted-foreground hover:text-foreground px-1"
                    onClick={() => setConfirmDeleteId(null)}
                  >
                    취소
                  </button>
                  <button
                    className="text-xs text-destructive font-medium px-1"
                    onClick={() => deleteMutation.mutate(session.id)}
                    disabled={deleteMutation.isPending}
                  >
                    삭제
                  </button>
                </div>
              ) : (
                /* 세션 아이템 */
                <button
                  onClick={() => selectSession(session.id)}
                  className={[
                    'w-full text-left px-3 py-2 pr-8 rounded-md text-xs transition-colors line-clamp-2 leading-snug',
                    currentSessionId === session.id
                      ? 'bg-muted font-medium text-foreground'
                      : 'text-foreground hover:bg-muted',
                  ].join(' ')}
                >
                  {session.title}
                </button>
              )}
              {/* 호버 시 삭제 버튼 */}
              {confirmDeleteId !== session.id && (
                <button
                  onClick={(e) => { e.stopPropagation(); setConfirmDeleteId(session.id); }}
                  className="absolute right-1.5 top-1/2 -translate-y-1/2 p-1 rounded opacity-0 group-hover:opacity-100 hover:text-destructive transition-opacity"
                  aria-label="세션 삭제"
                >
                  <Trash2 className="size-3" />
                </button>
              )}
            </div>
          ))}
        </nav>
      </aside>

      {/* 메인 채팅 영역 */}
      <div className="flex-1 flex flex-col min-w-0">
        <div className="flex-1 overflow-y-auto px-4 py-6 space-y-5">
          {displayMessages.length === 0 && !queryMutation.isPending && (
            <div className="h-full flex flex-col items-center justify-center gap-2 text-center">
              <p className="text-base font-medium">무엇이 궁금하신가요?</p>
              <p className="text-sm text-muted-foreground">
                Spring, Java 공식 문서 기반으로 답변합니다
              </p>
            </div>
          )}

          {displayMessages.map((turn, i) => (
            <div key={`${currentSessionId}-${i}`} className="space-y-2.5">
              <div className="flex justify-end">
                <div className="bg-primary text-primary-foreground rounded-2xl rounded-tr-sm px-4 py-2.5 max-w-[75%] text-sm">
                  {turn.question}
                </div>
              </div>
              <div className="flex justify-start">
                <div className="bg-muted rounded-2xl rounded-tl-sm px-4 py-2.5 max-w-[75%]">
                  <MarkdownRenderer content={turn.answer} />
                </div>
              </div>
            </div>
          ))}

          {queryMutation.isPending && (
            <div className="flex justify-start">
              <div className="bg-muted rounded-2xl rounded-tl-sm px-4 py-3 text-sm text-muted-foreground">
                <span className="animate-pulse">답변을 생성하고 있습니다...</span>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        <div className="shrink-0 border-t px-4 py-3">
          <form onSubmit={submitQuestion} className="flex gap-2 items-end">
            <div className="flex-1">
              <textarea
                rows={2}
                placeholder="질문을 입력하세요 (Shift+Enter 줄바꿈, Enter 전송)"
                aria-invalid={!!errors.question}
                className="border-input bg-background placeholder:text-muted-foreground focus-visible:ring-ring w-full rounded-lg border px-3 py-2 text-sm shadow-sm focus-visible:ring-1 focus-visible:outline-none resize-none aria-[invalid=true]:border-destructive"
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    submitQuestion();
                  }
                }}
                {...register('question')}
              />
              {errors.question && (
                <p className="text-sm text-destructive mt-1">{errors.question.message}</p>
              )}
            </div>
            <Button type="submit" disabled={queryMutation.isPending} className="shrink-0 mb-0.5">
              {queryMutation.isPending ? '생성 중' : '전송'}
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
