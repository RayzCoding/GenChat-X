package com.genchat.agent.core;

import com.agent.foundation.core.SpringAiChatModelAdapter;
import com.agent.foundation.output.StructuredOutputSupport;
import com.genchat.common.prompts.ReactAgentPrompts;
import com.genchat.common.utils.JacksonJson;
import com.genchat.common.validation.StructuredResults;
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
                    + "Return only a JSON object with a questions array of exactly 3 strings. No extra explanation."));
            var result = StructuredOutputSupport.generate(new SpringAiChatModelAdapter(chatModel), messages,
                    StructuredResults.recommendations());
            return JacksonJson.toJson(result.questions().stream().map(String::strip).toList());
        } catch (Exception e) {
            log.error("Anomaly in generating recommendation questions", e);
            return null;
        }
    }

}
