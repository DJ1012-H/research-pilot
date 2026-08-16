package com.dj1012h.researchpilot.literature.rag;

import com.dj1012h.researchpilot.literature.application.VerificationPolicy;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingBatch;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingPort;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingVector;
import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifiedPaperProjectionTest {

    private static final Instant SOURCE_UPDATED_AT = Instant.parse("2026-08-09T12:00:00Z");
    private static final RagEmbeddingProfile PROFILE = RagEmbeddingProfile.initial();

    @Test
    void shouldProjectVerifiedPaperWithNormalizedDoiThroughFakeEmbeddingPort() {
        FakeEmbeddingPort fake = new FakeEmbeddingPort(PROFILE);
        VerifiedPaperProjector projector = projector(fake);

        VerifiedPaperProjectionResult result = projector.project(source(
                paper("10.1000/example", "Controlled abstract."),
                verification(VerificationResult.VerificationStatus.VERIFIED, "10.1000/example"),
                "10.1000/example"));

        assertThat(result.admitted()).isTrue();
        assertThat(result.rejectionReason()).isNull();
        assertThat(fake.callCount).isEqualTo(1);
        assertThat(result.projections()).hasSize(2);
        assertThat(fake.lastInputs).containsExactlyElementsOf(
                result.projections().stream().map(VerifiedPaperProjection::text).toList());

        VerifiedPaperProjection metadata = result.projections().getFirst();
        assertThat(metadata.pointId()).isEqualTo(RagPointIdFactory.create(
                42L, PROFILE.version(), RagSegmentType.METADATA, 0));
        assertThat(metadata.paperId()).isEqualTo(42L);
        assertThat(metadata.doi()).isEqualTo("10.1000/example");
        assertThat(metadata.title()).isEqualTo("Controlled title");
        assertThat(metadata.publicationYear()).isEqualTo(2024);
        assertThat(metadata.venue()).isEqualTo("Controlled venue");
        assertThat(metadata.language()).isEqualTo("en");
        assertThat(metadata.verificationStatus()).isEqualTo(VerificationResult.VerificationStatus.VERIFIED);
        assertThat(metadata.verificationVersion()).isEqualTo(VerificationPolicy.VERSION);
        assertThat(metadata.embeddingModel()).isEqualTo(PROFILE.model());
        assertThat(metadata.embeddingVersion()).isEqualTo(PROFILE.version());
        assertThat(metadata.vectorDimensions()).isEqualTo(1024);
        assertThat(metadata.sourceUpdatedAt()).isEqualTo(SOURCE_UPDATED_AT);
        assertThat(metadata.text()).startsWith("Title: Controlled title\n");
        assertThat(metadata.contentHash()).matches("[0-9a-f]{64}");
        assertThatThrownBy(() -> metadata.vector().add(2.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldPrepareStablePayloadsWithoutCallingEmbeddingPort() {
        FakeEmbeddingPort fake = new FakeEmbeddingPort(PROFILE);
        VerifiedPaperProjector projector = projector(fake);
        VerifiedPaperSource source = source(
                paper("10.1000/example", "Controlled abstract."),
                verification(VerificationResult.VerificationStatus.VERIFIED, "10.1000/example"),
                "10.1000/example");

        VerifiedPaperProjectionPlanResult first = projector.prepare(source);
        VerifiedPaperProjectionPlanResult second = projector.prepare(source);

        assertThat(first.admitted()).isTrue();
        assertThat(first.plan()).isEqualTo(second.plan());
        assertThat(first.plan().points()).hasSize(2);
        assertThat(fake.callCount).isZero();

        VerifiedPaperProjectionResult projected = projector.project(first.plan());
        assertThat(projected.admitted()).isTrue();
        assertThat(projected.projections().stream().map(VerifiedPaperProjection::payload).toList())
                .containsExactlyElementsOf(first.plan().points());
        assertThat(fake.callCount).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = VerificationResult.VerificationStatus.class, names = "VERIFIED", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectEveryNonVerifiedStatusBeforeCallingEmbeddingPort(
            VerificationResult.VerificationStatus status
    ) {
        FakeEmbeddingPort fake = new FakeEmbeddingPort(PROFILE);

        VerifiedPaperProjectionResult result = projector(fake).project(source(
                paper("10.1000/example", "Abstract"),
                verification(status, "10.1000/example"),
                "10.1000/example"));

        assertThat(result.admitted()).isFalse();
        assertThat(result.rejectionReason()).isEqualTo(ProjectionRejectionReason.STATUS_NOT_VERIFIED);
        assertThat(result.projections()).isEmpty();
        assertThat(fake.callCount).isZero();
    }

    @Test
    void shouldRejectMissingInvalidUnnormalizedAndMismatchedDoiBeforeEmbedding() {
        FakeEmbeddingPort fake = new FakeEmbeddingPort(PROFILE);
        VerifiedPaperProjector projector = projector(fake);
        VerificationResult verified = verification(
                VerificationResult.VerificationStatus.VERIFIED,
                "10.1000/example");

        assertThat(projector.project(source(paper(null, "Abstract"), verified, null)).rejectionReason())
                .isEqualTo(ProjectionRejectionReason.DOI_MISSING);
        assertThat(projector.project(source(paper("not-a-doi", "Abstract"),
                verification(VerificationResult.VerificationStatus.VERIFIED, "not-a-doi"),
                "not-a-doi")).rejectionReason())
                .isEqualTo(ProjectionRejectionReason.DOI_INVALID);
        assertThat(projector.project(source(paper("10.1000/EXAMPLE", "Abstract"), verified,
                "10.1000/EXAMPLE")).rejectionReason())
                .isEqualTo(ProjectionRejectionReason.DOI_NOT_NORMALIZED);
        assertThat(projector.project(source(paper("10.1000/other", "Abstract"), verified,
                "10.1000/other")).rejectionReason())
                .isEqualTo(ProjectionRejectionReason.DOI_MISMATCH);

        assertThat(fake.callCount).isZero();
    }

    @Test
    void shouldRejectInvalidAuthorityFieldsBeforeEmbedding() {
        FakeEmbeddingPort fake = new FakeEmbeddingPort(PROFILE);
        VerifiedPaperProjector projector = projector(fake);
        PaperDTO validPaper = paper("10.1000/example", "Abstract");
        VerificationResult verified = verification(
                VerificationResult.VerificationStatus.VERIFIED,
                "10.1000/example");

        assertThat(projector.project(new VerifiedPaperSource(
                0L, validPaper, verified, "10.1000/example", VerificationPolicy.VERSION, SOURCE_UPDATED_AT))
                .rejectionReason()).isEqualTo(ProjectionRejectionReason.INVALID_PAPER_ID);
        assertThat(projector.project(new VerifiedPaperSource(
                42L, validPaper, verified, "10.1000/example", "verification-v2", SOURCE_UPDATED_AT))
                .rejectionReason()).isEqualTo(ProjectionRejectionReason.VERIFICATION_VERSION_MISMATCH);
        assertThat(projector.project(new VerifiedPaperSource(
                42L, validPaper, verified, "10.1000/example", "verification-v1|other", SOURCE_UPDATED_AT))
                .rejectionReason()).isEqualTo(ProjectionRejectionReason.VERIFICATION_VERSION_MISMATCH);
        assertThat(projector.project(new VerifiedPaperSource(
                42L, validPaper, verified, "10.1000/example", VerificationPolicy.VERSION, null))
                .rejectionReason()).isEqualTo(ProjectionRejectionReason.SOURCE_UPDATED_AT_MISSING);

        assertThat(fake.callCount).isZero();
    }

    @Test
    void shouldAdmitProviderTextContainingThePointNameSeparator() {
        FakeEmbeddingPort fake = new FakeEmbeddingPort(PROFILE);
        VerifiedPaperProjector projector = projector(fake);
        VerificationResult verified = verification(
                VerificationResult.VerificationStatus.VERIFIED,
                "10.1000/example");

        VerifiedPaperProjectionResult result = projector.project(source(
                paperWithText("Title | comparison", "Crossref abstract with A | B."),
                verified,
                "10.1000/example"));

        assertThat(result.admitted()).isTrue();
        assertThat(result.projections()).hasSize(2);
        assertThat(result.projections()).extracting(VerifiedPaperProjection::text)
                .anyMatch(text -> text.contains("Crossref abstract with A | B."));
        assertThat(fake.callCount).isEqualTo(1);
    }

    private VerifiedPaperProjector projector(EmbeddingPort port) {
        return new VerifiedPaperProjector(new DoiNormalizer(), new RagDocumentBuilder(), port, PROFILE);
    }

    private VerifiedPaperSource source(
            PaperDTO paper,
            VerificationResult verification,
            String normalizedDoi
    ) {
        return new VerifiedPaperSource(
                42L,
                paper,
                verification,
                normalizedDoi,
                VerificationPolicy.VERSION,
                SOURCE_UPDATED_AT);
    }

    private VerificationResult verification(
            VerificationResult.VerificationStatus status,
            String referenceDoi
    ) {
        return new VerificationResult(
                status,
                status == VerificationResult.VerificationStatus.VERIFIED ? 1.0 : null,
                VerificationResult.VerificationSource.CROSSREF,
                referenceDoi,
                List.of(),
                List.of("TEST"));
    }

    private PaperDTO paperWithText(String title, String abstractText) {
        return new PaperDTO(
                "W123",
                "10.1000/example",
                title,
                List.of(new PaperDTO.Author(null, "Ada Lovelace", null)),
                2024,
                "Controlled venue",
                List.of(),
                "article",
                null,
                abstractText,
                "en",
                List.of("testing"),
                1,
                PaperDTO.LiteratureSource.OPENALEX);
    }

    private PaperDTO paper(String doi, String abstractText) {
        return new PaperDTO(
                "W123",
                doi,
                "Controlled title",
                List.of(new PaperDTO.Author(null, "Ada Lovelace", null)),
                2024,
                "Controlled venue",
                List.of(),
                "article",
                null,
                abstractText,
                "en",
                List.of("testing"),
                1,
                PaperDTO.LiteratureSource.OPENALEX);
    }

    private static final class FakeEmbeddingPort implements EmbeddingPort {
        private final RagEmbeddingProfile profile;
        private int callCount;
        private List<String> lastInputs = List.of();

        private FakeEmbeddingPort(RagEmbeddingProfile profile) {
            this.profile = profile;
        }

        @Override
        public EmbeddingBatch embed(List<String> controlledTexts) {
            callCount++;
            lastInputs = List.copyOf(controlledTexts);
            List<EmbeddingVector> vectors = new ArrayList<>();
            for (int index = 0; index < controlledTexts.size(); index++) {
                vectors.add(new EmbeddingVector(Collections.nCopies(
                        profile.expectedDimensions(),
                        (double) index)));
            }
            return new EmbeddingBatch(
                    profile.model(),
                    vectors,
                    profile.expectedDimensions(),
                    Duration.ofMillis(7));
        }
    }
}
