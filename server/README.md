## JournAI Server Proxy

Minimal Fastify proxy to keep API keys off-device and apply privacy redaction.

### Endpoints
- POST `/embed` — wraps OpenAI embeddings (`text-embedding-3-small`)
- POST `/chat` — wraps chat completions (`gpt-4o-mini` by default)
- GET `/health` — basic status

### Environment
Copy `.env.sample` to `.env` and fill values:
```
PORT=8787
OPENAI_API_KEY=sk-...
OPENAI_EMBED_MODEL=text-embedding-3-small
OPENAI_CHAT_MODEL=gpt-4o-mini
RATE_LIMIT_MAX=60
RATE_LIMIT_TIME_WINDOW=1 minute
```

### Dev
```
npm install
npm run dev
```

### Redaction
Both `/embed` and `/chat` accept an optional `blacklist` array:
```json
{
  "blacklist": [
    { "pattern": "john doe", "replacement": "[person]" },
    { "pattern": "123-45-6789" }
  ]
}
```
Redaction runs before outbound requests; it is case-insensitive and literal.


