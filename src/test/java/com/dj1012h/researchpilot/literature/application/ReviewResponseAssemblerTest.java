package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.api.dto.ReviewResponse;
import com.dj1012h.researchpilot.literature.review.CitationId;
import com.dj1012h.researchpilot.literature.review.EvidencePaper;
import com.dj1012h.researchpilot.literature.review.ReviewInput;
import com.dj1012h.researchpilot.literature.review.ReviewOutcome;
import com.dj1012h.researchpilot.literature.review.ReviewOutcomeStatus;
import com.dj1012h.researchpilot.literature.review.ReviewStatementType;
import com.dj1012h.researchpilot.literature.review.ValidatedReview;
import com.dj1012h.researchpilot.literature.review.ValidatedReviewStatement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewResponseAssemblerTest {

    private final ReviewResponseAssembler assembler = new ReviewResponseAssembler();

    @Test
    void shouldRenderMarkersInJavaAndMapCitationMetadataFromEvidenceOnly() {
        ReviewInput input = input();
        ValidatedReview review = new ValidatedReview(List.of(
                statement("First bounded observation.", 3, 1),
                statement("Second bounded observation.", 1, 4)
        ));

        ReviewResponse response = assembler.assemble(
                ReviewOutcome.generated(review, input, 2, 1));

        assertThat(response.status()).isEqualTo(ReviewResponse.ReviewStatus.GENERATED);
        assertThat(response.summary()).isEqualTo(String.join(
                System.lineSeparator(),
                "First bounded observation. [P3][P1]",
                "Second bounded observation. [P1][P4]"
        ));
        assertThat(response.citations())
                .extracting(citation -> citation.citationId())
                .containsExactly("P3", "P1", "P4");
        assertThat(response.citations().getFirst().formalPaperPosition()).isEqualTo(3);
        assertThat(response.citations().getFirst().doi()).isEqualTo("10.1000/evidence-3");
        assertThat(response.citations().getFirst().title()).isEqualTo("Java title 3");
        assertThat(response.citations().getFirst().authors()).containsExactly("Java author 3");
        assertThat(response.summary())
                .doesNotContain("10.1000/evidence-3", "Java title 3", "Java author 3");
    }

    @Test
    void shouldExposeNoPartialReviewDataForEveryFailureStatus() {
        for (ReviewOutcomeStatus status : ReviewOutcomeStatus.values()) {
            if (status == ReviewOutcomeStatus.GENERATED) {
                continue;
            }

            ReviewResponse response = assembler.assemble(
                    ReviewOutcome.failed(status, 0, 0, 3, "SAFE_FAILURE"));

            assertThat(response.status().name()).isEqualTo(status.name());
            assertThat(response.summary()).isEmpty();
            assertThat(response.citations()).isEmpty();
            assertThat(response.message()).isNotBlank().doesNotContain("SAFE_FAILURE");
        }
    }

    @Test
    void shouldDistinguishVerifiedPaperAndAbstractEvidenceShortagesSafely() {
        ReviewResponse paperShortage = assembler.assemble(ReviewOutcome.failed(
                ReviewOutcomeStatus.INSUFFICIENT_EVIDENCE,
                0,
                0,
                0,
                "INSUFFICIENT_VERIFIED_PAPERS"
        ));
        ReviewResponse abstractShortage = assembler.assemble(ReviewOutcome.failed(
                ReviewOutcomeStatus.INSUFFICIENT_EVIDENCE,
                0,
                0,
                2,
                "INSUFFICIENT_ABSTRACTS"
        ));

        assertThat(paperShortage.message()).contains("可信论文数量不足");
        assertThat(abstractShortage.message()).contains("摘要证据不足");
    }

    private ReviewInput input() {
        return new ReviewInput(5, 4, 3, List.of(
                paper(1), paper(3), paper(4)
        ));
    }

    private EvidencePaper paper(int position) {
        return new EvidencePaper(
                new CitationId(position),
                "10.1000/evidence-" + position,
                "Java title " + position,
                List.of("Java author " + position),
                2025,
                "Java venue",
                "Sensitive abstract " + position
        );
    }

    private ValidatedReviewStatement statement(String text, int... positions) {
        return new ValidatedReviewStatement(
                ReviewStatementType.OBSERVATION,
                text,
                java.util.Arrays.stream(positions).mapToObj(CitationId::new).toList()
        );
    }
}
