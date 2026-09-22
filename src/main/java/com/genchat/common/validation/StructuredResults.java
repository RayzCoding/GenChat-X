package com.genchat.common.validation;

import com.agent.foundation.output.StructuredOutputSpec;
import com.genchat.common.TemplateSelectionResult;
import com.genchat.dto.CritiqueResult;
import com.genchat.dto.PlanTask;
import com.genchat.dto.PptIntent;
import com.genchat.dto.PptIntentResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Business contracts for model-generated records, separate from historical payload parsing. */
public final class StructuredResults {
    private StructuredResults() { }

    // Object roots also work with OpenAI native structured output (unlike a top-level array).
    public record ResearchPlan(List<PlanTask> tasks) { }
    public record Recommendations(List<String> questions) { }

    public static StructuredOutputSpec<ResearchPlan> plan() {
        return StructuredOutputSpec.of(ResearchPlan.class).withSchema("""
                {"type":"object","additionalProperties":false,"required":["tasks"],"properties":{
                  "tasks":{"type":"array","items":{"type":"object","additionalProperties":false,
                    "required":["id","instruction","order"],"properties":{
                      "id":{"type":["string","null"]},"instruction":{"type":"string","minLength":1},
                      "order":{"type":"integer","minimum":0}}}}}}
                """).withValidation(plan -> validatePlan(plan.tasks()));
    }

    public static void validatePlan(List<PlanTask> tasks) {
        require(tasks != null, "tasks must be an array");
        if (tasks.isEmpty()) {
            return;
        }
        if (tasks.size() == 1 && tasks.getFirst() != null && tasks.getFirst().id() == null) {
            PlanTask stop = tasks.getFirst();
            require(stop.order() == 0 && hasText(stop.instruction()), "A stop task requires order=0 and an explanation");
            return;
        }
        Set<String> ids = new HashSet<>();
        TreeSet<Integer> orders = new TreeSet<>();
        for (PlanTask task : tasks) {
            require(task != null && hasText(task.id()) && hasText(task.instruction()),
                    "Every execution task needs a nonblank id and instruction; do not mix stop and execution tasks");
            require(ids.add(task.id().strip()), "Task ids must be unique");
            require(task.order() > 0, "Execution task order must be positive");
            orders.add(task.order());
        }
        int expected = 1;
        for (int order : orders) {
            require(order == expected++, "Task order groups must start at 1 and be contiguous");
        }
    }

    public static StructuredOutputSpec<CritiqueResult> critique() {
        return StructuredOutputSpec.of(CritiqueResult.class).withSchema("""
                {"type":"object","additionalProperties":false,"required":["passed","feedback"],
                 "properties":{"passed":{"type":"boolean"},"feedback":{"type":["string","null"]}}}
                """).withValidation(result -> require(result.passed() || hasText(result.feedback()),
                        "A failed critique must explain what evidence or work is missing"));
    }

    public static StructuredOutputSpec<PptIntentResult> intent() {
        return StructuredOutputSpec.of(PptIntentResult.class).withValidation(result ->
                require(result.getIntent() == PptIntent.CREATE_PPT || result.getIntent() == PptIntent.MODIFY_PPT,
                        "Intent must be CREATE_PPT or MODIFY_PPT; resuming is decided by application state"));
    }

    public static StructuredOutputSpec<TemplateSelectionResult> template(Set<String> candidates) {
        Set<String> allowed = Set.copyOf(candidates);
        require(!allowed.isEmpty(), "No PPT templates are available");
        return StructuredOutputSpec.of(TemplateSelectionResult.class).withValidation(result ->
                require(result.getTemplateCode() != null && allowed.contains(result.getTemplateCode()),
                        "templateCode must be one of the supplied candidates: " + allowed));
    }

    public static StructuredOutputSpec<Recommendations> recommendations() {
        return StructuredOutputSpec.of(Recommendations.class).withSchema("""
                {"type":"object","additionalProperties":false,"required":["questions"],"properties":{
                  "questions":{"type":"array","minItems":3,"maxItems":3,"uniqueItems":true,
                    "items":{"type":"string","minLength":1}}}}
                """).withValidation(result -> {
                    require(result.questions() != null && result.questions().size() == 3, "Exactly three questions are required");
                    Set<String> unique = new HashSet<>();
                    for (String question : result.questions()) {
                        require(hasText(question) && unique.add(question.strip()), "Questions must be nonblank and distinct");
                    }
                });
    }

    static boolean hasText(String value) { return value != null && !value.isBlank(); }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}

