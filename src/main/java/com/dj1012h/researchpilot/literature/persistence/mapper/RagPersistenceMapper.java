package com.dj1012h.researchpilot.literature.persistence.mapper;

import com.dj1012h.researchpilot.literature.persistence.entity.RagIndexVersionEntity;
import com.dj1012h.researchpilot.literature.persistence.entity.RagPaperSourceRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface RagPersistenceMapper {

    @Select("SELECT paper_id, normalized_doi, openalex_id, title, authors_canonical, publication_year, venue, publication_type, language, abstract_text, cited_by_count, source, current_verification_status, verification_rule_version, updated_at AS source_updated_at FROM literature_paper WHERE current_verification_status='VERIFIED' ORDER BY paper_id")
    List<RagPaperSourceRow> findCurrentlyVerifiedPapers();

    @Select("""
            <script>
            SELECT paper_id, normalized_doi, openalex_id, title, authors_canonical, publication_year,
                   venue, publication_type, language, abstract_text, cited_by_count, source,
                   current_verification_status, verification_rule_version, updated_at AS source_updated_at
            FROM literature_paper
            WHERE paper_id IN
            <foreach collection="paperIds" item="paperId" open="(" separator="," close=")">
                #{paperId}
            </foreach>
            ORDER BY paper_id
            </script>
            """)
    List<RagPaperSourceRow> findPapersByIds(@Param("paperIds") java.util.Collection<Long> paperIds);

    @Select("SELECT embedding_version, collection_name, vector_dimensions, last_build_status, active, source_paper_count, point_count, last_failure_code, build_started_at, build_completed_at, activated_at FROM literature_rag_index_version WHERE embedding_version=#{embeddingVersion}")
    RagIndexVersionEntity findVersion(@Param("embeddingVersion") String embeddingVersion);

    @Select("SELECT embedding_version, collection_name, vector_dimensions, last_build_status, active, source_paper_count, point_count, last_failure_code, build_started_at, build_completed_at, activated_at FROM literature_rag_index_version WHERE active=TRUE ORDER BY activated_at DESC LIMIT 1")
    RagIndexVersionEntity findActiveVersion();

    @Insert("INSERT INTO literature_rag_index_version (embedding_version, collection_name, vector_dimensions, last_build_status, active, build_started_at) VALUES (#{definition.embeddingVersion}, #{definition.collectionName}, #{definition.vectorDimensions}, 'BUILDING', FALSE, #{startedAt})")
    int insertBuilding(
            @Param("definition") com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition definition,
            @Param("startedAt") Instant startedAt);

    @Update("UPDATE literature_rag_index_version SET collection_name=#{definition.collectionName}, vector_dimensions=#{definition.vectorDimensions}, last_build_status='BUILDING', last_failure_code=NULL, build_started_at=#{startedAt}, build_completed_at=NULL, updated_at=CURRENT_TIMESTAMP(6), version=version+1 WHERE embedding_version=#{definition.embeddingVersion}")
    int updateBuilding(
            @Param("definition") com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition definition,
            @Param("startedAt") Instant startedAt);

    @Update("UPDATE literature_rag_index_version SET active=FALSE, updated_at=CURRENT_TIMESTAMP(6), version=version+1 WHERE active=TRUE AND embedding_version<>#{embeddingVersion}")
    int deactivateOtherVersions(@Param("embeddingVersion") String embeddingVersion);

    @Update("UPDATE literature_rag_index_version SET last_build_status='SUCCEEDED', active=TRUE, source_paper_count=#{sourcePaperCount}, point_count=#{pointCount}, last_failure_code=NULL, build_completed_at=#{completedAt}, activated_at=#{completedAt}, updated_at=CURRENT_TIMESTAMP(6), version=version+1 WHERE embedding_version=#{definition.embeddingVersion}")
    int activate(
            @Param("definition") com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition definition,
            @Param("sourcePaperCount") int sourcePaperCount,
            @Param("pointCount") long pointCount,
            @Param("completedAt") Instant completedAt);

    @Update("UPDATE literature_rag_index_version SET last_build_status='FAILED', last_failure_code=#{failureCode}, build_completed_at=#{completedAt}, updated_at=CURRENT_TIMESTAMP(6), version=version+1 WHERE embedding_version=#{definition.embeddingVersion}")
    int markFailed(
            @Param("definition") com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition definition,
            @Param("failureCode") String failureCode,
            @Param("completedAt") Instant completedAt);
}
