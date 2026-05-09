# ADR-0022: Frontend 構造とライブラリ選定(i18n / a11y / form / chart)

- **Status**: Accepted
- **Date**: 2026-05-09
- **Deciders**: Frontend, Architecture

## Context

F1〜F2 で Frontend monorepo(Turborepo + pnpm + Vite + React 19 + TanStack Router/Query + Tailwind + shadcn semantic CSS 変数 + 4 BFF + 4 web app + OIDC SAML federation + JWT verify)が立ち上がり、 全業態の vertical を mock 解消まで進めた。 4 web app の MVP は dashboard 1 画面 + Login/Logout で稼働中。

ここから先は次の領域の選定を決めないと、 50+ engineers の規模で各 web app が個別に決め始めて分裂する:

1. **i18n**(国際化 / 多言語対応)— UI 文言の言語切替、 日付・数値・通貨 format
2. **a11y**(アクセシビリティ)— WCAG 2.1 AA レベルを目標とする統一基準
3. **form validation**(フォーム入力検証)— 大量の入力フォーム(在庫登録 / 注文編集 / マスタ管理)が今後追加される
4. **chart / visualization**(グラフ可視化)— 在庫推移 / 売上 / KPI dashboard
5. **client state management**(client 側状態管理)— server state(TanStack Query)以外の UI 状態
6. **error boundary**(エラー境界)— BFF 通信失敗 / レンダリング例外時の UX
7. **dev runtime config 配布**(env 注入)— Vite build 時 vs runtime injection

CLAUDE.md 上は外部公開 API description は英語、 内部コメントは日本語。 が、 **エンドユーザ向け UI 文言**の方針は未定(取引先含む B2B SaaS で日本語が主、 海外取引先のテナントが追加される可能性あり)。 ADR-0007(Cognito + SAML federation)で多国籍テナントが視野に入った時点で、 i18n 設計の必要性は確定した。

業務要件側の制約:

- **B2B SaaS**: 1 テナント = 1 企業。 言語は組織単位で固定可能(個人ごとに切替不要)。 ただし将来の多国籍テナント受入時に言語追加コストが小さいこと
- **WCAG 2.1 AA 必須**: 公的機関 / 大手取引先案件で要件として課される(プロジェクト初期から対応する方が後から retrofit より圧倒的に安い)
- **大量フォーム**: 在庫マスタ / 注文 / 取引先 / Workflow 承認 など、 13 サービス × 平均 5〜10 フォームで合計 ~80 フォーム規模が見込まれる
- **dashboard heavy**: 100M tx/day の在庫プラットフォームで、 KPI / 推移 / 分布の chart は中核 UX

## Decision

以下を Frontend monorepo の **採用ライブラリ + 構造ルール** として確定する。 例外は ADR で逆提案するまで適用される。

### 1. i18n: **react-i18next**(`i18next` + `react-i18next` + `i18next-browser-languagedetector`)

**採用構造**:

- 翻訳 catalog は **JSON ファイル**(`packages/shared/src/i18n/locales/{ja,en}/{namespace}.json`)
- `namespace` は **業態 + common** で分割(`common` / `retail-ec` / `manufacturing` / `tpl` / `wholesale`)
- 各 web app は `<I18nextProvider>` でラップし、 `useTranslation('namespace')` で参照
- フォーマット(日付 / 数値 / 通貨)は **`Intl` API を直接利用**(i18next の formatter は使わない、 native の方が型安全)
- 言語切替: 初期は **テナント単位固定**(Identity Broker が `tenant.locale` claim を返し、 web app 起動時に `i18n.changeLanguage()`)。 ユーザ単位切替は後フェーズ
- フォールバック言語: **`ja`**(国内取引先が主体)

### 2. a11y: **WCAG 2.1 AA を目標**、 4 層の防御

- **lint 層**: `eslint-plugin-jsx-a11y`(root eslint config に追加、 `recommended` rules、 `--max-warnings=0` で error 化)
- **dev 確認層**: `@axe-core/react`(dev mode のみ動的 audit、 production bundle には含めない)
- **CI 層**: Storybook(F5 で導入)に **`@storybook/addon-a11y`** を組み込み、 component 単位で違反検出
- **manual 層**: チェックリスト(`docs/frontend/a11y-checklist.md` 別タスク)で release 前に screen reader / keyboard nav / contrast を人手確認

shadcn/ui は **Radix UI primitives** ベースで a11y デフォルト(focus management / ARIA roles / keyboard nav)が標準装備。 自作 component を増やすより shadcn の primitive を組合せる方針(`@inventory/ui` の方針として明文化)。

### 3. form validation: **zod + react-hook-form + @hookform/resolvers/zod**

