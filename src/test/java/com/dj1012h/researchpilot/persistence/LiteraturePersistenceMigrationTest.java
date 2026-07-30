package com.dj1012h.researchpilot.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiteraturePersistenceMigrationTest {

    @Test
    void shouldCreateTheSchemaAndRecordBothVersionsOnAnEmptyDatabase() throws SQLException {
        Flyway flyway = newFlyway();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            Set<String> tables = Set.of(
                    "literature_search_task",
                    "literature_plan_attempt",
                    "literature_paper",
                    "literature_verification_evidence",
                    "literature_verification_field_evidence",
                    "literature_agent_step",
                    "literature_task_paper_result"
            );
            for (String table : tables) {
                assertThat(tableExists(connection, table)).isTrue();
            }
            assertThat(queryForInt(connection,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE"))
                    .isEqualTo(1);
            assertThat(queryForInt(connection,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = TRUE"))
                    .isEqualTo(1);
        }
    }

    @Test
    void shouldMakeRepeatedMigrationADataPreservingNoOp() throws SQLException {
        Flyway flyway = newFlyway();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            insertTask(connection, "00000000-0000-0000-0000-000000000001");
        }

        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            assertThat(queryForInt(connection, "SELECT COUNT(*) FROM literature_search_task")).isEqualTo(1);
        }
    }

    @Test
    void shouldEnforceDoiForeignKeyAttemptAndRangeInvariants() throws SQLException {
        Flyway flyway = newFlyway();
        flyway.migrate();

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            insertTask(connection, "00000000-0000-0000-0000-000000000002");
            long taskId = queryForLong(connection, "SELECT id FROM literature_search_task");

            insertPaper(connection, "10.1000/unique-doi");
            assertThatThrownBy(() -> insertPaper(connection, "10.1000/unique-doi"))
                    .isInstanceOf(SQLException.class);

            insertPlanAttempt(connection, taskId, 1);
            assertThatThrownBy(() -> insertPlanAttempt(connection, taskId, 1))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertPlanAttempt(connection, 999999L, 2))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "UPDATE literature_search_task SET candidate_count = -1 WHERE id = " + taskId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "UPDATE literature_search_task SET version = -1 WHERE id = " + taskId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO literature_verification_evidence "
                            + "(search_task_id, candidate_fingerprint, verification_status, verification_source, "
                            + "evidence_score, verification_rule_version, reason_codes_canonical) VALUES ("
                            + taskId + ", 'candidate-1', 'VERIFIED', 'CROSSREF', 1.1000, 'v1', '[]')"))
                    .isInstanceOf(SQLException.class);
            insertAgentStep(connection, taskId, "00000000-0000-0000-0000-000000000003", 0);
            assertThatThrownBy(() -> insertAgentStep(connection, taskId,
                    "00000000-0000-0000-0000-000000000003", 0))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO literature_agent_step (search_task_id, trace_id, step_index, action, "
                            + "stage_before, stage_after, step_status, elapsed_ms, search_round_count_before, "
                            + "search_round_count_after, plan_adjustment_count_before, plan_adjustment_count_after, "
                            + "business_step_count_before, business_step_count_after, unique_candidate_count_before, "
                            + "unique_candidate_count_after, crossref_call_count_before, crossref_call_count_after, "
                            + "deadline_exceeded_before, deadline_exceeded_after, observation_summary, started_at, finished_at) "
                            + "VALUES (999999, '00000000-0000-0000-0000-000000000004', 0, 'COMPLETE', 'PLAN_READY', "
                            + "COMPLETED', 'SUCCEEDED', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, FALSE, FALSE, 'test', "
                            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private Flyway newFlyway() {
        return Flyway.configure()
                .dataSource("jdbc:h2:mem:literature_persistence_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    private int queryForInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private long queryForLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private void insertTask(Connection connection, String taskId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO literature_search_task (task_id, task_status, review_status, requested_count, "
                        + "query_hash, query_length, started_at) VALUES (?, 'COMPLETED', "
                        + "'INSUFFICIENT_EVIDENCE', 1, '0000000000000000000000000000000000000000000000000000000000000000', "
                        + "0, CURRENT_TIMESTAMP)")) {
            statement.setString(1, taskId);
            statement.executeUpdate();
        }
    }

    private void insertPlanAttempt(Connection connection, long taskId, int attemptNo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO literature_plan_attempt (search_task_id, attempt_no, attempt_status, plan_version, "
                        + "prompt_version, schema_version, topic, search_query, keywords_canonical, "
                        + "languages_canonical, publication_types_canonical, search_sort, from_year, to_year, "
                        + "candidate_limit, result_limit) VALUES (?, ?, 'ACCEPTED', 'v1', 'v1', 'v1', "
                        + "'topic', 'query', 'keyword', '', '', 'NEWEST', 2022, 2026, 10, 5)")) {
            statement.setLong(1, taskId);
            statement.setInt(2, attemptNo);
            statement.executeUpdate();
        }
    }

    private void insertPaper(Connection connection, String doi) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO literature_paper (normalized_doi, title, authors_canonical, source) "
                        + "VALUES (?, 'title', 'author', 'OPENALEX')")) {
            statement.setString(1, doi);
            statement.executeUpdate();
        }
    }

    private void insertAgentStep(Connection connection, long taskId, String traceId, int stepIndex) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO literature_agent_step (search_task_id, trace_id, step_index, action, stage_before, "
                        + "stage_after, step_status, elapsed_ms, search_round_count_before, search_round_count_after, "
                        + "plan_adjustment_count_before, plan_adjustment_count_after, business_step_count_before, "
                        + "business_step_count_after, unique_candidate_count_before, unique_candidate_count_after, "
                        + "crossref_call_count_before, crossref_call_count_after, deadline_exceeded_before, "
                        + "deadline_exceeded_after, observation_summary, started_at, finished_at) "
                        + "VALUES (?, ?, ?, 'COMPLETE', 'PLAN_READY', 'COMPLETED', 'SUCCEEDED', 0, 0, 0, 0, 0, "
                        + "0, 0, 0, 0, 0, 0, FALSE, FALSE, 'test step', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            statement.setLong(1, taskId);
            statement.setString(2, traceId);
            statement.setInt(3, stepIndex);
            statement.executeUpdate();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
