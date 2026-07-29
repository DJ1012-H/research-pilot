package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.controller.ChatController;
import com.dj1012h.researchpilot.controller.SystemStatusController;
import com.dj1012h.researchpilot.integration.crossref.CrossrefSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.literature.api.LiteratureSearchController;
import com.dj1012h.researchpilot.literature.application.PaperVerificationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExecutionArchitectureTest {

    @Test
    void shouldKeepDecisionAndRefinementBoundariesFreeOfProviderTools() {
        Set<Class<?>> deciderDependencies = fieldTypes(SearchActionDecider.class);
        Set<Class<?>> refinerDependencies = fieldTypes(SearchPlanRefiner.class);

        assertThat(deciderDependencies).doesNotContain(
                OpenAlexSearchPort.class,
                CrossrefSearchPort.class,
                PaperVerificationService.class,
                SearchPlanRefiner.class
        );
        assertThat(refinerDependencies).noneMatch(type ->
                type.getName().contains(".integration.openalex.")
                        || type.getName().contains(".integration.crossref."));
    }

    @Test
    void shouldKeepControllersAndTraceRecorderOutsideTheActionExecutionBoundary() {
        assertThat(fieldTypes(ChatController.class)).doesNotContain(SearchActionExecutor.class);
        assertThat(fieldTypes(SystemStatusController.class)).doesNotContain(SearchActionExecutor.class);
        assertThat(fieldTypes(LiteratureSearchController.class)).doesNotContain(SearchActionExecutor.class);
        assertThat(fieldTypes(InMemoryExecutionTraceRecorder.class)).noneMatch(type ->
                type.getName().contains(".integration.")
                        || type.getName().contains(".controller."));
    }

    private Set<Class<?>> fieldTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getType)
                .collect(Collectors.toSet());
    }
}
