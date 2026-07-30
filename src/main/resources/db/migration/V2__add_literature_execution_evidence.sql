CREATE TABLE literature_agent_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    search_task_id BIGINT NOT NULL,
    trace_id CHAR(36) NOT NULL,
    step_index INT NOT NULL,
    action VARCHAR(32) NOT NULL,
    decision_source VARCHAR(32),
    stage_before VARCHAR(32) NOT NULL,
    stage_after VARCHAR(32) NOT NULL,
    step_status VARCHAR(32) NOT NULL,
    elapsed_ms BIGINT NOT NULL,
    search_round_count_before INT NOT NULL,
    search_round_count_after INT NOT NULL,
    plan_adjustment_count_before INT NOT NULL,
    plan_adjustment_count_after INT NOT NULL,
    business_step_count_before INT NOT NULL,
    business_step_count_after INT NOT NULL,
    unique_candidate_count_before INT NOT NULL,
    unique_candidate_count_after INT NOT NULL,
    crossref_call_count_before INT NOT NULL,
    crossref_call_count_after INT NOT NULL,
    deadline_exceeded_before BOOLEAN NOT NULL,
    deadline_exceeded_after BOOLEAN NOT NULL,
    observation_summary VARCHAR(500) NOT NULL,
    failure_code VARCHAR(128),
    termination_reason VARCHAR(64),
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_literature_agent_step_trace_index UNIQUE (trace_id, step_index),
    CONSTRAINT fk_literature_agent_step_task FOREIGN KEY (search_task_id)
        REFERENCES literature_search_task (id),
    CONSTRAINT ck_literature_agent_step_index CHECK (step_index >= 0),
    CONSTRAINT ck_literature_agent_step_elapsed CHECK (elapsed_ms >= 0),
    CONSTRAINT ck_literature_agent_step_counts CHECK (
        search_round_count_before >= 0 AND search_round_count_after >= 0
        AND plan_adjustment_count_before >= 0 AND plan_adjustment_count_after >= 0
        AND business_step_count_before >= 0 AND business_step_count_after >= 0
        AND unique_candidate_count_before >= 0 AND unique_candidate_count_after >= 0
        AND crossref_call_count_before >= 0 AND crossref_call_count_after >= 0
    ),
    CONSTRAINT ck_literature_agent_step_timestamps CHECK (finished_at >= started_at)
);

CREATE INDEX idx_literature_agent_step_task ON literature_agent_step (search_task_id);
CREATE INDEX idx_literature_agent_step_trace ON literature_agent_step (trace_id);
CREATE INDEX idx_literature_agent_step_task_created ON literature_agent_step (search_task_id, created_at);

CREATE TABLE literature_task_paper_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    search_task_id BIGINT NOT NULL,
    paper_id BIGINT NOT NULL,
    result_position INT NOT NULL,
    relevance_score DECIMAL(5,4) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_literature_task_paper_result_task_position UNIQUE (search_task_id, result_position),
    CONSTRAINT uq_literature_task_paper_result_task_paper UNIQUE (search_task_id, paper_id),
    CONSTRAINT fk_literature_task_paper_result_task FOREIGN KEY (search_task_id)
        REFERENCES literature_search_task (id),
    CONSTRAINT fk_literature_task_paper_result_paper FOREIGN KEY (paper_id)
        REFERENCES literature_paper (paper_id),
    CONSTRAINT ck_literature_task_paper_result_position CHECK (result_position >= 0),
    CONSTRAINT ck_literature_task_paper_result_score CHECK (relevance_score BETWEEN 0.0000 AND 1.0000)
);

CREATE INDEX idx_literature_task_paper_result_task ON literature_task_paper_result (search_task_id);
CREATE INDEX idx_literature_task_paper_result_paper ON literature_task_paper_result (paper_id);
