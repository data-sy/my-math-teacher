package com.mmt.api.repository.concept;

import com.mmt.api.domain.Concept;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcTemplateConceptRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateConceptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int findSkillIdByConceptId(int conceptId) {
        String sql = "SELECT skill_id FROM concepts WHERE concept_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, conceptId);
        } catch (EmptyResultDataAccessException e) {
            return -1; // 예를 들어 -1 또는 다른 기본값을 반환하거나 예외를 처리할 수 있습니다.
        }
    }

    public List<Concept> findAllByChapterId(int chapterId){
        String sql = "SELECT concept_id, concept_name FROM concepts WHERE concept_chapter_id = ?";
        return jdbcTemplate.query(sql, conceptNameRowMapper(), chapterId);
    }

    public String findSchoolLevelByConceptId(int conceptId){
        String sql = "SELECT ch.school_level FROM chapters ch JOIN concepts c ON ch.chapter_id = c.concept_chapter_id WHERE c.concept_id = ?";
        return jdbcTemplate.queryForObject(sql, String.class, conceptId);
    }

    public Concept findOneByConceptId(int conceptId) {
        String sql = "SELECT c.concept_id, c.concept_name, c.concept_description, c.concept_achievement_name, ch.school_level, ch.grade_level, ch.semester, ch.chapter_main, ch.chapter_sub, ch.chapter_name \n" +
                "FROM concepts c JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id WHERE c.concept_id = ?";
        return jdbcTemplate.queryForObject(sql, conceptRowMapper(), conceptId);
    }

    public List<ConceptDepth> findPrerequisitesWithDepth(int conceptId, int maxDepth) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be non-negative, got: " + maxDepth);
        }
        String sql = """
            WITH RECURSIVE prerequisite_path AS (
                SELECT concept_id, 0 AS depth
                FROM concepts WHERE concept_id = ?

                UNION ALL

                SELECT c.concept_id, pp.depth + 1
                FROM prerequisite_path pp
                JOIN knowledge_space ks ON pp.concept_id = ks.from_concept_id
                JOIN concepts c           ON ks.to_concept_id = c.concept_id
                WHERE pp.depth < ?
            )
            SELECT concept_id, MIN(depth) AS depth
            FROM prerequisite_path
            GROUP BY concept_id
            """;
        // MIN(depth): 다중 경로로 같은 노드에 도달 시 최단 거리 채택.
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new ConceptDepth(rs.getInt("concept_id"), rs.getInt("depth")),
            conceptId, maxDepth);
    }

    private RowMapper<Concept> conceptNameRowMapper() {
        return (rs, rowNum) -> {
            Concept concept = new Concept();
            concept.setConceptId(rs.getInt("concept_id"));
            concept.setName(rs.getString("concept_name"));
            return concept;
        };
    }
    private RowMapper<Concept> conceptRowMapper() {
        return (rs, rowNum) -> {
            Concept concept = new Concept();
            concept.setConceptId(rs.getInt("concept_id"));
            concept.setName(rs.getString("concept_name"));
            concept.setDesc(rs.getString("concept_description"));
            concept.setAchievementName(rs.getString("concept_achievement_name"));
            concept.setSchoolLevel(rs.getString("school_level"));
            concept.setGradeLevel(rs.getString("grade_level"));
            concept.setSemester(rs.getString("semester"));
            concept.setChapterMain(rs.getString("chapter_main"));
            concept.setChapterSub(rs.getString("chapter_sub"));
            concept.setChapterName(rs.getString("chapter_name"));
            return concept;
        };
    }
}
