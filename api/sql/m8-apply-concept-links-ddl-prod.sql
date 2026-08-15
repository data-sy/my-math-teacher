-- ============================================================
-- M8 spec-03 — 프로덕션 RDS 에 concept_links 테이블 적용
--   정본 스키마: api/sql/create.sql (M8 섹션에서 그대로 옮김)
--   설계 정본:   docs/specs/m7/spec-03-learning-path-links.md §2.1
--
-- 왜: 결과 카드의 links 가 라이브에서 항상 빈 배열이다. 이 테이블이 프로덕션에
--     없어(ddl-auto: none) 백엔드가 부착할 데이터 자체가 없다.
--
-- 성질: additive. 구 경로 미참조 → 구 기능 무영향. 이 테이블이 비어 있어도
--       결과 API 는 정상 동작한다(링크 결측이 계약 — §2.2 빈 배열 → UI 섹션 생략).
--       롤백 = 테이블 방치. 링크만 즉시 감추려면 UPDATE concept_links SET alive=FALSE.
--
-- 안전: 재실행 안전(멱등) — CREATE TABLE IF NOT EXISTS.
--       ⚠️ MySQL DDL 은 문장별 암묵 커밋 — 트랜잭션 롤백 불가. PREFLIGHT 로 먼저 확인.
--
-- 실행 (반드시 DB=mmt 로 접속. RDS 는 publicly_accessible=false → EC2 호스트에서):
--   mysql -h <RDS_HOST> -P <RDS_PORT> -u <USER> -p mmt < api/sql/m8-apply-concept-links-ddl-prod.sql
--   ⚠️ 프로덕션 DB 변경 — 실행 주체·시점은 사람 판단.
-- ============================================================

-- 접속한 DB 확인 (mmt 여야 함)
SELECT DATABASE() AS current_db, '↑ mmt 인지 확인' AS note;

-- ------------------------------------------------------------
-- [PREFLIGHT] 현재 상태 (read-only)
-- ------------------------------------------------------------
SELECT 'concept_links (table)' AS object, IF(COUNT(*)>0,'EXISTS','MISSING') AS state
  FROM information_schema.TABLES  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='concept_links'
UNION ALL SELECT 'concepts (선행 테이블)', IF(COUNT(*)>0,'EXISTS','MISSING')
  FROM information_schema.TABLES  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='concepts';

-- ------------------------------------------------------------
-- [APPLY] 멱등 적용
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS concept_links (
	concept_link_id BIGINT auto_increment,
	concept_id      INT          NOT NULL,
	title           VARCHAR(120) NOT NULL,
	url             VARCHAR(500) NOT NULL,
	provider        VARCHAR(50)  NOT NULL,
	display_order   INT          NOT NULL DEFAULT 0,
	alive           BOOLEAN      NOT NULL DEFAULT TRUE,
	last_checked_at TIMESTAMP    NULL,
	created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (concept_link_id),
	CONSTRAINT fk_cl_concept FOREIGN KEY (concept_id) REFERENCES concepts (concept_id),
	INDEX idx_cl_concept (concept_id)
);

-- ------------------------------------------------------------
-- [POSTFLIGHT] 적용 결과 검증 — 3개 모두 OK 여야 성공
-- ------------------------------------------------------------
SELECT 'concept_links (table)' AS object, IF(COUNT(*)>0,'OK','FAIL') AS result
  FROM information_schema.TABLES  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='concept_links'
UNION ALL SELECT 'fk_cl_concept (fk)', IF(COUNT(*)>0,'OK','FAIL')
  FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='concept_links' AND CONSTRAINT_NAME='fk_cl_concept'
UNION ALL SELECT 'idx_cl_concept (idx)', IF(COUNT(*)>0,'OK','FAIL')
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='concept_links' AND INDEX_NAME='idx_cl_concept';

-- 현재 적재된 링크 수 (시드 전이면 0 — 정상)
SELECT COUNT(*) AS link_rows, COUNT(DISTINCT concept_id) AS covered_concepts FROM concept_links;
