package com.dj1012h.researchpilot.integration.ollama;

import com.dj1012h.researchpilot.integration.ollama.dto.OllamaEmbedRequest;
import com.dj1012h.researchpilot.integration.ollama.dto.OllamaEmbedResponse;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingBatch;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingException;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingFailureType;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingPort;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingVector;
import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Windows-native Ollama adapter for the provider-neutral embedding port. */
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final RestClient restClient;
    private final RagEmbeddingProfile profile;
    private final boolean enabled;

    public OllamaEmbeddingAdapter(RestClient restClient, OllamaEmbeddingProperties properties) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        this.profile = properties.profile();
        this.enabled = properties.isEnabled();
    }

    @Override
    public EmbeddingBatch embed(List<String> controlledTexts) {
        if (!enabled) {
            throw new EmbeddingException(EmbeddingFailureType.DISABLED, "Ollama embedding is disabled");
        }
        List<String> inputs = validateInputs(controlledTexts);
        long startedAt = System.nanoTime();
        try {
            OllamaEmbedResponse response = restClient.post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OllamaEmbedRequest(profile.model(), inputs))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        throw new EmbeddingException(
                                EmbeddingFailureType.HTTP_FAILURE,
                                "Ollama embedding request failed with HTTP " + clientResponse.getStatusCode().value());
                    })
                    .body(OllamaEmbedResponse.class);
            return validatedBatch(response, inputs.size(), elapsed(startedAt));
        } catch (EmbeddingException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new EmbeddingException(
                    EmbeddingFailureType.TRANSPORT_FAILURE,
                    "Ollama embedding transport failed",
                    exception);
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new EmbeddingException(
                    EmbeddingFailureType.INVALID_RESPONSE,
                    "Ollama embedding response could not be parsed",
                    exception);
        }
    }

    EmbeddingBatch validatedBatch(OllamaEmbedResponse response, int expectedCount, Duration elapsed) {
        if (response == null || response.embeddings() == null) {
            throw new EmbeddingException(
                    EmbeddingFailureType.MISSING_EMBEDDINGS,
                    "Ollama response is missing embeddings");
        }
        List<List<Double>> rawVectors = response.embeddings();
        if (rawVectors.size() != expectedCount) {
            throw new EmbeddingException(
                    EmbeddingFailureType.VECTOR_COUNT_MISMATCH,
                    "Ollama response vector count does not match the request");
        }

        int measuredDimensions = -1;
        List<EmbeddingVector> vectors = new ArrayList<>(rawVectors.size());
        for (List<Double> rawVector : rawVectors) {
            if (rawVector == null || rawVector.isEmpty()) {
                throw new EmbeddingException(
                        EmbeddingFailureType.EMPTY_VECTOR,
                        "Ollama response contains an empty embedding vector");
            }
            if (measuredDimensions < 0) {
                measuredDimensions = rawVector.size();
            } else if (rawVector.size() != measuredDimensions) {
                throw new EmbeddingException(
                        EmbeddingFailureType.DIMENSION_MISMATCH,
                        "Ollama response vectors have inconsistent dimensions");
            }
            if (rawVector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new EmbeddingException(
                        EmbeddingFailureType.NON_FINITE_VECTOR,
                        "Ollama response contains a non-finite embedding value");
            }
            vectors.add(new EmbeddingVector(rawVector));
        }
        if (measuredDimensions != profile.expectedDimensions()) {
            throw new EmbeddingException(
                    EmbeddingFailureType.DIMENSION_MISMATCH,
                    "Ollama response dimension does not match the configured embedding version");
        }
        return new EmbeddingBatch(profile.model(), vectors, measuredDimensions, elapsed);
    }

    private List<String> validateInputs(List<String> controlledTexts) {
        if (controlledTexts == null || controlledTexts.isEmpty()) {
            throw new EmbeddingException(
                    EmbeddingFailureType.INVALID_INPUT,
                    "at least one controlled embedding input is required");
        }
        List<String> inputs;
        try {
            inputs = List.copyOf(controlledTexts);
        } catch (NullPointerException exception) {
            throw new EmbeddingException(
                    EmbeddingFailureType.INVALID_INPUT,
                    "controlled embedding inputs must not contain null",
                    exception);
        }
        if (inputs.stream().anyMatch(String::isBlank)) {
            throw new EmbeddingException(
                    EmbeddingFailureType.INVALID_INPUT,
                    "controlled embedding inputs must not be blank");
        }
        return inputs;
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
    }
}
