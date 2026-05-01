# ADR 0005: MySQL CTE 객체 반환 메서드의 도메인 매핑 — `concepts JOIN chapters`, conceptSection 매핑 생략

## Status

Accepted

## Context

Milestone 2 spec-01 진입 직전 audit 과정에서 `Flux<Concept>` 반환 메서드(`findNodesByConceptIdDepth3/5`, `findToConceptsByConceptId`)를 MySQL CTE로 옮길 때 `Concept` 도메인 ↔ MySQL `concepts` 테이블의 컬럼 매핑이 일치하지 않는다는 사실이 발견됐다(분석 1차).

피드백 검토에서 다음이 명확해졌다:
- spec-01은 ID 반환 메서드(`findPrerequisiteConceptIds`)만 도입하므로 객체 매핑 결정은 spec-01 진입 차단 사유가 아님
- 옵션 비교 전에 `ConceptResponse`·`ConceptConverter`·프론트 바인딩 3개를 확인해야 결정 가능
- 7개 부재 필드가 모두 사용된다는 가정은 검증되지 않음

본 ADR은 위 3개 자료와 시드/스키마/단일 필드 accessor를 모두 grep한 결과(보조 자료: ADR 0005 evidence)에 따라 spec-02 진입 전 객체 매핑 정책을 단일 결정으로 고정한다.

## Decision

`MysqlConceptRepository`의 객체 반환 메서드(spec-02에서 도입할 `findPrerequisiteConcepts(int conceptId, int maxDepth)` 등)는 **`concepts JOIN chapters` JOIN 패턴**으로 `Concept` 객체를 매핑한다. `JdbcTemplateConceptRepository.findOneByConceptId`의 prior art를 그대로 확장한다.

매핑 정책:

- **ConceptResponse 13개 필드 중 12개**는 `concepts` + `chapters` 두 테이블 JOIN으로 채운다
  - `concepts`: conceptId, conceptName, conceptDescription, conceptChapterId, conceptAchievementId, conceptAchievementName
  - `chapters` (JOIN): conceptSchoolLevel, conceptGradeLevel, conceptSemester, conceptChapterName, conceptChapterMain, conceptChapterSub
- **`conceptSection`은 매핑 생략** (RowMapper에서 미설정 → null)
  - 프론트 어디에서도 사용되지 않음 (`web/src/` 전체 grep 0건)
  - 단일 조회 경로(`ConceptConverter.convertToConceptResponse`)도 이미 conceptSection을 매핑하지 않음 → 일관성 유지
  - `concepts_sections` 다대다 JOIN과 평탄화 정책이 모두 불필요해짐

CTE 쿼리 형태(spec-02에서 적용):

```sql
WITH RECURSIVE prerequisite_path AS (
    SELECT concept_id, 0 AS depth
    FROM concepts WHERE concept_id = ?    -- 매개변수 1: 시작 conceptId

    UNION ALL

    SELECT c.concept_id, pp.depth + 1
    FROM prerequisite_path pp
    JOIN knowledge_space ks ON pp.concept_id = ks.from_concept_id
    JOIN concepts         c  ON ks.to_concept_id = c.concept_id
    WHERE pp.depth < ?                    -- 매개변수 2: maxDepth
)
SELECT c.concept_id, c.concept_name, c.concept_description,
       c.concept_chapter_id, c.concept_achievement_id, c.concept_achievement_name,
       ch.school_level, ch.grade_level, ch.semester,
       ch.chapter_main, ch.chapter_sub, ch.chapter_name
FROM (SELECT DISTINCT concept_id FROM prerequisite_path) pp   -- 다중 경로 중복 제거
JOIN concepts c  ON pp.concept_id = c.concept_id
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id;
```

**중복 제거 주의:** 다중 경로(예: A → Y → W와 A → Z → W가 모두 존재)에서 `prerequisite_path`에 동일 conceptId가 여러 depth로 등장 가능. 외부 SELECT에서 `(SELECT DISTINCT concept_id FROM prerequisite_path)` subquery로 평탄화하지 않으면 ConceptResponse 리스트에 중복 객체가 발생하여 cytoscape 노드 중복으로 이어진다. ID-only CTE(spec-01)는 마지막에 `SELECT DISTINCT concept_id`로 처리하지만, 객체 반환 SELECT는 외부에 DISTINCT 처리가 필수.

**매개변수 바인딩:** prepared statement 2개 매개변수, 순서대로 (1) 시작 `conceptId` (anchor의 WHERE), (2) `maxDepth` (재귀의 WHERE). spec-02의 `findPrerequisiteConcepts(int conceptId, int maxDepth)` 시그니처와 매핑.

`Concept` 도메인 클래스의 `@Node`/`@Property` Neo4j 어노테이션은 본 ADR 시점에 제거하지 않는다 — Neo4j 폐기(spec-03 Task 5.3)에서 일괄 정리.

## Consequences

