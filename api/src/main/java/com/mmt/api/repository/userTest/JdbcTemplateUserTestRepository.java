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

    @Override
    public List<UserTests> findRecordedTests(Long userId) {
        String sql = "SELECT ut.user_test_id, t.test_id, t.test_name FROM users_tests ut JOIN tests t ON ut.test_id = t.test_id\n" +
                "WHERE ut.user_id = ? AND EXISTS (SELECT 1 FROM answers a WHERE a.user_test_id = ut.user_test_id);";
        return jdbcTemplate.query(sql, recordedTestsRowMapper(), userId);
    }

    @Override
    public List<Long> findUserTestIds(Long userTestId) {
        // 조건1 : 해당 유저
        String condition1 = "user_id = (SELECT user_id FROM users_tests WHERE user_test_id=?)";
        // 조건2 : 답안 기록이 있는 것들
        String condition2 = "EXISTS (SELECT 1 FROM answers a WHERE a.user_test_id = ut.user_test_id)";
        String sql = String.format("SELECT user_test_id FROM users_tests ut WHERE %s AND %s", condition1, condition2);
        return jdbcTemplate.queryForList(sql, Long.class, userTestId);
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
    private RowMapper<UserTests> recordedTestsRowMapper() {
        return (rs, rowNum) -> {
            UserTests userTests = new UserTests();
            userTests.setUserTestId(rs.getLong("user_test_id"));
            userTests.setTestId(rs.getLong("test_id"));
            userTests.setTestName(rs.getString("test_name"));
            return userTests;
        };
    }
}
