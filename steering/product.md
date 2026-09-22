# GenChat-X — Product Overview

GenChat-X is an independent intelligent conversation platform extracted from GenChat's `refactor/agent-foundation` branch, built on Spring AI and the separate `agent-foundation` framework. It provides agent capabilities across web-connected search, file analysis, document generation, deep research, skills assistance, and simple ReAct chat.

The current codebase implements multiple streaming agent modes, including web search, file Q&A, PPT generation, deep research, skills assistant, and simple ReAct. These agents:
- Accept user questions through unified `POST /agent/stream` or mode-specific compatibility SSE endpoints
- Use Tavily MCP integration for web search when needed
- Return mode-specific streaming events; chat-oriented modes can include thinking, text, references, and recommended questions
- Persist conversation sessions to MySQL for chat memory and session retrieval
- Use agent-foundation JSON Schema validation, typed conversion, business checks, and bounded repair for PPT, deep-research, and recommendation contracts

The platform targets enterprise users and individual developers who need an out-of-the-box AI agent solution with session management, tool calling, and streaming output.
