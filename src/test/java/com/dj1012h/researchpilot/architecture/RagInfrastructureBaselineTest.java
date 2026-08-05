package com.dj1012h.researchpilot.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagInfrastructureBaselineTest {

    private static final Path COMPOSE = Path.of("infra", "docker-compose.rag.yml");
    private static final Path INDEX_CONTRACT = Path.of(
            "docs", "design", "trusted-rag-index-contract.md"
    );

    @Test
    void qdrantComposeIsPinnedLocalAndPersistent() throws IOException {
        Map<String, Object> root;
        try (InputStream input = Files.newInputStream(COMPOSE)) {
            root = new Yaml(new SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()))
                    .load(input);
        }

        Map<String, Object> services = map(root.get("services"));
        Map<String, Object> qdrant = map(services.get("qdrant"));

        assertThat(qdrant.get("image")).isEqualTo("qdrant/qdrant:v1.18.2");
        assertThat(list(qdrant.get("ports"))).containsExactly(
                "127.0.0.1:6333:6333",
                "127.0.0.1:6334:6334"
        );
        assertThat(list(qdrant.get("volumes")))
                .containsExactly("research-pilot-qdrant-data:/qdrant/storage");
        assertThat(map(qdrant.get("healthcheck"))).containsKey("test");
        assertThat(map(root.get("volumes"))).containsKey("research-pilot-qdrant-data");
        assertThat(qdrant).doesNotContainKey("environment");
    }

    @Test
    void indexContractKeepsTrustAndDimensionGatesExplicit() throws IOException {
        String contract = Files.readString(INDEX_CONTRACT, StandardCharsets.UTF_8);

        assertThat(contract)
                .contains("MySQL is the only durable source of truth")
                .contains("Qdrant is a rebuildable derived index")
                .contains("current MySQL status is `VERIFIED`")
                .contains("DOI is normalized")
                .contains("re-admitted against current MySQL state")
                .contains("`qe06b-d1024-t1-c350-o30-n1`")
                .contains("dimension must still not be trusted as a source-code assumption")
                .contains("must never run `docker compose down -v`");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object value) {
        return (List<String>) value;
    }
}
