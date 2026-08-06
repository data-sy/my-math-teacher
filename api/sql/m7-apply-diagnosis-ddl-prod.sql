-- ============================================================
-- M7 결함① 수복 — 프로덕션 RDS 에 자가진단 additive DDL 적용
--   정본 스키마: api/sql/create.sql:161-206 (여기 그대로 옮김)
--   배경/원인:  docs/backlog/m7-prod-auth-fresh-token-401.md
--
-- 왜: MMT_DIAGNOSIS_ENABLED=true 로 신규 경로만 켜졌는데 이 테이블/컬럼이
--     프로덕션 RDS 에 없어(ddl-auto: none) 진단 저장·큐 조회가 전부 500 났다.
--
-- 성질: 전부 additive. 구 경로 미참조(ADR-0010) → 구 기능 무영향.
--       롤백 = MMT_DIAGNOSIS_ENABLED=false (테이블 방치 가능, 필요 시 DROP).
--
-- 안전: 재실행 안전(멱등). 테이블=IF NOT EXISTS, 멱등 아닌 ALTER 3건은
--       information_schema 가드로 감쌌다. 이미 일부 적용돼 있어도 그 부분은 skip.
--       ⚠️ MySQL DDL 은 문장별 암묵 커밋 — 트랜잭션 롤백 불가. PREFLIGHT 로 먼저 확인.
--
-- 실행 (반드시 DB=mmt 로 접속):
--   mysql -h <RDS_HOST> -P <RDS_PORT> -u <USER> -p mmt < api/sql/m7-apply-diagnosis-ddl-prod.sql
--   (또는 클라이언트에서 mmt 선택 후 전체 실행)
--   ⚠️ 프로덕션 DB 변경 — 실행 주체·시점은 사람 판단. probabilities ALTER 는 구 경로
--      테이블이라 큰 테이블이면 잠금 시간 확인(본 서비스는 소규모라 대개 즉시).
-- ============================================================

-- 접속한 DB 확인 (mmt 여야 함)
SELECT DATABASE() AS current_db, '↑ mmt 인지 확인' AS note;

-- ------------------------------------------------------------
-- [PREFLIGHT] 현재 적용 상태 (read-only) — 무엇이 이미 있는지
-- ------------------------------------------------------------
SELECT 'self_report_answers'  AS object, IF(COUNT(*)>0,'EXISTS','MISSING') AS state FROM information_schema.TABLES  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='self_report_answers'
UNION ALL SELECT 'learning_queues',        IF(COUNT(*)>0,'EXISTS','MISSING') FROM information_schema.TABLES  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_queues'
UNION ALL SELECT 'learning_queue_items',   IF(COUNT(*)>0,'EXISTS','MISSING') FROM information_schema.TABLES  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_queue_items'
UNION ALL SELECT 'probabilities.user_test_id (col)', IF(COUNT(*)>0,'EXISTS','MISSING') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='probabilities' AND COLUMN_NAME='user_test_id'
UNION ALL SELECT 'fk_prob_user_test (fk)',  IF(COUNT(*)>0,'EXISTS','MISSING') FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='probabilities' AND CONSTRAINT_NAME='fk_prob_user_test'
UNION ALL SELECT 'idx_prob_user_test (idx)', IF(COUNT(*)>0,'EXISTS','MISSING') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='probabilities' AND INDEX_NAME='idx_prob_user_test';

-- ------------------------------------------------------------
-- [APPLY] 멱등 적용
-- ------------------------------------------------------------

-- (1) self_report_answers — IF NOT EXISTS 로 멱등
CREATE TABLE IF NOT EXISTS self_report_answers (
	self_report_answer_id BIGINT auto_increment,
	user_test_id BIGINT NOT NULL,
	concept_id INT NOT NULL,
	known BOOLEAN NOT NULL,
	created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (self_report_answer_id),
	FOREIGN KEY (user_test_id) REFERENCES users_tests (user_test_id),
	FOREIGN KEY (concept_id) REFERENCES concepts (concept_id),
	UNIQUE KEY uk_sra_session_concept (user_test_id, concept_id)
);

