# ADR 0005 보조 자료 — grep 결과 및 매핑 분석

본 문서는 ADR 0005(MySQL CTE 객체 반환 메서드의 도메인 매핑) 결정의 근거가 된 코드베이스 조사 결과를 보존한다. ADR 본문은 결정·근거·결과만 다루고, 검증에 사용한 raw 데이터는 본 문서에 둔다.

조사 일자: 2026-04-30
조사자: Claude Code (Robin 검토 후 커밋)

---

## 1. ConceptResponse 13개 필드 (전수)

`api/src/main/java/com/mmt/api/dto/concept/ConceptResponse.java`:

```java
private int conceptId;
private String conceptName;
private String conceptDescription;
private String conceptSchoolLevel;
private String conceptGradeLevel;
private String conceptSemester;
private int conceptChapterId;
private String conceptChapterName;
private String conceptChapterMain;
private String conceptChapterSub;
private int conceptAchievementId;
private String conceptAchievementName;
private String conceptSection;
```

## 2. ConceptConverter 두 변환 메서드 비교

`api/src/main/java/com/mmt/api/dto/concept/ConceptConverter.java`:

| 필드 | `convertToFluxConceptResponse` (그래프) | `convertToConceptResponse` (단일 조회) |
|---|---|---|
| conceptId | ✓ | ✓ |
| conceptName | ✓ | ✓ |
| conceptDescription | ✓ | ✓ |
| conceptSchoolLevel | ✓ | ✓ |
| conceptGradeLevel | ✓ | ✓ |
| conceptSemester | ✓ | ✓ |
| conceptChapterId | ✓ | ✗ |
| conceptChapterName | ✓ | ✓ |
| conceptChapterMain | ✓ | ✓ |
| conceptChapterSub | ✓ | ✓ |
| conceptAchievementId | ✓ | ✗ |
| conceptAchievementName | ✓ | ✓ |
| conceptSection | ✓ | **✗** |

핵심 관찰: 단일 조회 경로(이미 MySQL 사용 중)는 `conceptSection`을 매핑하지 않음.

## 3. prior art — `findOneByConceptId`의 RowMapper 패턴

`api/src/main/java/com/mmt/api/repository/concept/JdbcTemplateConceptRepository.java`:

```java
public Concept findOneByConceptId(int conceptId) {
    String sql = "SELECT c.concept_id, c.concept_name, c.concept_description, " +
                 "       c.concept_achievement_name, " +
                 "       ch.school_level, ch.grade_level, ch.semester, " +
                 "       ch.chapter_main, ch.chapter_sub, ch.chapter_name \n" +
                 "FROM concepts c JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id " +
                 "WHERE c.concept_id = ?";
    return jdbcTemplate.queryForObject(sql, conceptRowMapper(), conceptId);
}

public String findSchoolLevelByConceptId(int conceptId){
    String sql = "SELECT ch.school_level FROM chapters ch " +
                 "JOIN concepts c ON ch.chapter_id = c.concept_chapter_id " +
                 "WHERE c.concept_id = ?";
    return jdbcTemplate.queryForObject(sql, String.class, conceptId);
}
```

핵심 관찰: `concepts JOIN chapters ON c.concept_chapter_id = ch.chapter_id` 패턴이 이미 정착되어 있고 `concept_id`로 단일 row를 가져온다. CTE 메서드는 이 JOIN을 재귀 결과 집합에 적용하기만 하면 됨.

## 4. concepts / chapters 테이블 스키마 + 시드 샘플

### 4-1. DDL (`api/sql/create.sql`)

```sql
CREATE TABLE concepts (
    concept_id INT,
    concept_name VARCHAR(70),
    concept_description TEXT,
    concept_chapter_id INT,
    concept_achievement_id INT,
    concept_achievement_name VARCHAR(120),
    skill_id INT,
    PRIMARY KEY (concept_id),
    FOREIGN KEY (concept_chapter_id) REFERENCES chapters (chapter_id)
);

CREATE TABLE chapters (
    chapter_id INT,
    chapter_name VARCHAR(50),
    school_level VARCHAR(5),
    grade_level VARCHAR(5),
    semester VARCHAR(5),
    chapter_main VARCHAR(50),
    chapter_sub VARCHAR(50),
    PRIMARY KEY (chapter_id)
);
```

