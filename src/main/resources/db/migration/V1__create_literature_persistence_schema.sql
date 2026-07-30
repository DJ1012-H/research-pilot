CREATE TABLE literature_search_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    public_termination_reason VARCHAR(64),
    requested_count INT NOT NULL,
    candidate_count INT NOT NULL DEFAULT 0,
    deduplicated_count INT NOT NULL DEFAULT 0,
    verified_count INT NOT NULL DEFAULT 0,
    partially_verified_count INT NOT NULL DEFAULT 0,
    unverified_count INT NOT NULL DEFAULT 0,
    rejected_count INT NOT NULL DEFAULT 0,
    model_call_count INT NOT NULL DEFAULT 0,
    review_model_call_count INT NOT NULL DEFAULT 0,
    review_repair_count INT NOT NULL DEFAULT 0,
    query_hash CHAR(64) NOT NULL,
    query_length INT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_literature_search_task_task_id UNIQUE (task_id),
    CONSTRAINT ck_literature_search_task_requested_count CHECK (requested_count > 0),
    CONSTRAINT ck_literature_search_task_counts CHECK (
        candidate_count >= 0
        AND deduplicated_count >= 0
        AND verified_count >= 0
        AND partially_verified_count >= 0
        AND unverified_count >= 0
        AND rejected_count >= 0
        AND candidate_count >= deduplicated_count
        AND deduplicated_count = verified_count + partially_verified_count + unverified_count + rejected_count
    ),
    CONSTRAINT ck_literature_search_task_budgets CHECK (
        model_call_count >= 0
        AND review_model_call_count >= 0
        AND review_repair_count >= 0
        AND query_length >= 0
    ),
    CONSTRAINT ck_literature_search_task_timestamps CHECK (
        completed_at IS NULL OR completed_at >= started_at
    ),
    CONSTRAINT ck_literature_search_task_version CHECK (version >= 0)
);

CREATE TABLE literature_plan_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    search_task_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    attempt_status VARCHAR(32) NOT NULL,
    plan_version VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    search_query VARCHAR(300) NOT NULL,
    keywords_canonical VARCHAR(1200) NOT NULL,
    languages_canonical VARCHAR(256) NOT NULL,
    publication_types_canonical VARCHAR(512) NOT NULL,
    search_sort VARCHAR(32) NOT NULL,
    from_year SMALLINT NOT NULL,
    to_year SMALLINT NOT NULL,
    candidate_limit INT NOT NULL,
    result_limit INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_literature_plan_attempt_task_attempt UNIQUE (search_task_id, attempt_no),
    CONSTRAINT fk_literature_plan_attempt_task FOREIGN KEY (search_task_id)
        REFERENCES literature_search_task (id),
    CONSTRAINT ck_literature_plan_attempt_number CHECK (attempt_no > 0),
    CONSTRAINT ck_literature_plan_attempt_years CHECK (from_year >= 1900 AND to_year >= from_year),
    CONSTRAINT ck_literature_plan_attempt_limits CHECK (
        result_limit BETWEEN 1 AND 50
        AND candidate_limit BETWEEN result_limit AND 100
    ),
    CONSTRAINT ck_literature_plan_attempt_version CHECK (version >= 0)
);

CREATE INDEX idx_literature_plan_attempt_task ON literature_plan_attempt (search_task_id);

CREATE TABLE literature_paper (
    paper_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    normalized_doi VARCHAR(512) NOT NULL,
    openalex_id VARCHAR(64),
    title VARCHAR(1000) NOT NULL,
    authors_canonical VARCHAR(4000) NOT NULL,
    publication_year SMALLINT,
    venue VARCHAR(1000),
    publication_type VARCHAR(128),
    language VARCHAR(32),
    abstract_text MEDIUMTEXT,
    cited_by_count INT NOT NULL DEFAULT 0,
    source VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_literature_paper_normalized_doi UNIQUE (normalized_doi),
    CONSTRAINT uq_literature_paper_openalex_id UNIQUE (openalex_id),
    CONSTRAINT ck_literature_paper_publication_year CHECK (
        publication_year IS NULL OR publication_year BETWEEN 1000 AND 9999
    ),
    CONSTRAINT ck_literature_paper_cited_by_count CHECK (cited_by_count >= 0),
    CONSTRAINT ck_literature_paper_version CHECK (version >= 0)
);

CREATE TABLE literature_verification_evidence (
    evidence_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    search_task_id BIGINT NOT NULL,
    paper_id BIGINT,
    candidate_fingerprint VARCHAR(512) NOT NULL,
    verification_status VARCHAR(32) NOT NULL,
    verification_source VARCHAR(32) NOT NULL,
    reference_doi VARCHAR(512),
    evidence_score DECIMAL(5,4),
    verification_rule_version VARCHAR(64) NOT NULL,
    reason_codes_canonical VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_literature_verification_evidence_task_candidate
        UNIQUE (search_task_id, candidate_fingerprint),
    CONSTRAINT fk_literature_verification_evidence_task FOREIGN KEY (search_task_id)
        REFERENCES literature_search_task (id),
    CONSTRAINT fk_literature_verification_evidence_paper FOREIGN KEY (paper_id)
        REFERENCES literature_paper (paper_id),
    CONSTRAINT ck_literature_verification_evidence_score CHECK (
        evidence_score IS NULL OR evidence_score BETWEEN 0.0000 AND 1.0000
    ),
    CONSTRAINT ck_literature_verification_evidence_version CHECK (version >= 0)
);

CREATE INDEX idx_literature_verification_evidence_task
    ON literature_verification_evidence (search_task_id);
CREATE INDEX idx_literature_verification_evidence_paper
    ON literature_verification_evidence (paper_id);

CREATE TABLE literature_verification_field_evidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    verification_evidence_id BIGINT NOT NULL,
    field_ordinal INT NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    match_status VARCHAR(32) NOT NULL,
    candidate_normalized_value VARCHAR(1000),
    reference_normalized_value VARCHAR(1000),
    similarity_score DECIMAL(5,4),
    reason_code VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_literature_verification_field_evidence_ordinal
        UNIQUE (verification_evidence_id, field_ordinal),
    CONSTRAINT fk_literature_verification_field_evidence_parent FOREIGN KEY (verification_evidence_id)
        REFERENCES literature_verification_evidence (evidence_id),
    CONSTRAINT ck_literature_verification_field_evidence_ordinal CHECK (field_ordinal > 0),
    CONSTRAINT ck_literature_verification_field_evidence_score CHECK (
        similarity_score IS NULL OR similarity_score BETWEEN 0.0000 AND 1.0000
    )
);

CREATE INDEX idx_literature_verification_field_evidence_parent
    ON literature_verification_field_evidence (verification_evidence_id);
