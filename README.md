## JournAI — MVP Roadmap & To‑Do

An AI‑powered personal journal with local‑first storage, background indexing, and a private chat interface to navigate memories. This document is the working plan and checklist for the MVP.

### Scope (MVP)
- **Create entry**: Rich text + mood + tags + photos/voice. Stored locally.
- **Background indexer**: Chunk, embed (remote), catalog entries; extract entities/timelines.
- **Chat with your journal**: LLM via server proxy with tools (semantic search, timeline summaries, pattern mining, weekly reviews).
- **Privacy**: All data local on device; outbound text passes through a configurable blacklist/redactor. Optional encrypted sync in v2.

### Tech Stack
- **App**: Kotlin, Jetpack Compose, MVVM, Kotlin Flows
- **Storage**: Room (SQLite) + FTS5 for fast search; media on disk (scoped storage)
- **Crypto**: Android Keystore; SQLCipher for Room (or EncryptedFile + EncryptedSharedPreferences)
- **Background**: WorkManager (constraints: charging + unmetered for heavy runs)
- **Networking**: Retrofit/Ktor + OkHttp
- **Embeddings (MVP)**: Remote `text-embedding-3-small` via server proxy
- **LLM**: Server proxy (API key not in app)
- **Offline v2**: ONNX Runtime Mobile + on-device sentence transformer (MiniLM L6 v2)

---

### Milestones & Checklists

#### 0) Project Bootstrap
- [x] Create Android project (`minSdk` 24+), Kotlin, Compose Material 3
- [x] Add modules/deps: Room, WorkManager, OkHttp/Retrofit (or Ktor), Serialization/Moshi, Coil, Media3 (audio), Kotlin Coroutines/Flows
- [x] Set up DI (Hilt or Koin)
- [x] App architecture skeleton: MVVM + repositories + use-cases
- [x] Baseline theming, typography, and navigation (bottom bar: `Create`, `Chat`)
- [ ] Add logging and crash-safe error reporting (local only; no remote telemetry)

#### 1) Data Model & Local Storage
- [x] Define Room entities:
  - [x] `Entry(id, createdAt, editedAt, title?, richBody, mood, isArchived)`
  - [x] `Tag(id, name)` and `EntryTag(entryId, tagId)`
  - [x] `Media(id, entryId, type=image|audio, uri, metadata)`
  - [x] `Embedding(id, entryId, chunkId, vector, dims, model, createdAt)`
  - [x] `Entity(id, type, name)` and `EntryEntity(entryId, entityId, salience)`
  - [x] `TimelineItem(id, entryId, timestamp, summary)`
- [x] FTS5 virtual table for `Entry.richBody` and triggers to keep in sync
- [x] DAO layer with suspending queries and Flow streams
- [ ] Encryption:
  - [ ] Key management via Android Keystore
  - [ ] Choose: SQLCipher for Room OR keep Room plaintext + store sensitive blobs with `EncryptedFile`
  - [ ] `EncryptedSharedPreferences` for settings/feature flags
- [x] Media handling: persist URIs, request permissions, thumbnails with Coil
- [x] Voice recording: real-time speech-to-text transcription for journal entries (whisper.cpp integration with continuous streaming)
- [ ] Basic migrations and migration tests

#### 2) Create Entry UI
- [x] Screen: Simplified editor with content input, mood picker
- [x] Date as title (one entry per day, auto-loads existing for editing)
- [x] Date picker - tap date to select any date and edit/create entries
- [x] Voice notes (recording via Media3) - button inside text input
- [x] Save/update entry; persist to Room
- [x] "Organise" button → call LLM (via proxy) to rewrite/structure; update draft safely with diff
- [ ] "Chat about this entry" button → opens chat with entry context pinned

#### 3) Background Indexer
- [x] WorkManager task configured with constraints (charging + unmetered for heavy runs)
- [x] Detect new/changed entries; chunk via simple heuristic (paragraph-based ~2k chars)
- [x] For each chunk: request embedding from server proxy; store vector in `Embedding`
- [x] Cosine similarity search (brute force for MVP); verify dims and normalization
- [x] Basic entity extraction (LLM or lightweight rules) → populate `Entity`/`EntryEntity`
- [x] Timeline extraction (date/time expressions) → `TimelineItem`
- [x] Backoff, retries, and last-run markers in `EncryptedSharedPreferences`
  
Notes:
- Embeddings are L2-normalized at index time; searches normalize queries and use dot product.
- Entity extraction is heuristic for MVP; capped to top ~15 unique items per entry.
- Timeline extraction captures up to 5 dates per entry to avoid noise.
- Entity frequency threshold applied (>=2 mentions) to reduce noise.
- Semantic search filters results with a minimum similarity cutoff (>= 0.25).

