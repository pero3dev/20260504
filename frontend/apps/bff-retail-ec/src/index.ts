import { ApolloServer } from '@apollo/server';
import fastifyApollo, { fastifyApolloDrainPlugin } from '@as-integrations/fastify';
import Fastify from 'fastify';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { InventoryReadModelClient } from './clients/inventory-read-model-client.js';
import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const typeDefs = readFileSync(resolve(__dirname, '../src/schema.graphql'), 'utf8');

async function main() {
  const fastify = Fastify({ logger: true });

  const inventoryClient = new InventoryReadModelClient(
    process.env.INVENTORY_READ_MODEL_URL ?? 'http://localhost:8080',
  );

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
      return {
        loaders: createLoaders(inventoryClient, authToken),
        authToken,
      };
    },
  });

  fastify.get('/health', async () => ({ status: 'ok' }));

  const port = Number(process.env.PORT ?? 4001);
  await fastify.listen({ port, host: '0.0.0.0' });
  console.info(
    `bff-retail-ec listening on :${port}/graphql (inventory-read-model: ${
      process.env.INVENTORY_READ_MODEL_URL ?? 'http://localhost:8080'
    })`,
  );
}

main().catch((err) => {
  console.error('bff-retail-ec の起動に失敗:', err);
  process.exit(1);
});
