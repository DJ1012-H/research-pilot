package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchPlanValidationOrderTest {

    @Test
    void shouldAlwaysExecuteFiveLayersInFixedOrder() {
        JsonSyntaxValidator syntax = mock(JsonSyntaxValidator.class);
        SearchPlanSchemaValidator schema = mock(SearchPlanSchemaValidator.class);
        SearchPlanDraftMapper mapper = mock(SearchPlanDraftMapper.class);
        SearchPlanBusinessValidator business = mock(SearchPlanBusinessValidator.class);
        SearchPlanSecurityValidator security = mock(SearchPlanSecurityValidator.class);
        JsonNode syntaxNode = mock(JsonNode.class);
        JsonNode schemaNode = mock(JsonNode.class);
        SearchPlanDraft draft = mock(SearchPlanDraft.class);
        SearchPlan plan = mock(SearchPlan.class);
        SearchPlan trustedPlan = mock(SearchPlan.class);
        SearchPlanGenerationContext context = context();

        when(syntax.validate("raw")).thenReturn(syntaxNode);
        when(schema.validate(syntaxNode)).thenReturn(schemaNode);
        when(mapper.map(schemaNode)).thenReturn(draft);
        when(business.validate(context, draft)).thenReturn(plan);
        when(security.validate(plan)).thenReturn(trustedPlan);

        SearchPlan result = new SearchPlanValidationPipeline(
                syntax, schema, mapper, business, security
        ).validate(context, "raw");

        assertThat(result).isSameAs(trustedPlan);
        InOrder order = inOrder(syntax, schema, mapper, business, security);
        order.verify(syntax).validate("raw");
        order.verify(schema).validate(syntaxNode);
        order.verify(mapper).map(schemaNode);
        order.verify(business).validate(context, draft);
        order.verify(security).validate(plan);
        order.verifyNoMoreInteractions();
    }

    private SearchPlanGenerationContext context() {
        return new SearchPlanGenerationContext(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new SearchRequest("Mamba 遥感变化检测", null, null, 10),
                Instant.parse("2026-07-20T08:00:00Z"),
                2026
        );
    }
}
