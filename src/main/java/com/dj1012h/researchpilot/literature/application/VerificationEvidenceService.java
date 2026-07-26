package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.VerificationEvidence;

public interface VerificationEvidenceService {

    VerificationEvidence compare(CandidatePaper candidate, CrossrefWorkMetadata reference);
}
