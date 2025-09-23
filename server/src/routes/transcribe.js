import OpenAI from 'openai';
import { toFile } from 'openai/uploads';

export default async function registerTranscribeRoutes(app, config) {
  const client = new OpenAI({ apiKey: config.openai.apiKey });

  app.post('/transcribe', {
    schema: {
      body: {
        anyOf: [
          {
            type: 'object',
            required: ['audioBase64', 'sampleRate'],
            properties: {
              audioBase64: { type: 'string' },
              sampleRate: { type: 'number' },
              language: { type: 'string' }
            }
          },
          {
            type: 'object',
            required: ['chunks', 'sampleRate'],
            properties: {
              chunks: { type: 'array', items: { type: 'string' } },
              sampleRate: { type: 'number' },
              language: { type: 'string' }
            }
          }
        ]
      }
    }
  }, async (req, reply) => {
    const { audioBase64, chunks, sampleRate, language } = req.body;
    const model = config.openai.transcribeModel || 'gpt-4o-mini-transcribe';

    const transcribeOne = async (base64, index) => {
      const pcm = Buffer.from(base64, 'base64');
      const wav = buildWavFromPcm16le(pcm, sampleRate);
      const file = await toFile(wav, `audio_${index}.wav`, { type: 'audio/wav' });
      const response = await client.audio.transcriptions.create({ file, model, language });
      return response.text ?? '';
    };

    if (Array.isArray(chunks) && chunks.length > 0) {
      const parts = [];
      for (let i = 0; i < chunks.length; i++) {
        const text = await transcribeOne(chunks[i], i);
        parts.push(text);
      }
      return { text: parts.join(' ') };
    } else {
      const text = await transcribeOne(audioBase64, 0);
      return { text };
    }
  });

  function buildWavFromPcm16le(pcmBuffer, sampleRate) {
    const numChannels = 1;
    const bitsPerSample = 16;
    const byteRate = (sampleRate * numChannels * bitsPerSample) / 8;
    const blockAlign = (numChannels * bitsPerSample) / 8;
    const dataSize = pcmBuffer.length;
    const totalSize = 44 + dataSize;

    const header = Buffer.alloc(44);
    header.write('RIFF', 0);               // ChunkID
    header.writeUInt32LE(totalSize - 8, 4); // ChunkSize
    header.write('WAVE', 8);               // Format
    header.write('fmt ', 12);              // Subchunk1ID
    header.writeUInt32LE(16, 16);          // Subchunk1Size (PCM)
    header.writeUInt16LE(1, 20);           // AudioFormat (PCM)
    header.writeUInt16LE(numChannels, 22); // NumChannels
    header.writeUInt32LE(sampleRate, 24);  // SampleRate
    header.writeUInt32LE(byteRate, 28);    // ByteRate
    header.writeUInt16LE(blockAlign, 32);  // BlockAlign
    header.writeUInt16LE(bitsPerSample, 34); // BitsPerSample
    header.write('data', 36);              // Subchunk2ID
    header.writeUInt32LE(dataSize, 40);    // Subchunk2Size

    return Buffer.concat([header, pcmBuffer]);
  }
}


