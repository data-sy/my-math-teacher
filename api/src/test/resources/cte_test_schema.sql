-- M2 spec-01 Task 1.5: CTE 단위 테스트용 최소 스키마.
-- CTE 동작 검증에 필요한 최소 컬럼만 정의. FK 제약은 시드 단순화 위해 생략.

DROP TABLE IF EXISTS knowledge_space;
DROP TABLE IF EXISTS concepts;

CREATE TABLE concepts (
    concept_id INT PRIMARY KEY
);

CREATE TABLE knowledge_space (
    knowledge_space_id INT PRIMARY KEY,
    to_concept_id INT,
    from_concept_id INT
);

CREATE INDEX idx_knowledge_space_composite
    ON knowledge_space(from_concept_id, to_concept_id);
