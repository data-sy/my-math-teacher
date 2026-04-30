-- M2 Spec 01 Task 1.3 — 운영 DB 적용용 ALTER 스크립트
--
-- 신규 환경(create.sql 신규 init)은 인덱스가 인라인으로 정의되므로 본 스크립트가 불필요.
-- 기존 운영 DB는 PRIMARY KEY + FK 자동 인덱스만 있는 상태이므로 누락된 인덱스를 명시적으로 추가한다.
--
-- 적용 절차:
--   1. SHOW INDEX FROM knowledge_space;  -- 자동 생성된 FK 인덱스 이름 확인
--   2. 아래 ALTER 중 중복되지 않는 것만 실행
--   3. EXPLAIN WITH RECURSIVE ... 으로 재귀 단계마다 인덱스 사용 여부 확인
--
-- MySQL 은 CREATE INDEX IF NOT EXISTS 를 지원하지 않으므로 idempotent 처리는 운영자 수동 확인에 의존.

ALTER TABLE knowledge_space ADD INDEX idx_knowledge_space_from (from_concept_id);
ALTER TABLE knowledge_space ADD INDEX idx_knowledge_space_to (to_concept_id);
ALTER TABLE knowledge_space ADD INDEX idx_knowledge_space_composite (from_concept_id, to_concept_id);
