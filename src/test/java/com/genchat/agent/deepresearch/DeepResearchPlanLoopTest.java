package com.genchat.agent.deepresearch;

import org.junit.jupiter.api.Test;
import reactor.core.Disposables;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DeepResearchPlanLoopTest {

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

