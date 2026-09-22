package com.genchat.common.validation;

import com.agent.foundation.output.StructuredOutputSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.genchat.common.utils.JacksonJson;
import com.genchat.dto.AiPptTemplate;
import com.genchat.dto.FieldData;
import com.genchat.dto.PptSchema;
import com.genchat.dto.Slide;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Validates generated slides before persistence, image generation or rendering. */
public final class PptSchemaValidator {
    private PptSchemaValidator() { }

    private static final String SCHEMA = """
            {
              "type": "object", "additionalProperties": false, "required": ["slides"],
              "properties": {
                "slides": {"type": "array", "minItems": 1, "items": {"$ref": "#/$defs/slide"}}
              },
              "$defs": {
                "slide": {
                  "type": "object", "additionalProperties": false,
                  "required": ["pageType", "templatePageIndex", "data"],
                  "properties": {
                    "pageType": {"type": "string", "minLength": 1},
                    "pageDesc": {"type": ["string", "null"]},
                    "templatePageIndex": {"type": "integer", "minimum": 1},
                    "data": {"type": "object", "additionalProperties": {"$ref": "#/$defs/field"}}
                  }
                },
                "field": {
                  "type": "object", "additionalProperties": false, "required": ["type"],
                  "properties": {
                    "type": {"enum": ["text", "image", "background"]},
                    "content": {"type": ["string", "null"]},
                    "fontLimit": {"type": ["integer", "null"], "minimum": 0},
                    "url": {"type": ["string", "null"]}
                  },
                  "if": {"properties": {"type": {"const": "text"}}},
                  "then": {
                    "required": ["content", "fontLimit"],
                    "properties": {"content": {"type": "string"}, "fontLimit": {"type": "integer", "minimum": 0}}
                  }
                }
              }
            }
            """;

    public static StructuredOutputSpec<PptSchema> spec(AiPptTemplate template, PptSchema previous) {
        Objects.requireNonNull(template, "PPT template is required");
        Map<Integer, JsonNode> pages = new HashMap<>();
        StructuredResults.require(StructuredResults.hasText(template.getTemplateSchema()), "PPT template schema is missing");
        collectPages(JacksonJson.parseTree(template.getTemplateSchema()), pages);
        StructuredResults.require(!pages.isEmpty(),
                "Unsupported PPT template schema: provide page definitions with templatePageIndex and data field definitions");
        for (var page : pages.entrySet()) {
            StructuredResults.require(template.getSlideCount() == null || template.getSlideCount() > 0
                    && page.getKey() <= template.getSlideCount(), "Template page definition exceeds its declared slide count");
            page.getValue().path("data").fields().forEachRemaining(entry -> {
                JsonNode field = entry.getValue();
                String type = field.path("type").asText();
                StructuredResults.require(StructuredResults.hasText(entry.getKey())
                        && java.util.Set.of("text", "image", "background").contains(type), "Invalid template field definition: " + entry.getKey());
                if ("text".equals(type)) {
                    StructuredResults.require(field.path("fontLimit").isIntegralNumber() && field.path("fontLimit").canConvertToInt()
                            && field.path("fontLimit").asInt() >= 0, "Template text fields require a nonnegative fontLimit: " + entry.getKey());
                }
            });
        }
        return StructuredOutputSpec.of(PptSchema.class).withSchema(SCHEMA)
                .withValidation(result -> validate(result, template.getSlideCount(), pages, previous));
    }

    private static void collectPages(JsonNode node, Map<Integer, JsonNode> pages) {
        if (node == null) {
            return;
        }
        if (node.isObject() && node.path("templatePageIndex").isIntegralNumber() && node.path("data").isObject()) {
            int index = node.path("templatePageIndex").asInt();
            if (index < 1 || pages.putIfAbsent(index, node) != null) {
                throw new IllegalArgumentException("Template page definitions must have unique positive indexes");
            }
            return;
        }
        if (node.isContainerNode()) {
            node.forEach(child -> collectPages(child, pages));
        }
    }

