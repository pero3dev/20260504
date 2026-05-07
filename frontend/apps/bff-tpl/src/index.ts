import { ApolloServer } from '@apollo/server';
import fastifyApollo, { fastifyApolloDrainPlugin } from '@as-integrations/fastify';
import Fastify from 'fastify';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { TplClient } from './clients/tpl-client.js';
import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const typeDefs = readFileSync(resolve(__dirname, '../src/schema.graphql'), 'utf8');

async function main() {
  const fastify = Fastify({ logger: true });
  const backendUrl = process.env.TPL_URL ?? 'http://localhost:8086';
  const client = new TplClient(backendUrl);

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
      return { loaders: createLoaders(client, authToken), authToken };
    },
  });

  fastify.get('/health', async () => ({ status: 'ok' }));

  const port = Number(process.env.PORT ?? 4003);
  await fastify.listen({ port, host: '0.0.0.0' });
  console.info(`bff-tpl listening on :${port}/graphql (tpl: ${backendUrl})`);
}

main().catch((err) => {
  console.error('bff-tpl の起動に失敗:', err);
  process.exit(1);
});
