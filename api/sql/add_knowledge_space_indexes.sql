-- M2 spec-01 Task 1.3: knowledge_space 복합 인덱스 추가.
--
-- 기존재 인덱스 (SHOW INDEX 실측, 2026-05-11):
--   PRIMARY KEY (knowledge_space_id) — clustered
--   FK 자동 인덱스: to_concept_id (cardinality 1054)
--   FK 자동 인덱스: from_concept_id (cardinality 1428)
--
-- 신규 인덱스는 CTE 재귀 단계에서 covering index 로 작동:
--   JOIN ks ON pp.concept_id = ks.from_concept_id  -- 인덱스 lookup
--   SELECT ks.to_concept_id                        -- covering (row 페치 0)
--
-- 적용:
--   docker exec my-math-teacher-mmt-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" mmt' < api/sql/add_knowledge_space_indexes.sql
--
-- 롤백:
--   DROP INDEX idx_knowledge_space_composite ON knowledge_space;

CREATE INDEX idx_knowledge_space_composite
    ON knowledge_space(from_concept_id, to_concept_id);