`concepts`의 6개 컬럼 + `chapters`의 6개 컬럼 = 12개. ConceptResponse 13개 중 conceptSection 한 개만 두 테이블에 부재.

### 4-2. chapters 시드 샘플 (`api/sql/insert_chapters.sql`)

컬럼 순서: `(chapter_id, chapter_name, school_level, grade_level, semester, chapter_main, chapter_sub)`

```sql
INSERT INTO chapters VALUES (1,  '몇일까요 (1)',                 '초등', '초1', '1학기', '', '9까지의 수');
INSERT INTO chapters VALUES (3,  '몇일까요 (2)',                 '초등', '초1', '1학기', '', '9까지의 수');
INSERT INTO chapters VALUES (9,  '여러 가지 모양을 찾아볼까요',     '초등', '초1', '1학기', '', '여러 가지 모양');
INSERT INTO chapters VALUES (11, '모으기와 가르기를 해 볼까요 (1)', '초등', '초1', '1학기', '', '덧셈과 뺄셈');
INSERT INTO chapters VALUES (14, '더하기는 어떻게 나타낼까요',     '초등', '초1', '1학기', '', '덧셈과 뺄셈');
```

### 4-3. 컬럼 의미 (시드 분석 결과)

| 컬럼 | 의미 | 운영 시드 관찰 |
|---|---|---|
| `chapter_name` | 차시별 학습 활동/소제목 (질문 형태가 흔함) | "몇일까요 (1)", "더하기는 어떻게 나타낼까요" |
| `school_level` | 학교급 | "초등", "중등", "고등" 추정 |
| `grade_level` | 학년 | "초1", "초2"... |
| `semester` | 학기 | "1학기", "2학기" |
| `chapter_main` | (의미 미상 — 운영 시드에서 빈 문자열) | `''` (전부 빈 값) |
| `chapter_sub` | 큰 단원명 | "9까지의 수", "여러 가지 모양", "덧셈과 뺄셈" |

**중요 관찰**: 운영 시드에서 `chapter_main`은 모두 빈 문자열로 채워져 있다. spec-02 RowMapper로 가져오면 빈 문자열로 응답되며 프론트(`ResultView.vue:640`)에 그대로 표시될 수 있다 — `{conceptChapterMain}-{conceptChapterSub}-{conceptChapterName}` 포맷에서 첫 부분이 비어 표시됨. 이는 현재 Neo4j 경로도 동일한 결과(`Concept.chapterMain`이 빈 문자열로 매핑됨)이므로 회귀 아님. 본 ADR 결정에는 영향 없음.

## 5. sections / concepts_sections 테이블 (conceptSection 잠재 출처)

```sql
CREATE TABLE sections (
    section_id INT,
    section_name VARCHAR(20),
    PRIMARY KEY (section_id)
);

CREATE TABLE concepts_sections (
    concept_section_id INT auto_increment,
    concept_id INT,
    section_id INT,
    PRIMARY KEY (concept_section_id),
    FOREIGN KEY (concept_id) REFERENCES concepts (concept_id),
    FOREIGN KEY (section_id) REFERENCES sections (section_id)
);
```

다대다 M:N 관계. 한 conceptId에 여러 section_name 매핑 가능. ConceptResponse의 `conceptSection`은 `String` 단일이므로 평탄화 정책 필요.

## 6. 시드 파일 비교

### `api/sql/insert_concepts.sql` (현재 스키마와 일치, 운영 시드)

```sql
INSERT INTO concepts VALUES (5814, '1부터 5까지의 수', '...', 1, 1, '...', 8);
```

7개 컬럼: `concept_id, concept_name, concept_description, concept_chapter_id, concept_achievement_id, concept_achievement_name, skill_id`. chapters 정보(school_level 등)는 별도로 분리되어 있음(정규화).

### `api/sql/insert_concepts_v1.sql` (비활성, Neo4j-style 평탄)

