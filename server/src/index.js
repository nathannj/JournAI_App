import 'dotenv/config';
import Fastify from 'fastify';
import cors from '@fastify/cors';
import rateLimit from '@fastify/rate-limit';
import { jwtVerify, importJWK } from 'jose';
import staticFiles from '@fastify/static';
import compress from '@fastify/compress';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { getConfig } from './config.js';
import registerEmbedRoutes from './routes/embed.js';
import registerChatRoutes from './routes/chat.js';
import registerTranscribeRoutes from './routes/transcribe.js';
import registerRegisterRoute from './routes/register.js';
import registerDeviceAdminRoutes from './routes/devices.js';

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

// Basic auth via bearer token (per-device token recommended)
app.addHook('onRequest', async (request, reply) => {
  const open = request.url.startsWith('/health') || request.url.startsWith('/register') || request.url.startsWith('/cache')
  if (open) return
  if (config.auth?.devAllowNoAuth) return
  const auth = request.headers['authorization'] || ''
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : null
  if (!token) { reply.code(401); throw new Error('Missing token') }
  const secret = new TextEncoder().encode(process.env.JWT_SIGNING_SECRET || 'dev-secret-change')
  try {
    const { payload } = await jwtVerify(token, secret)
    request.deviceId = payload.deviceId
  } catch (e) {
    reply.code(401); throw new Error('Invalid token')
  }
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
  keyGenerator: async (req) => req.deviceId || req.ip
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

const state = {};
await registerRegisterRoute(app, config, state);
await registerDeviceAdminRoutes(app, config, state);
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


