import OpenAI from 'openai';
import { buildRedactor } from '../redact.js';
import { responseCache } from '../cache.js';

export default async function registerChatRoutes(app, config) {
  const client = new OpenAI({ 
    apiKey: config.openai.apiKey,
    httpAgent: new (await import('http')).Agent({
      keepAlive: true,
      maxSockets: 10,
      timeout: 30000
    }),
    httpsAgent: new (await import('https')).Agent({
      keepAlive: true,
      maxSockets: 10,
      timeout: 30000
    }),
    timeout: 30000 // 30 second timeout
  });

  app.post('/chat', {
    schema: {
      body: {
        type: 'object',
        required: ['messages'],
        properties: {
          messages: {
            type: 'array',
            items: {
              type: 'object',
              required: ['role', 'content'],
              properties: {
                role: { type: 'string', enum: ['system', 'user', 'assistant', 'tool'] },
                content: { type: 'string' },
              },
            },
          },
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
          stream: { type: 'boolean' },
          useCache: { type: 'boolean' },
        },
      },
    },
  }, async (req, reply) => {
    const { messages, blacklist = [], stream = false, useCache = true } = req.body;
    const redact = buildRedactor(blacklist);

    const redactedMessages = messages.map((m) => ({
      ...m,
      content: typeof m.content === 'string' ? redact(m.content) : m.content,
    }));

    // Convert chat-style messages to Responses API input content parts
    const responsesInput = redactedMessages.map((m) => {
      const isAssistant = m.role === 'assistant';
      const partType = isAssistant ? 'output_text' : 'input_text';
      return {
        role: m.role,
        content: [
          {
            type: partType,
            text: typeof m.content === 'string' ? m.content : JSON.stringify(m.content),
          },
        ],
      };
    });

    if (stream) {
      // Set up Server-Sent Events headers (following OpenAI format)
      reply.type('text/event-stream');
      reply.header('Cache-Control', 'no-cache');
      reply.header('Connection', 'keep-alive');
      reply.header('Access-Control-Allow-Origin', '*');
      reply.header('Access-Control-Allow-Headers', 'Cache-Control');

      try {
        // Log start of streaming with reasoning options (if any)
        try {
          reply.server.log.info({ reasoning: req.body?.reasoning }, 'responses.stream start');
        } catch (_) {}
        const respStream = await client.responses.create({
          model: config.openai.chatModel,
          reasoning: { effort: 'low' },
          stream: true,
          input: responsesInput
        });

        // Bridge Responses streaming events to Chat Completions-like SSE chunks
        let eventCount = 0;
        let outputDeltaCount = 0;
        let reasoningDeltaCount = 0;
        for await (const event of respStream) {
          eventCount++;
          // Debug-log event types and small previews
          try {
            const preview = typeof event?.delta === 'string' ? event.delta.slice(0, 80) : '';
            reply.server.log.info({ type: event?.type, preview, n: eventCount }, 'responses.stream event');
          } catch (_) {}
          if (!event || !event.type) continue;

          if (event.type === 'response.output_text.delta') {
            outputDeltaCount++;
            const textDelta = event.delta ?? '';
            const chunk = {
              id: event.response?.id ?? undefined,
              object: 'chat.completion.chunk',
              created: Math.floor(Date.now() / 1000),
              model: config.openai.chatModel,
              choices: [
                {
                  index: 0,
                  delta: { content: textDelta },
                  finish_reason: null,
                },
              ],
            };
            reply.raw.write(`data: ${JSON.stringify(chunk)}\n\n`);
            if (typeof reply.raw.flush === 'function') reply.raw.flush();
          } else if (event.type === 'response.completed') {
            // Emit a final stop signal in chat-completions shape
            const finalChunk = {
              id: event.response?.id ?? undefined,
              object: 'chat.completion.chunk',
              created: Math.floor(Date.now() / 1000),
              model: config.openai.chatModel,
              choices: [
                {
                  index: 0,
                  delta: {},
                  finish_reason: 'stop',
                },
              ],
            };
            reply.raw.write(`data: ${JSON.stringify(finalChunk)}\n\n`);
            if (typeof reply.raw.flush === 'function') reply.raw.flush();
          } else if (event.type === 'response.error' || event.type === 'error') {
            const message = event.error?.message || 'Unknown error';
            reply.raw.write(
              `data: ${JSON.stringify({ error: { message, type: 'server_error' } })}\n\n`
            );
            if (typeof reply.raw.flush === 'function') reply.raw.flush();
          } else if (event.type === 'response.reasoning.delta') {
            reasoningDeltaCount++;
            // We don't forward reasoning tokens to the UI, but we log them for debugging
            try {
              const rPreview = typeof event?.delta === 'string' ? event.delta.slice(0, 80) : '';
              reply.server.log.info({ rPreview }, 'responses.stream reasoning delta');
            } catch (_) {}
          }
        }
        try {
          reply.server.log.info({ eventCount, outputDeltaCount, reasoningDeltaCount }, 'responses.stream summary');
        } catch (_) {}

        // Send the [DONE] signal as per OpenAI spec
        reply.raw.write('data: [DONE]\n\n');
        reply.raw.end();
      } catch (error) {
        try {
          reply.server.log.error({ err: error }, 'responses.stream error');
        } catch (_) {}
        // Send error in OpenAI format
        reply.raw.write(`data: ${JSON.stringify({ 
          error: { 
            message: error.message,
            type: 'server_error'
          }
        })}\n\n`);
        reply.raw.end();
      }
    } else {
      // Regular non-streaming response with optional caching
      if (useCache && !stream) {
        const cacheKey = responseCache.generateKey(redactedMessages, config.openai.chatModel);
        const cachedResponse = responseCache.get(cacheKey);
        
        if (cachedResponse) {
          // Add cache hit header
          reply.header('X-Cache', 'HIT');
          return cachedResponse;
        }
      }

      const response = await client.responses.create({
        model: config.openai.chatModel,
        input: responsesInput,
        reasoning: { effort: 'low' },
      });

      // Adapt Responses output to Chat Completions response shape for client compatibility
      const assistantText = response.output_text ?? '';
      const adapted = {
        id: response.id,
        object: 'chat.completion',
        created: Math.floor(Date.now() / 1000),
        model: config.openai.chatModel,
        choices: [
          {
            index: 0,
            message: { role: 'assistant', content: assistantText },
            finish_reason: 'stop',
          },
        ],
      };

      // Cache the response if enabled
      if (useCache && !stream) {
        const cacheKey = responseCache.generateKey(redactedMessages, config.openai.chatModel);
        responseCache.set(cacheKey, adapted);
        reply.header('X-Cache', 'MISS');
      }

      return adapted;
    }
  });
}


