import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * BFF `Query.viewer` から取得した `locale` を `i18n.changeLanguage()` に適用する hook
 * (ADR-0022 phase 5b)。 同じ locale なら no-op、 値が変わった時だけ切替を起動。
 *
 * <p>未認証(viewer = null / locale = undefined)の場合は initial language(`createI18n`
 * に渡した値、 通常 `ja`)のまま据え置く。
 *
 * @example
 *   const { data } = useQuery({ queryKey: ['viewer'], queryFn: fetchViewer, staleTime: Infinity });
 *   useApplyTenantLocale(data?.viewer?.locale);
 */
export function useApplyTenantLocale(locale: string | null | undefined): void {
  const { i18n } = useTranslation();
  useEffect(() => {
    if (locale && locale !== i18n.language) {
      void i18n.changeLanguage(locale);
    }
  }, [locale, i18n]);
}
