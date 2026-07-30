package com.dj1012h.researchpilot.literature.persistence.mapper;

import com.dj1012h.researchpilot.literature.persistence.entity.*;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LiteraturePersistenceMapper {

    @Select("SELECT id FROM literature_search_task WHERE task_id = #{taskId}")
    Long findTaskDatabaseId(@Param("taskId") String taskId);

    @Insert("INSERT INTO literature_search_task (task_id, task_status, review_status, public_termination_reason, requested_count, candidate_count, deduplicated_count, verified_count, partially_verified_count, unverified_count, rejected_count, model_call_count, review_model_call_count, review_repair_count, query_hash, query_length, started_at, completed_at) VALUES (#{task.taskId}, #{task.taskStatus}, #{task.reviewStatus}, #{task.publicTerminationReason}, #{task.requestedCount}, #{task.candidateCount}, #{task.deduplicatedCount}, #{task.verifiedCount}, #{task.partiallyVerifiedCount}, #{task.unverifiedCount}, #{task.rejectedCount}, #{task.modelCallCount}, #{task.reviewModelCallCount}, #{task.reviewRepairCount}, #{task.queryHash}, #{task.queryLength}, #{task.startedAt}, #{task.completedAt})")
    int insertTask(@Param("task") LiteratureSearchTaskEntity task);

    @Update("UPDATE literature_search_task SET task_status=#{task.taskStatus}, review_status=#{task.reviewStatus}, public_termination_reason=#{task.publicTerminationReason}, candidate_count=#{task.candidateCount}, deduplicated_count=#{task.deduplicatedCount}, verified_count=#{task.verifiedCount}, partially_verified_count=#{task.partiallyVerifiedCount}, unverified_count=#{task.unverifiedCount}, rejected_count=#{task.rejectedCount}, model_call_count=#{task.modelCallCount}, review_model_call_count=#{task.reviewModelCallCount}, review_repair_count=#{task.reviewRepairCount}, completed_at=#{task.completedAt}, updated_at=CURRENT_TIMESTAMP(6), version=version+1 WHERE task_id=#{task.taskId}")
    int updateTaskFinal(@Param("task") LiteratureSearchTaskEntity task);

    @Insert("INSERT INTO literature_plan_attempt (search_task_id, attempt_no, attempt_status, plan_version, prompt_version, schema_version, topic, search_query, keywords_canonical, languages_canonical, publication_types_canonical, search_sort, from_year, to_year, candidate_limit, result_limit) VALUES (#{plan.searchTaskId}, #{plan.attemptNo}, #{plan.attemptStatus}, #{plan.planVersion}, #{plan.promptVersion}, #{plan.schemaVersion}, #{plan.topic}, #{plan.searchQuery}, #{plan.keywordsCanonical}, #{plan.languagesCanonical}, #{plan.publicationTypesCanonical}, #{plan.searchSort}, #{plan.fromYear}, #{plan.toYear}, #{plan.candidateLimit}, #{plan.resultLimit})")
    int insertPlanAttempt(@Param("plan") LiteraturePlanAttemptEntity plan);

    @Select("SELECT COUNT(*) FROM literature_plan_attempt WHERE search_task_id=#{taskId} AND attempt_no=#{attemptNo}")
    int planAttemptExists(@Param("taskId") long taskId, @Param("attemptNo") int attemptNo);

    @Select("SELECT paper_id FROM literature_paper WHERE normalized_doi=#{doi}")
    Long findPaperId(@Param("doi") String doi);

    @Insert("INSERT INTO literature_paper (normalized_doi, openalex_id, title, authors_canonical, publication_year, venue, publication_type, language, cited_by_count, source) VALUES (#{paper.normalizedDoi}, #{paper.openalexId}, #{paper.title}, #{paper.authorsCanonical}, #{paper.publicationYear}, #{paper.venue}, #{paper.publicationType}, #{paper.language}, #{paper.citedByCount}, #{paper.source})")
    int insertPaper(@Param("paper") LiteraturePaperEntity paper);

    @Select("SELECT evidence_id FROM literature_verification_evidence WHERE search_task_id=#{taskId} AND candidate_fingerprint=#{fingerprint}")
    Long findEvidenceId(@Param("taskId") long taskId, @Param("fingerprint") String fingerprint);

    @Insert("INSERT INTO literature_verification_evidence (search_task_id, paper_id, candidate_fingerprint, verification_status, verification_source, reference_doi, evidence_score, verification_rule_version, reason_codes_canonical) VALUES (#{evidence.searchTaskId}, #{evidence.paperId}, #{evidence.candidateFingerprint}, #{evidence.verificationStatus}, #{evidence.verificationSource}, #{evidence.referenceDoi}, #{evidence.evidenceScore}, #{evidence.verificationRuleVersion}, #{evidence.reasonCodesCanonical})")
    int insertEvidence(@Param("evidence") LiteratureVerificationEvidenceEntity evidence);

    @Select("SELECT COUNT(*) FROM literature_verification_field_evidence WHERE verification_evidence_id=#{evidenceId} AND field_ordinal=#{ordinal}")
    int fieldEvidenceExists(@Param("evidenceId") long evidenceId, @Param("ordinal") int ordinal);

    @Insert("INSERT INTO literature_verification_field_evidence (verification_evidence_id, field_ordinal, field_name, match_status, candidate_normalized_value, reference_normalized_value, similarity_score, reason_code) VALUES (#{field.verificationEvidenceId}, #{field.fieldOrdinal}, #{field.fieldName}, #{field.matchStatus}, #{field.candidateNormalizedValue}, #{field.referenceNormalizedValue}, #{field.similarityScore}, #{field.reasonCode})")
    int insertFieldEvidence(@Param("field") LiteratureVerificationFieldEvidenceEntity field);

    @Select("SELECT COUNT(*) FROM literature_agent_step WHERE trace_id=#{traceId} AND step_index=#{stepIndex}")
    int stepExists(@Param("traceId") String traceId, @Param("stepIndex") int stepIndex);

    @Insert("INSERT INTO literature_agent_step (search_task_id, trace_id, step_index, action, decision_source, stage_before, stage_after, step_status, elapsed_ms, search_round_count_before, search_round_count_after, plan_adjustment_count_before, plan_adjustment_count_after, business_step_count_before, business_step_count_after, unique_candidate_count_before, unique_candidate_count_after, crossref_call_count_before, crossref_call_count_after, deadline_exceeded_before, deadline_exceeded_after, observation_summary, failure_code, termination_reason, started_at, finished_at) VALUES (#{step.searchTaskId}, #{step.traceId}, #{step.stepIndex}, #{step.action}, #{step.decisionSource}, #{step.stageBefore}, #{step.stageAfter}, #{step.stepStatus}, #{step.elapsedMs}, #{step.searchRoundCountBefore}, #{step.searchRoundCountAfter}, #{step.planAdjustmentCountBefore}, #{step.planAdjustmentCountAfter}, #{step.businessStepCountBefore}, #{step.businessStepCountAfter}, #{step.uniqueCandidateCountBefore}, #{step.uniqueCandidateCountAfter}, #{step.crossrefCallCountBefore}, #{step.crossrefCallCountAfter}, #{step.deadlineExceededBefore}, #{step.deadlineExceededAfter}, #{step.observationSummary}, #{step.failureCode}, #{step.terminationReason}, #{step.startedAt}, #{step.finishedAt})")
    int insertStep(@Param("step") LiteratureAgentStepEntity step);

    @Select("SELECT COUNT(*) FROM literature_task_paper_result WHERE search_task_id=#{taskId} AND paper_id=#{paperId}")
    int taskPaperExists(@Param("taskId") long taskId, @Param("paperId") long paperId);

    @Insert("INSERT INTO literature_task_paper_result (search_task_id, paper_id, result_position, relevance_score) VALUES (#{result.searchTaskId}, #{result.paperId}, #{result.resultPosition}, #{result.relevanceScore})")
    int insertTaskPaperResult(@Param("result") LiteratureTaskPaperResultEntity result);
}