    private static void validate(PptSchema result, Integer slideCount, Map<Integer, JsonNode> pages, PptSchema previous) {
        StructuredResults.require(result.getSlides() != null && !result.getSlides().isEmpty(), "slides must not be empty");
        if (previous != null) {
            StructuredResults.require(previous.getSlides() != null
                    && previous.getSlides().size() == result.getSlides().size(), "Modification must preserve slide count and order");
        }
        for (int i = 0; i < result.getSlides().size(); i++) {
            Slide slide = result.getSlides().get(i);
            StructuredResults.require(slide != null && slide.getTemplatePageIndex() != null
                    && slide.getTemplatePageIndex() > 0, "Each slide needs a positive templatePageIndex");
            int index = slide.getTemplatePageIndex();
            StructuredResults.require(slideCount == null || slideCount <= 0 || index <= slideCount,
                    "templatePageIndex exceeds template slide count: " + index);
            StructuredResults.require(StructuredResults.hasText(slide.getPageType()) && slide.getData() != null,
                    "Each slide needs pageType and data");
            JsonNode templatePage = pages.get(index);
            if (!pages.isEmpty()) {
                StructuredResults.require(templatePage != null, "Unknown templatePageIndex: " + index);
                validateTemplate(slide, templatePage);
            }
            Slide original = previous == null ? null : previous.getSlides().get(i);
            if (original != null) {
                StructuredResults.require(Objects.equals(original.getTemplatePageIndex(), slide.getTemplatePageIndex())
                        && Objects.equals(original.getPageType(), slide.getPageType()), "Modification must preserve slide order and layout");
                StructuredResults.require(original.getData() != null && original.getData().keySet().equals(slide.getData().keySet()),
                        "Modification must preserve field names");
            }
            for (var entry : slide.getData().entrySet()) {
                FieldData field = entry.getValue();
                StructuredResults.require(StructuredResults.hasText(entry.getKey()) && field != null, "Slide fields must be named and non-null");
                if (original != null) {
                    FieldData before = original.getData().get(entry.getKey());
                    StructuredResults.require(before != null && Objects.equals(before.getType(), field.getType())
                            && Objects.equals(before.getFontLimit(), field.getFontLimit()),
                            "Modification must preserve field types and fontLimit: " + entry.getKey());
                    // Unchanged generation prompts must not silently discard existing image URLs.
                    if (!"text".equals(field.getType()) && Objects.equals(before.getContent(), field.getContent())
                            && StructuredResults.hasText(before.getUrl()) && !StructuredResults.hasText(field.getUrl())) {
                        field.setUrl(before.getUrl());
                    }
                }
                if ("text".equals(field.getType())) {
                    StructuredResults.require(field.getContent() != null && field.getFontLimit() != null && field.getFontLimit() >= 0,
                            "Text fields need content and nonnegative fontLimit");
                    int length = field.getContent().codePointCount(0, field.getContent().length());
                    StructuredResults.require(length <= field.getFontLimit(), "Text exceeds fontLimit for field " + entry.getKey());
                } else {
                    StructuredResults.require("image".equals(field.getType()) || "background".equals(field.getType()), "Invalid field type");
                    // Missing URL is intentional: generate from content, or keep the original template image.
                }
            }
        }
    }

    private static void validateTemplate(Slide slide, JsonNode page) {
        if (page.path("pageType").isTextual()) {
            StructuredResults.require(page.path("pageType").asText().equals(slide.getPageType()), "pageType must match the template");
        }
        JsonNode fields = page.path("data");
        java.util.Set<String> names = new java.util.HashSet<>();
        fields.fieldNames().forEachRemaining(names::add);
        StructuredResults.require(names.equals(slide.getData().keySet()), "Field names must match the template exactly");
        for (String name : names) {
            FieldData field = slide.getData().get(name);
            JsonNode definition = fields.get(name);
            StructuredResults.require(field != null && definition.path("type").asText().equals(field.getType()),
                    "Field type must match the template: " + name);
            if ("text".equals(field.getType()) && definition.path("fontLimit").isIntegralNumber()) {
                StructuredResults.require(Objects.equals(definition.path("fontLimit").asInt(), field.getFontLimit()),
                        "fontLimit must match the template: " + name);
            }
        }
    }
}

