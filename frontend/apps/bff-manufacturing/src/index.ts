import { ApolloServer } from '@apollo/server';
import fastifyApollo, { fastifyApolloDrainPlugin } from '@as-integrations/fastify';
import Fastify from 'fastify';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
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
      const authHeader = request.headers.authorization;
      const authToken = authHeader?.startsWith('Bearer ')
        ? authHeader.slice('Bearer '.length)
        : null;
      return { loaders: createLoaders(), authToken };
    },
  });

  fastify.get('/health', async () => ({ status: 'ok' }));

  const port = Number(process.env.PORT ?? 4002);
  await fastify.listen({ port, host: '0.0.0.0' });
  console.info(`bff-manufacturing listening on :${port}/graphql`);
}

main().catch((err) => {
  console.error('bff-manufacturing の起動に失敗:', err);
  process.exit(1);
});
