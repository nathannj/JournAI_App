import OpenAI from 'openai';
import { buildRedactor } from '../redact.js';

export default async function registerEmbedRoutes(app, config) {
  const client = new OpenAI({ apiKey: config.openai.apiKey });

  app.post('/embed', {
    schema: {
      body: {
        type: 'object',
        required: ['input'],
        properties: {
          input: { anyOf: [{ type: 'string' }, { type: 'array', items: { type: 'string' } }] },
          blacklist: {
            type: 'array',
            items: {
              type: 'object',
              required: ['pattern'],
              properties: {
                pattern: { type: 'string' },
                replacement: { type: 'string' },
              },
            },
          },
        },
      },
    },
  }, async (req, reply) => {
    const { input, blacklist = [] } = req.body;
    const redact = buildRedactor(blacklist);

    const arr = Array.isArray(input) ? input : [input];
    const redacted = arr.map((t) => redact(t));

    const response = await client.embeddings.create({
      model: config.openai.embedModel,
      input: redacted,
    });

    return { model: response.model, data: response.data };
  });
}


