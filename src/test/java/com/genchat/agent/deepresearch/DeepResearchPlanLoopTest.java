package com.genchat.agent.deepresearch;

import com.genchat.agent.deepresearch.model.OverAllState;
import com.genchat.dto.CritiqueResult;
import com.genchat.dto.PlanTask;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.Disposables;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DeepResearchPlanLoopTest {

    @ParameterizedTest
    @CsvSource({"true,1", "false,2"})
    void passingCritiqueStopsIterationsButStillProducesReport(boolean firstPasses, int expectedRounds) {
        var deps = mock(DeepResearchDependencies.class);
        when(deps.maxRounds()).thenReturn(3);
        var planner = mock(DeepResearchPlanner.class);
        var executor = mock(DeepResearchTaskExecutor.class);
        var critic = mock(DeepResearchCritic.class);
        var reporter = mock(DeepResearchReporter.class);
        when(planner.generatePlan(any(), any(), any(), any())).thenReturn(List.of(new PlanTask("a", "search", 1)));
        when(executor.executePlan(any(), anyList(), any(), any(), any())).thenReturn(Map.of());
        when(critic.critique(any(), any(), anyList(), anyMap(), any(), any()))
                .thenReturn(new CritiqueResult(firstPasses, "need evidence"), new CritiqueResult(true, ""));
        var ctx = new DeepResearchRunContext(deps);
        ctx.setCompositeDisposable(Disposables.composite());
        var state = new OverAllState("conversation", "question");
        var finished = new AtomicBoolean();
        new DeepResearchPlanLoop(deps, planner, executor, critic, reporter).executeLoop(ctx, state,
                Sinks.many().unicast().onBackpressureBuffer(), finished, new StringBuilder()).block();
        assertEquals(expectedRounds, state.getRound());
        assertFalse(finished.get(), "Report delivery must remain enabled");
        verify(planner, times(expectedRounds)).generatePlan(any(), any(), any(), any());
        verify(reporter, times(expectedRounds - 1)).compressIfNeeded(any(), any(), any(), any());
        verify(reporter).summarizeStream(any(), any(), any(), any(), any());
    }

    @Test
    void cancelledLoopDoesNotSummarize() {
        var deps = mock(DeepResearchDependencies.class);
        var reporter = mock(DeepResearchReporter.class);
        var ctx = new DeepResearchRunContext(deps);
        ctx.setCompositeDisposable(Disposables.composite());
        ctx.getCompositeDisposable().dispose();
        new DeepResearchPlanLoop(deps, mock(DeepResearchPlanner.class), mock(DeepResearchTaskExecutor.class),
                mock(DeepResearchCritic.class), reporter).executeLoop(ctx, new OverAllState("c", "q"),
                Sinks.many().unicast().onBackpressureBuffer(), new AtomicBoolean(), new StringBuilder()).block();
        verifyNoInteractions(reporter);
    }

    @Test
    void isUserStopReturnsTrueWhenCompositeDisposed() {
        var ctx = new DeepResearchRunContext(mock(DeepResearchDependencies.class));
        var composite = Disposables.composite();
        composite.dispose();
        ctx.setCompositeDisposable(composite);

        assertTrue(DeepResearchPlanLoop.isUserStop(ctx, new RuntimeException("boom")));
    }

    @Test
    void isUserStopReturnsFalseForNormalExceptions() {
        var ctx = new DeepResearchRunContext(mock(DeepResearchDependencies.class));
        ctx.setCompositeDisposable(Disposables.composite());

        assertFalse(DeepResearchPlanLoop.isUserStop(ctx, new RuntimeException("network timeout")));
    }

    @Test
    void isUserStopReturnsTrueForInterruptedMessage() {
        var ctx = new DeepResearchRunContext(mock(DeepResearchDependencies.class));
        ctx.setCompositeDisposable(Disposables.composite());

        assertTrue(DeepResearchPlanLoop.isUserStop(ctx, new RuntimeException("operation interrupted by user")));
    }
}

