package com.mmt.api.repository.userTest;

import com.mmt.api.domain.Test;
import com.mmt.api.domain.UserTests;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
public class JdbcTemplateUserTestRepository implements UserTestRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateUserTestRepository(DataSource dataSource) {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void save(Long userId, Long testId) {
        String sql = "INSERT INTO users_tests (user_id, test_id) VALUES (?, ?)";
        jdbcTemplate.update(sql, userId, testId);
    }

    @Override
    public List<UserTests> findByUserId(Long userId) {
        // is_record : answers 테이블 user_test_id 유무에 따라 T/F를 반환
        String sql ="SELECT ut.user_test_id, t.test_id, t.test_name, \n" +
                "CASE WHEN EXISTS (SELECT 1 FROM answers a WHERE a.user_test_id = ut.user_test_id) \n" +
                "THEN TRUE ELSE FALSE END AS is_record \n" +
                "FROM users_tests ut JOIN tests t ON ut.test_id = t.test_id \n" +
                "WHERE ut.user_id = ?;";
        return jdbcTemplate.query(sql, userTestsRowMapper(), userId);
    }

    private RowMapper<UserTests> userTestsRowMapper() {
        return (rs, rowNum) -> {
            UserTests userTests = new UserTests();
            userTests.setUserTestId(rs.getLong("user_test_id"));
            userTests.setTestId(rs.getLong("test_id"));
            userTests.setTestName(rs.getString("test_name"));
            userTests.setRecord(rs.getBoolean("is_Record"));
            return userTests;
        };
    }
}
