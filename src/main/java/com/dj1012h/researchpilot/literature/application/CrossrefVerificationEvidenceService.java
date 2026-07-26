package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.integration.crossref.VerificationThresholdProperties;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.FieldMatchStatus;
import com.dj1012h.researchpilot.literature.model.FieldVerificationEvidence;
import com.dj1012h.researchpilot.literature.model.VerificationEvidence;
import com.dj1012h.researchpilot.literature.model.VerificationField;
import com.dj1012h.researchpilot.literature.normalization.AuthorNormalizer;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.normalization.TitleNormalizer;
import com.dj1012h.researchpilot.literature.normalization.VenueNormalizer;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Creates field-level Crossref evidence and deliberately does not decide a final verification status. */
@Service
public class CrossrefVerificationEvidenceService implements VerificationEvidenceService {

    private static final String EVIDENCE_SOURCE = "CROSSREF";

    private final DoiNormalizer doiNormalizer;
    private final TitleNormalizer titleNormalizer;
    private final AuthorNormalizer authorNormalizer;
    private final VenueNormalizer venueNormalizer;
    private final BibliographicSimilarityCalculator similarityCalculator;
    private final VerificationThresholdProperties thresholds;

    public CrossrefVerificationEvidenceService(
            DoiNormalizer doiNormalizer,
            TitleNormalizer titleNormalizer,
            AuthorNormalizer authorNormalizer,
            VenueNormalizer venueNormalizer,
            BibliographicSimilarityCalculator similarityCalculator,
            VerificationThresholdProperties thresholds
    ) {
        this.doiNormalizer = Objects.requireNonNull(doiNormalizer, "doiNormalizer must not be null");
        this.titleNormalizer = Objects.requireNonNull(titleNormalizer, "titleNormalizer must not be null");
        this.authorNormalizer = Objects.requireNonNull(authorNormalizer, "authorNormalizer must not be null");
        this.venueNormalizer = Objects.requireNonNull(venueNormalizer, "venueNormalizer must not be null");
        this.similarityCalculator = Objects.requireNonNull(similarityCalculator, "similarityCalculator must not be null");
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds must not be null");
    }

