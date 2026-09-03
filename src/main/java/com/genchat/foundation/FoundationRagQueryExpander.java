package com.genchat.foundation;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Foundation-side adapter for Spring AI RAG query rewrite/expansion.
 */
@Component
@RequiredArgsConstructor
public class FoundationRagQueryExpander {

    private final ChatModel chatModel;

    public List<Query> expand(String question, int numberOfQueries, boolean includeOriginal) {
        var query = Query.builder().text(question).build();
        var chatClient = ChatClient.builder(chatModel).build();

        var queryTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClient.mutate())
                .build();
        var compressed = queryTransformer.transform(query);

        var queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClient.mutate())
                .numberOfQueries(numberOfQueries)
                .includeOriginal(includeOriginal)
                .build();

        return queryExpander.expand(compressed);
    }
}

