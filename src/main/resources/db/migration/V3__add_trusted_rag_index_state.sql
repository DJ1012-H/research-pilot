ALTER TABLE literature_paper
    ADD COLUMN current_verification_status VARCHAR(32) NOT NULL DEFAULT 'VERIFIED';

ALTER TABLE literature_paper
    ADD COLUMN verification_rule_version VARCHAR(64) NOT NULL DEFAULT 'verification-v1';

CREATE INDEX idx_literature_paper_current_verification
    ON literature_paper (current_verification_status, paper_id);

CREATE TABLE literature_rag_index_version (
    embedding_version VARCHAR(128) PRIMARY KEY,
    collection_name VARCHAR(255) NOT NULL,
    vector_dimensions INT NOT NULL,
    last_build_status VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    source_paper_count INT NOT NULL DEFAULT 0,
    point_count BIGINT NOT NULL DEFAULT 0,
    last_failure_code VARCHAR(128),
    build_started_at DATETIME(6),
    build_completed_at DATETIME(6),
    activated_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_literature_rag_index_collection UNIQUE (collection_name),
    CONSTRAINT ck_literature_rag_index_dimensions CHECK (vector_dimensions > 0),
    CONSTRAINT ck_literature_rag_index_counts CHECK (source_paper_count >= 0 AND point_count >= 0),
    CONSTRAINT ck_literature_rag_index_version CHECK (version >= 0)
);

CREATE INDEX idx_literature_rag_index_active
    ON literature_rag_index_version (active, activated_at);
