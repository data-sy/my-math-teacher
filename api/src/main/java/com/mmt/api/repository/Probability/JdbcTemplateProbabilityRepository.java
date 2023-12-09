package com.mmt.api.repository.Probability;

import com.mmt.api.domain.Probability;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
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
}
