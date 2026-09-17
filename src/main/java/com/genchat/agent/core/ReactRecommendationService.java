package com.genchat.agent.core;

import com.agent.foundation.model.RunnableParams;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.common.utils.JacksonJson;
import com.genchat.foundation.FoundationReactAgentFactory;
import com.genchat.service.session.SessionPayloadParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class ReactRecommendationService {

    private ReactRecommendationService() {
    }

    public static String generate(ChatModel chatModel,
                                  List<Message> historyMessages,
                                  String currentQuestion,
                                  String currentAnswer) {
        try {
            var messages = new ArrayList<Message>();
            messages.add(new SystemMessage(ReactAgentPrompts.getRecommendPrompt()));
            if (!CollectionUtils.isEmpty(historyMessages)) {
                messages.addAll(historyMessages);
            }
            messages.add(new UserMessage("Current conversation: "));
            messages.add(new UserMessage(currentQuestion));
            if (StringUtils.hasLength(currentAnswer)) {
                messages.add(new AssistantMessage(currentAnswer));
            }

            messages.add(new UserMessage("Please generate exactly 3 recommended questions based on the above dialogue. "
                    + "Return only a valid JSON array of 3 strings. No markdown fences, no schema, no extra explanation."));
            var agent = FoundationReactAgentFactory.create(chatModel, List.of(), 3, 0, false);
            var response = agent.callForResult(messages, RunnableParams.builder().build()).content();
            if (response != null && !response.isEmpty()) {
                List<String> recommendations = SessionPayloadParser.parseRecommend(response);
                if (!CollectionUtils.isEmpty(recommendations)) {
                    var jsonStr = JacksonJson.toJson(recommendations);
                    log.info("The recommendation question has been successfully generated: {}", jsonStr);
                    return jsonStr;
                }
            }
            log.warn("Failed to generate recommendation questions, response format is invalid: {}", response);
            return null;
        } catch (Exception e) {
            log.error("Anomaly in generating recommendation questions", e);
            return null;
        }
    }

}
