package com.dj1012h.researchpilot.integration.qdrant;

import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.rag.RagPointPayload;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjection;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexException;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexFailureType;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexPort;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexProbe;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchHit;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Qdrant REST adapter. Provider request and response contracts stay in this package. */
public class QdrantIndexAdapter implements RagIndexPort {

    private static final Map<String, String> REQUIRED_PAYLOAD_INDEXES = Map.of(
            "paperId", "integer",
            "doi", "keyword",
            "verificationStatus", "keyword",
            "publicationYear", "integer",
            "embeddingVersion", "keyword");

    private final RestClient restClient;
    private final boolean enabled;
    private final int batchSize;
    private final int scrollPageSize;

    public QdrantIndexAdapter(RestClient restClient, QdrantProperties properties) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        this.enabled = properties.isEnabled();
        if (properties.getBatchSize() < 1 || properties.getBatchSize() > 1024) {
            throw new IllegalArgumentException("Qdrant batchSize must be between 1 and 1024");
        }
        if (properties.getScrollPageSize() < 1 || properties.getScrollPageSize() > 10000) {
            throw new IllegalArgumentException("Qdrant scrollPageSize must be between 1 and 10000");
        }
        this.batchSize = properties.getBatchSize();
        this.scrollPageSize = properties.getScrollPageSize();
    }

    @Override
    public void ensureCollection(RagIndexDefinition definition) {
        requireEnabled();
        Objects.requireNonNull(definition, "definition must not be null");
        CollectionInfoResponse info = collectionInfo(definition.collectionName());
        if (info == null) {
            createCollection(definition);
            info = collectionInfo(definition.collectionName());
        }
        validateCollection(info, definition);
        ensurePayloadIndexes(definition, info.result().payloadSchema());
        CollectionInfoResponse refreshed = requireCollectionInfo(definition.collectionName());
        validateCollection(refreshed, definition);
        validatePayloadIndexes(refreshed.result().payloadSchema());
    }

    @Override
    public List<RagPointPayload> listPayloads(RagIndexDefinition definition) {
        requireEnabled();
        Objects.requireNonNull(definition, "definition must not be null");
        List<RagPointPayload> payloads = new ArrayList<>();
        Object offset = null;
        do {
            Object pageOffset = offset;
            ScrollResponse response = execute(() -> restClient.post()
                    .uri("/collections/{collection}/points/scroll", definition.collectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ScrollRequest(
                            versionFilter(definition.embeddingVersion()),
                            scrollPageSize,
                            true,
                            false,
                            pageOffset))
                    .retrieve()
                    .body(ScrollResponse.class));
            requireOk(response == null ? null : response.status(), response == null ? null : response.result());
            if (response.result().points() == null) {
                throw invalid("Qdrant scroll response is missing points");
            }
            for (ScrolledPoint point : response.result().points()) {
                payloads.add(toPayload(point, definition));
            }
            offset = response.result().nextPageOffset();
        } while (offset != null);
        return List.copyOf(payloads);
    }

    @Override
    public void upsert(RagIndexDefinition definition, List<VerifiedPaperProjection> projections) {
        requireEnabled();
        Objects.requireNonNull(definition, "definition must not be null");
        List<VerifiedPaperProjection> items = immutable(projections, "projections");
        for (VerifiedPaperProjection projection : items) {
            validateProjection(definition, projection);
        }
        forEachBatch(items, batch -> {
            List<UpsertPoint> points = batch.stream()
                    .map(projection -> new UpsertPoint(
                            projection.pointId().toString(),
                            projection.vector(),
                            PointPayload.from(projection.payload())))
                    .toList();
            OperationResponse response = execute(() -> restClient.put()
                    .uri("/collections/{collection}/points?wait=true", definition.collectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UpsertRequest(points))
                    .retrieve()
                    .body(OperationResponse.class));
            requireOk(response == null ? null : response.status(), response == null ? null : response.result());
        });
    }

    @Override
    public void replacePayloads(RagIndexDefinition definition, List<RagPointPayload> payloads) {
        requireEnabled();
        Objects.requireNonNull(definition, "definition must not be null");
        for (RagPointPayload payload : immutable(payloads, "payloads")) {
            validatePayload(definition, payload);
            OperationResponse response = execute(() -> restClient.put()
                    .uri("/collections/{collection}/points/payload?wait=true", definition.collectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReplacePayloadRequest(PointPayload.from(payload), List.of(payload.pointId().toString())))
                    .retrieve()
                    .body(OperationResponse.class));
            requireOk(response == null ? null : response.status(), response == null ? null : response.result());
        }
    }

    @Override
    public void deletePoints(RagIndexDefinition definition, List<UUID> pointIds) {
        requireEnabled();
        Objects.requireNonNull(definition, "definition must not be null");
        List<UUID> ids = immutable(pointIds, "pointIds");
        forEachBatch(ids, batch -> {
            OperationResponse response = execute(() -> restClient.post()
                    .uri("/collections/{collection}/points/delete?wait=true", definition.collectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new DeletePointsRequest(batch.stream().map(UUID::toString).toList()))
                    .retrieve()
                    .body(OperationResponse.class));
            requireOk(response == null ? null : response.status(), response == null ? null : response.result());
        });
    }

    @Override
    public long count(RagIndexDefinition definition) {
        requireEnabled();
        Objects.requireNonNull(definition, "definition must not be null");
        CountResponse response = execute(() -> restClient.post()
                .uri("/collections/{collection}/points/count", definition.collectionName())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CountRequest(versionFilter(definition.embeddingVersion()), true))
                .retrieve()
                .body(CountResponse.class));
        requireOk(response == null ? null : response.status(), response == null ? null : response.result());
        if (response.result().count() < 0) throw invalid("Qdrant returned a negative point count");
        return response.result().count();
    }

    @Override
    public void validateForActivation(RagIndexDefinition definition, RagPointPayload sample) {
        requireEnabled();
        Objects.requireNonNull(definition, "definition must not be null");
        validatePayload(definition, sample);
        PointResponse pointResponse = execute(() -> restClient.get()
                .uri(
                        "/collections/{collection}/points/{point}?with_payload=true&with_vector=true",
                        definition.collectionName(),
                        sample.pointId())
                .retrieve()
                .body(PointResponse.class));
        requireOk(
                pointResponse == null ? null : pointResponse.status(),
                pointResponse == null ? null : pointResponse.result());
        ScrolledPoint activationPoint = pointResponse.result();
        RagPointPayload stored = toPayload(activationPoint, definition);
        if (!sample.equals(stored)) {
            throw new RagIndexException(
                    RagIndexFailureType.POINT_MISMATCH,
                    "Qdrant activation point payload does not match the rebuild sample");
        }
        List<Double> queryVector = requireActivationVector(activationPoint.vector(), definition);
        Filter filter = new Filter(List.of(
                new FieldCondition("embeddingVersion", new MatchValue(definition.embeddingVersion())),
                new FieldCondition("verificationStatus", new MatchValue("VERIFIED")),
                new FieldCondition("paperId", new MatchValue(sample.paperId()))));
        QueryResponse response = execute(() -> restClient.post()
                .uri("/collections/{collection}/points/query", definition.collectionName())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new QueryRequest(queryVector, filter, 1, true, false))
                .retrieve()
                .body(QueryResponse.class));
        requireOk(response == null ? null : response.status(), response == null ? null : response.result());
        if (response.result().points() == null || response.result().points().isEmpty()) {
            throw new RagIndexException(
                    RagIndexFailureType.POINT_MISMATCH,
                    "Qdrant activation sample query returned no point");
        }
        RagPointPayload returned = toPayload(response.result().points().getFirst(), definition);
        if (!sample.equals(returned)) {
            throw new RagIndexException(
                    RagIndexFailureType.POINT_MISMATCH,
                    "Qdrant activation sample query returned a different point payload");
        }
    }

    private List<Double> requireActivationVector(List<Double> vector, RagIndexDefinition definition) {
        if (vector == null
                || vector.size() != definition.vectorDimensions()
                || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new RagIndexException(
                    RagIndexFailureType.POINT_MISMATCH,
                    "Qdrant activation point vector does not match the frozen collection contract");
        }
        return List.copyOf(vector);
    }

    @Override
    public List<RagIndexSearchHit> search(
            RagIndexDefinition definition,
            RagIndexSearchRequest request
    ) {
        requireEnabled();
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(request, "request must not be null");
        if (request.queryVector().size() != definition.vectorDimensions()) {
            throw new RagIndexException(
                    RagIndexFailureType.POINT_MISMATCH,
                    "query vector does not match the active collection dimension");
        }
        QueryResponse response = execute(() -> restClient.post()
                .uri("/collections/{collection}/points/query", definition.collectionName())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new QueryRequest(
                        request.queryVector(),
                        searchFilter(definition, request),
                        request.limit(),
                        true,
                        false))
                .retrieve()
                .body(QueryResponse.class));
        requireOk(response == null ? null : response.status(), response == null ? null : response.result());
        if (response.result().points() == null) {
            throw invalid("Qdrant query response is missing points");
        }
        List<RagIndexSearchHit> hits = new ArrayList<>(response.result().points().size());
        for (ScrolledPoint point : response.result().points()) {
            if (point == null || point.score() == null || !Double.isFinite(point.score())) {
                throw invalid("Qdrant query returned a missing or non-finite score");
            }
            hits.add(new RagIndexSearchHit(toPayload(point, definition), point.score()));
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(RagIndexSearchHit::score).reversed()
                        .thenComparing(hit -> hit.payload().paperId())
                        .thenComparing(hit -> hit.payload().pointId()))
                .toList();
    }

    @Override
    public RagIndexProbe probe() {
        if (!enabled) return new RagIndexProbe(false, "Qdrant indexing is disabled");
        try {
            String response = execute(() -> restClient.get().uri("/readyz").retrieve().body(String.class));
            if (response == null || response.isBlank()) return new RagIndexProbe(false, "Qdrant readiness was empty");
            return new RagIndexProbe(true, "Qdrant readiness probe succeeded");
        } catch (RagIndexException exception) {
            return new RagIndexProbe(false, exception.failureType().name());
        }
    }

    private CollectionInfoResponse collectionInfo(String collectionName) {
        try {
            return execute(() -> restClient.get()
                    .uri("/collections/{collection}", collectionName)
                    .retrieve()
                    .body(CollectionInfoResponse.class));
        } catch (RagIndexException exception) {
            if (exception.getCause() instanceof RestClientResponseException response
                    && response.getStatusCode().value() == 404) {
                return null;
            }
            throw exception;
        }
    }

    private CollectionInfoResponse requireCollectionInfo(String collectionName) {
        CollectionInfoResponse response = collectionInfo(collectionName);
        if (response == null) throw invalid("Qdrant collection disappeared during initialization");
        return response;
    }

    private void createCollection(RagIndexDefinition definition) {
        BooleanResultResponse response = execute(() -> restClient.put()
                .uri("/collections/{collection}", definition.collectionName())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCollectionRequest(new VectorParams(definition.vectorDimensions(), "Cosine")))
                .retrieve()
                .body(BooleanResultResponse.class));
        requireOk(response == null ? null : response.status(), response == null ? null : response.result());
        if (!Boolean.TRUE.equals(response.result())) throw invalid("Qdrant did not confirm collection creation");
    }

    private void ensurePayloadIndexes(
            RagIndexDefinition definition,
            Map<String, PayloadSchema> existingSchemas
    ) {
        Map<String, PayloadSchema> schemas = existingSchemas == null ? Map.of() : existingSchemas;
        for (Map.Entry<String, String> required : REQUIRED_PAYLOAD_INDEXES.entrySet()) {
            PayloadSchema existing = schemas.get(required.getKey());
            if (existing != null) {
                if (!required.getValue().equalsIgnoreCase(existing.dataType())) {
                    throw new RagIndexException(
                            RagIndexFailureType.COLLECTION_MISMATCH,
                            "Qdrant payload index type does not match the frozen contract");
                }
                continue;
            }
            OperationResponse response = execute(() -> restClient.put()
                    .uri("/collections/{collection}/index?wait=true", definition.collectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreatePayloadIndexRequest(required.getKey(), required.getValue()))
                    .retrieve()
                    .body(OperationResponse.class));
            requireOk(response == null ? null : response.status(), response == null ? null : response.result());
        }
    }

    private void validatePayloadIndexes(Map<String, PayloadSchema> schemas) {
        if (schemas == null) {
            throw new RagIndexException(
                    RagIndexFailureType.COLLECTION_MISMATCH,
                    "Qdrant collection is missing required payload indexes");
        }
        for (Map.Entry<String, String> required : REQUIRED_PAYLOAD_INDEXES.entrySet()) {
            PayloadSchema schema = schemas.get(required.getKey());
            if (schema == null || schema.dataType() == null
                    || !required.getValue().equalsIgnoreCase(schema.dataType())) {
                throw new RagIndexException(
                        RagIndexFailureType.COLLECTION_MISMATCH,
                        "Qdrant collection is missing a required payload index");
            }
        }
    }

    private void validateCollection(CollectionInfoResponse response, RagIndexDefinition definition) {
        requireOk(response == null ? null : response.status(), response == null ? null : response.result());
        if (response.result().config() == null || response.result().config().params() == null
                || response.result().config().params().vectors() == null) {
            throw invalid("Qdrant collection response is missing vector configuration");
        }
        VectorParams vectors = response.result().config().params().vectors();
        if (vectors.size() != definition.vectorDimensions()
                || vectors.distance() == null
                || !"Cosine".equalsIgnoreCase(vectors.distance())) {
            throw new RagIndexException(
                    RagIndexFailureType.COLLECTION_MISMATCH,
                    "Qdrant collection vector configuration does not match the embedding version");
        }
    }

    private RagPointPayload toPayload(ScrolledPoint point, RagIndexDefinition definition) {
        if (point == null || point.id() == null || point.payload() == null) {
            throw invalid("Qdrant scroll returned an incomplete point");
        }
        try {
            PointPayload payload = point.payload();
            RagPointPayload result = new RagPointPayload(
                    UUID.fromString(point.id()),
                    payload.paperId(),
                    payload.doi(),
                    payload.title(),
                    payload.publicationYear(),
                    payload.venue(),
                    payload.language(),
                    VerificationResult.VerificationStatus.valueOf(payload.verificationStatus()),
                    payload.verificationVersion(),
                    RagSegmentType.valueOf(payload.segmentType()),
                    payload.segmentIndex(),
                    payload.embeddingModel(),
                    payload.embeddingVersion(),
                    payload.contentHash(),
                    Instant.parse(payload.sourceUpdatedAt()),
                    payload.text());
            validatePayload(definition, result);
            return result;
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new RagIndexException(
                    RagIndexFailureType.POINT_MISMATCH,
                    "Qdrant point payload does not match the frozen contract",
                    exception);
        }
    }

    private void validateProjection(RagIndexDefinition definition, VerifiedPaperProjection projection) {
        Objects.requireNonNull(projection, "projection must not be null");
        validatePayload(definition, projection.payload());
        if (projection.vectorDimensions() != definition.vectorDimensions()) {
            throw new RagIndexException(
                    RagIndexFailureType.POINT_MISMATCH,
                    "projection dimension does not match the target collection");
        }
    }

    private void validatePayload(RagIndexDefinition definition, RagPointPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        if (!definition.embeddingVersion().equals(payload.embeddingVersion())) {
            throw new RagIndexException(
                    RagIndexFailureType.POINT_MISMATCH,
                    "point embedding version does not match the target collection");
        }
    }

    private Filter versionFilter(String embeddingVersion) {
        return new Filter(List.of(new FieldCondition(
                "embeddingVersion",
                new MatchValue(embeddingVersion))));
    }

    private Filter searchFilter(RagIndexDefinition definition, RagIndexSearchRequest request) {
        List<FieldCondition> conditions = new ArrayList<>();
        conditions.add(new FieldCondition(
                "embeddingVersion",
                new MatchValue(definition.embeddingVersion())));
        conditions.add(new FieldCondition("verificationStatus", new MatchValue("VERIFIED")));
        if (request.fromYear() != null || request.toYear() != null) {
            conditions.add(new FieldCondition(
                    "publicationYear",
                    null,
                    new Range(request.fromYear(), request.toYear())));
        }
        if (!request.paperIds().isEmpty()) {
            conditions.add(new FieldCondition(
                    "paperId",
                    new MatchValue(null, request.paperIds().stream().sorted().toList()),
                    null));
        }
        if (!request.segmentTypes().isEmpty()) {
            conditions.add(new FieldCondition(
                    "segmentType",
                    new MatchValue(null, request.segmentTypes().stream().map(Enum::name).sorted().toList()),
                    null));
        }
        return new Filter(List.copyOf(conditions));
    }

    private <T> List<T> immutable(List<T> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        try {
            return List.copyOf(values);
        } catch (NullPointerException exception) {
            throw new IllegalArgumentException(field + " must not contain null", exception);
        }
    }

    private <T> void forEachBatch(List<T> items, java.util.function.Consumer<List<T>> consumer) {
        for (int start = 0; start < items.size(); start += batchSize) {
            consumer.accept(items.subList(start, Math.min(items.size(), start + batchSize)));
        }
    }

    private void requireEnabled() {
        if (!enabled) throw new RagIndexException(RagIndexFailureType.DISABLED, "Qdrant indexing is disabled");
    }

    private void requireOk(String status, Object result) {
        if (!"ok".equalsIgnoreCase(status) || result == null) {
            throw invalid("Qdrant response did not contain a successful result");
        }
    }

    private RagIndexException invalid(String message) {
        return new RagIndexException(RagIndexFailureType.INVALID_RESPONSE, message);
    }

    private <T> T execute(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RagIndexException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new RagIndexException(
                    RagIndexFailureType.TRANSPORT_FAILURE,
                    "Qdrant transport failed",
                    exception);
        } catch (RestClientResponseException exception) {
            throw new RagIndexException(
                    RagIndexFailureType.HTTP_FAILURE,
                    "Qdrant request failed with HTTP " + exception.getStatusCode().value(),
                    exception);
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new RagIndexException(
                    RagIndexFailureType.INVALID_RESPONSE,
                    "Qdrant response could not be parsed",
                    exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CollectionInfoResponse(String status, CollectionResult result) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CollectionResult(
            CollectionConfig config,
            @JsonProperty("payload_schema") Map<String, PayloadSchema> payloadSchema
    ) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CollectionConfig(CollectionParams params) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CollectionParams(VectorParams vectors) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VectorParams(int size, String distance) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PayloadSchema(@JsonProperty("data_type") String dataType) { }
    private record CreateCollectionRequest(VectorParams vectors) { }
    private record CreatePayloadIndexRequest(
            @JsonProperty("field_name") String fieldName,
            @JsonProperty("field_schema") String fieldSchema
    ) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BooleanResultResponse(String status, Boolean result) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OperationResponse(String status, Map<String, Object> result) { }
    private record UpsertRequest(List<UpsertPoint> points) { }
    private record UpsertPoint(String id, List<Double> vector, PointPayload payload) { }
    private record ReplacePayloadRequest(PointPayload payload, List<String> points) { }
    private record DeletePointsRequest(List<String> points) { }
    private record CountRequest(Filter filter, boolean exact) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CountResponse(String status, CountResult result) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CountResult(long count) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ScrollRequest(
            Filter filter,
            int limit,
            @JsonProperty("with_payload") boolean withPayload,
            @JsonProperty("with_vector") boolean withVector,
            Object offset
    ) { }
    private record Filter(List<FieldCondition> must) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record FieldCondition(
            String key,
            MatchValue match,
            Range range
    ) {
        private FieldCondition(String key, MatchValue match) {
            this(key, match, null);
        }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MatchValue(Object value, List<?> any) {
        private MatchValue(Object value) {
            this(value, null);
        }
    }
    private record Range(Integer gte, Integer lte) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScrollResponse(String status, ScrollResult result) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PointResponse(String status, ScrolledPoint result) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScrollResult(
            List<ScrolledPoint> points,
            @JsonProperty("next_page_offset") Object nextPageOffset
    ) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScrolledPoint(
            String id,
            PointPayload payload,
            List<Double> vector,
            Double score
    ) { }
    private record QueryRequest(
            Object query,
            Filter filter,
            int limit,
            @JsonProperty("with_payload") boolean withPayload,
            @JsonProperty("with_vector") boolean withVector
    ) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QueryResponse(String status, QueryResult result) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QueryResult(List<ScrolledPoint> points) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PointPayload(
            long paperId,
            String doi,
            String title,
            Integer publicationYear,
            String venue,
            String language,
            String verificationStatus,
            String verificationVersion,
            String segmentType,
            int segmentIndex,
            String embeddingModel,
            String embeddingVersion,
            String contentHash,
            String sourceUpdatedAt,
            String text
    ) {
        private static PointPayload from(RagPointPayload payload) {
            return new PointPayload(
                    payload.paperId(),
                    payload.doi(),
                    payload.title(),
                    payload.publicationYear(),
                    payload.venue(),
                    payload.language(),
                    payload.verificationStatus().name(),
                    payload.verificationVersion(),
                    payload.segmentType().name(),
                    payload.segmentIndex(),
                    payload.embeddingModel(),
                    payload.embeddingVersion(),
                    payload.contentHash(),
                    payload.sourceUpdatedAt().toString(),
                    payload.text());
        }
    }
}
