import { Link } from '@tanstack/react-router';
import type { ReactNode } from 'react';

export interface AppShellNavItem {
  to: string;
  label: string;
}

interface AppShellProps {
  /** Header の左側に出す業態名 / プロダクト名(クリックで `/` へ)。 */
  brand: string;
  /** Header の右側ナビ。 各 item は TanStack Router の `to` を取る。 */
  nav?: AppShellNavItem[];
  children: ReactNode;
}

/**
 * 4 業態 web app 共通の外殻。 Header(brand + nav)+ max-w-6xl のメイン領域。 dark mode や sidebar
 * は F5(design system 拡張)で追加。 各 app は children に dashboard 等の page を流し込む。
 */
export function AppShell({ brand, nav = [], children }: AppShellProps) {
  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-background/95 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-3">
          <Link to="/" className="text-lg font-semibold">
            {brand}
          </Link>
          {nav.length > 0 && (
            <nav className="flex gap-4 text-sm">
              {nav.map((item) => (
                <Link
                  key={item.to}
                  to={item.to}
                  activeProps={{ className: 'font-semibold' }}
                  className="text-muted-foreground hover:text-foreground"
                >
                  {item.label}
                </Link>
              ))}
            </nav>
          )}
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">{children}</main>
    </div>
  );
}
