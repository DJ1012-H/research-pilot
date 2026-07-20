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
    void currentSearchServiceMustNotIntroduceLaterPhasePersistenceOrCache() throws IOException {
        String service = source(BASE.resolve(Path.of(
                "literature", "application", "LiteratureSearchService.java"
        )));

        assertThat(service)
                .doesNotContain("Redis")
                .doesNotContain("Repository")
                .doesNotContain("MyBatis")
                .doesNotContain("Persistence");
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
