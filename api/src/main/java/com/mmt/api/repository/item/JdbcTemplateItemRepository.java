package com.mmt.api.repository.item;

import com.mmt.api.domain.Item;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Primary
public class JdbcTemplateItemRepository implements ItemRepository{

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Item findByConceptId(int conceptId) {
        String sql = "SELECT i.item_id, i.item_answer, i.item_image_path, i.concept_id, c.concept_name, ch.school_level, ch.grade_level, ch.semester FROM items i \n" +
                "JOIN concepts c ON i.concept_id = c.concept_id JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id WHERE c.concept_id = ? ORDER BY RAND() LIMIT 1";
        return jdbcTemplate.queryForObject(sql, itemRowMapper(), conceptId);
    }

    @Override
    public List<Item> findOriginalItemsByUserTestId(Long userTestId, boolean onlyWrong) {
        // 재출제(Scope B): 학생이 원래 응시한 문항을 단일 쿼리로 조회 (N+1 없음).
        String sql = "SELECT i.item_id, i.item_answer, i.item_image_path, i.concept_id, c.concept_name, ch.school_level, ch.grade_level, ch.semester FROM answers a \n" +
                "JOIN items i ON a.item_id = i.item_id JOIN concepts c ON i.concept_id = c.concept_id JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id \n" +
                "WHERE a.user_test_id = ?" + (onlyWrong ? " AND a.answer_code = 0" : "");
        return jdbcTemplate.query(sql, itemRowMapper(), userTestId);
    }

    @Override
    public List<Item> findItemsByConceptIds(List<Integer> conceptIds) {
        if (conceptIds == null || conceptIds.isEmpty()) return new ArrayList<>();
        // 범위 채우기(Scope B): 개념들의 전체 문항을 랜덤 순서로 단일 쿼리 조회.
        String placeholders = String.join(",", conceptIds.stream().map(id -> "?").toArray(String[]::new));
        String sql = "SELECT i.item_id, i.item_answer, i.item_image_path, i.concept_id, c.concept_name, ch.school_level, ch.grade_level, ch.semester FROM items i \n" +
                "JOIN concepts c ON i.concept_id = c.concept_id JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id \n" +
                "WHERE i.concept_id IN (" + placeholders + ") ORDER BY RAND()";
        return jdbcTemplate.query(sql, itemRowMapper(), conceptIds.toArray());
    }

    private RowMapper<Item> itemRowMapper(){
        return (rs, rowNum) -> {
            Item item = new Item();
            item.setItemId(rs.getLong("item_id"));
            item.setItemAnswer(rs.getString("item_answer"));
            item.setItemImagePath(rs.getString("item_image_path"));
            item.setConceptId(rs.getInt("concept_id"));
            item.setConceptName(rs.getString("concept_name"));
            item.setSchoolLevel(rs.getString("school_level"));
            item.setGradeLevel(rs.getString("grade_level"));
            item.setSemester(rs.getString("semester"));
            return item;
        };
    }

}

