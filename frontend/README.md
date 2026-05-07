# Frontend monorepo

4 業態(Retail/EC、 Manufacturing、 3PL、 Wholesale)向けの **Web UI** + **GraphQL BFF** を 1 monorepo に集約。 Backend(Java services / `services/*`)とは別 build 経路で、 共有は契約 layer(GraphQL schema)と認証 token のみ。

## 構造

```
frontend/
├── apps/
│   ├── bff-retail-ec/       Apollo Server v4(GraphQL)+ DataLoader 雛形
│   ├── bff-manufacturing/   (後続フェーズ)
│   ├── bff-tpl/             (後続フェーズ)
│   ├── bff-wholesale/       (後続フェーズ)
│   ├── web-retail-ec/       Vite + React 19 + Tailwind + shadcn 雛形
│   ├── web-manufacturing/   (後続フェーズ)
│   ├── web-tpl/             (後続フェーズ)
│   └── web-wholesale/       (後続フェーズ)
├── packages/
│   └── shared/              型 / 共通 helper
├── package.json             ルート pnpm workspace + Turborepo
├── pnpm-workspace.yaml
├── turbo.json
├── tsconfig.base.json       全 app / package が継承する厳格 tsconfig
├── eslint.config.mjs        Flat config
└── .prettierrc.json
```

## 技術スタック

| 領域 | 採択 | 理由 |
|---|---|---|
| Monorepo | **Turborepo + pnpm workspace** | Nx より lightweight、 lock-in が無い |
| BFF | TypeScript + **Apollo Server v4** + DataLoader | schema-first / N+1 防止(CLAUDE.md ルール) |
| BFF Auth | JWT pass-through(Identity Broker 発行) | backend service 各々が JWT を verify する設計に整合 |
| UI build | **Vite + React 19 + TypeScript** | SSR 不要(internal SaaS)、 fast HMR |
| UI routing | **TanStack Router**(file-based) | type-safe、 loader/action モデルで TanStack Query 親和 |
| UI data | **TanStack Query + graphql-request + GraphQL Code Generator** | Apollo Client より軽量、 server-state は TanStack Query で統一 |
| UI styling | **Tailwind CSS + shadcn/ui** | owned-code、 Radix UI primitive、 MIT |
| UI auth | **oidc-client-ts**(Cognito SAML federation 経由) | Identity Broker token 交換と整合 |
| Test | Vitest + React Testing Library | Jest より早く、 ESM 標準 |

## 必要な前提

- Node.js >= 20.10
- pnpm >= 9.0(`corepack enable && corepack prepare pnpm@9.15.0 --activate`)
- 開発時はローカルで Java backend を起動済(`mvn -pl services/inventory-core spring-boot:run` 等)

## 初回セットアップ

```bash
cd frontend
corepack enable
pnpm install
```

## 開発(BFF + UI を並列起動)

```bash
pnpm dev
```

Turborepo が `apps/*` の `dev` task を並列で起動。 Retail/EC vertical だけなら:

```bash
pnpm --filter web-retail-ec dev   # Vite dev server (http://localhost:5173)
pnpm --filter bff-retail-ec dev   # Apollo Server  (http://localhost:4001/graphql)
```

## ビルド / lint / 型チェック / test

```bash
pnpm build       # 全 app を build
pnpm lint        # ESLint
pnpm typecheck   # tsc --noEmit
pnpm test        # Vitest
pnpm format      # Prettier(差分書込み)
pnpm format:check
```

## 認証(MVP)

`web-retail-ec` は **stub auth**(localStorage に dev token を入れる)で起動する。 Cognito federation 実配線は後続フェーズ:

1. Identity Broker(`services/identity-broker`)で Cognito SAML 連携を完成
2. `web-*` の `lib/auth.ts` に `oidc-client-ts` ベースの実装を流し込む
3. `bff-*` で `Authorization: Bearer <jwt>` を verify(jwks 取得 → 検証 → `tenantId` 取り出し)+ pass-through

## 後続フェーズ

| 順 | 内容 |
|---|---|
| F2 | Cognito SAML 実配線 + Identity Broker token 交換 + BFF JWT verify |
| F3 | 残 3 業態の BFF + UI を同パターンで複製 |
| F4 | `packages/ui/` に共通 design system(Button / Form / Table / Pagination 等)を切り出し |
| F5 | Storybook + Playwright E2E |
| F6 | BFF resolver を本物の backend(`inventory-read-model` / `inventory-core` / 業態 service)に繋ぐ |
| F7 | ADR-0022 で Frontend 構造を明文化、 i18n + a11y 方針追加 |

詳細は CHANGELOG.md の Future Work セクションを参照。
