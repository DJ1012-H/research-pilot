package com.dj1012h.researchpilot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureConstraintsTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path BASE = MAIN_JAVA.resolve(
            Path.of("com", "dj1012h", "researchpilot")
    );

    @Test
    void onlyBusinessValidatorMayConstructTrustedSearchPlan() throws IOException {
        List<Path> constructors;
        try (Stream<Path> files = productionJavaFiles()) {
            constructors = files
                    .filter(path -> source(path).contains("new SearchPlan("))
                    .toList();
        }

        assertThat(constructors)
                .extracting(path -> path.getFileName().toString())
                .containsExactly("SearchPlanBusinessValidator.java");
    }

    @Test
    void onlyFactoryMayConstructOpenAlexQueryInProduction() throws IOException {
        List<Path> constructors;
        try (Stream<Path> files = productionJavaFiles()) {
            constructors = files
                    .filter(path -> source(path).contains("new OpenAlexQuery("))
                    .toList();
        }

        assertThat(constructors)
                .extracting(path -> path.getFileName().toString())
                .containsExactly("OpenAlexQueryFactory.java");
    }

    @Test
    void draftMustNotReachOpenAlexIntegration() throws IOException {
        assertThat(sourceTree(BASE.resolve(Path.of("integration", "openalex"))))
                .doesNotContain("SearchPlanDraft");
        assertThat(source(BASE.resolve(Path.of(
                "literature", "application", "OpenAlexQueryFactory.java"
        )))).doesNotContain("SearchPlanDraft");
    }

    @Test
    void agentAndControllerMustNotCallOpenAlexClient() throws IOException {
        assertThat(source(BASE.resolve(Path.of(
                "literature", "application", "SearchAgent.java"
        )))).doesNotContain("OpenAlex");
        assertThat(source(BASE.resolve(Path.of(
                "literature", "api", "LiteratureSearchController.java"
        )))).doesNotContain("OpenAlex");
        assertThat(source(BASE.resolve(Path.of(
                "literature", "application", "SearchAgent.java"
        )))).doesNotContain("Crossref");
        assertThat(source(BASE.resolve(Path.of(
                "literature", "api", "LiteratureSearchController.java"
        )))).doesNotContain("Crossref");
    }

    @Test
    void plannerMustUseSharedModelBoundaryInsteadOfChatService() throws IOException {
        String planner = source(BASE.resolve(Path.of(
                "literature", "application", "LlmQueryPlanner.java"
        )));

        assertThat(planner)
                .contains("ModelInvoker")
                .doesNotContain("ChatService")
                .doesNotContain("ChatModel");
    }

    @Test
    void domainModelMustRemainFrameworkAndProviderIndependent() throws IOException {
        String modelSources = sourceTree(BASE.resolve(Path.of("literature", "model")));

        assertThat(modelSources)
                .doesNotContain("org.springframework")
                .doesNotContain("dev.langchain4j")
                .doesNotContain("integration.openalex.dto")
                .doesNotContain("com.baomidou");
    }

    @Test
    void searchServiceMustUseOnlyTheDedicatedPersistenceFacadeAndNoCache() throws IOException {
        String service = source(BASE.resolve(Path.of(
                "literature", "application", "LiteratureSearchService.java"
        )));

        assertThat(service)
                .doesNotContain("Redis")
                .doesNotContain("Repository")
                .doesNotContain("MyBatis")
                .contains("LiteraturePersistenceFacade")
                .doesNotContain("LiteraturePersistenceMapper");
    }

    @Test
    void searchServiceMustDelegateControlledExecutionInsteadOfCallingCrossrefDirectly() throws IOException {
        String service = source(BASE.resolve(Path.of(
                "literature", "application", "LiteratureSearchService.java"
        )));
        String application = sourceTree(BASE.resolve(Path.of("literature", "application")));

        assertThat(service)
                .contains("LiteratureResearchAgent")
                .doesNotContain("CrossrefCandidateLookupService")
                .doesNotContain("CrossrefClient")
                .doesNotContain("CrossrefSearchPort");
        assertThat(application).doesNotContain("integration.crossref.dto");
    }

    @Test
    void actionDeciderMustStayReadOnlyAndToolFree() throws IOException {
        String decider = source(BASE.resolve(Path.of(
                "literature", "agent", "SearchActionDecider.java"
        )));
        String transitionPolicy = source(BASE.resolve(Path.of(
                "literature", "agent", "AgentTransitionPolicy.java"
        )));
        String agentSources = sourceTree(BASE.resolve(Path.of("literature", "agent")));

        assertThat(decider)
                .doesNotContain("OpenAlexSearchPort")
                .doesNotContain("CrossrefSearchPort")
                .doesNotContain("OpenAlexClient")
                .doesNotContain("CrossrefClient")
                .doesNotContain("startAction(")
                .doesNotContain("ActionExecutionPermit");
        assertThat(transitionPolicy)
                .doesNotContain("ChatModel")
                .doesNotContain("OpenAlex")
                .doesNotContain("Crossref");
        assertThat(agentSources).doesNotContain("@Tool");
    }

    @Test
    void planRefinerMustStayToolPersistenceAndCacheFree() {
        String refiner = source(BASE.resolve(Path.of(
                "literature", "agent", "SearchPlanRefiner.java"
        )));

        assertThat(refiner)
                .doesNotContain("OpenAlex")
                .doesNotContain("Crossref")
                .doesNotContain("Redis")
                .doesNotContain("Repository")
                .doesNotContain("@Tool")
                .contains("SearchPlanValidationPipeline");
    }

    @Test
    void reviewFlowMustRemainToolFreeProviderIndependentAndOutsidePublicInternalContracts() throws IOException {
        String reviewSources = sourceTree(BASE.resolve(Path.of("literature", "review")));
        String response = source(BASE.resolve(Path.of(
                "literature", "api", "dto", "SearchResponse.java"
        )));
        String agent = source(BASE.resolve(Path.of(
                "literature", "agent", "LiteratureResearchAgent.java"
        )));

        assertThat(reviewSources)
                .doesNotContain("integration.openalex.dto")
                .doesNotContain("integration.crossref.dto")
                .doesNotContain("OpenAlexSearchPort")
                .doesNotContain("CrossrefSearchPort")
                .doesNotContain("@Tool")
                .doesNotContain("Repository")
                .doesNotContain("Redis")
                .doesNotContain("SearchResponse.review");
        assertThat(response)
                .doesNotContain("ReviewInput")
                .doesNotContain("ReviewDraft")
                .doesNotContain("UntrustedReviewDraft")
                .doesNotContain("AgentState");
        assertThat(agent)
                .doesNotContain("ReviewGenerationService")
                .doesNotContain("literature.review");
    }

    @Test
    void citationValidationRenderingAndPromptConstructionMustStaySeparated() throws IOException {
        String citationGuard = source(BASE.resolve(Path.of(
                "literature", "review", "CitationGuard.java"
        )));
        String promptBuilder = source(BASE.resolve(Path.of(
                "literature", "review", "EvidenceReviewPromptBuilder.java"
        )));
        String responseAssembler = source(BASE.resolve(Path.of(
                "literature", "application", "ReviewResponseAssembler.java"
        )));
        String searchService = source(BASE.resolve(Path.of(
                "literature", "application", "LiteratureSearchService.java"
        )));

        assertThat(citationGuard)
                .doesNotContain("ModelInvoker")
                .doesNotContain("EvidenceReviewGenerator");
        assertThat(promptBuilder)
                .doesNotContain("ModelInvoker")
                .doesNotContain("EvidenceReviewGenerator");
        assertThat(responseAssembler)
                .doesNotContain("ModelInvoker")
                .doesNotContain("EvidenceReviewGenerator")
                .doesNotContain("UntrustedReviewDraft");
        assertThat(searchService)
                .doesNotContain("readTree")
                .doesNotContain("ObjectMapper")
                .doesNotContain("CitationGuard");
    }

    @Test
    void publicDtosMustNotExposeAgentStateInternalDraftsOrInternalTerminationReason() throws IOException {
        String apiDtos = sourceTree(BASE.resolve(Path.of("literature", "api", "dto")));

        assertThat(apiDtos)
                .doesNotContain("literature.agent")
                .doesNotContain("AgentState")
                .doesNotContain("ReviewInput")
                .doesNotContain("ReviewDraft")
                .doesNotContain("UntrustedReviewDraft")
                .doesNotContain("terminationDetail");
    }

    @Test
    void reviewRepairLimitMustBeJavaOwnedAndAtMostOne() {
        String orchestrator = source(BASE.resolve(Path.of(
                "literature", "review", "EvidenceReviewOrchestrator.java"
        )));
        String outcome = source(BASE.resolve(Path.of(
                "literature", "review", "ReviewOutcome.java"
        )));

        assertThat(orchestrator)
                .containsOnlyOnce("generateInitial(")
                .containsOnlyOnce("generatePrompt(")
                .doesNotContain("while (")
                .doesNotContain("for (");
        assertThat(outcome)
                .contains("modelCallCount > 2")
                .contains("repairCount > 1");
    }

    @Test
    void productionMustNotIntroduceToolAnnotationsForReview() throws IOException {
        try (Stream<Path> files = productionJavaFiles()) {
            assertThat(files.map(this::source).reduce("", String::concat))
                    .doesNotContain("@Tool");
        }
    }

    private Stream<Path> productionJavaFiles() throws IOException {
        return Files.walk(MAIN_JAVA)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"));
    }

    private String sourceTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(this::source)
                    .reduce("", (left, right) -> left + System.lineSeparator() + right);
        }
    }

    private String source(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取架构约束源文件: " + path, exception);
        }
    }
}
