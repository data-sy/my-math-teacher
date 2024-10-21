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
