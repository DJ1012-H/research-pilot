package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchActionValidationPipelineTest {

    @Test
    void shouldApplyTheFiveStageBoundaryToOneTrustedAction() {
        assertThat(pipeline().validate("{\"action\":\"REFINE_PLAN\"}",
                Set.of(AgentAction.REFINE_PLAN, AgentAction.COMPLETE))).isEqualTo(AgentAction.REFINE_PLAN);
    }

    @Test
    void shouldRejectNonJsonAndAdditionalBudgetFieldsBeforeBusinessOrSecurity() {
        assertFailure("```json\n{\"action\":\"REFINE_PLAN\"}\n```", SearchActionValidationStage.JSON_SYNTAX,
                "ACTION_JSON_INVALID");
        assertFailure("{\"action\":\"REFINE_PLAN\",\"maxSearchRounds\":100}",
                SearchActionValidationStage.JSON_SCHEMA, "ADDITIONAL_PROPERTY_NOT_ALLOWED");
        assertFailure("{\"action\":\"TERMINATE\"}", SearchActionValidationStage.JSON_SCHEMA, "INVALID_ACTION");
        assertFailure("{\"action\":12}", SearchActionValidationStage.JSON_SCHEMA, "INVALID_FIELD_TYPE");
    }

    @Test
    void shouldRejectGloballyValidActionOutsideCurrentAllowedActionsAtSecurityStage() {
        assertFailure("{\"action\":\"VERIFY_WITH_CROSSREF\"}", SearchActionValidationStage.SECURITY,
                "ACTION_NOT_ALLOWED");
    }

    private void assertFailure(String raw, SearchActionValidationStage stage, String code) {
        assertThatThrownBy(() -> pipeline().validate(raw, Set.of(AgentAction.REFINE_PLAN, AgentAction.COMPLETE)))
                .isInstanceOfSatisfying(SearchActionValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(stage);
                    assertThat(exception.getIssues()).extracting(SearchActionValidationIssue::code).contains(code);
                });
    }

    private SearchActionValidationPipeline pipeline() {
        StructuredOutputMapper mapper = new StructuredOutputMapper(
                new StructuredOutputConfiguration().structuredOutputObjectMapper());
        return new SearchActionValidationPipeline(mapper, new SearchActionSchemaValidator(),
                new SearchActionDraftMapper(mapper), new SearchActionBusinessValidator(), new SearchActionSecurityValidator());
    }
}
