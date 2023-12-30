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
        String sql;
        Object[] params;
        if (conceptIdList.size() == 1) {
            sql = "SELECT * FROM knowledge_space WHERE from_concept_id = ?";
            params = new Object[]{conceptIdList.get(0)};
        } else {
            String inSql = String.join(",", Collections.nCopies(conceptIdList.size(), "?"));
            sql = String.format("SELECT * FROM knowledge_space WHERE from_concept_id IN (%s)", inSql);
            params = conceptIdList.toArray();
        }
        return jdbcTemplate.query(sql, knowledgeSpaceRowMapper(), params);
    }

    private RowMapper<KnowledgeSpace> knowledgeSpaceRowMapper() {
        return (rs, rowNum) -> {
            KnowledgeSpace knowledgeSpace = new KnowledgeSpace();
            knowledgeSpace.setId(String.valueOf(rs.getInt("knowledge_space_id")));
            knowledgeSpace.setSource(String.valueOf(rs.getInt("to_concept_id")));
            knowledgeSpace.setTarget(String.valueOf(rs.getInt("from_concept_id")));
            return knowledgeSpace;
        };
    }

}