#### 4) Chat with Your Journal
- [x] Chat UI (Compose): input, streaming messages, retry, copy
- [ ] Chat histories:
  - [x] Overall history (MVP: threads saved, titles generated on first exchange; sidebar placeholder)
  - [ ] Entry-specific history (when launched from an entry)
- [x] Tooling layer exposed to LLM:
  - [x] `semanticSearch(query)` → returns top-k chunks + metadata (MVP)
  - [x] `timelineSummary(range?)` → summarize timeline items (stub)
  - [x] `minePatterns(window?)` → recurring themes/moods/tags (stub)
  - [x] `weeklyReview()` → pull last 7 days summaries and insights (stub)
- [ ] Prompt/response privacy filter runs preflight redaction (see Privacy below)
- [x] Server proxy integration for chat completions (no keys in app)
  
Notes:
- Chats are persisted in Room (`chat_threads`, `chat_messages`); thread title generated after first assistant reply.
- Sidebar toggle (MVP) shows placeholder; to be replaced with threads list sorted by recency.
- ChatService injects tool-derived context (last 7 days timeline, semantic search) for recap-style queries such as "past week".
- ChatService retries transient errors (e.g., 503) with exponential backoff to avoid first-call failures.
- System prompt instructs the model to use provided context and not claim lack of access.
- Agentic planner (app-side): model suggests which local tools to run (JSON plan), app executes tools and injects results before final response.
  - Multi-hop: planner may iterate up to 2 times, proposing additional tool calls based on newly gathered context; entries are deduped.
  
Logging:
- App: ChatService, AgentOrchestrator, and ChatTools log query, tool planning, context size, retries, and response length.
- Server: `/chat` logs request metadata, last user preview, and response length; adds `X-Trace`, `X-Model`, and `X-Content-Length` headers for tracing.
  
Fallbacks:
- If embeddings are not available yet, semantic search falls back to lexical search over entries.
- If no timeline items exist, weekly summary falls back to recent entries.
- Tool context is placed before the user message to improve grounding.
  
Date-aware tool usage:
- DateRangeParser extracts ranges (e.g., "2024", "last year", "past 30 days").
- Chat injects a focus range system message and uses range-based timeline summaries.
 - entriesSummaryRange lists entries within the range when timeline is empty.
  
Semantic search behavior:
- Vectors are normalized; dot product used for speed.
- If no vectors for the current model exist, search falls back to any vectors available.
- Low similarity threshold (>= 0.05) to favor recall for chat context.
- Lexical fallback used when vector search returns no results.

#### 5) Privacy & Security (MVP)
- [ ] All data local; no analytics/telemetry
- [ ] Blacklist/obfuscation settings UI: user can add terms and replacements
- [ ] Redaction pipeline:
  - [ ] Build a compiled matcher (e.g., Aho‑Corasick or regex) from blacklist
  - [ ] Replace before sending to LLM or embeddings endpoint
  - [ ] Show preview of redacted text when submitting
- [ ] Network hardening: timeouts, retries, error surfaces
- [ ] Secure storage for settings/keys using `EncryptedSharedPreferences`

#### 6) Server Proxy (Minimal)
- [x] Implement Node.js service (Express/Fastify) to avoid keys on device
- [x] Endpoints:
  - [x] `POST /embed` → wraps OpenAI `text-embedding-3-small`
  - [x] `POST /chat` → wraps chat completions; supports tool calls
- [x] Apply the same blacklist redaction server-side defensively (idempotent)
- [x] Rate limiting and basic auth/token per device (Bearer token)
- [x] Configurable base URL in app (dev/prod)

Hardening for production (to implement):
- [ ] Device attestation & registration
  - [ ] Server: `/register` endpoint verifies Google Play Integrity (or SafetyNet) for package name + signing cert digest
  - [ ] Server: issues short‑lived JWT (aud=prod, sub=deviceId, exp≈7d) after successful attestation
  - [ ] Server: stores device record, supports revocation
  - [ ] App: requests attestation, calls `/register`, stores token in `EncryptedSharedPreferences`
- [ ] Proof of possession (DPoP)
  - [ ] App: generate device keypair (Android Keystore; StrongBox if available); send public key at registration
  - [ ] App: sign nonce per request (header) with private key
  - [ ] Server: verify DPoP signature against stored public key
- [ ] Per-device rate limits & abuse controls
  - [ ] Rate limit by deviceId from JWT; stricter burst/window
  - [ ] Admin: revoke device tokens and view device activity
- [ ] TLS and pinning
  - [ ] Enforce HTTPS/TLS 1.2+ on proxy; HSTS in fronting CDN
  - [ ] App: certificate pinning in OkHttp
