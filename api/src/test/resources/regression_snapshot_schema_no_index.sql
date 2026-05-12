-- M2 사후 측정: 복합 인덱스 효과 분리용 회귀 스키마 (인덱스 제외).
-- regression_snapshot_schema.sql 와 동일하되 idx_knowledge_space_composite 만 생략.
-- FK 자동 인덱스(to_concept_id, from_concept_id) 는 본 스키마에서 FK 정의를 생략하므로
-- 함께 부재 상태 — 즉 knowledge_space 의 인덱스가 PRIMARY 만 있는 가장 raw 한 상태.
-- 이 차이가 CTE 재귀 단계의 JOIN 비용에 어떻게 반영되는지 측정용.

DROP TABLE IF EXISTS knowledge_space;
DROP TABLE IF EXISTS concepts;
DROP TABLE IF EXISTS chapters;

CREATE TABLE chapters (
    chapter_id INT PRIMARY KEY,
    chapter_name TEXT,
    school_level TEXT,
    grade_level TEXT,
    semester TEXT,
    chapter_main TEXT,
    chapter_sub TEXT
);

CREATE TABLE concepts (
    concept_id INT PRIMARY KEY,
    concept_name TEXT,
    concept_description TEXT,
    concept_chapter_id INT,
    concept_achievement_id INT,
    concept_achievement_name TEXT,
    skill_id INT
);

CREATE TABLE knowledge_space (
    knowledge_space_id INT PRIMARY KEY,
    to_concept_id INT,
    from_concept_id INT
);
-- 의도적으로 idx_knowledge_space_composite 생성 생략.
