package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.config.ReviewProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewDraftValidationPipelineTest {

    private final ReviewProperties properties = new ReviewProperties();
    private final StructuredOutputMapper mapper = new StructuredOutputMapper(
            new StructuredOutputConfiguration().structuredOutputObjectMapper());
    private final ReviewDraftValidationPipeline pipeline = new ReviewDraftValidationPipeline(
            mapper,
            new ReviewDraftSchemaValidator(),
            new ReviewDraftMapper(mapper),
            new ReviewDraftBusinessValidator(),
            new CitationGuard(new CitationIdParser()),
            properties
    );
    private final ReviewInput input = input();

    @Test
    void shouldRunTheFullChainAndPreserveCitationOrder() {
        ValidatedReview review = pipeline.validate(draft("""
                {
                  "statements": [
                    {
                      "type": "METHOD",
                      "text": "The abstracts describe a selective state-space approach.",
                      "citationIds": ["P3", "P1"]
                    }
                  ]
                }
                """), input);

        assertThat(review.statements()).hasSize(1);
        assertThat(review.statements().getFirst().citationIds())
                .extracting(CitationId::value)
                .containsExactly("P3", "P1");
    }

    @Test
    void shouldRejectMalformedJsonAtSyntaxStageWithoutEchoingRawOutput() {
        String sensitiveRaw = "{SENSITIVE_RAW_DRAFT";

        assertThatThrownBy(() -> pipeline.validate(draft(sensitiveRaw), input))
                .isInstanceOfSatisfying(ReviewDraftValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ReviewValidationStage.JSON_SYNTAX);
                    assertThat(exception.safeCodes()).containsExactly("INVALID_JSON_SYNTAX");
                    assertThat(exception.getMessage()).doesNotContain(sensitiveRaw);
                });
    }

    @Test
    void shouldRejectMissingCitationsAndAdditionalPropertiesAtSchemaStage() {
        assertThatThrownBy(() -> pipeline.validate(draft("""
                {
                  "statements": [
                    {
                      "type": "METHOD",
                      "text": "A bounded method observation.",
                      "unexpected": "not allowed"
                    }
                  ]
                }
                """), input))
                .isInstanceOfSatisfying(ReviewDraftValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ReviewValidationStage.JSON_SCHEMA);
                    assertThat(exception.safeCodes())
                            .contains("MISSING_REQUIRED_FIELD", "ADDITIONAL_PROPERTY_NOT_ALLOWED");
                });
    }

    @Test
    void shouldRejectUnknownEvidenceCitationAtCitationGuard() {
        assertThatThrownBy(() -> pipeline.validate(draft(validJson("P999")), input))
                .isInstanceOfSatisfying(ReviewDraftValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ReviewValidationStage.CITATION_GUARD);
                    assertThat(exception.safeCodes()).containsExactly("UNKNOWN_CITATION_ID");
                });
    }

    @Test
    void shouldEnforceThe4ASchemaCardinalityAndTextLimits() {
        String thirteenStatements = java.util.stream.IntStream.range(0, 13)
                .mapToObj(index -> """
                        {"type":"METHOD","text":"bounded text","citationIds":["P1"]}""")
                .collect(java.util.stream.Collectors.joining(",", "{\"statements\":[", "]}"));
        assertSchemaCode(thirteenStatements, "TOO_MANY_ITEMS");
        assertSchemaCode(json("x".repeat(801), "P1"), "TEXT_LIMIT_EXCEEDED");
        assertSchemaCode("""
                {
                  "statements": [
                    {
                      "type": "METHOD",
                      "text": "bounded text",
                      "citationIds": ["P1", "P3", "P4", "P5", "P6", "P7"]
                    }
                  ]
                }
                """, "TOO_MANY_ITEMS");
        assertSchemaCode("""
                {
                  "statements": [
                    {
                      "type": "METHOD",
                      "text": "bounded text",
                      "citationIds": ["P1", "P1"]
                    }
                  ]
                }
                """, "DUPLICATE_CITATION_ID");
        assertSchemaCode(validJson("P01"), "MALFORMED_CITATION_ID");
    }

    @Test
    void shouldRejectFormalPaperPositionThatHasNoAbstractEvidence() {
        assertThat(input.evidencePapers())
                .extracting(paper -> paper.citationId().value())
                .containsExactly("P1", "P3", "P4");

        assertThatThrownBy(() -> pipeline.validate(draft(validJson("P2")), input))
                .isInstanceOfSatisfying(ReviewDraftValidationException.class, exception ->
                        assertThat(exception.safeCodes()).containsExactly("UNKNOWN_CITATION_ID"));
    }

    @Test
    void shouldRejectBibliographicAndRendererControlledText() {
        List<String> forbiddenTexts = List.of(
                "The DOI 10.1000/fake supports this.",
                "See https://malicious.example for details.",
                "Evidence title 1 reports a method.",
                "Author 1 reports a method.",
                "The result was published in 2025.",
                "A claim [P1] was observed.",
                "<b>A claim</b>",
                "[source](https://malicious.example)",
                "The citation was VERIFIED."
        );

        for (String text : forbiddenTexts) {
            assertThatThrownBy(() -> pipeline.validate(draft(json(text, "P1")), input))
                    .as("text=%s", text)
                    .isInstanceOfSatisfying(ReviewDraftValidationException.class, exception -> {
                        assertThat(exception.getStage()).isEqualTo(ReviewValidationStage.BUSINESS_RULE);
                        assertThat(exception.safeCodes())
                                .containsExactly("BIBLIOGRAPHIC_TEXT_NOT_ALLOWED");
                    });
        }
    }

    @Test
    void shouldFailClosedWithoutRepairWhenRawDraftExceedsServerLimit() {
        properties.setMaxRawDraftLength(16);
        ReviewDraftValidationPipeline smallPipeline = new ReviewDraftValidationPipeline(
                mapper,
                new ReviewDraftSchemaValidator(),
                new ReviewDraftMapper(mapper),
                new ReviewDraftBusinessValidator(),
                new CitationGuard(new CitationIdParser()),
                properties
        );

        assertThatThrownBy(() -> smallPipeline.validate(draft(validJson("P1")), input))
                .isInstanceOfSatisfying(ReviewDraftValidationException.class, exception -> {
                    assertThat(exception.safeCodes()).containsExactly("MODEL_OUTPUT_TOO_LARGE");
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    private ReviewInput input() {
        return new ReviewInput(5, 4, 3, List.of(
                paper(1), paper(3), paper(4)
        ));
    }

    private EvidencePaper paper(int position) {
        return new EvidencePaper(
                new CitationId(position),
                "10.1000/" + position,
                "Evidence title " + position,
                List.of("Author " + position),
                2025,
                "Venue",
                "Abstract evidence " + position
        );
    }

    private UntrustedReviewDraft draft(String value) {
        return new UntrustedReviewDraft(value);
    }

    private String validJson(String citationId) {
        return json("A bounded abstract-level observation.", citationId);
    }

    private String json(String text, String citationId) {
        try {
            return mapper.writeValueAsString(new ReviewDraft(List.of(
                    new ReviewStatement(
                            ReviewStatementType.OBSERVATION,
                            text,
                            List.of(citationId)
                    )
            )));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertSchemaCode(String raw, String expectedCode) {
        assertThatThrownBy(() -> pipeline.validate(draft(raw), input))
                .isInstanceOfSatisfying(ReviewDraftValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ReviewValidationStage.JSON_SCHEMA);
                    assertThat(exception.safeCodes()).contains(expectedCode);
                });
    }
}
