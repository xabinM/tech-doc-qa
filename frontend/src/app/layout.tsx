import type { Metadata } from 'next';
import { Geist } from 'next/font/google';
import './globals.css';
import QueryProvider from '@/providers/QueryProvider';
import { Toaster } from '@/components/ui/sonner';

const geist = Geist({ subsets: ['latin'], variable: '--font-sans' });

export const metadata: Metadata = {
  title: 'Tech Doc Q&A',
  description: 'Spring/Java 기술 문서 기반 Q&A 서비스',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko" className={`${geist.variable} h-full antialiased`}>
      <body className="min-h-full">
        <QueryProvider>
          {children}
          <Toaster richColors position="top-right" />
        </QueryProvider>
      </body>
    </html>
  );
}
