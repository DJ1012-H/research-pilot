package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.model.CandidateLookupResult;
import com.dj1012h.researchpilot.literature.model.FieldMatchStatus;
import com.dj1012h.researchpilot.literature.model.FieldVerificationEvidence;
import com.dj1012h.researchpilot.literature.model.VerificationEvidence;
import com.dj1012h.researchpilot.literature.model.VerificationField;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Converts already collected Crossref field evidence into an explainable final status. */
@Service
public class VerificationPolicy {

    private final DoiNormalizer doiNormalizer;

    public VerificationPolicy(DoiNormalizer doiNormalizer) {
        this.doiNormalizer = Objects.requireNonNull(doiNormalizer, "doiNormalizer must not be null");
    }

    public VerificationResult evaluate(
            CandidateLookupResult lookup,
            List<VerificationEvidence> evidenceByReference
    ) {
        Objects.requireNonNull(lookup, "lookup must not be null");
        evidenceByReference = List.copyOf(Objects.requireNonNull(
                evidenceByReference, "evidenceByReference must not be null"));
        return switch (lookup.status()) {
            case NOT_FOUND -> result(VerificationResult.VerificationStatus.NOT_FOUND, null, List.of(),
                    List.of("CROSSREF_NOT_FOUND"));
            case SOURCE_UNAVAILABLE -> result(VerificationResult.VerificationStatus.SOURCE_UNAVAILABLE, null, List.of(),
                    List.of("CROSSREF_SOURCE_UNAVAILABLE"));
            case SOURCE_DISABLED, SKIPPED_BY_LIMIT, FAILED, NOT_ELIGIBLE -> result(
                    VerificationResult.VerificationStatus.NOT_CHECKED, null, List.of(),
                    List.of("CROSSREF_NOT_CHECKED_" + lookup.status()));
            case FOUND -> evaluateFound(lookup, evidenceByReference);
        };
    }

    private VerificationResult evaluateFound(
            CandidateLookupResult lookup,
            List<VerificationEvidence> evidenceByReference
    ) {
        if (evidenceByReference.size() != lookup.references().size()) {
            throw new IllegalArgumentException("FOUND references and field evidence must have equal sizes");
        }
        if (lookup.candidate().normalizedDoi() != null) {
            return evaluateCandidateWithDoi(lookup, evidenceByReference);
        }
        return evaluateCandidateWithoutDoi(lookup, evidenceByReference);
    }

    private VerificationResult evaluateCandidateWithDoi(
            CandidateLookupResult lookup,
            List<VerificationEvidence> evidenceByReference
    ) {
        VerificationEvidence evidence = evidenceByReference.getFirst();
        CrossrefWorkMetadata reference = lookup.references().getFirst();
        Map<VerificationField, FieldVerificationEvidence> fields = fields(evidence);
        if (status(fields, VerificationField.DOI) != FieldMatchStatus.MATCH) {
            return conflicted(evidence, "DOI_CONFLICT");
        }
        for (VerificationField field : List.of(
                VerificationField.TITLE, VerificationField.FIRST_AUTHOR,
                VerificationField.AUTHORS, VerificationField.YEAR
        )) {
            if (status(fields, field) == FieldMatchStatus.MISMATCH) {
                return conflicted(evidence, "HARD_FIELD_CONFLICT_" + field);
            }
        }
        return result(VerificationResult.VerificationStatus.VERIFIED, normalizedReferenceDoi(reference),
                toFieldResults(evidence), List.of("DOI_EXACT_MATCH_NO_HARD_CONFLICT"));
    }

    private VerificationResult evaluateCandidateWithoutDoi(
            CandidateLookupResult lookup,
            List<VerificationEvidence> evidenceByReference
    ) {
        List<Integer> strongMatches = java.util.stream.IntStream.range(0, evidenceByReference.size())
                .filter(index -> isStrongNoDoiMatch(lookup.references().get(index), evidenceByReference.get(index)))
                .boxed()
                .toList();
        if (strongMatches.size() == 1) {
            int index = strongMatches.getFirst();
            return result(VerificationResult.VerificationStatus.VERIFIED,
                    normalizedReferenceDoi(lookup.references().get(index)),
                    toFieldResults(evidenceByReference.get(index)), List.of("UNIQUE_STRONG_BIBLIOGRAPHIC_MATCH"));
        }
        if (strongMatches.size() > 1) {
            return result(VerificationResult.VerificationStatus.PARTIALLY_VERIFIED, null,
                    toFieldResults(evidenceByReference.getFirst()), List.of("MULTIPLE_STRONG_BIBLIOGRAPHIC_MATCHES"));
        }
        boolean possible = evidenceByReference.stream().anyMatch(this::isPossibleNoDoiMatch);
        if (possible) {
            return result(VerificationResult.VerificationStatus.PARTIALLY_VERIFIED, null,
                    toFieldResults(evidenceByReference.getFirst()), List.of("NO_UNIQUE_STRONG_BIBLIOGRAPHIC_MATCH"));
        }
        return conflicted(evidenceByReference.getFirst(), "ALL_BIBLIOGRAPHIC_REFERENCES_CONFLICT");
    }

