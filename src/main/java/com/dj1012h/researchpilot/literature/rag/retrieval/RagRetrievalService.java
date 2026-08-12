package com.dj1012h.researchpilot.literature.rag.retrieval;

import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.rag.RagDocumentBuilder;
import com.dj1012h.researchpilot.literature.rag.RagDocumentSegment;
import com.dj1012h.researchpilot.literature.rag.RagPaperDocument;
import com.dj1012h.researchpilot.literature.rag.RagPointPayload;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingBatch;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingException;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingFailureType;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexException;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexFailureType;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexPort;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchHit;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchRequest;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexStateStore;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexVersionState;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingPort;
import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Fail-closed orchestration from query embedding through MySQL re-admission. */
@Service
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);
    private static final String SUCCEEDED = "SUCCEEDED";

    private final RagRetrievalProperties properties;
    private final ObjectProvider<EmbeddingPort> embeddingPortProvider;
    private final ObjectProvider<RagEmbeddingProfile> embeddingProfileProvider;
    private final ObjectProvider<RagIndexPort> indexPortProvider;
    private final ObjectProvider<RagIndexStateStore> stateStoreProvider;
    private final ObjectProvider<TrustedPaperReadRepository> paperRepositoryProvider;
    private final DoiNormalizer doiNormalizer;
    private final RagDocumentBuilder documentBuilder;
    private final Clock clock;

    public RagRetrievalService(
            RagRetrievalProperties properties,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<RagEmbeddingProfile> embeddingProfileProvider,
            ObjectProvider<RagIndexPort> indexPortProvider,
            ObjectProvider<RagIndexStateStore> stateStoreProvider,
            ObjectProvider<TrustedPaperReadRepository> paperRepositoryProvider,
            DoiNormalizer doiNormalizer,
            RagDocumentBuilder documentBuilder,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.embeddingPortProvider = Objects.requireNonNull(embeddingPortProvider, "embeddingPortProvider must not be null");
        this.embeddingProfileProvider = Objects.requireNonNull(embeddingProfileProvider, "embeddingProfileProvider must not be null");
        this.indexPortProvider = Objects.requireNonNull(indexPortProvider, "indexPortProvider must not be null");
        this.stateStoreProvider = Objects.requireNonNull(stateStoreProvider, "stateStoreProvider must not be null");
        this.paperRepositoryProvider = Objects.requireNonNull(paperRepositoryProvider, "paperRepositoryProvider must not be null");
        this.doiNormalizer = Objects.requireNonNull(doiNormalizer, "doiNormalizer must not be null");
        this.documentBuilder = Objects.requireNonNull(documentBuilder, "documentBuilder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public RagRetrievalResult retrieve(RagRetrievalRequest request) {
        long startedAt = System.nanoTime();
        int requestedTopK = requestedTopK(request);
        if (!properties.isEnabled()) {
            return failure(requestedTopK, null, RagRetrievalFailureType.RAG_RETRIEVAL_DISABLED, startedAt, 0, 0, 0, 0);
        }

        RagSearchQuery query;
        try {
            query = validate(request);
        } catch (IllegalArgumentException exception) {
            return failure(requestedTopK, null, RagRetrievalFailureType.RAG_QUERY_INVALID, startedAt, 0, 0, 0, 0);
        }

        RagIndexVersionState active;
        try {
            RagIndexStateStore stateStore = stateStoreProvider.getIfAvailable();
            if (stateStore == null) {
                return failure(query.topK(), null, RagRetrievalFailureType.RAG_ACTIVE_VERSION_MISSING, startedAt, 0, 0, 0, 0);
            }
            active = stateStore.active().filter(this::isUsableActiveVersion).orElse(null);
            if (active == null) {
                return failure(query.topK(), null, RagRetrievalFailureType.RAG_ACTIVE_VERSION_MISSING, startedAt, 0, 0, 0, 0);
            }
        } catch (RuntimeException exception) {
            return failure(query.topK(), null, RagRetrievalFailureType.RAG_ACTIVE_VERSION_MISSING, startedAt, 0, 0, 0, 0);
        }

        int candidateLimit;
        try {
            candidateLimit = Math.min(
                    properties.getMaxCandidatePoints(),
                    Math.multiplyExact(query.topK(), properties.getCandidateMultiplier()));
        } catch (ArithmeticException exception) {
            return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_QUERY_INVALID, startedAt, 0, 0, 0, 0);
        }
        if (candidateLimit < query.topK()) {
            return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_QUERY_INVALID, startedAt, 0, 0, 0, 0);
        }

        RagEmbeddingProfile embeddingProfile = embeddingProfileProvider.getIfAvailable();
        if (embeddingProfile != null
                && !active.embeddingVersion().equals(embeddingProfile.version())) {
            return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_INDEX_VERSION_MISMATCH, startedAt, 0, 0, 0, 0);
        }
        if (embeddingProfile != null && embeddingProfile.expectedDimensions() != active.vectorDimensions()) {
            return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_EMBEDDING_DIMENSION_MISMATCH, startedAt, 0, 0, 0, 0);
        }

        EmbeddingBatch embedding;
        try {
            EmbeddingPort embeddingPort = embeddingPortProvider.getIfAvailable();
            if (embeddingPort == null) {
                return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_EMBEDDING_UNAVAILABLE, startedAt, 0, 0, 0, 0);
            }
            embedding = embeddingPort.embed(List.of(query.query()));
            if (embedding == null || embedding.embeddings().size() != 1) {
                return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_EMBEDDING_UNAVAILABLE, startedAt, 0, 0, 0, 0);
            }
            if (embedding.dimensions() != active.vectorDimensions()
                    || embedding.embeddings().getFirst().dimensions() != active.vectorDimensions()) {
                return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_EMBEDDING_DIMENSION_MISMATCH, startedAt, 0, 0, 0, 0);
            }
        } catch (EmbeddingException exception) {
            RagRetrievalFailureType code = exception.failureType() == EmbeddingFailureType.DIMENSION_MISMATCH
                    ? RagRetrievalFailureType.RAG_EMBEDDING_DIMENSION_MISMATCH
                    : RagRetrievalFailureType.RAG_EMBEDDING_UNAVAILABLE;
            return failure(query.topK(), active.embeddingVersion(), code, startedAt, 0, 0, 0, 0);
        } catch (RuntimeException exception) {
            return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_EMBEDDING_UNAVAILABLE, startedAt, 0, 0, 0, 0);
        }

        List<RagIndexSearchHit> candidates;
        try {
            RagIndexPort indexPort = indexPortProvider.getIfAvailable();
            if (indexPort == null) {
                return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_INDEX_UNAVAILABLE, startedAt, 0, 0, 0, 0);
            }
            RagIndexDefinition definition = new RagIndexDefinition(
                    active.collectionName(), active.embeddingVersion(), active.vectorDimensions());
            candidates = indexPort.search(
                    definition,
                    new RagIndexSearchRequest(
                            embedding.embeddings().getFirst().values(),
                            candidateLimit,
                            query.filter().fromYear(),
                            query.filter().toYear(),
                            query.filter().paperIds(),
                            query.filter().segmentTypes()));
            if (candidates == null) {
                return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_INDEX_RESPONSE_INVALID, startedAt, 0, 0, 0, 0);
            }
        } catch (RagIndexException exception) {
            return failure(query.topK(), active.embeddingVersion(), mapIndexFailure(exception.failureType()), startedAt, 0, 0, 0, 0);
        } catch (RuntimeException exception) {
            return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_INDEX_UNAVAILABLE, startedAt, 0, 0, 0, 0);
        }

        if (candidates.stream().anyMatch(Objects::isNull)) {
            return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_INDEX_RESPONSE_INVALID,
                    startedAt, candidates.size(), 0, 0, 0);
        }
        if (candidates.stream().anyMatch(hit -> !active.embeddingVersion().equals(hit.payload().embeddingVersion()))) {
            return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_INDEX_VERSION_MISMATCH,
                    startedAt, candidates.size(), 0, 0, 0);
        }
        List<RagIndexSearchHit> scopedCandidates = candidates.stream()
                .sorted(candidateOrder())
                .toList();
        Map<Long, RagIndexSearchHit> uniqueByPaper = new LinkedHashMap<>();
        scopedCandidates.forEach(hit -> uniqueByPaper.putIfAbsent(hit.payload().paperId(), hit));
        List<RagIndexSearchHit> uniqueCandidates = uniqueByPaper.values().stream()
                .sorted(candidateOrder())
                .toList();
        if (uniqueCandidates.isEmpty()) {
            return success(query.topK(), active.embeddingVersion(), candidates.size(), 0, 0, 0,
                    List.of(), RagRetrievalFailureType.RAG_NO_TRUSTED_RESULTS.name(), startedAt);
        }

        Map<Long, TrustedPaperRecord> currentByPaper;
        try {
            TrustedPaperReadRepository paperRepository = paperRepositoryProvider.getIfAvailable();
            if (paperRepository == null) {
                return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_TRUSTED_SOURCE_UNAVAILABLE, startedAt, candidates.size(), uniqueCandidates.size(), 0, 0);
            }
            currentByPaper = paperRepository.findByPaperIds(uniqueCandidates.stream()
                    .map(hit -> hit.payload().paperId())
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new)))
                    .stream()
                    .collect(Collectors.toMap(TrustedPaperRecord::paperId, value -> value, (left, right) -> left, HashMap::new));
        } catch (RuntimeException exception) {
            return failure(query.topK(), active.embeddingVersion(), RagRetrievalFailureType.RAG_TRUSTED_SOURCE_UNAVAILABLE, startedAt, candidates.size(), uniqueCandidates.size(), 0, 0);
        }

        List<RagSearchHit> admitted = new ArrayList<>();
        int filteredCount = 0;
        for (RagIndexSearchHit candidate : uniqueCandidates) {
            Optional<RagSearchHit> result = reAdmit(candidate, currentByPaper.get(candidate.payload().paperId()), query.filter());
            if (result.isPresent() && admitted.size() < query.topK()) {
                admitted.add(result.get());
            } else if (result.isEmpty()) {
                filteredCount++;
            }
        }
        String failureCode = admitted.isEmpty() ? RagRetrievalFailureType.RAG_NO_TRUSTED_RESULTS.name() : null;
        return success(query.topK(), active.embeddingVersion(), candidates.size(), uniqueCandidates.size(),
                admitted.size(), filteredCount, List.copyOf(admitted), failureCode, startedAt);
    }

    private RagSearchQuery validate(RagRetrievalRequest request) {
        if (request == null || request.query() == null) throw new IllegalArgumentException("query is required");
        String query = normalize(request.query());
        if (query.isEmpty() || query.length() > properties.getMaxQueryLength()) {
            throw new IllegalArgumentException("query length is outside the allowed range");
        }
        int topK = request.topK() == null ? properties.getDefaultTopK() : request.topK();
        if (topK < 1 || topK > properties.getMaxTopK()) throw new IllegalArgumentException("topK is outside the allowed range");
        if (request.paperIds().size() > properties.getMaxPaperIds()
                || request.paperIds().stream().anyMatch(id -> id == null || id < 1)) {
            throw new IllegalArgumentException("paperIds are outside the allowed range");
        }
        if (request.segmentTypes().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("segmentTypes must not contain null");
        }
        validateYear(request.fromYear());
        validateYear(request.toYear());
        if (request.fromYear() != null && request.toYear() != null && request.fromYear() > request.toYear()) {
            throw new IllegalArgumentException("year range is inverted");
        }
        return new RagSearchQuery(
                query,
                topK,
                new RagSearchFilter(
                        request.fromYear(),
                        request.toYear(),
                        Set.copyOf(request.paperIds()),
                        Set.copyOf(request.segmentTypes())));
    }

    private Optional<RagSearchHit> reAdmit(
            RagIndexSearchHit candidate,
            TrustedPaperRecord source,
            RagSearchFilter filter
    ) {
        if (source == null
                || source.currentVerificationStatus() != VerificationResult.VerificationStatus.VERIFIED
                || source.normalizedDoi() == null
                || source.sourceUpdatedAt() == null
                || source.verificationVersion() == null) {
            return Optional.empty();
        }
        RagPointPayload payload = candidate.payload();
        if (payload.paperId() != source.paperId()
                || !source.normalizedDoi().equals(payload.doi())
                || !source.normalizedDoi().equals(doiNormalizer.normalize(source.normalizedDoi()))
                || !source.normalizedDoi().equals(doiNormalizer.normalize(source.paper().doi()))
                || !source.verificationVersion().equals(payload.verificationVersion())
                || !source.sourceUpdatedAt().equals(payload.sourceUpdatedAt())
                || !matchesFilter(source.paperId(), source.paper(), payload.segmentType(), filter)) {
            return Optional.empty();
        }
        try {
            RagPaperDocument document = documentBuilder.build(source.paper(), source.normalizedDoi());
            Optional<RagDocumentSegment> segment = document.segments().stream()
                    .filter(item -> item.segmentType() == payload.segmentType())
                    .filter(item -> item.segmentIndex() == payload.segmentIndex())
                    .findFirst();
            if (segment.isEmpty() || !segment.get().contentHash().equals(payload.contentHash())) {
                return Optional.empty();
            }
            return Optional.of(new RagSearchHit(
                    source.paperId(),
                    source.normalizedDoi(),
                    source.paper().title(),
                    source.paper().publicationYear(),
                    source.paper().venue(),
                    candidate.score(),
                    payload.segmentType(),
                    payload.segmentIndex(),
                    boundedExcerpt(payload.text()),
                    payload.contentHash(),
                    source.sourceUpdatedAt()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private boolean matchesFilter(long paperId, PaperDTO paper, RagSegmentType segmentType, RagSearchFilter filter) {
        if (!filter.paperIds().isEmpty() && !filter.paperIds().contains(paperId)) return false;
        if (!filter.segmentTypes().isEmpty() && !filter.segmentTypes().contains(segmentType)) return false;
        Integer year = paper.publicationYear();
        return (filter.fromYear() == null || year != null && year >= filter.fromYear())
                && (filter.toYear() == null || year != null && year <= filter.toYear());
    }

    private Comparator<RagIndexSearchHit> candidateOrder() {
        return Comparator.comparingDouble(RagIndexSearchHit::score).reversed()
                .thenComparing(hit -> hit.payload().paperId())
                .thenComparing(hit -> hit.payload().pointId());
    }

    private String boundedExcerpt(String text) {
        int codePoints = text.codePointCount(0, text.length());
        if (codePoints <= properties.getMaxExcerptChars()) return text;
        return text.substring(0, text.offsetByCodePoints(0, properties.getMaxExcerptChars())) + "…";
    }

    private String normalize(String value) {
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        StringBuilder result = new StringBuilder(nfc.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < nfc.length();) {
            int codePoint = nfc.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (Character.isWhitespace(codePoint)
                    || type == Character.SPACE_SEPARATOR
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                pendingSpace = result.length() > 0;
                continue;
            }
            if (Character.isISOControl(codePoint)) throw new IllegalArgumentException("query contains a control character");
            if (pendingSpace) result.append(' ');
            pendingSpace = false;
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }

    private void validateYear(Integer year) {
        if (year != null && (year < properties.getEarliestSupportedYear() || year > properties.getLatestSupportedYear())) {
            throw new IllegalArgumentException("year is outside the supported range");
        }
    }

    private boolean isUsableActiveVersion(RagIndexVersionState state) {
        return state.active()
                && SUCCEEDED.equals(state.lastBuildStatus())
                && state.lastFailureCode() == null;
    }

    private RagRetrievalFailureType mapIndexFailure(RagIndexFailureType failureType) {
        return switch (failureType) {
            case COLLECTION_MISMATCH -> RagRetrievalFailureType.RAG_INDEX_VERSION_MISMATCH;
            case INVALID_RESPONSE, POINT_MISMATCH -> RagRetrievalFailureType.RAG_INDEX_RESPONSE_INVALID;
            case DISABLED, TRANSPORT_FAILURE, HTTP_FAILURE -> RagRetrievalFailureType.RAG_INDEX_UNAVAILABLE;
        };
    }

    private RagRetrievalResult success(
            int requestedTopK,
            String activeVersion,
            int candidates,
            int uniqueCandidates,
            int admitted,
            int filtered,
            List<RagSearchHit> results,
            String failureCode,
            long startedAt
    ) {
        String status = results.isEmpty() ? "NO_TRUSTED_RESULTS" : "SUCCESS";
        return new RagRetrievalResult(
                status,
                activeVersion,
                requestedTopK,
                candidates,
                uniqueCandidates,
                admitted,
                filtered,
                elapsedMs(startedAt),
                results,
                new RagRetrievalDiagnostics(failureCode));
    }

    private RagRetrievalResult failure(
            int requestedTopK,
            String activeVersion,
            RagRetrievalFailureType failureType,
            long startedAt,
            int candidates,
            int uniqueCandidates,
            int admitted,
            int filtered
    ) {
        long elapsed = elapsedMs(startedAt);
        log.info(
                "event=rag_retrieval_failed failureCode={} queryLength={} requestedTopK={} candidateCount={} uniquePaperCount={} admittedPaperCount={} filteredCount={} elapsedMs={}",
                failureType.name(),
                0,
                requestedTopK,
                candidates,
                uniqueCandidates,
                admitted,
                filtered,
                elapsed);
        return new RagRetrievalResult(
                "FAILED",
                activeVersion,
                requestedTopK,
                candidates,
                uniqueCandidates,
                admitted,
                filtered,
                elapsed,
                List.of(),
                new RagRetrievalDiagnostics(failureType.name()));
    }

    private int requestedTopK(RagRetrievalRequest request) {
        return request == null || request.topK() == null ? properties.getDefaultTopK() : request.topK();
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
