package com.mmt.api.repository.concept;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
