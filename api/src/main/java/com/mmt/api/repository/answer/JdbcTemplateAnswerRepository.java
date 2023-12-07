package com.mmt.api.repository.answer;

import com.mmt.api.domain.Answer;
import com.mmt.api.domain.AnswerCode;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@Primary
public class JdbcTemplateAnswerRepository implements AnswerRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateAnswerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Answer answer) {
        Long userTestId = answer.getUserTestId();
        List<AnswerCode> answerCodeList = answer.getAnswerCodeList();
        String sql =  "INSERT INTO answers (user_test_id, item_id, answer_code) VALUES (?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, userTestId);
                AnswerCode answerCode = answerCodeList.get(i);
                ps.setLong(2, answerCode.getItemId());
                ps.setInt(3, answerCode.getAnswerCode());
            }
            @Override
            public int getBatchSize() {
                return answerCodeList.size();
            }
        });
    }

    @Override
    public List<AnswerCode> findAnswerCode(Long userTestId) {
        String sql = "SELECT c.skill_id, a.answer_code FROM answers a JOIN items i ON a.item_id = i.item_id JOIN concepts c ON c.concept_id = i.concept_id WHERE a.user_test_id=?";
        return jdbcTemplate.query(sql, answerCodeRowMapper(), userTestId);
    }

    private RowMapper<AnswerCode> answerCodeRowMapper() {
        return (rs, rowNum) -> {
            AnswerCode answerCode = new AnswerCode();
            answerCode.setSkillId(rs.getInt("skill_id"));
            answerCode.setAnswerCode(rs.getInt("answer_code"));
            return answerCode;
        };
    }

}
