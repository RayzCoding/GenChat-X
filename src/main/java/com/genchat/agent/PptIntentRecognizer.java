package com.genchat.agent;

import com.agent.foundation.core.SpringAiChatModelAdapter;
import com.agent.foundation.output.StructuredOutputSupport;
import com.genchat.common.validation.StructuredResults;
import com.genchat.common.prompts.PptBuilderPrompts;
import com.genchat.dto.PptIntent;
import com.genchat.dto.PptIntentResult;
import com.genchat.entity.PptInstStatus;
import com.genchat.service.AiPptInstService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Slf4j
public class PptIntentRecognizer {
    private final ChatModel chatModel;
    private final AiPptInstService pptInstService;

    public PptIntentRecognizer(ChatModel chatModel, AiPptInstService pptInstService) {
        this.chatModel = chatModel;
        this.pptInstService = pptInstService;
    }

    public PptIntentResult recognize(String conversationId, String question) {
        var latestInst = pptInstService.getLatestInst(conversationId);
        if (ObjectUtils.isEmpty(latestInst)) {
            log.info("Conversation non contain ppt instant, defualt create new instant.");
            return new PptIntentResult(PptIntent.CREATE_PPT,
                    "Conversation non contain ppt instant, defualt create new instant.");
        }
        var pptInstStatus = PptInstStatus.fromCode(latestInst.getStatus());
        var errorMsg = latestInst.getErrorMsg();

        // Check if breakpoint reconnection is required
        if (needsResume(pptInstStatus, errorMsg, question)) {
            log.info("Conversation resume needs resume. status: {}, error msg: {}", pptInstStatus, errorMsg);
            return new PptIntentResult(PptIntent.RESUME_PPT,
                    "The last execution was detected as incomplete, Continue from status " + pptInstStatus);
        }
        // If it's in the SUCCESS state, call the LLM for intent recognition (CREATE_PPT or MODIFY_PPT)
        if (pptInstStatus == PptInstStatus.SUCCESS) {
            return recognizeWithLLM(question);
        }
        // For other intermediate states (non-failed), it also defaults to CREATE_PPT (new)
        return new PptIntentResult(PptIntent.CREATE_PPT, "Default Create, status: " + pptInstStatus);
    }

    private PptIntentResult recognizeWithLLM(String question) {
        var recognitionPrompt = PptBuilderPrompts.INTENT_RECOGNITION_PROMPT;
        return StructuredOutputSupport.generate(new SpringAiChatModelAdapter(chatModel),
                java.util.List.of(new SystemMessage(recognitionPrompt), new UserMessage(question)), StructuredResults.intent());
    }

    private boolean needsResume(PptInstStatus pptInstStatus, String errorMsg, String question) {
        if (StringUtils.hasLength(errorMsg)) {
            return true;
        }
        String[] resumeKeywords = {"resume", "retry", "continue", "keep going", "go on"};
        var containResume = Arrays.stream(resumeKeywords)
                .anyMatch(keyword -> question.toLowerCase().contains(keyword));
        if (containResume) {
            return true;
        }
        if (pptInstStatus != PptInstStatus.SUCCESS && pptInstStatus != PptInstStatus.INIT) {
            String[] newKeywords = {"new", "create new", "start over", "redo", "regenerate"};
            var containNew = Arrays.stream(newKeywords)
                    .anyMatch(keyword -> question.toLowerCase().contains(keyword));
            if (!containNew) {
                return true;
            }
        }
        return false;
    }
}
