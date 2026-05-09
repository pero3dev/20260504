import type { CodegenConfig } from '@graphql-codegen/cli';

/**
 * GraphQL Codegen 設定(F6 follow-up phase 2、 retail-ec pilot からの同型展開)。
 *
 * <p>同 monorepo の `bff-manufacturing/src/schema.graphql` を schema 入力に、
 * 本 web app の `src/lib/graphql-client.ts` から operations を抽出し、
 * `src/__generated__/graphql.ts` へ TS 型を出す。
 */
const config: CodegenConfig = {
  overwrite: true,
  schema: '../bff-manufacturing/src/schema.graphql',
  documents: ['src/lib/graphql-client.ts'],
  generates: {
    'src/__generated__/graphql.ts': {
      plugins: ['typescript', 'typescript-operations'],
      config: {
        skipTypename: true,
        useTypeImports: true,
        scalars: {
          Date: 'string',
        },
      },
    },
  },
};

export default config;