    @Override
    public VerificationEvidence compare(CandidatePaper candidate, CrossrefWorkMetadata reference) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
        return new VerificationEvidence(
                evidenceCandidateId(candidate),
                EVIDENCE_SOURCE,
                List.of(
                        compareDoi(candidate.doi(), reference.doi()),
                        compareTitle(candidate.title(), reference.title()),
                        compareFirstAuthor(firstCandidateAuthor(candidate), firstReferenceAuthor(reference)),
                        compareAuthors(candidate.authors(), reference.authorNames()),
                        compareYear(candidate.publicationYear(), reference.publicationYear()),
                        compareVenue(candidate.sourceName(), reference.venue())
                )
        );
    }

    private FieldVerificationEvidence compareDoi(String candidateDoi, String referenceDoi) {
        String candidate = doiNormalizer.normalize(candidateDoi);
        String reference = doiNormalizer.normalize(referenceDoi);
        FieldVerificationEvidence missing = missing(VerificationField.DOI, candidate, reference);
        if (missing != null) return missing;
        return candidate.equals(reference)
                ? evidence(VerificationField.DOI, candidate, reference, FieldMatchStatus.MATCH, 1.0, "DOI_EQUAL")
                : evidence(VerificationField.DOI, candidate, reference, FieldMatchStatus.MISMATCH, 0.0, "DOI_CONFLICT");
    }

    private FieldVerificationEvidence compareTitle(String candidateTitle, String referenceTitle) {
        String candidate = titleNormalizer.normalize(candidateTitle);
        String reference = titleNormalizer.normalize(referenceTitle);
        FieldVerificationEvidence missing = missing(VerificationField.TITLE, candidate, reference);
        if (missing != null) return missing;
        double score = similarityCalculator.titleSimilarity(candidate, reference);
        FieldMatchStatus status = classifyTitleScore(score, thresholds);
        if (status == FieldMatchStatus.MATCH) {
            String reason = candidate.equals(reference) ? "NORMALIZED_VALUES_EQUAL" : "TITLE_SIMILARITY_STRONG";
            return evidence(VerificationField.TITLE, candidate, reference, FieldMatchStatus.MATCH, score, reason);
        }
        if (status == FieldMatchStatus.NOT_EVALUATED) {
            return evidence(VerificationField.TITLE, candidate, reference,
                    FieldMatchStatus.NOT_EVALUATED, score, "TITLE_SIMILARITY_POSSIBLE");
        }
        return evidence(VerificationField.TITLE, candidate, reference,
                FieldMatchStatus.MISMATCH, score, "TITLE_SIMILARITY_BELOW_THRESHOLD");
    }

    private FieldVerificationEvidence compareFirstAuthor(String candidateAuthor, String referenceAuthor) {
        String candidate = authorNormalizer.normalizeForComparison(candidateAuthor);
        String reference = authorNormalizer.normalizeForComparison(referenceAuthor);
        FieldVerificationEvidence missing = missing(VerificationField.FIRST_AUTHOR, candidate, reference);
        if (missing != null) return missing;
        return candidate.equals(reference)
                ? evidence(VerificationField.FIRST_AUTHOR, candidate, reference, FieldMatchStatus.MATCH, 1.0, "FIRST_AUTHOR_EQUAL")
                : evidence(VerificationField.FIRST_AUTHOR, candidate, reference, FieldMatchStatus.MISMATCH, 0.0, "FIRST_AUTHOR_CONFLICT");
    }

    private FieldVerificationEvidence compareAuthors(List<CandidatePaper.Author> candidateAuthors, List<String> referenceAuthors) {
        Set<String> candidate = candidateAuthorKeys(candidateAuthors);
        Set<String> reference = referenceAuthorKeys(referenceAuthors);
        if (candidate.isEmpty() || reference.isEmpty()) {
            return missing(VerificationField.AUTHORS,
                    candidate.isEmpty() ? null : String.join("|", candidate),
                    reference.isEmpty() ? null : String.join("|", reference));
        }
        Set<String> union = new HashSet<>(candidate);
        union.addAll(reference);
        Set<String> intersection = new HashSet<>(candidate);
        intersection.retainAll(reference);
        double score = (double) intersection.size() / union.size();
        return score >= thresholds.authorOverlap()
                ? evidence(VerificationField.AUTHORS, String.join("|", candidate), String.join("|", reference),
                FieldMatchStatus.MATCH, score, "AUTHOR_OVERLAP_ABOVE_THRESHOLD")
                : evidence(VerificationField.AUTHORS, String.join("|", candidate), String.join("|", reference),
                FieldMatchStatus.MISMATCH, score, "AUTHOR_OVERLAP_BELOW_THRESHOLD");
    }

    private FieldVerificationEvidence compareYear(Integer candidateYear, Integer referenceYear) {
        if (candidateYear == null || referenceYear == null) {
            return missing(VerificationField.YEAR,
                    candidateYear == null ? null : candidateYear.toString(),
                    referenceYear == null ? null : referenceYear.toString());
        }
        int difference = Math.abs(candidateYear - referenceYear);
        if (difference == 0) {
            return evidence(VerificationField.YEAR, candidateYear.toString(), referenceYear.toString(),
                    FieldMatchStatus.MATCH, 1.0, "PUBLICATION_YEAR_EQUAL");
        }
        if (difference <= thresholds.publicationYearTolerance()) {
            return evidence(VerificationField.YEAR, candidateYear.toString(), referenceYear.toString(),
                    FieldMatchStatus.EXPLAINABLE_DIFFERENCE, null,
                    "PUBLICATION_YEAR_WITHIN_TOLERANCE;delta=" + difference);
        }
        return evidence(VerificationField.YEAR, candidateYear.toString(), referenceYear.toString(),
                FieldMatchStatus.MISMATCH, null, "PUBLICATION_YEAR_OUTSIDE_TOLERANCE;delta=" + difference);
    }

    private FieldVerificationEvidence compareVenue(String candidateVenue, String referenceVenue) {
        String candidate = venueNormalizer.normalize(candidateVenue);
        String reference = venueNormalizer.normalize(referenceVenue);
        FieldVerificationEvidence missing = missing(VerificationField.VENUE, candidate, reference);
        if (missing != null) return missing;
        double score = similarityCalculator.venueSimilarity(candidate, reference);
        return score >= thresholds.sourceMatch()
                ? evidence(VerificationField.VENUE, candidate, reference, FieldMatchStatus.MATCH, score,
                candidate.equals(reference) ? "NORMALIZED_VALUES_EQUAL" : "SOURCE_SIMILARITY_ABOVE_THRESHOLD")
                : evidence(VerificationField.VENUE, candidate, reference, FieldMatchStatus.MISMATCH, score,
                "SOURCE_SIMILARITY_BELOW_THRESHOLD");
    }

    private static FieldVerificationEvidence missing(VerificationField field, String candidate, String reference) {
        if (candidate != null && reference != null) return null;
        if (candidate == null && reference == null) {
            return evidence(field, null, null, FieldMatchStatus.MISSING_FROM_BOTH, null, "BOTH_VALUES_MISSING");
        }
        if (candidate == null) {
            return evidence(field, null, reference, FieldMatchStatus.MISSING_FROM_CANDIDATE, null,
                    "CANDIDATE_VALUE_MISSING");
        }
        return evidence(field, candidate, null, FieldMatchStatus.MISSING_FROM_EVIDENCE, null,
                "REFERENCE_VALUE_MISSING");
    }

    private static FieldVerificationEvidence evidence(
            VerificationField field,
            String candidate,
            String reference,
            FieldMatchStatus status,
            Double score,
            String reason
    ) {
        return new FieldVerificationEvidence(field, candidate, reference, status, score, reason);
    }

    static FieldMatchStatus classifyTitleScore(double rawScore, VerificationThresholdProperties thresholds) {
        if (rawScore >= thresholds.titleStrongMatch()) {
            return FieldMatchStatus.MATCH;
        }
        if (rawScore >= thresholds.titlePossibleMatch()) {
            // No public POSSIBLE_MATCH state exists. This means that field evidence is inconclusive,
            // not that a business rule has explained the title difference.
            return FieldMatchStatus.NOT_EVALUATED;
        }
        return FieldMatchStatus.MISMATCH;
    }

    private static String firstCandidateAuthor(CandidatePaper candidate) {
        if (candidate.authors() == null) return null;
        return candidate.authors().stream()
                .filter(Objects::nonNull)
                .map(CandidatePaper.Author::displayName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String firstReferenceAuthor(CrossrefWorkMetadata reference) {
        return reference.authorNames().stream()
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);
    }

    private Set<String> candidateAuthorKeys(List<CandidatePaper.Author> authors) {
        if (authors == null) return Set.of();
        Set<String> keys = new java.util.TreeSet<>();
        for (CandidatePaper.Author author : authors) {
            if (author == null) continue;
            String key = authorNormalizer.normalizeForComparison(author.displayName());
            if (key != null) keys.add(key);
        }
        return Set.copyOf(keys);
    }

    private Set<String> referenceAuthorKeys(List<String> authors) {
        Set<String> keys = new java.util.TreeSet<>();
        for (String author : authors) {
            String key = authorNormalizer.normalizeForComparison(author);
            if (key != null) keys.add(key);
        }
        return Set.copyOf(keys);
    }

    private static String evidenceCandidateId(CandidatePaper candidate) {
        if (candidate.openAlexId() != null && !candidate.openAlexId().isBlank()) return candidate.openAlexId().trim();
        if (candidate.doi() != null && !candidate.doi().isBlank()) return candidate.doi().trim();
        if (candidate.title() != null && !candidate.title().isBlank()) return candidate.title().trim();
        return "unidentified-candidate";
    }
}
