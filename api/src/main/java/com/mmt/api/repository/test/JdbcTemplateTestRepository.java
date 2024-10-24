package com.mmt.api.repository.test;

import com.mmt.api.domain.Test;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
public class JdbcTemplateTestRepository implements TestRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateTestRepository(DataSource dataSource) {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public List<Test> findTestsBySchoolLevel(String schoolLevel) {
        String sql = "SELECT * FROM tests WHERE test_school_level = ?";
        return jdbcTemplate.query(sql, testRowMapper(), schoolLevel);
    }

    private RowMapper<Test> testRowMapper() {
        return (rs, rowNum) -> {
            Test test = new Test();
            test.setTestId(rs.getLong("test_id"));
            Optional.ofNullable(rs.getString("test_name")).ifPresent(test::setTestName);
            Optional.ofNullable(rs.getString("test_comments")).ifPresent(test::setTestComments);
            Optional.ofNullable(rs.getString("test_school_level")).ifPresent(test::setTestSchoolLevel);
            Optional.ofNullable(rs.getString("test_grade_level")).ifPresent(test::setTestGradeLevel);
            Optional.ofNullable(rs.getString("test_semester")).ifPresent(test::setTestSemester);
            return test;
        };
    }

}
