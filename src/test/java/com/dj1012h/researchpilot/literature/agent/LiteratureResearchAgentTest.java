package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiteratureResearchAgentTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldBlockOpenAlexBeforeThePortWhenSearchBudgetIsExhausted() {
        OpenAlexSearchPort port = mock(OpenAlexSearchPort.class);
        LiteratureResearchAgent agent = agent(port);
        AgentState state = agent.registerInitialPlan(agent.initialize(new SearchRequest("query", null, null, 10)), plan())
                .startAction(new ActionExecutionPermit(AgentAction.SEARCH_OPENALEX, ActionCost.none()))
                .startAction(new ActionExecutionPermit(AgentAction.SEARCH_OPENALEX, ActionCost.none()));

        ControlledOpenAlexSearchResult result = agent.executeOpenAlexSearch(state, query());

        assertThat(result.executed()).isFalse();
        assertThat(result.state().terminationReason()).isEqualTo(TerminationReason.SEARCH_ROUND_LIMIT_REACHED);
        verify(port, never()).search(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldCallOpenAlexOnlyAfterBudgetApprovalAndRecordTheObservation() {
        OpenAlexSearchPort port = mock(OpenAlexSearchPort.class);
        when(port.search(query())).thenReturn(new OpenAlexSearchResult(0, List.of(), null));
        LiteratureResearchAgent agent = agent(port);
        AgentState state = agent.registerInitialPlan(agent.initialize(new SearchRequest("query", null, null, 10)), plan());

        ControlledOpenAlexSearchResult result = agent.executeOpenAlexSearch(state, query());

        assertThat(result.executed()).isTrue();
        assertThat(result.state().searchRoundCount()).isOne();
        assertThat(result.state().observations()).hasSize(1);
        assertThat(result.state().currentStage()).isEqualTo(AgentStage.CANDIDATES_RETRIEVED);
        verify(port).search(query());
    }

    private LiteratureResearchAgent agent(OpenAlexSearchPort port) {
        AgentBudgetProperties budgets = new AgentBudgetProperties();
        return new LiteratureResearchAgent(new AgentBudgetPolicy(budgets, CLOCK), budgets,
                new LiteratureSearchProperties(), port, CLOCK);
    }
    private SearchPlan plan() {
        return new SearchPlan("query", "topic", List.of("keyword"), "keyword", Set.of(LanguageCode.EN),
                List.of("article"), SearchSort.RELEVANCE, 2020, 2026, 20, 10);
    }
    private OpenAlexQuery query() {
        return new OpenAlexQuery("keyword", LocalDate.of(2020, 1, 1), LocalDate.of(2026, 12, 31),
                List.of("article"), List.of("en"), OpenAlexQuery.Sort.RELEVANCE, 10);
    }
}
