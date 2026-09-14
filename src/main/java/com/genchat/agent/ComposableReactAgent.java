package com.genchat.agent;

import com.genchat.agent.core.AbstractReactAgent;
import com.genchat.agent.core.ReactStreamRequest;
import com.genchat.agent.model.AgentState;
import com.genchat.common.AgentStreamEvent;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.common.utils.JacksonJson;
import com.genchat.dto.AiChatSession;
import com.genchat.service.AgentTaskService;
import com.genchat.service.AiChatSessionService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Arrays;
import java.util.List;

/**
 * Configurable React agent used by chat/file/skills modes.
 */
public class ComposableReactAgent extends AbstractReactAgent {

    public enum Mode {
        CHAT,
        FILE,
        SKILLS
    }

    private final Mode mode;

    public ComposableReactAgent(Mode mode,
                                ChatModel chatModel,
                                AiChatSessionService sessionService,
                                AgentTaskService agentTaskService,
                                ToolCallback[] toolCallbacks,
                                int maxRounds) {
        super(chatModel, sessionService, agentTaskService, Arrays.asList(toolCallbacks));
        this.mode = mode;
        this.maxRounds = maxRounds;
    }

    @Override
    protected String getAgentType() {
        return switch (mode) {
            case CHAT -> "webSearchReactAgent";
            case FILE -> "fileReactAgent";
            case SKILLS -> "skillsReactAgent";
        };
    }

    @Override
    protected String getPrimarySystemPrompt() {
        return switch (mode) {
            case CHAT -> ReactAgentPrompts.getWebSearchPrompt();
            case FILE -> ReactAgentPrompts.getFilePrompt();
            case SKILLS -> ReactAgentPrompts.getSkillsPrompt();
        };
    }

    @Override
    protected String getHistoryLabel() {
        if (mode == Mode.FILE) {
            return "Conversation history:";
        }
        return super.getHistoryLabel();
    }

    @Override
    protected AiChatSession buildSessionForSave(ReactStreamRequest request) {
        String fileId = switch (mode) {
            case CHAT -> null;
            case FILE -> request.fileId();
            case SKILLS -> resolveFileId(request);
        };
        return AiChatSession.builder()
                .question(request.question())
                .sessionId(request.conversationId())
                .fileid(fileId)
                .build();
    }

    @Override
    protected void appendExtraUserMessages(List<Message> messages, ReactStreamRequest request) {
        String fileId = switch (mode) {
            case CHAT -> null;
            case FILE -> request.fileId();
            case SKILLS -> resolveFileId(request);
        };
        if (StringUtils.hasLength(fileId)) {
            messages.add(new UserMessage("<fileid>" + fileId + "</fileid>"));
        }
    }

    @Override
    protected void emitToolThinking(String toolName, String argsJson, Sinks.Many<String> sink) {
        if (mode == Mode.CHAT && toolName.contains("search")) {
            var args = JacksonJson.parseTreeLenient(argsJson);
            var query = args != null ? args.path("query").asText(null) : null;
            var queryThink = StringUtils.hasLength(query)
                    ? "🔍 Searching for information: " + query + "\n"
                    : "🔍 Searching for related information\n";
            sink.tryEmitNext(new AgentStreamEvent.Thinking(queryThink).toJSON());
            return;
        }
        if ((mode == Mode.FILE || mode == Mode.SKILLS)
                && (toolName.contains("loadFileContents") || toolName.contains("loadContent"))) {
            String thinking = mode == Mode.FILE
                    ? "📖 Retrieving the contents of the file, please wait... "
                    : "📖 Loading file contents, please wait...\n";
            sink.tryEmitNext(new AgentStreamEvent.Thinking(thinking).toJSON());
        }
    }

    @Override
    protected AgentState createAgentState() {
        if (mode == Mode.SKILLS) {
            return null;
        }
        return super.createAgentState();
    }

    @Override
    protected void onNonToolFinish(String finalText,
                                   Sinks.Many<String> sink,
                                   String conversationId,
                                   AgentState agentState) {
        if (mode == Mode.SKILLS) {
            return;
        }
        super.onNonToolFinish(finalText, sink, conversationId, agentState);
    }

    @Override
    protected void onForceFinalComplete(String finalText,
                                        Sinks.Many<String> sink,
                                        String conversationId,
                                        AgentState agentState) {
        if (mode == Mode.SKILLS) {
            return;
        }
        super.onForceFinalComplete(finalText, sink, conversationId, agentState);
    }

    @Override
    protected String getForceFinalSystemPrompt() {
        if (mode == Mode.SKILLS) {
            return ReactAgentPrompts.getFilePrompt();
        }
        return super.getForceFinalSystemPrompt();
    }

    public Flux<String> stream(String conversationId, String question) {
        return streamInternal(ReactStreamRequest.of(conversationId, question));
    }

    public Flux<String> stream(String conversationId, String question, String fileId) {
        return streamInternal(ReactStreamRequest.withFile(conversationId, question, fileId));
    }

    private String resolveFileId(ReactStreamRequest request) {
        if (StringUtils.hasLength(request.fileId())) {
            return request.fileId();
        }
        return sessionService.findLatestFileIdBySessionId(request.conversationId()).orElse(null);
    }
}
