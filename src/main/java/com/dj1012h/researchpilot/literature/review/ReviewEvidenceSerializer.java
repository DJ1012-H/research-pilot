package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Serializes only the minimal review evidence projection. */
@Component
public class ReviewEvidenceSerializer {

    private final StructuredOutputMapper structuredOutputMapper;

    public ReviewEvidenceSerializer(StructuredOutputMapper structuredOutputMapper) {
        this.structuredOutputMapper = Objects.requireNonNull(
                structuredOutputMapper, "structuredOutputMapper must not be null");
    }

    public String serialize(ReviewInput input) {
        Objects.requireNonNull(input, "input must not be null");
        PromptEvidence payload = new PromptEvidence(
                input.requestedCount(),
                input.verifiedPaperCount(),
                input.abstractEvidenceCount(),
                input.evidencePapers().stream().map(PromptEvidencePaper::from).toList()
        );
        return write(payload);
    }

    public String serializeValue(Object value) {
        return write(value);
    }

    private String write(Object value) {
        try {
            return structuredOutputMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("unable to serialize review data", exception);
        }
    }

    private record PromptEvidence(
            int requestedCount,
            int verifiedPaperCount,
            int abstractEvidenceCount,
            List<PromptEvidencePaper> evidencePapers
    ) { }

    private record PromptEvidencePaper(
            String citationId,
            String normalizedDoi,
            String title,
            List<String> authorDisplayNames,
            Integer publicationYear,
            String venue,
            String abstractText
    ) {
        private static PromptEvidencePaper from(EvidencePaper paper) {
            return new PromptEvidencePaper(
                    paper.citationId().value(),
                    paper.normalizedDoi(),
                    paper.title(),
                    paper.authorDisplayNames(),
                    paper.publicationYear(),
                    paper.venue(),
                    paper.abstractText()
            );
        }
    }
}
