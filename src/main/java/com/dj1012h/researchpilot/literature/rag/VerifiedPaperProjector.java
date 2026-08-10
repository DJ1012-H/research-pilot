package com.dj1012h.researchpilot.literature.rag;

import com.dj1012h.researchpilot.literature.application.VerificationPolicy;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingBatch;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingException;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingFailureType;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingPort;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingVector;
import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies authoritative admission before building and embedding controlled paper segments. */
public class VerifiedPaperProjector {

    private final DoiNormalizer doiNormalizer;
    private final RagDocumentBuilder documentBuilder;
    private final EmbeddingPort embeddingPort;
    private final RagEmbeddingProfile embeddingProfile;

    public VerifiedPaperProjector(
            DoiNormalizer doiNormalizer,
            RagDocumentBuilder documentBuilder,
            EmbeddingPort embeddingPort,
            RagEmbeddingProfile embeddingProfile
    ) {
        this.doiNormalizer = Objects.requireNonNull(doiNormalizer, "doiNormalizer must not be null");
        this.documentBuilder = Objects.requireNonNull(documentBuilder, "documentBuilder must not be null");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort must not be null");
        this.embeddingProfile = Objects.requireNonNull(embeddingProfile, "embeddingProfile must not be null");
    }

    public VerifiedPaperProjectionResult project(VerifiedPaperSource source) {
        ProjectionRejectionReason rejection = admissionFailure(source);
        if (rejection != null) {
            return VerifiedPaperProjectionResult.rejected(rejection);
        }

        RagPaperDocument document;
        try {
            document = documentBuilder.build(source.paper(), source.normalizedDoi());
        } catch (IllegalArgumentException exception) {
            return VerifiedPaperProjectionResult.rejected(ProjectionRejectionReason.INVALID_INPUT);
        }
        List<String> texts = document.segments().stream().map(RagDocumentSegment::text).toList();
        EmbeddingBatch batch = embeddingPort.embed(texts);
        validateEmbeddingBatch(batch, texts.size());

        List<VerifiedPaperProjection> projections = new ArrayList<>(document.segments().size());
        for (int index = 0; index < document.segments().size(); index++) {
            RagDocumentSegment segment = document.segments().get(index);
            EmbeddingVector embedding = batch.embeddings().get(index);
            projections.add(new VerifiedPaperProjection(
                    RagPointIdFactory.create(
                            source.paperId(),
                            embeddingProfile.version(),
                            segment.segmentType(),
                            segment.segmentIndex()),
                    source.paperId(),
                    document.doi(),
                    document.title(),
                    document.publicationYear(),
                    document.venue(),
                    document.language(),
                    source.verification().status(),
                    source.verificationVersion(),
                    segment.segmentType(),
                    segment.segmentIndex(),
                    embeddingProfile.model(),
                    embeddingProfile.version(),
                    segment.contentHash(),
                    source.sourceUpdatedAt(),
                    segment.text(),
                    embedding.values(),
                    batch.dimensions(),
                    batch.elapsed()));
        }
        return VerifiedPaperProjectionResult.admitted(projections);
    }

    private ProjectionRejectionReason admissionFailure(VerifiedPaperSource source) {
        if (source == null || source.paper() == null || source.verification() == null) {
            return ProjectionRejectionReason.INVALID_INPUT;
        }
        if (source.paperId() < 1) {
            return ProjectionRejectionReason.INVALID_PAPER_ID;
        }
        if (source.verification().status() != VerificationResult.VerificationStatus.VERIFIED) {
            return ProjectionRejectionReason.STATUS_NOT_VERIFIED;
        }
        if (source.sourceUpdatedAt() == null) {
            return ProjectionRejectionReason.SOURCE_UPDATED_AT_MISSING;
        }
        if (containsSeparator(source)) {
            return ProjectionRejectionReason.ILLEGAL_SEPARATOR;
        }
        if (!VerificationPolicy.VERSION.equals(source.verificationVersion())) {
            return ProjectionRejectionReason.VERIFICATION_VERSION_MISMATCH;
        }
        if (source.normalizedDoi() == null || source.normalizedDoi().isBlank()
                || source.paper().doi() == null || source.paper().doi().isBlank()
                || source.verification().referenceDoi() == null
                || source.verification().referenceDoi().isBlank()) {
            return ProjectionRejectionReason.DOI_MISSING;
        }
        String sourceDoi = doiNormalizer.normalize(source.normalizedDoi());
        String paperDoi = doiNormalizer.normalize(source.paper().doi());
        String verificationDoi = doiNormalizer.normalize(source.verification().referenceDoi());
        if (sourceDoi == null || paperDoi == null || verificationDoi == null) {
            return ProjectionRejectionReason.DOI_INVALID;
        }
        if (!sourceDoi.equals(source.normalizedDoi()) || !paperDoi.equals(source.paper().doi())) {
            return ProjectionRejectionReason.DOI_NOT_NORMALIZED;
        }
        if (!sourceDoi.equals(paperDoi) || !sourceDoi.equals(verificationDoi)) {
            return ProjectionRejectionReason.DOI_MISMATCH;
        }
        return null;
    }

    private boolean containsSeparator(VerifiedPaperSource source) {
        PaperDTO paper = source.paper();
        if (containsPipe(source.normalizedDoi())
                || containsPipe(source.verificationVersion())
                || containsPipe(source.verification().referenceDoi())
                || containsPipe(paper.openAlexId())
                || containsPipe(paper.doi())
                || containsPipe(paper.title())
                || containsPipe(paper.venue())
                || containsPipe(paper.publicationType())
                || containsPipe(paper.landingPageUrl())
                || containsPipe(paper.abstractText())
                || containsPipe(paper.language())) {
            return true;
        }
        if (paper.authors().stream().anyMatch(author -> containsPipe(author.openAlexAuthorId())
                || containsPipe(author.displayName()) || containsPipe(author.orcid()))) {
            return true;
        }
        return paper.issns().stream().anyMatch(this::containsPipe)
                || paper.keywords().stream().anyMatch(this::containsPipe);
    }

    private boolean containsPipe(String value) {
        return value != null && value.indexOf('|') >= 0;
    }

    private void validateEmbeddingBatch(EmbeddingBatch batch, int expectedCount) {
        if (batch == null) {
            throw new EmbeddingException(
                    EmbeddingFailureType.INVALID_RESPONSE,
                    "embedding port returned no batch");
        }
        if (!embeddingProfile.model().equals(batch.model())) {
            throw new EmbeddingException(
                    EmbeddingFailureType.MODEL_MISMATCH,
                    "embedding port returned an unexpected model");
        }
        if (batch.embeddings().size() != expectedCount) {
            throw new EmbeddingException(
                    EmbeddingFailureType.VECTOR_COUNT_MISMATCH,
                    "embedding port returned an unexpected vector count");
        }
        if (batch.dimensions() != embeddingProfile.expectedDimensions()) {
            throw new EmbeddingException(
                    EmbeddingFailureType.DIMENSION_MISMATCH,
                    "embedding port returned an incompatible vector dimension");
        }
    }
}
