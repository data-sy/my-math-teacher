package com.mmt.api.repository.item;

import com.mmt.api.domain.Item;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class JdbcTemplateItemRepository implements ItemRepository{

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Item findByConceptId(int conceptId) {
        String sql = "SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester FROM items i \n" +
                "JOIN concepts c ON i.concept_id = c.concept_id JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id WHERE c.concept_id = ? ORDER BY RAND() LIMIT 1";
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