    private boolean isStrongNoDoiMatch(CrossrefWorkMetadata reference, VerificationEvidence evidence) {
        if (normalizedReferenceDoi(reference) == null) return false;
        Map<VerificationField, FieldVerificationEvidence> fields = fields(evidence);
        if (status(fields, VerificationField.TITLE) != FieldMatchStatus.MATCH) return false;
        return List.of(VerificationField.FIRST_AUTHOR, VerificationField.AUTHORS,
                        VerificationField.YEAR, VerificationField.VENUE)
                .stream()
                .noneMatch(field -> status(fields, field) == FieldMatchStatus.MISMATCH);
    }

    private boolean isPossibleNoDoiMatch(VerificationEvidence evidence) {
        Map<VerificationField, FieldVerificationEvidence> fields = fields(evidence);
        return List.of(VerificationField.TITLE, VerificationField.FIRST_AUTHOR,
                        VerificationField.AUTHORS, VerificationField.YEAR, VerificationField.VENUE)
                .stream()
                .noneMatch(field -> status(fields, field) == FieldMatchStatus.MISMATCH);
    }

    private VerificationResult conflicted(VerificationEvidence evidence, String reason) {
        return result(VerificationResult.VerificationStatus.CONFLICTED, null,
                toFieldResults(evidence), List.of(reason));
    }

    private static Map<VerificationField, FieldVerificationEvidence> fields(VerificationEvidence evidence) {
        Map<VerificationField, FieldVerificationEvidence> values = new EnumMap<>(VerificationField.class);
        evidence.fieldEvidence().forEach(field -> values.put(field.field(), field));
        return values;
    }

    private static FieldMatchStatus status(Map<VerificationField, FieldVerificationEvidence> fields, VerificationField field) {
        FieldVerificationEvidence evidence = fields.get(field);
        return evidence == null ? FieldMatchStatus.NOT_EVALUATED : evidence.status();
    }

    private String normalizedReferenceDoi(CrossrefWorkMetadata reference) {
        return doiNormalizer.normalize(reference.doi());
    }

    private static VerificationResult result(
            VerificationResult.VerificationStatus status,
            String referenceDoi,
            List<VerificationResult.FieldVerification> fields,
            List<String> reasons
    ) {
        return new VerificationResult(status, score(fields), VerificationResult.VerificationSource.CROSSREF,
                referenceDoi, fields, reasons);
    }

    private static Double score(List<VerificationResult.FieldVerification> fields) {
        java.util.OptionalDouble average = fields.stream().map(VerificationResult.FieldVerification::similarity)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    private static List<VerificationResult.FieldVerification> toFieldResults(VerificationEvidence evidence) {
        return evidence.fieldEvidence().stream()
                .sorted(Comparator.comparing(field -> field.field().name()))
                .map(field -> new VerificationResult.FieldVerification(
                        field.field().name(), map(field.status()), field.candidateNormalizedValue(),
                        field.evidenceNormalizedValue(), field.score(), field.explanation()))
                .toList();
    }

    private static VerificationResult.FieldStatus map(FieldMatchStatus status) {
        return switch (status) {
            case MATCH -> VerificationResult.FieldStatus.MATCHED;
            case EXPLAINABLE_DIFFERENCE -> VerificationResult.FieldStatus.EXPLAINABLE_DIFFERENCE;
            case MISMATCH -> VerificationResult.FieldStatus.MISMATCHED;
            case MISSING_FROM_CANDIDATE, MISSING_FROM_EVIDENCE, MISSING_FROM_BOTH, NOT_EVALUATED ->
                    VerificationResult.FieldStatus.UNKNOWN;
        };
    }
}
