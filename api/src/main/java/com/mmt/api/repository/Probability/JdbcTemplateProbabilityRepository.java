package com.mmt.api.repository.Probability;

import com.mmt.api.domain.Probability;
import com.mmt.api.domain.Result;
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
public class JdbcTemplateProbabilityRepository implements ProbabilityRepository{

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateProbabilityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    @Override
    public void save(List<Probability> probabilities) {
        String sql =  "INSERT INTO probabilities (answer_id, concept_id, to_concept_depth, probability_percent) VALUES (?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Probability probability = probabilities.get(i);
                ps.setLong(1, probability.getAnswerId());
                ps.setInt(2, probability.getConceptId());
                ps.setInt(3, probability.getToConceptDepth());
                ps.setDouble(4, probability.getProbabilityPercent());
            }
            @Override
            public int getBatchSize() {
                return probabilities.size();
            }
        });
    }

    //
    @Override
    public List<Result> findResults(Long userTestId) {
        String sql ="SELECT p.probability_id, ti.test_item_number, p.concept_id, p.to_concept_depth, p.probability_percent, c.concept_name, ch.school_level, ch.grade_level, ch.semester, ch.chapter_main, ch.chapter_sub, ch.chapter_name\n" +
                "FROM chapters ch JOIN concepts c ON c.concept_chapter_id = ch.chapter_id\n" +
                "JOIN probabilities p ON p.concept_id = c.concept_id JOIN answers a ON a.answer_id = p.answer_id JOIN tests_items ti ON ti.item_id = a.item_id\n" +
                "WHERE a.user_test_id = ?";
        return jdbcTemplate.query(sql, resultRowMapper(), userTestId);
    }

    private RowMapper<Result> resultRowMapper() {
        return (rs, rowNum) -> {
            Result result = new Result();
            result.setProbabilityId(rs.getLong("probability_id"));
            result.setTestItemNumber(rs.getInt("test_item_number"));
            result.setConceptId(rs.getInt("concept_id"));
            result.setToConceptDepth(rs.getInt("to_concept_depth"));
            result.setProbabilityPercent(rs.getDouble("probability_percent"));
            result.setConceptName(rs.getString("concept_name"));
            result.setSchoolLevel(rs.getString("school_level"));
            result.setGradeLevel(rs.getString("grade_level"));
            result.setSemester(rs.getString("semester"));
            result.setChapterMain(rs.getString("chapter_main"));
            result.setChapterSub(rs.getString("chapter_sub"));
            result.setChapterName(rs.getString("chapter_name"));
            return result;
        };
    }

}
