package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.NormalizedCandidate;
import com.dj1012h.researchpilot.literature.normalization.AuthorNormalizer;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.normalization.OpenAlexIdNormalizer;
import com.dj1012h.researchpilot.literature.normalization.TitleNormalizer;
import com.dj1012h.researchpilot.literature.normalization.VenueNormalizer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** Converts provider candidates into an immutable identity view without changing source metadata. */
@Service
public class CandidateNormalizationService {

    private final DoiNormalizer doiNormalizer;
    private final OpenAlexIdNormalizer openAlexIdNormalizer;
    private final TitleNormalizer titleNormalizer;
    private final AuthorNormalizer authorNormalizer;
    private final VenueNormalizer venueNormalizer;

    public CandidateNormalizationService(
            DoiNormalizer doiNormalizer,
            OpenAlexIdNormalizer openAlexIdNormalizer,
            TitleNormalizer titleNormalizer,
            AuthorNormalizer authorNormalizer,
            VenueNormalizer venueNormalizer
    ) {
        this.doiNormalizer = doiNormalizer;
        this.openAlexIdNormalizer = openAlexIdNormalizer;
        this.titleNormalizer = titleNormalizer;
        this.authorNormalizer = authorNormalizer;
        this.venueNormalizer = venueNormalizer;
    }

    public List<NormalizedCandidate> normalize(List<CandidatePaper> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        List<NormalizedCandidate> normalized = new java.util.ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            CandidatePaper candidate = candidates.get(index);
            if (candidate != null) {
                normalized.add(normalize(candidate, index));
            }
        }
        return List.copyOf(normalized);
    }

    public NormalizedCandidate normalize(CandidatePaper candidate, int inputIndex) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        String candidateId = candidate.openAlexId() == null || candidate.openAlexId().isBlank()
                ? "candidate-" + inputIndex
                : candidate.openAlexId().trim();
        return new NormalizedCandidate(
                candidateId,
                candidate,
                doiNormalizer.normalize(candidate.doi()),
                openAlexIdNormalizer.normalize(candidate.openAlexId()),
                titleNormalizer.normalize(candidate.title()),
                authorNormalizer.normalizeFirstAuthor(candidate.authors()),
                candidate.publicationYear(),
                venueNormalizer.normalize(candidate.sourceName()),
                inputIndex
        );
    }
}
