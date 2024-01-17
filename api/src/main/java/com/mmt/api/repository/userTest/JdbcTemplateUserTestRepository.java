package com.mmt.api.repository.userTest;

import com.mmt.api.domain.Test;
import com.mmt.api.domain.UserTests;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
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
        String sql ="SELECT ut.user_test_id, ut.user_test_timestamp, t.test_id, t.test_name, t.test_school_level, t.test_grade_level, t.test_semester, \n" +
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
            userTests.setTestDate(formatTestDate(rs.getTimestamp("user_test_timestamp")));
            userTests.setTestId(rs.getLong("test_id"));
            userTests.setTestName(rs.getString("test_name"));
            userTests.setTestSchoolLevel(rs.getString("test_school_level"));
            userTests.setTestGradeLevel(rs.getString("test_grade_level"));
            userTests.setTestSemester(rs.getString("test_semester"));
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

    private String formatTestDate(Timestamp timestamp) {
        // 타임스탬프가 null인지 확인
        if (timestamp == null) {
            return "";
        }
        // 연, 월, 일 추출
        int year = timestamp.toLocalDateTime().getYear() % 100;
        int month = timestamp.toLocalDateTime().getMonthValue();
        int day = timestamp.toLocalDateTime().getDayOfMonth();
        // 날짜 포맷
        return String.format("%02d/%02d/%02d", year, month, day);
    }
}
