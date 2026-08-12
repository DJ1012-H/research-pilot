package com.dj1012h.researchpilot.literature.rag.index;

import com.dj1012h.researchpilot.literature.rag.RagPointPayload;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjection;

import java.util.List;
import java.util.UUID;

/** Provider-neutral boundary for the rebuildable vector index. */
public interface RagIndexPort {

    void ensureCollection(RagIndexDefinition definition);

    List<RagPointPayload> listPayloads(RagIndexDefinition definition);

    void upsert(RagIndexDefinition definition, List<VerifiedPaperProjection> projections);

    void replacePayloads(RagIndexDefinition definition, List<RagPointPayload> payloads);

    void deletePoints(RagIndexDefinition definition, List<UUID> pointIds);

    long count(RagIndexDefinition definition);

    void validateForActivation(RagIndexDefinition definition, RagPointPayload sample);

    /** Queries the derived index with a server-built, bounded request. */
    List<RagIndexSearchHit> search(RagIndexDefinition definition, RagIndexSearchRequest request);

    RagIndexProbe probe();
}
