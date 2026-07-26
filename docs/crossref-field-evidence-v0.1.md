# Crossref Field Evidence v0.1

## Scope

This component compares an OpenAlex `CandidatePaper` with already retrieved
`CrossrefWorkMetadata` and produces evidence for DOI, title, first author,
author set, publication year, and venue. It is pure local computation: it does
not call Crossref or OpenAlex, access a database or cache, invoke an LLM, or
decide `VerificationResult` / `VERIFIED`.

`CrossrefWorkMetadata` remains the provider-isolated reference contract. Its
DOI invariant is unchanged, so a missing Crossref DOI is rejected at model
construction rather than represented as field evidence.

## Outcomes and thresholds

The existing `FieldMatchStatus` record is extended with
`EXPLAINABLE_DIFFERENCE` and `MISSING_FROM_BOTH`; missing values always have a
null score. Title scores combine token Jaccard (0.65) and normalized edit
similarity (0.35). Venue scores use exact normalized equality, then token
Jaccard. Author-set scores use Jaccard overlap after the existing
`AuthorNormalizer` produces a surname-and-initials comparison key.

All thresholds compare the raw, unrounded score. The title possible interval
(`titlePossibleMatch <= score < titleStrongMatch`) is recorded as
`NOT_EVALUATED` with `TITLE_SIMILARITY_POSSIBLE`: it means the title evidence
is insufficient for a strong match. It is neither a probability nor an
explained business difference. `EXPLAINABLE_DIFFERENCE` is reserved here for
rules with an explicit explanation, such as a publication-year delta within
the configured tolerance; a future `VerificationPolicy` decides how to use
possible title evidence.

`MISSING_FROM_CANDIDATE`, `MISSING_FROM_EVIDENCE`, and `MISSING_FROM_BOTH` are
all non-match states. They always have a null score and never use
`NORMALIZED_VALUES_EQUAL`.

The configured thresholds are version-one engineering calibration rules, not
probabilities, confidence intervals, or statistical conclusions. They must be
calibrated only on the isolated `eval/crossref-verification-v1` branch; this
application branch contains no copied evaluation fixtures, split manifest, or
acceptance result.

## Current limits

The comparison key does not resolve author identity, aliases, or complex
multi-part surnames. In particular, different full names sharing a surname and
given-name initial can produce the same key; that result is only field
evidence and never a final paper-identity decision. Venue abbreviations are not expanded. A future
`VerificationPolicy` may interpret this evidence, but is deliberately outside
this component.