- [ ] Token refresh and retry
  - [ ] App: on 401, refresh by re‑running attestation and `/register`
  - [ ] Server: rotate signing keys, support key ID (kid)

#### 7) Offline Embeddings (v2)
- [ ] Integrate ONNX Runtime Mobile
- [ ] Download and validate local model (MiniLM L6 v2) with checksum
- [ ] Compute embeddings on-device; fall back to remote when constrained
- [ ] Verify cosine similarity parity within tolerance

#### 8) Optional Sync (v2)
- [ ] E2E encrypted backup + multi-device sync
- [ ] Key derivation (passphrase → KDF), per-device subkeys
- [ ] Encrypted blob store (e.g., object storage); metadata minimization
- [ ] Conflict resolution strategy (CRDT or last-write-wins for MVP)

#### 9) QA, Performance, and Release Prep
- [ ] Seed data/dev tools; test data reset
- [ ] Cold/warm start profiling; WorkManager battery impact review
- [ ] Large DB testing (e.g., 5k entries) search latency budget
- [ ] Accessibility review (TalkBack, contrast); offline/airplane mode behavior
- [ ] Play Console artifacts, signing, privacy policy draft

---

### Data Model Sketch
```
Entry
  id (PK)
  createdAt, editedAt
  title?
  richBody (FTS5 indexed)
  mood (enum/int)
  isArchived (bool)

Tag (PK: id), EntryTag (entryId+tagId)
Media (id, entryId, type, uri, metadata)

Embedding (id, entryId, chunkId, vector BLOB, dims, model, createdAt)
Entity (id, type, name), EntryEntity (entryId, entityId, salience)
TimelineItem (id, entryId, timestamp, summary)
```

FTS5: maintain a contentless FTS table or external content table tied to `Entry`; keep triggers to sync on insert/update/delete.

### Indexing & Search Notes
- Chunking target: ~300–500 tokens per chunk; keep sentence boundaries when possible
- Store normalized vectors (L2) or compute cosine via dot / (||a||·||b||)
- Brute force top‑k is acceptable for MVP; paginate if needed
- Combine lexical (FTS) and vector scores for hybrid ranking later

### Privacy Redaction
- Blacklist is a list of patterns with optional replacement strings
- Apply on outbound text to `/embed` and `/chat`; log nothing sensitive
- Provide a “view redacted text” toggle before send

### UI Structure
- Bottom bar: `Create`, `Entries`, `Chat`
- `Create`:
  - Simplified daily entry editor: content input, mood picker, voice button in input
  - Tappable date header opens date picker to navigate to any date
  - Auto-loads existing entry for selected date (edit mode) or creates new entry
  - Date serves as title, one entry per day
- `Entries`:
  - List of saved entries with search and mood filtering
  - Entry preview with date/time, content, and mood
- `Chat`:
  - Global history and entry-specific threads

---

### Open Questions / Decisions
- Room encryption: SQLCipher integration vs. plaintext DB + encrypted blobs only
- Rich text representation: Markdown vs. HTML vs. annotated text + spans
- Server proxy choice: Express vs. Fastify; response streaming
- Tool interface shape for LLM: JSON schema for tool calls

---

### Getting Started (Dev)
- Android Studio Ladybug+ recommended
- Set `local.properties` or in‑app debug settings for server proxy URL
- Use a separate non‑prod OpenAI project/key on the proxy
 - See `server/README.md` for proxy setup and environment

---

### Progress Log
- [x] 0) Bootstrap — Android project created with Compose, Hilt, Room, navigation
- [x] 1) Data & Storage — Room entities, DAOs, FTS5 search, and database setup complete
- [x] 2) Create UI — Functional screens with ViewModels, repositories, and data persistence
- [x] 2.1) Voice Recording — whisper.cpp integration with continuous streaming transcription, audio visualizer, and production-ready speech-to-text for journal entries
- [ ] 3) Indexer
- [x] 4) Chat — Basic chat UI with placeholder AI responses
- [ ] 5) Privacy
- [x] 6) Proxy — server running with `/health`, `/embed`, `/chat`, redaction, rate limiting, caching
- [ ] 7) Offline Embeddings (v2)
- [ ] 8) Sync (v2)
- [ ] 9) QA & Release

Notes:
- Rate limiting is enabled; basic auth/token pending.
- Android app now has simplified UX: daily entries with date as title, mood tracking, and search.
- FTS5 full-text search implemented with triggers for automatic sync.
- One entry per day system with date picker navigation - tap date to jump to any date.
- Voice recording fully implemented with whisper.cpp: continuous streaming transcription, real-time audio visualizer, and production-ready speech-to-text integration.
- Next: background indexing and "chat about this entry" integration.


