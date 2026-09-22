package com.genchat.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenChatPropertiesTest {

    @Test
    void defaultValuesMatchCurrentDefaults() {
        var props = new GenChatProperties();

        assertEquals(1, props.getAgent().getMaxRounds());
        assertEquals(0, props.getAgent().getMaxRetries());
        assertEquals(30, props.getAgent().getChatMemorySize());
        assertEquals(500, props.getFile().getChunkSize());
        assertEquals(50, props.getFile().getChunkOverlap());
        assertEquals(1, props.getDeepResearch().getMaxRounds());
        assertEquals(15, props.getDeepResearch().getTaskMaxRounds());
        assertEquals(1, props.getDeepResearch().getToolSemaphorePermits());
        assertEquals(60000, props.getContext().getTokenThreshold());
        assertEquals(4, props.getContext().getKeepRecentTools());
        assertEquals(200, props.getContext().getMaxToolLength());
        assertEquals(10, props.getContext().getTruncationKeepMessages());
    }

    @Test
    void bindsTaskBudgetIndependentlyOfResearchIterations() {
        var source = new MapConfigurationPropertySource(Map.of("genchat.deep-research.task-max-rounds", "20"));
        var props = new Binder(source).bind("genchat", GenChatProperties.class).get();

        assertEquals(20, props.getDeepResearch().getTaskMaxRounds());
        assertEquals(1, props.getDeepResearch().getMaxRounds());
    }

    @Test
    void rejectsBudgetsWithoutRoomForSearchAndAnswer() {
        var props = new GenChatProperties.DeepResearch();
        for (int rounds : new int[]{-1, 0, 1}) {
            assertThrows(IllegalArgumentException.class, () -> props.setTaskMaxRounds(rounds));
        }
        props.setTaskMaxRounds(2);
        assertEquals(2, props.getTaskMaxRounds());
    }
}
