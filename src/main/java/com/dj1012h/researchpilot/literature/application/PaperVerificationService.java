package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.model.CandidateLookupResult;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.VerificationEvidence;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** Coordinates field-evidence production and policy evaluation without external calls. */
@Service
public class PaperVerificationService {

    private final VerificationEvidenceService evidenceService;
    private final VerificationPolicy verificationPolicy;
    private final DoiNormalizer doiNormalizer;

    public PaperVerificationService(
            VerificationEvidenceService evidenceService,
            VerificationPolicy verificationPolicy,
            DoiNormalizer doiNormalizer
    ) {
        this.evidenceService = Objects.requireNonNull(evidenceService, "evidenceService must not be null");
        this.verificationPolicy = Objects.requireNonNull(verificationPolicy, "verificationPolicy must not be null");
        this.doiNormalizer = Objects.requireNonNull(doiNormalizer, "doiNormalizer must not be null");
    }

    public List<CandidateVerificationOutcome> verify(CrossrefLookupSummary lookupSummary) {
        Objects.requireNonNull(lookupSummary, "lookupSummary must not be null");
        return lookupSummary.candidateResults().stream().map(this::verify).toList();
    }

    private CandidateVerificationOutcome verify(CandidateLookupResult lookup) {
        List<VerificationEvidence> evidence = lookup.status() == CandidateLookupResult.LookupStatus.FOUND
                ? lookup.references().stream().map(reference -> evidenceService.compare(
                lookup.candidate().originalCandidate(), reference)).toList()
                : List.of();
        VerificationResult verification = verificationPolicy.evaluate(lookup, evidence);
        CrossrefWorkMetadata selected = selectReference(lookup.references(), verification.referenceDoi());
        return new CandidateVerificationOutcome(lookup.candidate().originalCandidate(), selected, verification);
    }

    private CrossrefWorkMetadata selectReference(List<CrossrefWorkMetadata> references, String referenceDoi) {
        if (referenceDoi == null) return null;
        return references.stream()
                .filter(reference -> referenceDoi.equals(doiNormalizer.normalize(reference.doi())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("verified reference DOI was not retained by lookup"));
    }
}
