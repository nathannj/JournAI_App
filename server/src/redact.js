// Cache for compiled redactors to avoid rebuilding regex
const redactorCache = new Map();

/**
 * Build a redactor from a list of { pattern: string, replacement?: string }.
 * Patterns are treated as case-insensitive literals by default.
 * Uses caching to avoid rebuilding regex for same patterns.
 */
export function buildRedactor(blacklist) {
  // Create cache key from blacklist
  const cacheKey = JSON.stringify(blacklist);
  
  if (redactorCache.has(cacheKey)) {
    return redactorCache.get(cacheKey);
  }

  const items = (blacklist ?? []).map((b) => ({
    pattern: b.pattern ?? '',
    replacement: b.replacement ?? '█',
  })).filter((b) => b.pattern.length > 0);

  if (items.length === 0) {
    const noopRedactor = (text) => text;
    redactorCache.set(cacheKey, noopRedactor);
    return noopRedactor;
  }

  // Build one big regex with alternatives; escape literals
  const escaped = items.map((b) => escapeRegex(b.pattern));
  const re = new RegExp(`(${escaped.join('|')})`, 'gi');

  // Pre-compile replacements for faster lookup
  const replacementMap = new Map();
  items.forEach((item) => {
    replacementMap.set(item.pattern.toLowerCase(), item.replacement);
  });

  const redactor = (text) => {
    return text.replace(re, (match) => {
      const found = replacementMap.get(match.toLowerCase());
      return found || '█';
    });
  };

  // Cache the redactor
  redactorCache.set(cacheKey, redactor);
  
  // Limit cache size
  if (redactorCache.size > 100) {
    const firstKey = redactorCache.keys().next().value;
    redactorCache.delete(firstKey);
  }

  return redactor;
}

function escapeRegex(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}


