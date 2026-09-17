package com.genchat.agent;

import com.genchat.agent.core.ReactToolSupport;
import com.agent.foundation.model.RunnableParams;
import com.genchat.common.prompts.PlanExecutePrompts;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.context.ContextCompactor;
import com.genchat.dto.SimpleReactResult;
import com.genchat.agent.model.AgentState;
import com.genchat.foundation.FoundationReactAgentFactory;
import com.genchat.foundation.FoundationToolPayloads;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * It does not contain any conversational memory and only answers the current question
 */
@Slf4j
@Setter
public class SimpleReactAgent {
    private final ChatModel chatModel;
    private final List<ToolCallback> tools;
    private String systemPrompt;
    private int maxRounds;
    protected Set<String> usedTools;
    private ContextCompactor contextCompactor;

    public SimpleReactAgent(ChatModel chatModel,
                            List<ToolCallback> webSearchToolCallbacks) {
        this.systemPrompt = "";
        this.tools = webSearchToolCallbacks;
        this.chatModel = chatModel;
        this.usedTools = new HashSet<>();
    }

    public Flux<String> stream(String question) {
        var foundationMessages = buildFoundationMessages(question);
        var foundationAgent = FoundationReactAgentFactory.create(
                chatModel,
                tools,
                maxRounds,
                0,
                contextCompactor != null);
        var hasTextDelta = new AtomicBoolean(false);

        return foundationAgent
                .streamForEvents(foundationMessages, RunnableParams.builder().build())
                .flatMap(event -> switch (event.type()) {
                    case TEXT_DELTA -> {
                        hasTextDelta.set(true);
                        yield Flux.just(event.content());
                    }
                    case COMPLETED -> hasTextDelta.get() ? Flux.empty() : Flux.just(event.content());
                    case REASONING_CONTENT, RETRY, PAUSED -> Flux.just(event.content() + "\n");
                    case REASONING_DELTA -> Flux.just(event.content());
                    case ERROR -> Flux.error(new IllegalStateException(event.content()));
                    default -> Flux.empty();
                })
                .filter(StringUtils::hasLength);
    }

    public SimpleReactResult executeInternal(String conversationId, String question, boolean withReference) {
        AgentState agentState = withReference ? new AgentState() : null;
        var foundationAgent = FoundationReactAgentFactory.create(
                chatModel,
                tools,
                maxRounds,
                0,
                contextCompactor != null);
        var foundationMessages = buildFoundationMessages(question);

        String answer = foundationAgent
                .streamForEvents(foundationMessages, RunnableParams.builder().conversationId(conversationId).build())
                .doOnNext(event -> {
                    if (agentState != null && event.type() == com.agent.foundation.model.AgentStreamEvent.Type.TOOL_EXECUTED) {
                        var payload = FoundationToolPayloads.decode(event.content());
                        ReactToolSupport.parseTavilySearchResult(payload.result(), agentState);
                    }
                })
                .filter(event -> event.type() == com.agent.foundation.model.AgentStreamEvent.Type.COMPLETED)
                .map(com.agent.foundation.model.AgentStreamEvent::content)
                .last("")
                .block();

        return SimpleReactResult.builder()
                .answer(answer)
                .searchResults(agentState != null ? agentState.searchResults : Collections.emptyList())
                .build();
    }

    private List<Message> buildFoundationMessages(String question) {
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage(PlanExecutePrompts.getCurrentTime()));
        messages.add(new SystemMessage(ReactAgentPrompts.getReactAgentSystemPrompt()));
        if (StringUtils.hasLength(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage("<question>" + question + "</question>"));
        return messages;
    }
}
