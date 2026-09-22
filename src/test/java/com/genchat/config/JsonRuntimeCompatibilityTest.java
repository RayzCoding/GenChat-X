package com.genchat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonDefaults;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.codec.autoconfigure.CodecsAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.SpringVersion;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRuntimeCompatibilityTest {

    @Test
    void jacksonCodecsAndMcpSchemaValidatorInitializeOnBoot4() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class, CodecsAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(JsonMapper.class);
                    assertThat(SpringBootVersion.getVersion()).startsWith("4.");
                    assertThat(SpringVersion.getVersion()).startsWith("7.");
                    assertThat(context).hasBean("jacksonCodecCustomizer");
                    assertThat(McpJsonDefaults.getSchemaValidator()).isNotNull();

                    String json = "{\"message\":\"ok\"}";
                    assertThat(context.getBean(JsonMapper.class).readTree(json)
                            .get("message").asString()).isEqualTo("ok");
                    assertThat(new ObjectMapper().readTree(json)
                            .get("message").asText()).isEqualTo("ok");
                });
    }
}
