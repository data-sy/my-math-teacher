package com.mmt.api.repository.knowledgeSpace;

import com.mmt.api.domain.KnowledgeSpace;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
@Primary
public class JdbcTemplateKnowledgeSpaceRepository implements KnowledgeSpaceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateKnowledgeSpaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<KnowledgeSpace> findEdgesByConceptId(List<Integer> conceptIdList) {
        String inSql = String.join(",", Collections.nCopies(conceptIdList.size(), "?"));
        String sql = String.format("SELECT * FROM knowledge_space WHERE from_concept_id IN (%s)", inSql);
        return jdbcTemplate.query(sql, knowledgeSpaceRowMapper(), conceptIdList.toArray());
    }

    private RowMapper<KnowledgeSpace> knowledgeSpaceRowMapper() {
        return (rs, rowNum) -> {
            KnowledgeSpace knowledgeSpace = new KnowledgeSpace();
            knowledgeSpace.setId(rs.getInt("knowledge_space_id"));
            knowledgeSpace.setToConceptId(rs.getInt("to_concept_id"));
            knowledgeSpace.setFromConceptId(rs.getInt("from_concept_id"));
            return knowledgeSpace;
        };
    }

}
