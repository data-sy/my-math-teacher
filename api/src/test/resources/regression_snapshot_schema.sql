-- M2 spec-03 Task 4.1: prod 시드 재활용 회귀 테스트용 스키마.
-- 컬럼 정의는 api/sql/create.sql 의 chapters / concepts / knowledge_space 와 일치.
-- 단위 테스트 스키마(cte_test_schema.sql) 와 달리 prod 시드(insert_concepts.sql,
-- insert_chapters.sql, insert_knowledge_space.sql) 의 컬럼 수에 정확히 맞춰야
-- 한다 — concepts 의 skill_id 컬럼 포함.
-- FK 제약은 시드 단순화 + CTE 회귀 검증의 무관성으로 생략(cte_test_schema 정책 동일).
-- 복합 인덱스(add_knowledge_space_indexes.sql) 는 CTE 재귀 성능에는 영향을 주지만
-- 결과 집합 동등성에는 무관 — 그래도 운영 환경과 동일 인덱스 상태에서 검증하도록 포함.

DROP TABLE IF EXISTS knowledge_space;
DROP TABLE IF EXISTS concepts;
DROP TABLE IF EXISTS chapters;

-- 문자열 컬럼은 모두 TEXT 로 완화한다. prod 시드 일부 행이 prod 스키마의
-- VARCHAR 길이를 초과하는 데이터를 포함 (MysqlDataTruncation 발생). prod
-- MySQL 은 sql_mode 가 비-STRICT 거나 컬럼이 실측 후 확장됐을 가능성이 있고,
-- testcontainers MySQL 8.0 은 STRICT_TRANS_TABLES 기본값이라 truncation 을
-- 거부한다. 회귀 검증은 정수 ID 컬럼만 사용하므로 텍스트 길이는 무관 — TEXT 로
-- 통일해 strict mode 와 무관하게 시드를 적재한다.
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

CREATE INDEX idx_knowledge_space_composite
    ON knowledge_space(from_concept_id, to_concept_id);
