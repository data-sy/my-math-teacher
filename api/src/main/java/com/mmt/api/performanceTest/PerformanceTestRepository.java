package com.mmt.api.performanceTest;

import com.mmt.api.domain.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PerformanceTestRepository {

    private final JdbcTemplate jdbcTemplate;

    public PerformanceTestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Item findOneByConceptId(Long conceptId) {
        String sql = "SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester FROM items i \n" +
                "JOIN concepts c ON i.concept_id = c.concept_id JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id WHERE c.concept_id = ? ORDER BY RAND() LIMIT 1";
        return jdbcTemplate.queryForObject(sql, itemRowMapper(), conceptId);
    }

    public List<Item> findListByConceptId(Long conceptId) {
        String sql = "SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester FROM items i \n" +
                "JOIN concepts c ON i.concept_id = c.concept_id JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id WHERE c.concept_id = ?";
        return jdbcTemplate.query(sql, itemRowMapper(), conceptId);
    }

    public List<Long> findItemIdByConceptId(Long conceptId) {
        String sql = "SELECT item_id FROM items WHERE concept_id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("item_id"), conceptId);
    }

    public Item findOneByItemId(Long itemId) {
        String sql = "SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester FROM items i \n" +
                "JOIN concepts c ON i.concept_id = c.concept_id JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id WHERE i.item_id = ?";
        return jdbcTemplate.queryForObject(sql, itemRowMapper(), itemId);
    }

    public Item findByConceptIdOpti(Long conceptId) {
        String subQuery = "SELECT item_id FROM items WHERE concept_id = ? ORDER BY RAND() LIMIT 1";
        String sql = String.format("SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester FROM ( %s ) AS random_item\n" +
                "JOIN items i ON i.item_id = random_item.item_id JOIN concepts c ON i.concept_id = c.concept_id JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id ", subQuery);
        return jdbcTemplate.queryForObject(sql, itemRowMapper(), conceptId);
    }

    private RowMapper<Item> itemRowMapper(){
        return (rs, rowNum) -> {
            Item item = new Item();
            item.setItemId(rs.getLong("item_id"));
            item.setItemAnswer(rs.getString("item_answer"));
            item.setItemImagePath(rs.getString("item_image_path"));
            item.setConceptName(rs.getString("concept_name"));
            item.setSchoolLevel(rs.getString("school_level"));
            item.setGradeLevel(rs.getString("grade_level"));
            item.setSemester(rs.getString("semester"));
            return item;
        };
    }
}
