package com.dj1012h.researchpilot.integration.ollama;

import com.dj1012h.researchpilot.integration.ollama.dto.OllamaEmbedResponse;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingBatch;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingException;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingFailureType;
import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaEmbeddingAdapterTest {

    private static final String BASE_URL = "http://127.0.0.1:11434";

    @Test
    void shouldReturnImmutableVectorsWithMeasuredDimensionAndElapsedTime() {
        Fixture fixture = fixture(RagEmbeddingProfile.INITIAL_VERSION, 1024);
        fixture.server.expect(requestTo(BASE_URL + "/api/embed"))
                .andRespond(withSuccess(responseJson(1, 1024), MediaType.APPLICATION_JSON));

        EmbeddingBatch result = fixture.adapter.embed(List.of("controlled text"));

        assertThat(result.model()).isEqualTo(RagEmbeddingProfile.INITIAL_MODEL);
        assertThat(result.dimensions()).isEqualTo(1024);
        assertThat(result.embeddings()).hasSize(1);
        assertThat(result.embeddings().getFirst().values()).hasSize(1024);
        assertThat(result.elapsed().isNegative()).isFalse();
        assertThatThrownBy(() -> result.embeddings().getFirst().values().add(1.0))
                .isInstanceOf(UnsupportedOperationException.class);
        fixture.server.verify();
    }

    @Test
    void shouldRejectMissingAndEmptyEmbeddings() {
        Fixture missing = fixture("test-d2", 2);
        missing.server.expect(requestTo(BASE_URL + "/api/embed"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertFailure(missing.adapter, List.of("text"), EmbeddingFailureType.MISSING_EMBEDDINGS);
        missing.server.verify();

        Fixture empty = fixture("test-d2", 2);
        empty.server.expect(requestTo(BASE_URL + "/api/embed"))
                .andRespond(withSuccess("{\"embeddings\":[[]]}", MediaType.APPLICATION_JSON));
        assertFailure(empty.adapter, List.of("text"), EmbeddingFailureType.EMPTY_VECTOR);
        empty.server.verify();
    }

    @Test
    void shouldRejectMalformedJsonAndHttpFailure() {
        Fixture malformed = fixture("test-d2", 2);
        malformed.server.expect(requestTo(BASE_URL + "/api/embed"))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));
        assertFailure(malformed.adapter, List.of("text"), EmbeddingFailureType.INVALID_RESPONSE);
        malformed.server.verify();

        Fixture httpFailure = fixture("test-d2", 2);
        httpFailure.server.expect(requestTo(BASE_URL + "/api/embed"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertFailure(httpFailure.adapter, List.of("text"), EmbeddingFailureType.HTTP_FAILURE);
        httpFailure.server.verify();
    }

    @Test
    void shouldRejectUnexpectedVectorCountAndInconsistentDimensions() {
        Fixture countMismatch = fixture("test-d2", 2);
        countMismatch.server.expect(requestTo(BASE_URL + "/api/embed"))
                .andRespond(withSuccess("{\"embeddings\":[[1.0,2.0]]}", MediaType.APPLICATION_JSON));
        assertFailure(
                countMismatch.adapter,
                List.of("first", "second"),
                EmbeddingFailureType.VECTOR_COUNT_MISMATCH);
        countMismatch.server.verify();

        Fixture dimensionMismatch = fixture("test-d2", 2);
        dimensionMismatch.server.expect(requestTo(BASE_URL + "/api/embed"))
                .andRespond(withSuccess(
                        "{\"embeddings\":[[1.0,2.0],[1.0,2.0,3.0]]}",
                        MediaType.APPLICATION_JSON));
        assertFailure(
                dimensionMismatch.adapter,
                List.of("first", "second"),
                EmbeddingFailureType.DIMENSION_MISMATCH);
        dimensionMismatch.server.verify();
    }

    @Test
    void shouldRejectNonFiniteVectorValues() {
        Fixture fixture = fixture("test-d2", 2);

        assertThatThrownBy(() -> fixture.adapter.validatedBatch(
                new OllamaEmbedResponse(List.of(List.of(Double.NaN, 1.0))),
                1,
                Duration.ofMillis(1)))
                .isInstanceOfSatisfying(EmbeddingException.class,
                        exception -> assertThat(exception.failureType())
                                .isEqualTo(EmbeddingFailureType.NON_FINITE_VECTOR));
        assertThatThrownBy(() -> fixture.adapter.validatedBatch(
                new OllamaEmbedResponse(List.of(List.of(Double.POSITIVE_INFINITY, 1.0))),
                1,
                Duration.ofMillis(1)))
                .isInstanceOfSatisfying(EmbeddingException.class,
                        exception -> assertThat(exception.failureType())
                                .isEqualTo(EmbeddingFailureType.NON_FINITE_VECTOR));
    }

    @Test
    void shouldRejectARealDimensionOtherThan1024ForTheInitialVersion() {
        Fixture fixture = fixture(RagEmbeddingProfile.INITIAL_VERSION, 1024);
        fixture.server.expect(requestTo(BASE_URL + "/api/embed"))
                .andRespond(withSuccess("{\"embeddings\":[[1.0,2.0]]}", MediaType.APPLICATION_JSON));

        assertFailure(fixture.adapter, List.of("text"), EmbeddingFailureType.DIMENSION_MISMATCH);
        fixture.server.verify();
    }

    @Test
    void shouldRejectChangingInitialVersionDimensionInConfiguration() {
        OllamaEmbeddingProperties properties = properties(RagEmbeddingProfile.INITIAL_VERSION, 2);

        assertThatThrownBy(() -> new OllamaEmbeddingAdapter(RestClient.create(BASE_URL), properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initial embedding version");
    }

    private void assertFailure(
            OllamaEmbeddingAdapter adapter,
            List<String> inputs,
            EmbeddingFailureType failureType
    ) {
        assertThatThrownBy(() -> adapter.embed(inputs))
                .isInstanceOfSatisfying(EmbeddingException.class,
                        exception -> assertThat(exception.failureType()).isEqualTo(failureType));
    }

    private Fixture fixture(String embeddingVersion, int expectedDimensions) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OllamaEmbeddingAdapter adapter = new OllamaEmbeddingAdapter(
                builder.build(),
                properties(embeddingVersion, expectedDimensions));
        return new Fixture(adapter, server);
    }

    private OllamaEmbeddingProperties properties(String embeddingVersion, int expectedDimensions) {
        OllamaEmbeddingProperties properties = new OllamaEmbeddingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(BASE_URL);
        properties.setModel(RagEmbeddingProfile.INITIAL_MODEL);
        properties.setEmbeddingVersion(embeddingVersion);
        properties.setExpectedDimensions(expectedDimensions);
        return properties;
    }

    private String responseJson(int vectorCount, int dimensions) {
        String vector = IntStream.range(0, dimensions)
                .mapToObj(index -> "0.125")
                .collect(Collectors.joining(",", "[", "]"));
        return IntStream.range(0, vectorCount)
                .mapToObj(index -> vector)
                .collect(Collectors.joining(",", "{\"embeddings\":[", "]}"));
    }

    private record Fixture(OllamaEmbeddingAdapter adapter, MockRestServiceServer server) { }
}
