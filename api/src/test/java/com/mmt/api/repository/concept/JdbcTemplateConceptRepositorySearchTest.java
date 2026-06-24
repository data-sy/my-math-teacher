package com.mmt.api.repository.concept;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.domain.Concept;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spec-09 Task 2: 개념명 검색(searchByName) 단위 테스트.
 *
 * 인프라는 CteTest 와 동일(Testcontainers MySQL + @Sql 스키마/시드).
 * 시드(cte_test_seed.sql): 모든 개념이 chapter_id=1(school_level='초등') 에 매핑.
 * 이름은 "개념 NNN"(접두에 '개념') + "고립 개념"(중간에 '개념').
 */
@JdbcTest
@Import({TestcontainersConfig.class, JdbcTemplateConceptRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = {"classpath:cte_test_schema.sql", "classpath:cte_test_seed.sql"})
class JdbcTemplateConceptRepositorySearchTest {

    @Autowired
    JdbcTemplateConceptRepository repository;

    @Test
    void searchByName_mapsContextColumns() {
        List<Concept> result = repository.searchByName("고립", null, 10);

        assertThat(result).hasSize(1);
        Concept c = result.get(0);
        assertThat(c.getConceptId()).isEqualTo(100);
        assertThat(c.getName()).isEqualTo("고립 개념");
        assertThat(c.getSchoolLevel()).isEqualTo("초등");
        assertThat(c.getGradeLevel()).isEqualTo("1");
        assertThat(c.getSemester()).isEqualTo("1");
        assertThat(c.getChapterName()).isEqualTo("테스트 단원");
    }

    @Test
    void searchByName_prefixMatchesRankBeforeContainsMatches() {
        // '개념' 은 "개념 NNN"(접두)·"고립 개념"(부분) 모두 매칭.
        // 접두 일치가 먼저, 부분 일치("고립 개념")가 마지막에 와야 한다.
        List<Concept> result = repository.searchByName("개념", null, 100);

        assertThat(result).extracting(Concept::getName)
            .doesNotHaveDuplicates()
            .contains("고립 개념");
        assertThat(result.get(result.size() - 1).getName()).isEqualTo("고립 개념");
        assertThat(result.get(0).getName()).startsWith("개념");
    }

    @Test
    void searchByName_respectsLimit() {
        List<Concept> result = repository.searchByName("개념", null, 3);

        assertThat(result).hasSize(3);
    }

    @Test
    void searchByName_filtersBySchoolLevel() {
        assertThat(repository.searchByName("개념", "초등", 100)).isNotEmpty();
        assertThat(repository.searchByName("개념", "중등", 100)).isEmpty();
    }

    @Test
    void searchByName_blankSchoolLevelMeansAll() {
        List<Concept> withBlank = repository.searchByName("개념", "  ", 100);
        List<Concept> withNull = repository.searchByName("개념", null, 100);

        assertThat(withBlank).hasSameSizeAs(withNull);
    }

    @Test
    void searchByName_escapesLikeWildcards() {
        // 리터럴 '%' 를 이름에 가진 개념이 없으므로, 와일드카드가 이스케이프되면 빈 결과.
        assertThat(repository.searchByName("%", null, 10)).isEmpty();
        assertThat(repository.searchByName("_", null, 10)).isEmpty();
    }

    @Test
    void searchByName_emptyWhenNoMatch() {
        assertThat(repository.searchByName("존재하지않는개념", null, 10)).isEmpty();
    }
}
