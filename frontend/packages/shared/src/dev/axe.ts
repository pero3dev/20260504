/**
 * dev mode 限定の axe-core a11y scanner(ADR-0022 phase 3、 a11y 4 層の第 2 層)。
 *
 * <p>WCAG 2.1 AA 違反を browser console に流す。 polling 方式(default 5s 間隔)で
 * SPA の route 切替後も自動で再 scan される。 production bundle には含めないため、
 * 各 web app の main.tsx で `import.meta.env.DEV` ガード下で呼出すこと:
 *
 * <pre>{@code
 * if (import.meta.env.DEV) {
 *   void startAxeDevScanner();
 * }
 * }</pre>
 *
 * <p>本来は `@axe-core/react` を使いたいが、 同 package が React 18 ベースで
 * `react-dom.findDOMNode`(React 19 で削除)に依存しているため React 19 環境では動かない。
 * `@axe-core/react` が React 19 対応した時点で本ヘルパは非推奨化予定。
 */
export interface AxeDevScannerOptions {
  /** scan 間隔(ms、 default 5000)。 0 以下を渡すと初回 1 回のみで polling 停止 */
  intervalMs?: number;
  /** 初回 scan までの待ち時間(ms、 default 1500)。 mount 完了を待つ */
  initialDelayMs?: number;
}

export async function startAxeDevScanner(options: AxeDevScannerOptions = {}): Promise<void> {
  if (typeof window === 'undefined' || typeof document === 'undefined') return;

  const { intervalMs = 5000, initialDelayMs = 1500 } = options;
  const axe = (await import('axe-core')).default;

  const scan = async () => {
    try {
      const results = await axe.run(document.body);
      if (results.violations.length > 0) {
        console.warn(
          `[axe-core] ${results.violations.length} a11y violation(s)`,
          results.violations,
        );
      }
    } catch (err) {
      console.warn('[axe-core] scan failed:', err);
    }
  };

  window.setTimeout(() => void scan(), initialDelayMs);
  if (intervalMs > 0) {
    window.setInterval(() => void scan(), intervalMs);
  }
}