### Positive
- 12개 필드가 prior art(`findOneByConceptId`) 패턴 그대로 도달 가능 → spec-02 RowMapper 작성이 단순한 SQL 확장으로 끝남
- 단일 조회 경로와 그래프 경로의 conceptSection 매핑 정책이 일관됨 (둘 다 미매핑)
- `concepts_sections`/`sections` 다대다 JOIN, 평탄화 정책(LIMIT 1 vs GROUP_CONCAT) 결정이 모두 불필요해져 spec-02 작업 부담 축소
- 별도 MySQL 도메인 클래스(옵션 D) 신설 회피 — `Concept` 단일 도메인 유지로 변환 boilerplate 0
- M2 scope 재정의 발동 안 됨 — 8일 일정 그대로 진행 가능

### Negative
- `Concept` 도메인의 일부 필드(MySQL `concepts`/`chapters`에 부재한 항목)가 그래프 경로에서도 null로 채워짐 — 프론트 미사용이므로 영향 0이지만 의미상 "노드는 항상 동일 형태"라는 가정이 약해짐. spec-03 Task 5.3에서 Neo4j 어노테이션 제거 시 도메인 정리 함께 진행
- 객체 반환 경로는 ID 반환 대비 `concepts`·`chapters` 두 테이블 JOIN + N row 패치가 추가됨 → 응답 시간 증가 가능. spec-03 Task 4.2에서 ID 반환과 객체 반환 허용치를 분리해서 측정·기록 권장 (M1 baseline은 두 형태 모두 측정했으나 마일스톤 성능표에 명시적 분리 없음)

### Neutral
- 향후 진단 결과 페이지 UI 변경으로 `conceptSection` 필요 시점이 오면 ADR 후속 결정으로 sections JOIN 보강. 현재는 미사용 확인됨

## Alternatives Considered

1. **옵션 A — null 허용 (모든 부재 필드를 null로)** — 기각. `chapters` JOIN으로 즉시 도달 가능한 12개 필드 중 10개가 프론트에서 실제 바인딩되고(나머지 2개는 응답엔 있으나 미바인딩의 ID 필드), 채울 수 있는 데이터를 null로 두는 결정은 회귀를 만든다. 본 ADR은 evidence §8 grep 결과로 미바인딩이 확인된 conceptSection만 매핑 생략 (작업 단순화 + 단일 조회 경로와 일관성).
2. **옵션 B — `concepts` 테이블에 부재 컬럼 추가 + 시드 보강 (스키마 변경)** — 기각. 데이터가 이미 `chapters` 테이블에 정규화 형태로 존재하므로 컬럼 중복. `insert_concepts_v1.sql`의 평탄 시드는 비활성 상태이며 운영 시드(`insert_concepts.sql`)는 정규화 채택. 스키마를 역정규화할 이유 없음.
3. **옵션 D — MySQL 전용 도메인 클래스(`ConceptRow` 등) 신설** — 기각. 데이터 소싱 문제 자체를 풀지 않고 도메인 분리만 함. 결국 본 ADR과 동일한 JOIN을 어딘가에서 해야 하므로 추가 변환 boilerplate만 발생.
4. **옵션 E — 객체 반환 메서드 도입 자체를 M2 비범위로** — 기각. `findNodesByConceptId`·`findToConcepts`가 진단 결과 페이지의 핵심 기능이므로 Neo4j 폐기 전제 조건. M2 scope에 반드시 포함되어야 함. 데이터가 이미 MySQL에 있으므로 부분 이연 불필요.
5. **conceptSection을 GROUP_CONCAT로 합쳐 매핑** — 기각. 프론트 미사용이 확인됐으므로 매핑 자체가 dead code. 정책 결정 부담만 추가.
6. **conceptSection을 LIMIT 1로 첫 매핑만** — 기각. 위와 동일.

## References

- 결정 근거 raw 데이터: `docs/adr/0005-cte-object-mapping-evidence.md`
- prior art: `api/src/main/java/com/mmt/api/repository/concept/JdbcTemplateConceptRepository.java` (`findOneByConceptId`, `findSchoolLevelByConceptId`)
- 매핑 대조: `api/src/main/java/com/mmt/api/dto/concept/ConceptResponse.java`, `ConceptConverter.java`
- 스키마: `api/sql/create.sql` (`concepts`, `chapters`, `sections`, `concepts_sections`)
- 프론트 바인딩: `web/src/views/ResultView.vue` (cytoscape 노드 데이터·상세 패널 표시)
- 적용 spec: `docs/specs/m2/spec-02-service-integration-and-caching.md` (Task 3.1의 객체 반환 메서드 분기 형태)
- 관련 ADR: ADR 0002 §2 (테스트 프로파일)
- 후속 정리: spec-03 Task 5.3 (Neo4j 폐기 시 `Concept` 도메인의 `@Node`/`@Property` 어노테이션 제거)
