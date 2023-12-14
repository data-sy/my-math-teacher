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
        String sql = "SELECT concept_id, concept_name, concept_description, concept_achievement_name FROM concepts WHERE concept_chapter_id = ?";
        return jdbcTemplate.query(sql, conceptRowMapper(), chapterId);
    }

    private RowMapper<Concept> conceptRowMapper() {
        return (rs, rowNum) -> {
            Concept concept = new Concept();
            concept.setConceptId(rs.getInt("concept_id"));
            concept.setName(rs.getString("concept_name"));
            concept.setDesc(rs.getString("concept_description"));
            concept.setAchievementName(rs.getString("concept_achievement_name"));
//            concept.setSection(rs.getString("section_name"));
            return concept;
        };
    }
}
