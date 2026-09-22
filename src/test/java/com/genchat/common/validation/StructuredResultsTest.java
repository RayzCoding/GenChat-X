package com.genchat.common.validation;

import com.genchat.dto.AiPptTemplate;
import com.genchat.dto.PptIntent;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructuredResultsTest {
    @Test
    void templateMustBelongToCandidates() {
        var spec = StructuredResults.template(Set.of("known"));
        assertEquals("known", spec.parse("{\"templateCode\":\"known\",\"reason\":\"fits\"}").getTemplateCode());
        assertThrows(RuntimeException.class, () -> spec.parse("{\"templateCode\":\"invented\",\"reason\":\"fits\"}"));
        assertThrows(RuntimeException.class, () -> StructuredResults.template(Set.of()));
    }

    @Test
    void intentRequiresAnExplicitSupportedAction() {
        var spec = StructuredResults.intent();
        assertEquals(PptIntent.MODIFY_PPT, spec.parse("{\"intent\":\"MODIFY_PPT\",\"reason\":\"update text\"}").getIntent());
        for (String value : new String[]{"null", "\"UNKNOWN\"", "\"RESUME_PPT\""}) {
            assertThrows(RuntimeException.class, () -> spec.parse("{\"intent\":" + value + ",\"reason\":\"test\"}"));
        }
    }

    @Test
    void recommendationsRequireExactlyThreeDistinctStringsWithoutCoercion() {
        var spec = StructuredResults.recommendations();
        assertEquals(3, spec.parse("{\"questions\":[\"a\",\"b\",\"c\"]}").questions().size());
        for (String array : new String[]{"[1,true,{}]", "[\"a\",\"b\"]", "[\"a\",\"a \",\"c\"]", "[\" \",\"b\",\"c\"]"}) {
            assertThrows(RuntimeException.class, () -> spec.parse("{\"questions\":" + array + "}"));
        }
    }

    @Test
    void planSupportsStopAndParallelGroupsButRejectsInvalidTasks() {
        var spec = StructuredResults.plan();
        assertTrue(spec.parse("{\"tasks\":[]}").tasks().isEmpty());
        assertNull(spec.parse("{\"tasks\":[{\"id\":null,\"instruction\":\"Enough evidence\",\"order\":0}]}").tasks().getFirst().id());
        assertEquals(2, spec.parse("{\"tasks\":[{\"id\":\"a\",\"instruction\":\"search\",\"order\":1},"
                + "{\"id\":\"b\",\"instruction\":\"search\",\"order\":1}]}").tasks().size());
        for (String tasks : new String[]{
                "[{\"id\":\"a\",\"instruction\":\"search\",\"order\":1},{\"id\":\"a\",\"instruction\":\"search\",\"order\":1}]",
                "[{\"id\":\"a\",\"instruction\":\"search\",\"order\":2}]",
                "[{\"id\":\"a\",\"instruction\":\"search\",\"order\":\"1\"}]",
                "[{\"id\":\"a\",\"instruction\":\"search\"}]",
                "[{\"id\":null,\"instruction\":\"stop\",\"order\":0},{\"id\":\"a\",\"instruction\":\"search\",\"order\":1}]"}) {
            assertThrows(RuntimeException.class, () -> spec.parse("{\"tasks\":" + tasks + "}"));
        }
    }

    @Test
    void critiqueRequiresBooleanAndActionableFailureFeedback() {
        var spec = StructuredResults.critique();
        assertTrue(spec.parse("{\"passed\":true,\"feedback\":\"\"}").passed());
        for (String json : new String[]{"{\"feedback\":\"missing flag\"}", "{\"passed\":\"true\",\"feedback\":\"\"}",
                "{\"passed\":false,\"feedback\":\" \"}"}) {
            assertThrows(RuntimeException.class, () -> spec.parse(json));
        }
    }

    @Test
    void pptAllowsMissingImageUrlsAndUnicodeWithinCharacterLimit() {
        String json = slides("{\"title\":{\"type\":\"text\",\"content\":\"😀\",\"fontLimit\":1},"
                + "\"image\":{\"type\":\"image\",\"content\":\"landscape\"}}", 1);
        var spec = PptSchemaValidator.spec(template(json), null);
        var result = spec.parse(json);
        assertNull(result.getSlides().getFirst().getData().get("image").getUrl());
        assertEquals("😀", result.getSlides().getFirst().getData().get("title").getContent());
    }

    @Test
    void pptRejectsEmptySlidesInvalidIndexesWrongTypesAndLongText() {
        var spec = PptSchemaValidator.spec(template(slides("{\"title\":{\"type\":\"text\",\"fontLimit\":1}}", 1)), null);
        assertThrows(RuntimeException.class, () -> spec.parse("{\"slides\":[]}"));
        assertThrows(RuntimeException.class, () -> spec.parse("{\"slides\":null}"));
        assertThrows(RuntimeException.class, () -> spec.parse(slides("{}", 3)));
        assertThrows(RuntimeException.class, () -> spec.parse(slides("{\"title\":{\"type\":\"text\",\"content\":\"long\",\"fontLimit\":1}}", 1)));
        assertThrows(RuntimeException.class, () -> spec.parse(slides("{\"title\":{\"type\":\"text\",\"content\":3,\"fontLimit\":1}}", 1)));
    }

    @Test
    void pptRejectsUnrecognizedOrUnboundedTemplateDefinitionsBeforeCallingModel() {
        assertThrows(IllegalArgumentException.class, () -> PptSchemaValidator.spec(template("{}"), null));
        assertThrows(IllegalArgumentException.class, () -> PptSchemaValidator.spec(template("prose, not JSON"), null));
        assertThrows(IllegalArgumentException.class, () -> PptSchemaValidator.spec(
                template(slides("{\"title\":{\"type\":\"text\"}}", 1)), null));
    }

    @Test
    void pptUsesTemplateFieldsAndLimitsAndPreservesModifiedLayout() {
        String original = slides("{\"title\":{\"type\":\"text\",\"content\":\"Hi\",\"fontLimit\":3}}", 1);
        var template = template(original);
        var spec = PptSchemaValidator.spec(template, null);
        var previous = spec.parse(original);
        assertThrows(RuntimeException.class, () -> spec.parse(original.replace("\"title\"", "\"invented\"")));
        assertThrows(RuntimeException.class, () -> spec.parse(original.replace("\"fontLimit\":3", "\"fontLimit\":100")));
        var modify = PptSchemaValidator.spec(template, previous);
        assertThrows(RuntimeException.class, () -> modify.parse(slides("{}", 2)));
        assertEquals("New", modify.parse(original.replace("\"Hi\"", "\"New\"")).getSlides().getFirst().getData().get("title").getContent());
    }

    private static AiPptTemplate template(String schema) {
        return AiPptTemplate.builder().templateCode("test").slideCount(2).templateSchema(schema).build();
    }

    private static String slides(String data, int index) {
        return "{\"slides\":[{\"pageType\":\"COVER\",\"templatePageIndex\":" + index + ",\"data\":" + data + "}]}";
    }
}

