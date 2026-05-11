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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M2 spec-01 Task 1.5: 재귀 CTE 단위 테스트.
 *
 * @Sql 의 스키마·시드는 src/test/resources/ 의 cte_test_*.sql 파일 (test classpath).
 * @JdbcTest 는 JPA 엔티티 스캔을 하지 않아 ddl-auto 가 작동하지 않으므로
 * @Sql 로 명시적 스키마 부트스트랩이 필요. @AutoConfigureTestDatabase(replace=NONE)
 * 로 in-memory DB 대체를 막고 Testcontainers MySQL 을 사용.
 */
@JdbcTest
@Import({TestcontainersConfig.class, JdbcTemplateConceptRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = {"classpath:cte_test_schema.sql", "classpath:cte_test_seed.sql"})
class JdbcTemplateConceptRepositoryCteTest {

    @Autowired
    JdbcTemplateConceptRepository repository;

    @Test
    void anchorOnly_whenMaxDepthZero() {
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(200, 0);

        assertThat(result).containsExactly(new ConceptDepth(200, 0));
    }

    @Test
    void directPrerequisite_whenMaxDepthOne() {
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(200, 1);

        assertThat(result).containsExactlyInAnyOrder(
            new ConceptDepth(200, 0),
            new ConceptDepth(210, 1)
        );
    }

    @Test
    void multiStepChain() {
        // 시드 체인: 300 의 선수 = 310 → 320 → 330.
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(300, 3);

        assertThat(result).containsExactlyInAnyOrder(
            new ConceptDepth(300, 0),
            new ConceptDepth(310, 1),
            new ConceptDepth(320, 2),
            new ConceptDepth(330, 3)
        );
    }

    @Test
    void emptyResult_whenConceptIdNotExists() {
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(999, 3);

        assertThat(result).isEmpty();
    }

    @Test
    void throwsIllegalArgument_whenNegativeMaxDepth() {
        assertThatThrownBy(() -> repository.findPrerequisitesWithDepth(200, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxDepth");
    }

    @Test
    void multiplePathsAbsorbedToMinDepth() {
        // 400 → {410, 420}, 410 → 430, 420 → 430. 430 은 두 경로로 depth 2 도달.
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(400, 2);

        assertThat(result).containsExactlyInAnyOrder(
            new ConceptDepth(400, 0),
            new ConceptDepth(410, 1),
            new ConceptDepth(420, 1),
            new ConceptDepth(430, 2)
        );
    }

    @Test
    void twoCycleTerminatesAndAbsorbsBackEdge() {
        // 600 ↔ 601 양방향. 600 의 선수 탐색 시 600 자체가 back-edge 로 depth 2
        // 에 재등장하지만 MIN(depth) 로 anchor 의 depth 0 만 살아남음.
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(600, 3);

        assertThat(result).containsExactlyInAnyOrder(
            new ConceptDepth(600, 0),
            new ConceptDepth(601, 1)
        );
    }

    // ── 객체 반환 CTE 단위 테스트 (spec-02 Task 3.1a, ADR 0005) ──────────────

    @Test
    void findPrerequisiteConcepts_returnsChainWithMappedColumns() {
        // 300 → 310 → 320 → 330 체인. depth 3 호출은 자기 자신 포함 4개 객체 반환.
        List<Concept> result = repository.findPrerequisiteConcepts(300, 3);

        assertThat(result).hasSize(4);
        assertThat(result).extracting(Concept::getConceptId)
            .containsExactlyInAnyOrder(300, 310, 320, 330);

        // 매핑 컬럼 검증 — concept 300 의 row 로 12 컬럼 확인.
        Concept c300 = result.stream()
            .filter(c -> c.getConceptId() == 300).findFirst().orElseThrow();
        assertThat(c300.getName()).isEqualTo("개념 300");
        assertThat(c300.getDesc()).isEqualTo("설명 300");
        assertThat(c300.getChapterId()).isEqualTo(1);
        assertThat(c300.getAchievementId()).isEqualTo(1300);
        assertThat(c300.getAchievementName()).isEqualTo("성취기준-300");
        assertThat(c300.getSchoolLevel()).isEqualTo("초등");
        assertThat(c300.getGradeLevel()).isEqualTo("1");
        assertThat(c300.getSemester()).isEqualTo("1");
        assertThat(c300.getChapterMain()).isEqualTo("수와 연산");
        assertThat(c300.getChapterSub()).isEqualTo("자연수");
        assertThat(c300.getChapterName()).isEqualTo("테스트 단원");
        // ADR 0005: section 매핑 생략 → null.
        assertThat(c300.getSection()).isNull();
    }

    @Test
    void findPrerequisiteConcepts_multiPathDeduplicates() {
        // 400 → {410, 420}, 410 → 430, 420 → 430. 430 은 두 경로로 depth 2 도달하지만
        // 외부 (SELECT DISTINCT concept_id FROM prerequisite_path) 로 1행만 반환되어야 함.
        List<Concept> result = repository.findPrerequisiteConcepts(400, 2);

        assertThat(result).hasSize(4);
        assertThat(result).extracting(Concept::getConceptId)
            .containsExactlyInAnyOrder(400, 410, 420, 430);
    }

    @Test
    void findPrerequisiteConcepts_isolatedNodeReturnsSelfOnly() {
        // 100: 고립 노드. depth 5 호출에도 자기 자신 1개만 반환.
        List<Concept> result = repository.findPrerequisiteConcepts(100, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConceptId()).isEqualTo(100);
        assertThat(result.get(0).getName()).isEqualTo("고립 개념");
    }

    @Test
    void findPrerequisiteConcepts_emptyWhenConceptIdNotExists() {
        List<Concept> result = repository.findPrerequisiteConcepts(999, 3);

        assertThat(result).isEmpty();
    }

    @Test
    void findPrerequisiteConcepts_throwsIllegalArgumentWhenNegativeMaxDepth() {
        assertThatThrownBy(() -> repository.findPrerequisiteConcepts(300, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxDepth");
    }
}
