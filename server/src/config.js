export function getEnv(key, fallback) {
  const value = process.env[key];
  if (value === undefined || value === '') return fallback;
  return value;
}

export function getConfig() {
  const port = Number(getEnv('PORT', 8787));
  const openaiApiKey = process.env.OPENAI_API_KEY;
  const embedModel = getEnv('OPENAI_EMBED_MODEL', 'text-embedding-3-small');
  const chatModel = getEnv('OPENAI_CHAT_MODEL', 'gpt-5-nano');
  const rateLimitMax = Number(getEnv('RATE_LIMIT_MAX', 60));
  const rateLimitTimeWindow = getEnv('RATE_LIMIT_TIME_WINDOW', '1 minute');
  const authToken = getEnv('PROXY_AUTH_TOKEN', null);

  if (!openaiApiKey) {
    throw new Error('OPENAI_API_KEY is required');
  }

  return {
    port,
    rateLimit: {
      max: rateLimitMax,
      timeWindow: rateLimitTimeWindow,
    },
    auth: {
      token: authToken
    },
    openai: {
      apiKey: openaiApiKey,
      embedModel,
      chatModel,
    },
  };
}


