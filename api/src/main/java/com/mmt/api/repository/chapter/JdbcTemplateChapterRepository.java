package com.mmt.api.repository.chapter;

import com.mmt.api.domain.Chapter;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
public class JdbcTemplateChapterRepository implements ChapterRepository{

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateChapterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Chapter> findAllByGradeLevelAndSemester(String gradeLevel, String semester) {
        String sql = "SELECT chapter_id, chapter_name, chapter_main, chapter_sub FROM chapters WHERE grade_level = ? AND semester = ?";
        return jdbcTemplate.query(sql, chapterRowMapper(), gradeLevel, semester);
    }

    private RowMapper<Chapter> chapterRowMapper() {
        return (rs, rowNum) -> {
            Chapter chapter = new Chapter();
            chapter.setChapterId(rs.getInt("chapter_id"));
            chapter.setChapterName(rs.getString("chapter_name"));
            chapter.setChapterMain(rs.getString("chapter_main"));
            chapter.setChapterSub(rs.getString("chapter_sub"));
            return chapter;
        };
    }
}
