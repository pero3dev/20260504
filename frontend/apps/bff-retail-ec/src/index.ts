import { ApolloServer } from '@apollo/server';
import fastifyApollo, { fastifyApolloDrainPlugin } from '@as-integrations/fastify';
import Fastify from 'fastify';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
// schema.graphql は build 時に dist 配下にコピーされない構成のため、 src からの相対参照で読む(本番では rollup
// 等で bundle するか generate-sources で .ts に変換するのが望ましいが MVP は read at boot)。
const typeDefs = readFileSync(resolve(__dirname, '../src/schema.graphql'), 'utf8');

async function main() {
  const fastify = Fastify({ logger: true });

  const apollo = new ApolloServer<BffContext>({
    typeDefs,
    resolvers,
    plugins: [fastifyApolloDrainPlugin(fastify)],
  });
  await apollo.start();

  await fastify.register(fastifyApollo(apollo), {
    context: async (request) => {
      // F2 で Identity Broker JWT verify を入れる。 F1 stub は header をそのまま流すだけ。
      const authHeader = request.headers.authorization;
      const authToken = authHeader?.startsWith('Bearer ')
        ? authHeader.slice('Bearer '.length)
        : null;
      return {
        loaders: createLoaders(),
        authToken,
      };
    },
  });

  fastify.get('/health', async () => ({ status: 'ok' }));

  const port = Number(process.env.PORT ?? 4001);
  await fastify.listen({ port, host: '0.0.0.0' });
  console.info(`bff-retail-ec listening on :${port}/graphql`);
}

main().catch((err) => {
  console.error('bff-retail-ec の起動に失敗:', err);
  process.exit(1);
});
