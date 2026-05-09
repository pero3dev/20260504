import type { CodegenConfig } from '@graphql-codegen/cli';

/**
 * GraphQL Codegen 設定(F6 follow-up、 retail-ec pilot)。
 *
 * <p>同 monorepo の `bff-retail-ec/src/schema.graphql` を schema 入力に、
 * 本 web app の `src/lib/graphql-client.ts` の `gql\`...\`` テンプレ literal を
 * documents に取り、 `src/__generated__/graphql.ts` へ TS 型を出す。
 *
 * <p>pilot 検証後、 残 3 web app(manufacturing / tpl / wholesale)に同型展開する。
 *
 * <p>設定ポイント:
 *
 * <ul>
 *   <li>{@code skipTypename: true} — `__typename` を含めない(現行手書き interface と互換、
 *       Apollo cache を使わないので不要)
 *   <li>{@code useTypeImports: true} — 出力で `import type` を使い tree-shake friendly に
 *   <li>{@code scalars.DateTime} — schema 上の `scalar DateTime` を `string` にマップ
 * </ul>
 */
const config: CodegenConfig = {
  overwrite: true,
  schema: '../bff-retail-ec/src/schema.graphql',
  documents: ['src/lib/graphql-client.ts'],
  generates: {
    'src/__generated__/graphql.ts': {
      plugins: ['typescript', 'typescript-operations'],
      config: {
        skipTypename: true,
        useTypeImports: true,
        scalars: {
          DateTime: 'string',
        },
      },
    },
  },
};

export default config;
