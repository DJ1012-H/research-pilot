package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationKey;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;
import com.dj1012h.researchpilot.literature.model.DeduplicationKeyType;
import com.dj1012h.researchpilot.literature.model.DeduplicationReason;
import com.dj1012h.researchpilot.literature.model.DuplicateCandidateGroup;
import com.dj1012h.researchpilot.literature.model.NormalizedCandidate;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Performs deterministic, conservative identity deduplication before external verification. */
@Service
public class CandidateDeduplicationService {

    private final CandidateNormalizationService normalizationService;

    public CandidateDeduplicationService(CandidateNormalizationService normalizationService) {
        this.normalizationService = normalizationService;
    }

    public CandidateDeduplicationResult deduplicate(List<CandidatePaper> candidates) {
        List<NormalizedCandidate> normalized = normalizationService.normalize(candidates);
        Map<CandidateDeduplicationKey, List<NormalizedCandidate>> grouped = new LinkedHashMap<>();
        List<NormalizedCandidate> withoutKey = new ArrayList<>();

        for (NormalizedCandidate candidate : normalized) {
            CandidateDeduplicationKey key = identityKey(candidate);
            if (key == null) {
                withoutKey.add(candidate);
            } else {
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
            }
        }

        List<NormalizedCandidate> selected = new ArrayList<>(withoutKey);
        List<DuplicateCandidateGroup> duplicateGroups = new ArrayList<>();
        for (Map.Entry<CandidateDeduplicationKey, List<NormalizedCandidate>> entry : grouped.entrySet()) {
            List<NormalizedCandidate> group = entry.getValue();
            NormalizedCandidate retained = group.stream()
                    .max(this::compareForRetention)
                    .orElseThrow();
            selected.add(retained);

            if (group.size() > 1) {
                List<String> removed = group.stream()
                        .filter(candidate -> candidate != retained)
                        .sorted(Comparator.comparingInt(NormalizedCandidate::inputIndex))
                        .map(NormalizedCandidate::candidateId)
                        .toList();
                duplicateGroups.add(new DuplicateCandidateGroup(
                        entry.getKey(),
                        retained.candidateId(),
                        removed,
                        reason(entry.getKey().type())
                ));
            }
        }

        selected.sort(Comparator.comparingInt(NormalizedCandidate::inputIndex));
        return new CandidateDeduplicationResult(
                selected,
                duplicateGroups,
                normalized.size(),
                selected.size(),
                normalized.size() - selected.size()
        );
    }

    private CandidateDeduplicationKey identityKey(NormalizedCandidate candidate) {
        return CandidateDeduplicationKey.from(candidate).orElse(null);
    }

    private int compareForRetention(NormalizedCandidate left, NormalizedCandidate right) {
        int comparison = Boolean.compare(hasValue(left.normalizedDoi()), hasValue(right.normalizedDoi()));
        if (comparison != 0) return comparison;
        comparison = Boolean.compare(hasValue(left.normalizedOpenAlexId()), hasValue(right.normalizedOpenAlexId()));
        if (comparison != 0) return comparison;
        comparison = Boolean.compare(hasValue(left.normalizedTitle()), hasValue(right.normalizedTitle()));
        if (comparison != 0) return comparison;
        comparison = Integer.compare(left.originalCandidate().authors().size(),
                right.originalCandidate().authors().size());
        if (comparison != 0) return comparison;
        comparison = Boolean.compare(left.publicationYear() != null, right.publicationYear() != null);
        if (comparison != 0) return comparison;
        comparison = Boolean.compare(hasValue(left.normalizedVenue()), hasValue(right.normalizedVenue()));
        if (comparison != 0) return comparison;
        comparison = Boolean.compare(hasValue(left.originalCandidate().workType()),
                hasValue(right.originalCandidate().workType()));
        if (comparison != 0) return comparison;
        comparison = Integer.compare(metadataCompleteness(left), metadataCompleteness(right));
        if (comparison != 0) return comparison;
        return Integer.compare(right.inputIndex(), left.inputIndex());
    }

    private int metadataCompleteness(NormalizedCandidate candidate) {
        CandidatePaper original = candidate.originalCandidate();
        int score = 0;
        if (!original.authors().isEmpty()) score++;
        if (original.publicationDate() != null) score++;
        if (hasValue(original.language())) score++;
        if (hasValue(original.abstractText())) score++;
        if (hasValue(original.landingPageUrl())) score++;
        if (hasValue(original.pdfUrl())) score++;
        if (original.openAccess()) score++;
        return score;
    }

    private DeduplicationReason reason(DeduplicationKeyType type) {
        return switch (Objects.requireNonNull(type)) {
            case DOI -> DeduplicationReason.SAME_NORMALIZED_DOI;
            case OPENALEX_ID -> DeduplicationReason.SAME_OPENALEX_ID;
            case BIBLIOGRAPHIC -> DeduplicationReason.SAME_EXACT_BIBLIOGRAPHIC_KEY;
        };
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
