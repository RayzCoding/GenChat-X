package com.genchat.config;

import com.genchat.application.agent.AgentFacade;
import com.genchat.common.AgentStreamEvent;
import com.genchat.controller.AgentController;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.servlet.autoconfigure.HttpEncodingAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SseEncodingTest {

    @ParameterizedTest
    @ValueSource(strings = {"text/event-stream", "*/*"})
    void agentSseUsesUtf8InBothHeadersAndBytes(String accept) throws Exception {
        var facade = mock(AgentFacade.class);
        String text = "📄 文件内容总结（Staffing团队介绍 —— Inspire主题色更新版）";
        String event = new AgentStreamEvent.Text(text).toJSON();
        when(facade.chatStream("test", "hello")).thenReturn(Flux.just(event));
        var properties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class,
                        HttpEncodingAutoConfiguration.class))
                .withInitializer(context -> properties.forEach(context.getEnvironment().getPropertySources()::addLast))
                .withBean(AgentController.class, () -> new AgentController(facade))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var encodingFilter = context.getBean(CharacterEncodingFilter.class);
                    var mvc = MockMvcBuilders.webAppContextSetup(context)
                            .addFilters(encodingFilter, new SseStreamHeaderFilter()).build();

                    var pending = mvc.perform(get("/agent/chat/stream")
                                    .param("conversationId", "test").param("question", "hello")
                                    .accept(accept))
                            .andExpect(request().asyncStarted()).andReturn();
                    var response = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                            .andReturn().getResponse();

                    assertThat(response.getContentAsByteArray()).isEqualTo(("data:" + event + "\n\n")
                            .getBytes(StandardCharsets.UTF_8));
                    assertThat(MediaType.parseMediaType(response.getContentType()).getCharset())
                            .isEqualTo(StandardCharsets.UTF_8);
                    assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
                    assertThat(encodingFilter.isForceResponseEncoding()).isTrue();
                });
    }
}
