import 'dotenv/config';
import Fastify from 'fastify';
import cors from '@fastify/cors';
import rateLimit from '@fastify/rate-limit';
import staticFiles from '@fastify/static';
import compress from '@fastify/compress';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { getConfig } from './config.js';
import registerEmbedRoutes from './routes/embed.js';
import registerChatRoutes from './routes/chat.js';
import registerTranscribeRoutes from './routes/transcribe.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const config = getConfig();
const app = Fastify({ 
  logger: true,
  maxParamLength: 500,
  bodyLimit: 10485760, // 10MB
  keepAliveTimeout: 65000,
  headersTimeout: 66000
});

// Enable compression for better performance
await app.register(compress, { 
  encodings: ['gzip', 'deflate', 'br'] 
});

await app.register(cors, { 
  origin: true,
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'Cache-Control', 'Connection']
});
await app.register(rateLimit, {
  max: config.rateLimit.max,
  timeWindow: config.rateLimit.timeWindow,
});

// Serve static files (including the chat test HTML)
await app.register(staticFiles, {
  root: join(__dirname, '..'),
  prefix: '/'
});

app.get('/health', async () => {
  return {
    ok: true,
    service: 'journai-proxy',
    embedModel: config.openai.embedModel,
    chatModel: config.openai.chatModel,
  };
});

// Cache management endpoints
app.get('/cache/status', async (request, reply) => {
  const { responseCache } = await import('./cache.js');
  return {
    size: responseCache.size(),
    maxSize: responseCache.maxSize,
    ttl: responseCache.ttl
  };
});

app.delete('/cache/clear', async (request, reply) => {
  const { responseCache } = await import('./cache.js');
  responseCache.clear();
  return { message: 'Cache cleared' };
});

await registerEmbedRoutes(app, config);
await registerChatRoutes(app, config);
await registerTranscribeRoutes(app, config);

const start = async () => {
  try {
    await app.listen({ port: config.port, host: '0.0.0.0' });
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
};

start();


