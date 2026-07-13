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

    /**
     * 개념명 부분 일치 검색 (자동완성용). concepts JOIN chapters 로 학교/학년/단원 맥락 포함.
     * 접두 일치를 부분 일치보다 먼저 노출하고, schoolLevel 이 주어지면 학교급으로 좁힌다.
     * LIKE 와일드카드(%, _, \)는 이스케이프(MySQL 기본 escape '\').
     */
    public List<Concept> searchByName(String query, String schoolLevel, int limit) {
        boolean hasSchoolLevel = schoolLevel != null && !schoolLevel.isBlank();
        String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String contains = "%" + escaped + "%";
        String prefix = escaped + "%";
        String sql = "SELECT c.concept_id, c.concept_name, ch.school_level, ch.grade_level, ch.semester, ch.chapter_name "
                + "FROM concepts c JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id "
                + "WHERE c.concept_name LIKE ? "
                + (hasSchoolLevel ? "AND ch.school_level = ? " : "")
                + "ORDER BY CASE WHEN c.concept_name LIKE ? THEN 0 ELSE 1 END, c.concept_name "
                + "LIMIT ?";
        if (hasSchoolLevel) {
            return jdbcTemplate.query(sql, conceptSearchRowMapper(), contains, schoolLevel, prefix, limit);
        }
        return jdbcTemplate.query(sql, conceptSearchRowMapper(), contains, prefix, limit);
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

    public List<Concept> findPrerequisiteConcepts(int conceptId, int maxDepth) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be non-negative, got: " + maxDepth);
        }
        // ADR 0005: concepts JOIN chapters 패턴. conceptSection 매핑 생략(프론트 미바인딩).
        // 외부 SELECT 의 (SELECT DISTINCT concept_id FROM prerequisite_path) 로
        // 다중 경로 중복을 평탄화하지 않으면 동일 conceptId 가 여러 번 등장한다.
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
            SELECT c.concept_id, c.concept_name, c.concept_description,
                   c.concept_chapter_id, c.concept_achievement_id, c.concept_achievement_name,
                   ch.school_level, ch.grade_level, ch.semester,
                   ch.chapter_main, ch.chapter_sub, ch.chapter_name
            FROM (SELECT DISTINCT concept_id FROM prerequisite_path) pp
            JOIN concepts c  ON pp.concept_id = c.concept_id
            JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id
            """;
        return jdbcTemplate.query(sql, conceptWithChapterRowMapper(), conceptId, maxDepth);
    }

    // ADR 0005: 그래프 객체 반환 경로 전용 RowMapper. concepts JOIN chapters 의
    // 12 컬럼을 매핑하며 conceptSection 은 미설정(프론트 미바인딩 — null 유지).
    private RowMapper<Concept> conceptWithChapterRowMapper() {
        return (rs, rowNum) -> {
            Concept concept = new Concept();
            concept.setConceptId(rs.getInt("concept_id"));
            concept.setName(rs.getString("concept_name"));
            concept.setDesc(rs.getString("concept_description"));
            concept.setChapterId(rs.getInt("concept_chapter_id"));
            concept.setAchievementId(rs.getInt("concept_achievement_id"));
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

    private RowMapper<Concept> conceptNameRowMapper() {
        return (rs, rowNum) -> {
            Concept concept = new Concept();
            concept.setConceptId(rs.getInt("concept_id"));
            concept.setName(rs.getString("concept_name"));
            return concept;
        };
    }

    // 검색 결과 경량 매핑: 자동완성/breadcrumb 에 필요한 학교/학년/학기/단원만(설명 제외).
    private RowMapper<Concept> conceptSearchRowMapper() {
        return (rs, rowNum) -> {
            Concept concept = new Concept();
            concept.setConceptId(rs.getInt("concept_id"));
            concept.setName(rs.getString("concept_name"));
            concept.setSchoolLevel(rs.getString("school_level"));
            concept.setGradeLevel(rs.getString("grade_level"));
            concept.setSemester(rs.getString("semester"));
            concept.setChapterName(rs.getString("chapter_name"));
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

    // ===== M7 spec-01 자가진단 (ADR-0010) — 이하 신규 경로 전용, 구 경로 무접촉 =====

    /**
     * 시작 프론티어(spec-01 §4.2): 단원 내 후수-최상위 개념 — 같은 단원의 다른 개념이
     * 자기를 선수로 요구하지 않는 개념. ORDER BY concept_id = 결정론 계약의 일부.
     */
    public List<ConceptSummary> findFrontierByChapterId(int chapterId) {
        String sql = """
            SELECT c.concept_id, c.concept_name, c.concept_description FROM concepts c
            WHERE c.concept_chapter_id = ?
              AND NOT EXISTS (SELECT 1 FROM knowledge_space ks
                              JOIN concepts c2 ON c2.concept_id = ks.from_concept_id
                              WHERE ks.to_concept_id = c.concept_id
                                AND c2.concept_chapter_id = c.concept_chapter_id)
            ORDER BY c.concept_id
            """;
        return jdbcTemplate.query(sql, conceptSummaryRowMapper(), chapterId);
    }

    /**
     * 전체 훑기 escape(spec-01 §4.2-b): 학교급 전 단원 프론티어 합집합.
     */
    public List<ConceptSummary> findFrontierBySchoolLevel(String schoolLevel) {
        String sql = """
            SELECT c.concept_id, c.concept_name, c.concept_description FROM concepts c
            JOIN chapters ch ON ch.chapter_id = c.concept_chapter_id
            WHERE ch.school_level = ?
              AND NOT EXISTS (SELECT 1 FROM knowledge_space ks
                              JOIN concepts c2 ON c2.concept_id = ks.from_concept_id
                              WHERE ks.to_concept_id = c.concept_id
                                AND c2.concept_chapter_id = c.concept_chapter_id)
            ORDER BY c.concept_id
            """;
        return jdbcTemplate.query(sql, conceptSummaryRowMapper(), schoolLevel);
    }

    /**
     * knowledge_space 전체 간선(from=후수, to=선수). 신규 진단 경로의 앱 계층 BFS 용 —
     * depth 10 CTE 는 세션 cte_max_recursion_depth=10 과 경계 충돌(ERROR 3636 실측,
     * 실그래프 폐쇄 깊이 22)이라 선수 폐쇄 전체는 visited-set BFS 로 계산한다.
     */
    public List<KnowledgeEdge> findAllEdges() {
        String sql = "SELECT from_concept_id, to_concept_id FROM knowledge_space " +
                "WHERE from_concept_id IS NOT NULL AND to_concept_id IS NOT NULL";
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new KnowledgeEdge(rs.getInt("from_concept_id"), rs.getInt("to_concept_id")));
    }

    public java.util.Optional<ConceptSummary> findConceptSummaryById(int conceptId) {
        String sql = "SELECT concept_id, concept_name, concept_description FROM concepts WHERE concept_id = ?";
        try {
            return java.util.Optional.ofNullable(
                    jdbcTemplate.queryForObject(sql, conceptSummaryRowMapper(), conceptId));
        } catch (EmptyResultDataAccessException e) {
            return java.util.Optional.empty();
        }
    }

    private RowMapper<ConceptSummary> conceptSummaryRowMapper() {
        return (rs, rowNum) -> new ConceptSummary(
                rs.getInt("concept_id"),
                rs.getString("concept_name"),
                rs.getString("concept_description"));
    }
}
