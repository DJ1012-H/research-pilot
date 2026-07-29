package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.config.ReviewProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewInputBudgeterTest {

    private final StructuredOutputMapper mapper = new StructuredOutputMapper(
            new StructuredOutputConfiguration().structuredOutputObjectMapper());
    private final ReviewEvidenceSerializer serializer = new ReviewEvidenceSerializer(mapper);

    @Test
    void shouldTruncateByUnicodeCodePointStopAtTheTailAndNeverRenumber() {
        ReviewInput original = input(List.of(
                paper(1, "A😀BCD"),
                paper(3, "B😀CDE"),
                paper(4, "C😀DEF"),
                paper(6, "D😀EFG")
        ));
        ReviewInput firstThreeBounded = input(List.of(
                paper(1, "A😀B"),
                paper(3, "B😀C"),
                paper(4, "C😀D")
        ));
        ReviewProperties properties = new ReviewProperties();
        properties.setMaxAbstractChars(3);
        properties.setMaxEvidenceJsonLength(serializer.serialize(firstThreeBounded).length());

        ReviewBudgetResult result = new ReviewInputBudgeter(properties, serializer).apply(original);

        ReviewInput bounded = result.reviewInput().orElseThrow();
        assertThat(result.status()).isEqualTo(ReviewBudgetResult.ReviewBudgetStatus.READY);
        assertThat(bounded.evidencePapers())
                .extracting(paper -> paper.citationId().value())
                .containsExactly("P1", "P3", "P4");
        assertThat(bounded.evidencePapers())
                .extracting(EvidencePaper::abstractText)
                .containsExactly("A😀B", "B😀C", "C😀D");
        assertThat(bounded.abstractEvidenceCount()).isEqualTo(3);
    }

    @Test
    void shouldFailBeforeModelGenerationWhenOnlyTwoPapersFit() {
        ReviewInput original = input(List.of(
                paper(1, "abstract one"),
                paper(2, "abstract two"),
                paper(3, "abstract three")
        ));
        ReviewInput firstTwo = input(List.of(
                paper(1, "abstract one"),
                paper(2, "abstract two")
        ));
        ReviewProperties properties = new ReviewProperties();
        properties.setMaxEvidenceJsonLength(serializer.serialize(firstTwo).length());

        ReviewBudgetResult result = new ReviewInputBudgeter(properties, serializer).apply(original);

        assertThat(result.status())
                .isEqualTo(ReviewBudgetResult.ReviewBudgetStatus.INPUT_BUDGET_EXCEEDED);
        assertThat(result.reviewInput()).isEmpty();
    }

    @Test
    void shouldEnforceJavaHardMaximaOnConfiguration() {
        ReviewProperties properties = new ReviewProperties();

        assertThatThrownBy(() -> properties.setMaxEvidencePapers(21))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxRawDraftLength(16_385))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxAbstractChars(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ReviewInput input(List<EvidencePaper> papers) {
        return new ReviewInput(5, 5, papers.size(), papers);
    }

    private EvidencePaper paper(int position, String abstractText) {
        return new EvidencePaper(
                new CitationId(position),
                "10.1000/" + position,
                "Evidence title " + position,
                List.of("Author " + position),
                2025,
                "Venue",
                abstractText
        );
    }
}