- **zod** で schema を 1 箇所に書き、 type 推論で TS 型を導出(`z.infer<typeof schema>`)
- **react-hook-form** が controlled/uncontrolled の両方を効率的に扱う(再 render 最小化 = 大規模フォームでも 60fps 維持)
- BFF (GraphQL) との form payload 型整合は **GraphQL Codegen(F6 follow-up)で生成された型 + zod schema を併存**(GraphQL が wire format、 zod が UI side バリデーション、 サーバ側 RFC 7807 を別途 catch)
- `@inventory/ui` に `<Form>` / `<FormField>` / `<FormError>` の wrapper を提供(F4 follow-up と同タイミングで追加)

### 4. chart: **recharts**(主)、 **visx** は逃げ道

- 第 1 選択は **recharts**(declarative、 React 19 対応、 ~50 community-maintained chart types で 95% の dashboard 用途をカバー)
- 個別 dashboard で recharts の表現力不足が出たら **visx**(low-level、 D3 + React で完全 custom 可)に**その chart のみ**逃がす(全体置換はしない)
- chart wrapper は web app 単位ではなく `@inventory/ui/charts` で集約(色 token / theme / accessibility 対応を 1 箇所)
- 大規模 time-series(>10k points)は `recharts` の `<LineChart>` を Canvas mode で運用、 それでも追いつかなければ `visx + canvas` に逃げる

### 5. client state: **server state は TanStack Query、 client state は useState/useReducer**

- **server state**(API/GraphQL からの fetch 結果): すべて **TanStack Query** で管理(既に F1 から採用)
- **form state**: react-hook-form(上記 form validation 内で完結)
- **router state**: TanStack Router の search params(URL に乗る、 戻るボタンが効く)
- **local UI state**(modal open / 折りたたみ / hover 等): React 標準 `useState` / `useReducer`
- **Redux / Zustand / Jotai は採用しない**: 採用したくなる UX 要件(global undo / 大規模 form の cross-section state / cross-tab sync 等)が出たら別 ADR で再評価

### 6. error boundary: **`react-error-boundary` + 3 層**

- **route 層**: TanStack Router の `errorComponent` を全 root route に設定(navigation 失敗を吸収)
- **suspense / query 層**: `<QueryErrorResetBoundary>` で TanStack Query の error 状態を境界化、 `react-error-boundary` の `<ErrorBoundary>` でラップして retry button を提供
- **app 層**: 最外側に root `<ErrorBoundary>` を 1 つ置き、 全捕捉外の例外で「不明なエラー」画面 + Datadog RUM へ送信

`@inventory/ui/<DefaultErrorFallback>` を提供し、 各 web app は import 1 行で揃える。

### 7. dev runtime config: **build-time(`VITE_*` env)で十分、 runtime 注入はしない**

- 4 web app の env(`VITE_OIDC_*` / `VITE_GRAPHQL_ENDPOINT` / `VITE_DD_RUM_*` 等)は **CI image build 時に注入** する。 同 image を staging / prod で使い回すパターンは採らない(env ごとに別 image を build)
- 理由: K8s ConfigMap → window.__ENV__ injection は dev/prod 差を生み、 SSR でないため意味が小さく、 build-time の方が単純で間違いが少ない
- 例外: **緊急 toggle**(feature flag)は **Unleash**(CLAUDE.md で既決)を runtime fetch で参照する

## Consequences

### Positive

- **小〜中規模に最適**: TanStack 系 + zod/react-hook-form + recharts は 50+ engineers が経験済みのスタックで onboarding コスト最小
- **bundle size が控えめ**: i18next ~40KB + react-hook-form ~25KB + recharts ~100KB + zod ~14KB ≈ 180KB(各業態 web app + Vite tree-shaking で実効 ~120KB 増)。 acceptable
- **a11y を最初から組込む**: lint/CI 層を最初に整備すれば後付けの retrofit コストが激減(WCAG 2.1 AA 後付け実装は経験則で 3〜5 倍)
- **shadcn/ui との整合**: a11y は Radix primitives 由来でほぼ無料、 design token は F4 で確立済み CSS 変数を使い続けられる

### Negative

- **i18next の learning curve**: namespace + plural rules + interpolation の習得に 1〜2 日 / 人。 docs は読まれない傾向 → `docs/frontend/i18n-guide.md` を別タスクで用意
- **react-hook-form の controlled component 連携**: shadcn の Select / Combobox 等 controlled component は `<Controller>` 経由が必要、 docs 整備が要る
- **recharts の React 19 対応**: 2026-05 時点で最新版が React 19 を実験 support。 stable に乗るのは数ヶ月後の見込み(問題が出れば一時的に React 18.x にダウングレードする選択肢を残す)
- **CSS-in-JS は採用しない**: Tailwind + shadcn semantic CSS 変数で完結する方針(F4 既決)。 動的 styling が要求された場合は CSS 変数の runtime 書換 or `data-*` attribute + Tailwind variant で対応

### Neutral