```sql
INSERT INTO concepts VALUES (5814, '1부터 5까지의 수', '...', '초등', '초1', '1학기', 1, '9까지의 수', '몇일까요 (1)', '', 1, '50까지의수 ...', 8);
```

13개 컬럼. school_level/grade_level/semester/chapter_*가 평탄하게 들어가 있음. **사용 안 함** — 운영 시드는 정규화 형태.

### `api/sql/insert_concepts_opti.sql` (성능 테스트용, 비활성)

`concept_raw_id` 추가 컬럼 사용. 본 ADR과 무관.

## 7. 단일 필드 accessor 메서드 grep

```
$ grep -rn "findGradeLevelByConceptId|findSemester|findSection|findChapter" api/src/main/java/

api/src/main/java/com/mmt/api/controller/ChapterController.java:27:
  return chapterService.findChapters(gradeLevel, semester);
api/src/main/java/com/mmt/api/service/ChapterService.java:19:
  public List<ChapterResponse> findChapters(String gradeLevel, String semester){
```

핵심 관찰: `findGradeLevelByConceptId`, `findSemesterByConceptId`, `findSectionByConceptId` 등 단일 필드 accessor는 **존재하지 않음**. `findSchoolLevelByConceptId`만 단독 존재 — 이는 `findNodesByConceptId`의 학교급 분기에 직접 사용되기 때문(spec-02 audit에서 확인). 다른 필드는 `findOneByConceptId`의 통합 SELECT로만 조회됨.

## 8. 프론트 conceptSection 사용 여부

```
$ grep -rn "conceptSection|concept_section|\.section" web/src/

(no output)
```

```
$ grep -n "concept[A-Z]" web/src/views/ResultView.vue web/src/views/ConceptView.vue
```

ResultView.vue에서 실제 바인딩되는 필드 (line 번호 포함):
- line 312: `nodeData.conceptGradeLevel` — cytoscape 노드 색상 결정
- line 343: `filteredNode.conceptGradeLevel` — cytoscape 데이터 바인딩
- line 636: `selectedNode1.conceptSchoolLevel`, `conceptGradeLevel`, `conceptSemester` — 상세 패널
- line 640: `conceptChapterMain`, `conceptChapterSub`, `conceptChapterName` — 상세 패널
- line 644: `conceptAchievementName` — 상세 패널
- line 649: `conceptDescription` — 상세 패널
- line 84/85/115/116/329/337/341/342: `conceptId`, `conceptName` — 식별·라벨

미바인딩 필드: `conceptSection`, `conceptChapterId`, `conceptAchievementId`. 단 id 필드는 응답엔 있지만 화면 표시에는 안 씀.

핵심 관찰: **`conceptSection`은 프론트 어디서도 쓰지 않음**. null 또는 빈 문자열로 두어도 영향 없음.

---

## 종합

| 필드 | concepts JOIN chapters로 도달 | 비고 |
|---|---|---|
| conceptId | ✓ | concepts.concept_id |
| conceptName | ✓ | concepts.concept_name |
| conceptDescription | ✓ | concepts.concept_description |
| conceptChapterId | ✓ | concepts.concept_chapter_id |
| conceptAchievementId | ✓ | concepts.concept_achievement_id |
| conceptAchievementName | ✓ | concepts.concept_achievement_name |
| conceptSchoolLevel | ✓ | chapters.school_level |
| conceptGradeLevel | ✓ | chapters.grade_level |
| conceptSemester | ✓ | chapters.semester |
| conceptChapterName | ✓ | chapters.chapter_name |
| conceptChapterMain | ✓ | chapters.chapter_main |
| conceptChapterSub | ✓ | chapters.chapter_sub |
| conceptSection | ✗ | sections JOIN 필요. **프론트 미사용 → 매핑 생략 안전** |

12개 필드는 `findOneByConceptId`의 prior art와 동일 JOIN 패턴으로 즉시 도달 가능. conceptSection 한 개는 매핑 생략하여 단일 조회 경로(`convertToConceptResponse`)와 일관성 유지 — 프론트 영향 0.
