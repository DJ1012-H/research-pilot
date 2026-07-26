package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.DeduplicationKeyType;
import com.dj1012h.researchpilot.literature.model.DeduplicationReason;
import com.dj1012h.researchpilot.literature.normalization.AuthorNormalizer;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.normalization.OpenAlexIdNormalizer;
import com.dj1012h.researchpilot.literature.normalization.TitleNormalizer;
import com.dj1012h.researchpilot.literature.normalization.VenueNormalizer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateDeduplicationServiceTest {

    private final CandidateDeduplicationService service = new CandidateDeduplicationService(
            new CandidateNormalizationService(
                    new DoiNormalizer(),
                    new OpenAlexIdNormalizer(),
                    new TitleNormalizer(),
                    new AuthorNormalizer(),
                    new VenueNormalizer()
            )
    );

    @Test
    void shouldDeduplicateByNormalizedDoiAndRetainTheRicherCandidate() {
        CandidatePaper sparse = candidate("W1", "10.1000/ABC", "A paper", List.of(), null, null);
        CandidatePaper rich = candidate("W2", "https://doi.org/10.1000/abc", "A paper",
                List.of(new CandidatePaper.Author("A1", "Author", null)), "Journal", 2026);

        CandidateDeduplicationResult result = service.deduplicate(List.of(sparse, rich));

        assertThat(result.uniqueCount()).isOne();
        assertThat(result.removedCount()).isOne();
        assertThat(result.uniqueCandidates().getFirst().originalCandidate()).isSameAs(rich);
        assertThat(result.duplicateGroups()).singleElement().satisfies(group -> {
            assertThat(group.key().type()).isEqualTo(DeduplicationKeyType.DOI);
            assertThat(group.reason()).isEqualTo(DeduplicationReason.SAME_NORMALIZED_DOI);
            assertThat(group.retainedCandidateId()).isEqualTo("W2");
            assertThat(group.removedCandidateIds()).containsExactly("W1");
        });
    }

    @Test
    void shouldDeduplicateByOpenAlexIdOnlyWhenDoiIsMissing() {
        CandidatePaper first = candidate("W123", null, "First", List.of(), null, null);
        CandidatePaper second = candidate("https://openalex.org/w123", null, "Second", List.of(), null, null);

        CandidateDeduplicationResult result = service.deduplicate(List.of(first, second));

        assertThat(result.uniqueCount()).isOne();
        assertThat(result.duplicateGroups()).singleElement().satisfies(group -> {
            assertThat(group.key().type()).isEqualTo(DeduplicationKeyType.OPENALEX_ID);
            assertThat(group.reason()).isEqualTo(DeduplicationReason.SAME_OPENALEX_ID);
        });
    }

    @Test
    void shouldUseOnlyTheCompleteBibliographicKeyAsTheFallbackIdentity() {
        CandidatePaper first = candidate(null, null, "Mamba—Remote Sensing", authors("John Smith"), "TGRS", 2026);
        CandidatePaper second = candidate(null, null, "mamba-remote sensing", authors("John Smith"), "Other", 2026);

        CandidateDeduplicationResult result = service.deduplicate(List.of(first, second));

        assertThat(result.uniqueCount()).isOne();
        assertThat(result.duplicateGroups()).singleElement().extracting(group -> group.key().type())
                .isEqualTo(DeduplicationKeyType.BIBLIOGRAPHIC);
    }

    @Test
    void shouldKeepConservativeNearMatchesAndIncompleteCandidates() {
        List<CandidatePaper> candidates = List.of(
                candidate(null, null, "Same title", authors("Author One"), null, 2026),
                candidate(null, null, "Same title", authors("Author Two"), null, 2026),
                candidate(null, null, "Same title", authors("Author One"), null, 2025),
                candidate(null, null, "Same title", List.of(), null, 2026),
                candidate(null, null, "Same title extended", authors("Author One"), null, 2026),
                candidate("W999", "10.1000/preprint", "Same title", authors("Author One"), null, 2025),
                candidate("W998", "10.1000/formal", "Same title", authors("Author One"), "Journal", 2026)
        );

        CandidateDeduplicationResult result = service.deduplicate(candidates);

        assertThat(result.uniqueCount()).isEqualTo(7);
        assertThat(result.duplicateGroups()).isEmpty();
    }

    @Test
    void shouldProduceStableOutputAndPreserveCandidatesWithoutIdentityKeys() {
        List<CandidatePaper> candidates = List.of(
                candidate(null, null, "No author", List.of(), null, 2026),
                candidate(null, null, null, List.of(), null, null),
                candidate("W1", null, "With id", List.of(), null, null),
                candidate("https://openalex.org/W1", null, "With id duplicate", List.of(), null, null)
        );

        CandidateDeduplicationResult first = service.deduplicate(candidates);
        CandidateDeduplicationResult second = service.deduplicate(candidates);

        assertThat(first).isEqualTo(second);
        assertThat(first.inputCount()).isEqualTo(4);
        assertThat(first.uniqueCount()).isEqualTo(3);
        assertThat(first.removedCount()).isEqualTo(1);
        assertThat(first.uniqueCandidates()).extracting(candidate -> candidate.inputIndex())
                .containsExactly(0, 1, 2);
    }

    private List<CandidatePaper.Author> authors(String name) {
        return List.of(new CandidatePaper.Author(null, name, null));
    }

    private CandidatePaper candidate(String openAlexId, String doi, String title,
                                     List<CandidatePaper.Author> authors, String venue, Integer year) {
        return new CandidatePaper(
                openAlexId,
                doi,
                title,
                authors,
                venue,
                year == null ? null : LocalDate.of(year, 1, 1),
                year,
                "article",
                "en",
                0,
                null,
                null,
                null,
                false,
                CandidatePaper.CandidateSource.OPENALEX
        );
    }
}
