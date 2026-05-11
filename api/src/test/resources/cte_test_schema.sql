-- M2 spec-01 Task 1.5 + spec-02 Task 3.1a: CTE 단위 테스트용 스키마.
-- ID-only CTE(`findPrerequisitesWithDepth`)는 concept_id 컬럼만 필요하나,
-- 객체 반환 CTE(`findPrerequisiteConcepts`, ADR 0005)는 concepts JOIN chapters
-- 매핑이 필요해 concepts/chapters 풀 컬럼 + chapters 테이블을 함께 정의한다.

DROP TABLE IF EXISTS knowledge_space;
DROP TABLE IF EXISTS concepts;
DROP TABLE IF EXISTS chapters;

CREATE TABLE chapters (
    chapter_id INT PRIMARY KEY,
    chapter_name VARCHAR(50),
    school_level VARCHAR(5),
    grade_level VARCHAR(5),
    semester VARCHAR(5),
    chapter_main VARCHAR(50),
    chapter_sub VARCHAR(50)
);

CREATE TABLE concepts (
    concept_id INT PRIMARY KEY,
    concept_name VARCHAR(70),
    concept_description TEXT,
    concept_chapter_id INT,
    concept_achievement_id INT,
    concept_achievement_name VARCHAR(120)
);

CREATE TABLE knowledge_space (
    knowledge_space_id INT PRIMARY KEY,
    to_concept_id INT,
    from_concept_id INT
);

CREATE INDEX idx_knowledge_space_composite
    ON knowledge_space(from_concept_id, to_concept_id);
