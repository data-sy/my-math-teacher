package com.mmt.api.repository.TestItem;

import com.mmt.api.domain.TestItems;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@Primary
public class JdbcTemplateTestItemRepository implements TestItemRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateTestItemRepository(DataSource dataSource) {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public List<TestItems> findByTestId(Long testId){
        String sql = "SELECT i.item_id, i.item_answer, i.item_image_path, ti.test_item_number FROM tests_items ti JOIN items i ON ti.item_id = i.item_id WHERE ti.test_id = ?";
        return jdbcTemplate.query(sql, testItemsRowMapper(), testId);
    }

    private RowMapper<TestItems> testItemsRowMapper() {
        return (rs, rowNum) -> {
            TestItems testItems = new TestItems();
            testItems.setItemId(rs.getLong("item_id"));
            testItems.setItemAnswer(rs.getString("item_answer"));
            testItems.setItemImagePath(rs.getString("item_image_path"));
            testItems.setTestItemNumber(rs.getInt("test_item_number"));
            return testItems;
        };
    }

}