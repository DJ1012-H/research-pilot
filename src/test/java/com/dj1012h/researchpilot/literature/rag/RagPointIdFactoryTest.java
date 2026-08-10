package com.dj1012h.researchpilot.literature.rag;

import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagPointIdFactoryTest {

    @Test
    void shouldProduceStableUuidV5AndChangeWithEveryCanonicalField() {
        UUID first = RagPointIdFactory.create(42L, RagEmbeddingProfile.INITIAL_VERSION, RagSegmentType.METADATA, 0);
        UUID repeated = RagPointIdFactory.create(42L, RagEmbeddingProfile.INITIAL_VERSION, RagSegmentType.METADATA, 0);

        assertThat(first).isEqualTo(repeated);
        assertThat(first).isEqualTo(UUID.fromString("64ad71af-aece-51b2-8604-561b424218be"));
        assertThat(first.version()).isEqualTo(5);
        assertThat(first.variant()).isEqualTo(2);
        assertThat(RagPointIdFactory.create(42L, "版本", RagSegmentType.ABSTRACT, 1))
                .isEqualTo(UUID.fromString("d4eee0a3-7701-5ddb-8581-fb63a0c8f37f"));
        assertThat(Set.of(
                first,
                RagPointIdFactory.create(43L, RagEmbeddingProfile.INITIAL_VERSION, RagSegmentType.METADATA, 0),
                RagPointIdFactory.create(42L, "qe06b-d1024-t1-c350-o30-n2", RagSegmentType.METADATA, 0),
                RagPointIdFactory.create(42L, RagEmbeddingProfile.INITIAL_VERSION, RagSegmentType.ABSTRACT, 0),
                RagPointIdFactory.create(42L, RagEmbeddingProfile.INITIAL_VERSION, RagSegmentType.METADATA, 1)))
                .hasSize(5);
    }

    @Test
    void shouldRejectSeparatorAndInvalidCanonicalFields() {
        assertThatThrownBy(() -> RagPointIdFactory.create(42L, "version|other", RagSegmentType.METADATA, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagEmbeddingProfile("model|other", "version", 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RagPointIdFactory.create(0L, "version", RagSegmentType.METADATA, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RagPointIdFactory.create(42L, "version", RagSegmentType.METADATA, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
