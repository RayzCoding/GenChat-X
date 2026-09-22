package com.genchat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

@Configuration
@Slf4j
@Getter
public class WebSearchToolInitConfig {

    /**
     * Tavily API Key
     */
    @Value("${tavily.api-key}")
    private String tavilyApiKey;

    /**
     * Tavily MCP URL
     */
    @Value("${tavily.mcp-url}")
    private String tavilyMcpUrl;

    private ToolCallback[] webSearchToolCallbacks = new ToolCallback[0];

    @PostConstruct
    public void init() {
        log.info("Initialize web search tool callback...");
        try {
            // tavily search engine
            String authorizationHeader = "Bearer " + tavilyApiKey;

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .header("Authorization", authorizationHeader);

            HttpClientStreamableHttpTransport tavilyTransport = HttpClientStreamableHttpTransport.builder(tavilyMcpUrl)
                    .requestBuilder(requestBuilder)
                    .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                    .build();
            McpSyncClient tavilyMcp = McpClient.sync(tavilyTransport)
                    .requestTimeout(Duration.ofSeconds(300))
                    .build();
            tavilyMcp.initialize();

            List<McpSyncClient> mcpClients = List.of(tavilyMcp);
            SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder().mcpClients(mcpClients).build();

            webSearchToolCallbacks = provider.getToolCallbacks();
            log.info("Web search tool callback initialization complete, number of tools: {}", webSearchToolCallbacks.length);
        } catch (Throwable e) {
            webSearchToolCallbacks = new ToolCallback[0];
            log.warn("Web search tool initialization failed; continuing without web search tools", e);
        }
    }
}
