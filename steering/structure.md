# GenChat-X — Project Structure

Independent project root: `GenChat-X/`. The sibling `../agent-foundation` is included as a Gradle composite build, not embedded in this repository. Base package: `com.genchat` (retained for compatibility).

The tree below includes historical architecture references; consult the actual source for current implementations and `refactor/` for the original migration plan.

```
src/main/java/com/genchat/
├── GenChatApplication.java
├── agent/                         # Agent implementations
│   ├── core/                      # Application-side ReAct abstractions and compatibility code
│   ├── model/                     # Agent domain models (AgentState, RoundState, …)
│   ├── deepresearch/              # Plan-Execute deep research pipeline
│   │   └── model/                 # Deep-research state (OverAllState)
│   ├── WebSearchReactAgent.java
│   ├── FileReactAgent.java
│   ├── SkillsReactAgent.java
│   ├── DeepResearchAgent.java
│   ├── PPTBuilderAgent.java
│   └── SimpleReactAgent.java
├── application/
│   ├── agent/                     # AgentFacade, AgentFactory, PersistentChatAgent
│   ├── stream/                    # AgentStreamLifecycle, StreamChunkAccumulator, …
│   ├── validation/                # StreamRequestValidator (SSE param checks)
│   ├── strategy/                  # PPT state machine strategies
│   ├── FileApplication.java
│   └── tool/                      # SkillsTool, GrepTool, FileContentTool
│       └── grep/                  # Grep executors (ripgrep / Java fallback)
├── common/
│   ├── AgentStreamEvent.java      # SSE event protocol
│   ├── Result.java                # Unified REST wrapper
│   ├── error/                     # Error contracts and exception mapping
│   ├── validation/                # Shared validation helpers
│   ├── utils/JacksonJson.java     # Unified JSON helper
│   └── prompts/                   # PromptLoader + thin Java wrappers
├── config/                        # Spring configuration + GenChatProperties
├── context/                       # Context compaction and token budget controls
├── controller/                    # REST + SSE endpoints
├── converter/                     # MapStruct converters
├── dto/                           # API/session transport objects
├── entity/                        # Persistence entities
├── foundation/                    # Foundation adapters, factories, RAG expansion, multimodal bridge
├── service/                       # Business services
│   └── session/                   # SessionPayloadParser (reference / recommend)
└── repository/                    # MyBatis-Plus repositories

src/main/resources/
├── prompts/                       # Externalized markdown prompt templates
├── python/render_ppt.py           # PPT rendering script
├── db/migration/                  # Flyway SQL migrations
└── application.yml                # Base config (secrets via env vars)
```

## API Surface

| Prefix | Controller | Notes |
|--------|------------|-------|
| `/agent/**` | AgentController | Unified `POST /agent/stream`, six compatibility streams, and `/agent/stop` |
| `/agent/sessions/**` | SessionController | Session list/search/detail/delete + `/by-file/{fileId}`; returns `Result<T>` |
| `/file/**` | FileController | Presign URL + upload/list/delete files |

## Agent Modes

1. Web search (`/agent/chat/stream`)
2. File Q&A (`/agent/file/stream`)
3. PPT generation (`/agent/ppt/stream`)
4. Deep research (`/agent/deep/stream`)
5. Skills assistant (`/agent/skills/stream`)
6. Simple ReAct (`/agent/simple/stream`)

Parameter naming note: the unified endpoint prefers `question` and `conversationId`, while accepting `query` and `conversationsId` as compatibility aliases. The mode-specific deep/PPT/Skills routes also accept the singular `conversationId`; new clients should use singular names consistently.

## Frontend

React + Vite app under `frontend/`. In dev, `/agent` and `/file` proxy to `http://localhost:8080`.
