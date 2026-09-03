package com.genchat.foundation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

/**
 * Foundation-side adapter for multimodal image-to-text extraction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FoundationMultimodalInterpreter {

    private final OpenAiChatModel multimodalChatModel;

    public String describeImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new RuntimeException("The image file content is empty.");
        }

        try {
            ByteArrayResource imageResource = new ByteArrayResource(imageBytes);
            var userMessage = UserMessage.builder()
                    .text("Please describe the content of this image, including scenes, objects, layouts, colors, and text information, and output the plain text description directly, without unnecessary explanations.")
                    .media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageResource)))
                    .build();
            var response = multimodalChatModel.call(new Prompt(List.of(userMessage)));
            String resp = response.getResult().getOutput().getText();
            if (resp == null || resp.trim().isEmpty()) {
                return "[The image content is not recognized]";
            }
            return resp.trim();
        } catch (Exception e) {
            log.error("Image recognition is abnormal", e);
            throw new RuntimeException("Image recognition failed: " + e.getMessage(), e);
        }
    }
}

