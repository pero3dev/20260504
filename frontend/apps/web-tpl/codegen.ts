import type { CodegenConfig } from '@graphql-codegen/cli';

/**
 * GraphQL Codegen 設定(F6 follow-up phase 2、 retail-ec pilot からの同型展開)。
 * tpl の schema は scalar Date を持たないので scalars マップは不要。
 */
const config: CodegenConfig = {
  overwrite: true,
  schema: '../bff-tpl/src/schema.graphql',
  documents: ['src/lib/graphql-client.ts'],
  generates: {
    'src/__generated__/graphql.ts': {
      plugins: ['typescript', 'typescript-operations'],
      config: {
        skipTypename: true,
        useTypeImports: true,
      },
    },
  },
};

export default config;
