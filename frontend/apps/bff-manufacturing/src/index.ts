import { ApolloServer } from '@apollo/server';
import fastifyApollo, { fastifyApolloDrainPlugin } from '@as-integrations/fastify';
import {
  buildBffAuth,
  createJwtVerifier,
  createRemoteJwks,
  JwtVerificationError,
  type JwtVerifier,
} from '@inventory/shared';
import Fastify from 'fastify';
import { GraphQLError } from 'graphql';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { ManufacturingClient } from './clients/manufacturing-client.js';
import { createLoaders } from './dataloaders.js';
import { resolvers, type BffContext } from './resolvers.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const typeDefs = readFileSync(resolve(__dirname, '../src/schema.graphql'), 'utf8');

function buildJwtVerifier(): JwtVerifier | null {
  const jwksUrl = process.env.JWT_JWKS_URL;
  const issuer = process.env.JWT_ISSUER;
  if (!jwksUrl || !issuer) return null;
  return createJwtVerifier({
    jwks: createRemoteJwks(jwksUrl),
    issuer,
    audience: process.env.JWT_AUDIENCE,
  });
}

async function main() {
  const fastify = Fastify({ logger: true });
  const backendUrl = process.env.MANUFACTURING_URL ?? 'http://localhost:8088';
  const client = new ManufacturingClient(backendUrl);
  const verifier = buildJwtVerifier();
  if (!verifier) {
    fastify.log.warn(
      'JWT_JWKS_URL / JWT_ISSUER 未設定のため JWT verify を skip。 dev 用途のみ。 prod では必ず設定する。',
    );
  }

  const apollo = new ApolloServer<BffContext>({
    typeDefs,
    resolvers,
    plugins: [fastifyApolloDrainPlugin(fastify)],
  });
  await apollo.start();

  await fastify.register(fastifyApollo(apollo), {
    context: async (request) => {
      let auth;
      try {
        auth = await buildBffAuth({
          authorizationHeader: request.headers.authorization,
          verifier,
        });
      } catch (err) {
        if (err instanceof JwtVerificationError) {
          throw new GraphQLError('認証に失敗しました', {
            extensions: { code: 'UNAUTHENTICATED', http: { status: 401 } },
          });
        }
        throw err;
      }
      return {
        loaders: createLoaders(client, auth.authToken),
        authToken: auth.authToken,
        user: auth.user,
      };
    },
  });

  fastify.get('/health', async () => ({ status: 'ok' }));

  const port = Number(process.env.PORT ?? 4002);
  await fastify.listen({ port, host: '0.0.0.0' });
  console.info(`bff-manufacturing listening on :${port}/graphql (manufacturing: ${backendUrl})`);
}

main().catch((err) => {
  console.error('bff-manufacturing の起動に失敗:', err);
  process.exit(1);
});
