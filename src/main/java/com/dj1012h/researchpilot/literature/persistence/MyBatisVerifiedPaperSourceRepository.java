package com.dj1012h.researchpilot.literature.persistence;

import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.persistence.entity.RagPaperSourceRow;
import com.dj1012h.researchpilot.literature.persistence.mapper.RagPersistenceMapper;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperSource;
import com.dj1012h.researchpilot.literature.rag.index.VerifiedPaperSourceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(name = "app.literature.persistence.enabled", havingValue = "true")
public class MyBatisVerifiedPaperSourceRepository implements VerifiedPaperSourceRepository {

    private final RagPersistenceMapper mapper;

    public MyBatisVerifiedPaperSourceRepository(RagPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public List<VerifiedPaperSource> findCurrentlyVerified() {
        return mapper.findCurrentlyVerifiedPapers().stream().map(this::toSource).toList();
    }

    private VerifiedPaperSource toSource(RagPaperSourceRow row) {
        VerificationResult.VerificationStatus status;
        try {
            status = VerificationResult.VerificationStatus.valueOf(row.currentVerificationStatus());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new LiteraturePersistenceException("stored paper has an invalid current verification status", exception);
        }
        if (status != VerificationResult.VerificationStatus.VERIFIED) {
            throw new LiteraturePersistenceException("trusted-paper query returned a non-VERIFIED row");
        }
        PaperDTO paper;
        try {
            paper = new PaperDTO(
                    row.openalexId(),
                    row.normalizedDoi(),
                    row.title(),
                    authors(row.authorsCanonical()),
                    row.publicationYear(),
                    row.venue(),
                    List.of(),
                    row.publicationType(),
                    null,
                    row.abstractText(),
                    row.language(),
                    List.of(),
                    row.citedByCount(),
                    PaperDTO.LiteratureSource.valueOf(row.source()));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new LiteraturePersistenceException("stored paper cannot form a controlled RAG source", exception);
        }
        VerificationResult verification = new VerificationResult(
                status,
                1.0,
                VerificationResult.VerificationSource.CROSSREF,
                row.normalizedDoi(),
                List.of(),
                List.of("CURRENT_MYSQL_TRUST_STATE"));
        return new VerifiedPaperSource(
                row.paperId(),
                paper,
                verification,
                row.normalizedDoi(),
                row.verificationRuleVersion(),
                row.sourceUpdatedAt());
    }

    private List<PaperDTO.Author> authors(String canonical) {
        if (canonical == null || canonical.isBlank()) return List.of();
        return Arrays.stream(canonical.split("\\|", -1))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> new PaperDTO.Author(null, value, null))
                .toList();
    }
}