-- (2) probabilities.user_test_id 컬럼 (가드: 없을 때만)
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='probabilities' AND COLUMN_NAME='user_test_id');
SET @sql := IF(@c=0,
  'ALTER TABLE probabilities ADD COLUMN user_test_id BIGINT NULL',
  'SELECT ''skip (2): probabilities.user_test_id 이미 존재'' AS note');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- (3) FK fk_prob_user_test (가드: 없을 때만 — 컬럼이 (2)에서 보장됨)
SET @c := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='probabilities' AND CONSTRAINT_NAME='fk_prob_user_test');
SET @sql := IF(@c=0,
  'ALTER TABLE probabilities ADD CONSTRAINT fk_prob_user_test FOREIGN KEY (user_test_id) REFERENCES users_tests (user_test_id)',
  'SELECT ''skip (3): fk_prob_user_test 이미 존재'' AS note');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- (4) INDEX idx_prob_user_test (가드: 없을 때만)
--     주의: (3) FK 추가 시 InnoDB 가 컬럼에 인덱스를 자동 생성할 수 있으나 이름이 다르므로
--           create.sql 원본대로 명시 인덱스 idx_prob_user_test 를 별도로 둔다.
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='probabilities' AND INDEX_NAME='idx_prob_user_test');
SET @sql := IF(@c=0,
  'ALTER TABLE probabilities ADD INDEX idx_prob_user_test (user_test_id)',
  'SELECT ''skip (4): idx_prob_user_test 이미 존재'' AS note');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- (5) learning_queues — IF NOT EXISTS
CREATE TABLE IF NOT EXISTS learning_queues (
	queue_id BIGINT auto_increment,
	user_id BIGINT NOT NULL,
	user_test_id BIGINT NOT NULL,
	created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (queue_id),
	FOREIGN KEY (user_id) REFERENCES users (user_id),
	FOREIGN KEY (user_test_id) REFERENCES users_tests (user_test_id)
);

-- (6) learning_queue_items — IF NOT EXISTS (learning_queues 선행 필요)
CREATE TABLE IF NOT EXISTS learning_queue_items (
	queue_item_id BIGINT auto_increment,
	queue_id BIGINT NOT NULL,
	position INT NOT NULL,
	concept_id INT NOT NULL,
	done BOOLEAN NOT NULL DEFAULT FALSE,
	done_at TIMESTAMP NULL,
	PRIMARY KEY (queue_item_id),
	FOREIGN KEY (queue_id) REFERENCES learning_queues (queue_id),
	FOREIGN KEY (concept_id) REFERENCES concepts (concept_id),
	UNIQUE KEY uk_lqi_queue_position (queue_id, position)
);

-- ------------------------------------------------------------
-- [POSTFLIGHT] 적용 결과 검증 — 6개 모두 EXISTS 여야 성공
-- ------------------------------------------------------------
SELECT 'self_report_answers'  AS object, IF(COUNT(*)>0,'OK','FAIL') AS result FROM information_schema.TABLES  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='self_report_answers'
UNION ALL SELECT 'learning_queues',        IF(COUNT(*)>0,'OK','FAIL') FROM information_schema.TABLES  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_queues'
UNION ALL SELECT 'learning_queue_items',   IF(COUNT(*)>0,'OK','FAIL') FROM information_schema.TABLES  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_queue_items'
UNION ALL SELECT 'probabilities.user_test_id (col)', IF(COUNT(*)>0,'OK','FAIL') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='probabilities' AND COLUMN_NAME='user_test_id'
UNION ALL SELECT 'fk_prob_user_test (fk)',  IF(COUNT(*)>0,'OK','FAIL') FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='probabilities' AND CONSTRAINT_NAME='fk_prob_user_test'
UNION ALL SELECT 'idx_prob_user_test (idx)', IF(COUNT(*)>0,'OK','FAIL') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='probabilities' AND INDEX_NAME='idx_prob_user_test';