- **TypeScript 型整合**: zod / react-hook-form / TanStack Query / GraphQL Codegen は全て型生成 / 型推論が中核設計。 型整合は手元で機能し続ける前提
- **Storybook(F5)依存**: a11y 4 層のうち CI 層は Storybook 導入後にしか機能しない。 F5 が遅れる場合は lint + dev + manual の 3 層で運用継続
- **monorepo 横断の version 同期**: `pnpm.overrides` で react-i18next / zod / recharts のメジャーバージョンを単一にロック(F1 で確立済みのパターン継続)

## Alternatives considered

### i18n: react-intl(FormatJS)

ICU MessageFormat 採用、 React Intl の歴史が長い。 ICU の plural / select / number formatting の表現力は i18next より厚い。

**Rejected**:

- **書き味**: ICU MessageFormat は読み書きが冗長(`{count, plural, one {# 件} other {# 件}}`)。 i18next の `t('key', { count: n })` の方が daily devで圧倒的に楽
- **JSON catalog の汎用性**: i18next は JSON で素直、 react-intl は専用 extractor + format が必要
- **community 活性度**: 2025 以降の Issue / Release 頻度は i18next の方が高い

**Reconsider 条件**: 多国籍テナントで複雑な plural / gender 表現が大量必要になり、 i18next の `Trans` component で限界を超えたとき。

### chart: visx を主にする

D3 + React の low-level 構成。 完全 custom が前提の data viz チームに最適。

**Rejected**: 95% の dashboard は recharts で足り、 残り 5% に visx を逃がす方が学習コスト・開発速度のバランスが良い。 全部 visx で書くと「KPI カード追加」程度の細かい task で boilerplate が膨らむ。

### chart: nivo / Apache ECharts

- **nivo**: API 表現力は recharts と同程度、 但し Canvas/SVG 切替が組込み。 React 19 対応の lag が大きく見送り
- **Apache ECharts**: 表現力 No.1 だが React wrapper が薄く、 imperative API 寄りで TanStack Query との相性が悪い

### state: Zustand / Jotai を採用

global UI state(theme / sidebar 開閉 / 通知トレイ等)が増えてきたら採用議論。 現状は React Context + useState で十分、 過剰設計を避ける。

### form: Formik

react-hook-form より歴史が長いが、 controlled component 中心で大規模フォームでの再 render コストが大きい。 現代では react-hook-form が業界標準。

### runtime config: K8s ConfigMap → window.__ENV__

env ごとに同 image を再利用できるが、 (a) 4 web app × 複数 env で複雑度が上がる、 (b) Vite build 時に env を埋め込む方が CI cache が単純、 (c) prod image を staging で動かすデバッグ需要が顕在化していない。 必要が出たら別 ADR。

## Implementation status

- ✅ 本 ADR 確定(2026-05-09)
- ⏳ phase 1: i18next + zod + react-hook-form + recharts + react-error-boundary を `pnpm add` し、 `packages/shared/src/i18n/` の skeleton + `packages/ui/<Form>` wrapper + `packages/ui/charts/` を作成(別 PR)
- ⏳ phase 2: 既存 4 web app の dashboard を i18n 化(`ja` catalog 投入)+ a11y lint 違反 0 + recharts で在庫推移 chart を 1 つ追加(vertical 動作確認)
- ⏳ phase 3: 各 web app の form を順次 react-hook-form + zod に統一(在庫マスタ編集 / 注文 form etc 個別 task)
- ⏳ phase 4: F5 Storybook 導入 + a11y addon + visual regression(別タスク)

## References

- ADR-0007: Cognito + SAML federation(多国籍テナント受入の基盤、 i18n の前提)
- ADR-0012: Trunk-Based Development(短い PR で本 ADR の各 phase を分割実装する前提)
- F4 PR: `packages/ui` 共通 design system(CSS 変数 / shadcn semantic tokens、 本 ADR の design token は再利用)
- F2 phase A〜C: BFF JWT verify + web OIDC + IB exchange(本 ADR の認可基盤、 user.locale claim の流路)
- WCAG 2.1: <https://www.w3.org/TR/WCAG21/>(AA 基準、 1.4 contrast / 2.1 keyboard / 3.3 forms 等)
- 採用ライブラリ:
  - [i18next](https://www.i18next.com/) + [react-i18next](https://react.i18next.com/)
  - [react-hook-form](https://react-hook-form.com/) + [zod](https://zod.dev/)
  - [recharts](https://recharts.org/) / [visx](https://airbnb.io/visx/)(escape hatch)
  - [react-error-boundary](https://github.com/bvaughn/react-error-boundary)
  - [@axe-core/react](https://github.com/dequelabs/axe-core-npm/tree/develop/packages/react) + [eslint-plugin-jsx-a11y](https://github.com/jsx-eslint/eslint-plugin-jsx-a11y)
