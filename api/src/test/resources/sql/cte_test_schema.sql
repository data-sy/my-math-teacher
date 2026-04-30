-- M2 Spec 01 Task 1.5 / Spec 02 Task 1.1: CTE 단위 테스트 전용 schema (옵션 A)
--
-- production create.sql 의 일부 (concepts + chapters + knowledge_space) 를 단위 테스트용으로 축소.
-- @Sql 이 매 테스트 전 실행되므로 DROP IF EXISTS 로 idempotent 유지.
-- 인덱스는 production 과 동일하게 박아 EXPLAIN 동작 일관성 검증 가능.
--
-- chapters 테이블은 spec-02 의 findPrerequisiteConcepts (concepts JOIN chapters, ADR 0006) 검증용.
-- spec-01 의 findPrerequisiteConceptIds 는 chapters 미사용 → JOIN 영향 없음.

DROP TABLE IF EXISTS knowledge_space;
DROP TABLE IF EXISTS concepts;
DROP TABLE IF EXISTS chapters;

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

CREATE TABLE concepts (
    concept_id INT,
    concept_name VARCHAR(70),
    concept_description TEXT,
    concept_chapter_id INT,
    concept_achievement_id INT,
    concept_achievement_name VARCHAR(120),
    PRIMARY KEY (concept_id)
);

CREATE TABLE knowledge_space (
    knowledge_space_id INT,
    to_concept_id INT,
    from_concept_id INT,
    PRIMARY KEY (knowledge_space_id),
    KEY idx_knowledge_space_from (from_concept_id),
    KEY idx_knowledge_space_to (to_concept_id),
    KEY idx_knowledge_space_composite (from_concept_id, to_concept_id)
);
