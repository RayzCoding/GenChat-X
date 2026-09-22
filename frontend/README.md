# GenChat-X Frontend

React workbench for all six backend modes: web chat, simple chat, file Q&A, deep research, PPT generation, and Skills. It integrates with `AgentController`, `SessionController`, and file APIs.

The npm package is `genchat-x-frontend`. Requires Node.js 20+; the backend requires JDK 25 and the sibling `agent-foundation` project. See the [project README](../README_EN.md) for backend configuration.

## Local development

```bash
# 1. Start the backend (project root, port 8080)
./gradlew bootRun

# 2. Install dependencies and start the frontend
cd frontend
npm ci
npm run dev
```

Open http://localhost:5173 in your browser. Vite proxies `/agent/*` and `/file/*` to `http://localhost:8080`; `/file-qa` remains a frontend route instead of being proxied.

Available package scripts:

```bash
npm run dev
npm run lint
npm run build
npm run preview
```

## Production build

```bash
cd frontend
npm ci
npm run build
```

Build output is in `frontend/dist/` and can be deployed to Nginx or any static host. The current client uses relative `/agent` and `/file` URLs; configure the production reverse proxy to route those prefixes to the backend. The dev proxy target is fixed in `vite.config.ts`.

## Main APIs

| Endpoint | Description |
|----------|-------------|
| `POST /agent/stream` | Unified SSE entry for `chat`, `simple`, `file`, `deep`, `ppt`, and `skills` |
| `GET /agent/chat/stream` | SSE streaming chat |
| `GET /agent/file/stream` | File Q&A stream |
| `GET /agent/deep/stream` | Deep-research stream |
| `GET /agent/ppt/stream` | PPT workflow stream |
| `GET /agent/skills/stream` | Skills assistant stream |
| `GET /agent/simple/stream` | Single-turn stream |
| `GET /agent/stop` | Stop generation |
| `GET /agent/sessions` | Session list |
| `GET /agent/sessions/search?q=` | Search history |
| `GET /agent/sessions/{conversationId}` | Session detail |
| `/file/**` | Presigned upload, file listing, and deletion APIs |

The unified endpoint accepts `question` (`query` compatibility alias), `conversationId` (`conversationsId` alias), and mode-specific fields such as `fileId`. See the root [English README](../README_EN.md#unified-streaming-endpoint-optional) for the parameter matrix.

## Internationalization

Storage keys are retained for compatibility. Deploy GenChat-X and GenChat on different browser origins if their preferences and file-conversation mappings must remain isolated.

Supports `zh-CN` / `en-US`. Language preference is stored in `localStorage` (key: `genchat-lang`).
