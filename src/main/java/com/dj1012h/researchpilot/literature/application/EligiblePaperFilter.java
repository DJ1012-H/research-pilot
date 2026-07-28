package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Formal-output gate: only verified candidates with a normalized DOI may pass. */
@Service
public class EligiblePaperFilter {

    private final DoiNormalizer doiNormalizer;

    public EligiblePaperFilter(DoiNormalizer doiNormalizer) {
        this.doiNormalizer = Objects.requireNonNull(doiNormalizer, "doiNormalizer must not be null");
    }

    public List<SearchResponse.PaperResult> filter(List<CandidateVerificationOutcome> outcomes, int resultLimit) {
        Objects.requireNonNull(outcomes, "outcomes must not be null");
        if (resultLimit < 1) throw new IllegalArgumentException("resultLimit must be positive");
        Set<String> seenDois = new LinkedHashSet<>();
        java.util.ArrayList<SearchResponse.PaperResult> papers = new java.util.ArrayList<>();
        for (int index = 0; index < outcomes.size() && papers.size() < resultLimit; index++) {
            CandidateVerificationOutcome outcome = outcomes.get(index);
            if (outcome.verification().status() != VerificationResult.VerificationStatus.VERIFIED) continue;
            String doi = doiNormalizer.normalize(outcome.verification().referenceDoi());
            if (doi == null || !seenDois.add(doi)) continue;
            papers.add(new SearchResponse.PaperResult(
                    paper(outcome, doi), rankDerivedDisplayScore(index, outcomes.size()), outcome.verification()));
        }
        return List.copyOf(papers);
    }

    /** Display-only score derived from stable OpenAlex rank; it is not relevance probability or evidence score. */
    static double rankDerivedDisplayScore(int originalIndex, int totalOutcomes) {
        if (totalOutcomes < 1 || originalIndex < 0 || originalIndex >= totalOutcomes) {
            throw new IllegalArgumentException("original rank must be within outcome range");
        }
        return (double) (totalOutcomes - originalIndex) / totalOutcomes;
    }

    private PaperDTO paper(CandidateVerificationOutcome outcome, String verifiedDoi) {
        CandidatePaper candidate = outcome.candidate();
        var reference = outcome.selectedReference();
        String title = text(candidate.title()) != null ? candidate.title() : reference.title();
        if (text(title) == null) throw new IllegalStateException("verified paper has no usable title");
        List<PaperDTO.Author> authors = candidate.authors().isEmpty()
                ? reference.authorNames().stream().filter(this::hasText)
                .map(name -> new PaperDTO.Author(null, name, null)).toList()
                : candidate.authors().stream().map(author -> new PaperDTO.Author(
                author.openAlexAuthorId(), author.displayName(), author.orcid())).toList();
        return new PaperDTO(candidate.openAlexId(), verifiedDoi, title, authors,
                candidate.publicationYear() != null ? candidate.publicationYear() : reference.publicationYear(),
                text(candidate.sourceName()) != null ? candidate.sourceName() : reference.venue(),
                List.of(), candidate.workType() != null ? candidate.workType() : reference.workType(),
                candidate.landingPageUrl(), candidate.abstractText(), candidate.language(), List.of(),
                candidate.citedByCount(), PaperDTO.LiteratureSource.OPENALEX);
    }

    private boolean hasText(String value) { return text(value) != null; }

    private String text(String value) { return value == null || value.isBlank() ? null : value; }
}
