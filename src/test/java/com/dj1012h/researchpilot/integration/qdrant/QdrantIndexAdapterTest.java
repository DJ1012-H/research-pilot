package com.dj1012h.researchpilot.integration.qdrant;

import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.rag.RagPointPayload;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjection;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexException;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexFailureType;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchHit;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QdrantIndexAdapterTest {

    private static final String BASE_URL = "http://127.0.0.1:6333";
    private static final RagIndexDefinition DEFINITION = new RagIndexDefinition("test_collection", "test-v1", 2);

    @Test
    void shouldCreateCollectionAndRequiredPayloadIndexes() {
        Fixture fixture = fixture(32);
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                        {"vectors":{"size":2,"distance":"Cosine"}}
                        """))
                .andRespond(withSuccess("{\"status\":\"ok\",\"result\":true}", MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection"))
                .andRespond(withSuccess(collectionInfo("{}"), MediaType.APPLICATION_JSON));
        for (int index = 0; index < 5; index++) {
            fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection/index?wait=true"))
                    .andExpect(method(HttpMethod.PUT))
                    .andRespond(withSuccess(operationOk(), MediaType.APPLICATION_JSON));
        }
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection"))
                .andRespond(withSuccess(collectionInfo(requiredSchemas()), MediaType.APPLICATION_JSON));

        fixture.adapter.ensureCollection(DEFINITION);

        fixture.server.verify();
    }

    @Test
    void shouldFailClosedOnCollectionDimensionMismatch() {
        Fixture fixture = fixture(32);
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection"))
                .andRespond(withSuccess(collectionInfo(3, requiredSchemas()), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.adapter.ensureCollection(DEFINITION))
                .isInstanceOfSatisfying(RagIndexException.class,
                        exception -> assertThat(exception.failureType())
                                .isEqualTo(RagIndexFailureType.COLLECTION_MISMATCH));
        fixture.server.verify();
    }

    @Test
    void shouldBatchUpsertsAndSurfaceAPartialBatchFailureForSafeRetry() {
        Fixture fixture = fixture(1);
        VerifiedPaperProjection first = projection(1L, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        VerifiedPaperProjection second = projection(2L, UUID.fromString("00000000-0000-0000-0000-000000000002"));
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection/points?wait=true"))
                .andExpect(content().json("""
                        {"points":[{"id":"00000000-0000-0000-0000-000000000001","vector":[0.25,0.75]}]}
                        """))
                .andRespond(withSuccess(operationOk(), MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection/points?wait=true"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> fixture.adapter.upsert(DEFINITION, List.of(first, second)))
                .isInstanceOfSatisfying(RagIndexException.class,
                        exception -> assertThat(exception.failureType())
                                .isEqualTo(RagIndexFailureType.HTTP_FAILURE));
        fixture.server.verify();
    }

    @Test
    void shouldReadReplaceCountAndDeleteContractPayloads() {
        Fixture fixture = fixture(32);
        RagPointPayload payload = projection(
                7L,
                UUID.fromString("00000000-0000-0000-0000-000000000007")).payload();
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection/points/scroll"))
                .andExpect(content().json("""
                        {"filter":{"must":[{"key":"embeddingVersion","match":{"value":"test-v1"}}]},
                         "limit":256,"with_payload":true,"with_vector":false}
                        """))
                .andRespond(withSuccess(scrollResponse(payload), MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection/points/payload?wait=true"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(operationOk(), MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection/points/count"))
                .andRespond(withSuccess("{\"status\":\"ok\",\"result\":{\"count\":1}}", MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(BASE_URL
                        + "/collections/test_collection/points/00000000-0000-0000-0000-000000000007"
                        + "?with_payload=true&with_vector=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(pointResponse(payload, "[0.25,0.75]"), MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection/points/query"))
                .andExpect(content().json("""
                        {"query":[0.25,0.75],"filter":{"must":[
                          {"key":"embeddingVersion","match":{"value":"test-v1"}},
                          {"key":"verificationStatus","match":{"value":"VERIFIED"}},
                          {"key":"paperId","match":{"value":7}}]},"limit":1,
                          "with_payload":true,"with_vector":false}
                        """))
                .andRespond(withSuccess(queryResponse(payload), MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection/points/delete?wait=true"))
                .andExpect(content().json("""
                        {"points":["00000000-0000-0000-0000-000000000007"]}
                        """))
                .andRespond(withSuccess(operationOk(), MediaType.APPLICATION_JSON));

        assertThat(fixture.adapter.listPayloads(DEFINITION)).containsExactly(payload);
        fixture.adapter.replacePayloads(DEFINITION, List.of(payload));
        assertThat(fixture.adapter.count(DEFINITION)).isEqualTo(1L);
        fixture.adapter.validateForActivation(DEFINITION, payload);
        fixture.adapter.deletePoints(DEFINITION, List.of(payload.pointId()));

        fixture.server.verify();
    }

    @Test
    void shouldFailActivationWhenStoredPointVectorDoesNotMatchCollectionContract() {
        Fixture fixture = fixture(32);
        RagPointPayload payload = projection(
                7L,
                UUID.fromString("00000000-0000-0000-0000-000000000007")).payload();
        fixture.server.expect(requestTo(BASE_URL
                        + "/collections/test_collection/points/00000000-0000-0000-0000-000000000007"
                        + "?with_payload=true&with_vector=true"))
                .andRespond(withSuccess(pointResponse(payload, "[0.25]"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.adapter.validateForActivation(DEFINITION, payload))
                .isInstanceOfSatisfying(RagIndexException.class,
                        exception -> assertThat(exception.failureType())
                                .isEqualTo(RagIndexFailureType.POINT_MISMATCH));
        fixture.server.verify();
    }

    @Test
    void shouldForceTrustedVersionAndControlledFiltersForSearch() {
        Fixture fixture = fixture(32);
        RagPointPayload payload = projection(
                7L,
                UUID.fromString("00000000-0000-0000-0000-000000000007")).payload();
        fixture.server.expect(requestTo(BASE_URL + "/collections/test_collection/points/query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"query":[0.25,0.75],"filter":{"must":[
                          {"key":"embeddingVersion","match":{"value":"test-v1"}},
                          {"key":"verificationStatus","match":{"value":"VERIFIED"}},
                          {"key":"publicationYear","range":{"gte":2020,"lte":2026}},
                          {"key":"paperId","match":{"any":[7]}},
                          {"key":"segmentType","match":{"any":["ABSTRACT","METADATA"]}}
                        ]},"limit":5,"with_payload":true,"with_vector":false}
                        """))
                .andRespond(withSuccess(queryResponseWithScore(payload), MediaType.APPLICATION_JSON));

        List<RagIndexSearchHit> hits = fixture.adapter.search(
                DEFINITION,
                new RagIndexSearchRequest(
                        List.of(0.25, 0.75), 5, 2020, 2026, Set.of(7L),
                        Set.of(RagSegmentType.METADATA, RagSegmentType.ABSTRACT)));

        assertThat(hits).singleElement().satisfies(hit -> assertThat(hit.score()).isEqualTo(0.75));
        fixture.server.verify();
    }

    private Fixture fixture(int batchSize) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        QdrantProperties properties = new QdrantProperties();
        properties.setEnabled(true);
        properties.setBatchSize(batchSize);
        return new Fixture(new QdrantIndexAdapter(builder.build(), properties), server);
    }

    private VerifiedPaperProjection projection(long paperId, UUID pointId) {
        return new VerifiedPaperProjection(
                pointId,
                paperId,
                "10.1000/example" + paperId,
                "Controlled title " + paperId,
                2026,
                "Controlled venue",
                "en",
                VerificationResult.VerificationStatus.VERIFIED,
                "verification-v1",
                RagSegmentType.METADATA,
                0,
                "test-model",
                "test-v1",
                "a".repeat(64),
                Instant.parse("2026-08-10T00:00:00Z"),
                "Title: Controlled title " + paperId,
                List.of(0.25, 0.75),
                2,
                Duration.ofMillis(1));
    }

    private String collectionInfo(String payloadSchema) {
        return collectionInfo(2, payloadSchema);
    }

    private String collectionInfo(int dimensions, String payloadSchema) {
        return """
                {"status":"ok","result":{"config":{"params":{"vectors":{"size":%d,"distance":"Cosine"}}},
                "payload_schema":%s}}
                """.formatted(dimensions, payloadSchema);
    }

    private String requiredSchemas() {
        return """
                {"paperId":{"data_type":"integer"},"doi":{"data_type":"keyword"},
                 "verificationStatus":{"data_type":"keyword"},"publicationYear":{"data_type":"integer"},
                 "embeddingVersion":{"data_type":"keyword"}}
                """;
    }

    private String operationOk() {
        return "{\"status\":\"ok\",\"result\":{\"status\":\"completed\",\"operation_id\":1}}";
    }

    private String scrollResponse(RagPointPayload payload) {
        return """
                {"status":"ok","result":{"points":[{"id":"%s","payload":{
                  "paperId":%d,"doi":"%s","title":"%s","publicationYear":%d,
                  "venue":"%s","language":"%s","verificationStatus":"%s",
                  "verificationVersion":"%s","segmentType":"%s","segmentIndex":%d,
                  "embeddingModel":"%s","embeddingVersion":"%s","contentHash":"%s",
                  "sourceUpdatedAt":"%s","text":"%s"}}],"next_page_offset":null}}
                """.formatted(
                payload.pointId(), payload.paperId(), payload.doi(), payload.title(), payload.publicationYear(),
                payload.venue(), payload.language(), payload.verificationStatus(), payload.verificationVersion(),
                payload.segmentType(), payload.segmentIndex(), payload.embeddingModel(), payload.embeddingVersion(),
                payload.contentHash(), payload.sourceUpdatedAt(), payload.text());
    }

    private String queryResponse(RagPointPayload payload) {
        String scroll = scrollResponse(payload);
        return scroll.replace("\"next_page_offset\":null", "\"sample\":true");
    }

    private String queryResponseWithScore(RagPointPayload payload) {
        return scrollResponse(payload)
                .replace("\"}}],\"next_page_offset\":null", "\"},\"score\":0.75}],\"next_page_offset\":null");
    }

    private String pointResponse(RagPointPayload payload, String vectorJson) {
        return """
                {"status":"ok","result":{"id":"%s","payload":{
                  "paperId":%d,"doi":"%s","title":"%s","publicationYear":%d,
                  "venue":"%s","language":"%s","verificationStatus":"%s",
                  "verificationVersion":"%s","segmentType":"%s","segmentIndex":%d,
                  "embeddingModel":"%s","embeddingVersion":"%s","contentHash":"%s",
                  "sourceUpdatedAt":"%s","text":"%s"},"vector":%s}}
                """.formatted(
                payload.pointId(), payload.paperId(), payload.doi(), payload.title(), payload.publicationYear(),
                payload.venue(), payload.language(), payload.verificationStatus(), payload.verificationVersion(),
                payload.segmentType(), payload.segmentIndex(), payload.embeddingModel(), payload.embeddingVersion(),
                payload.contentHash(), payload.sourceUpdatedAt(), payload.text(), vectorJson);
    }

    private record Fixture(QdrantIndexAdapter adapter, MockRestServiceServer server) { }
}
