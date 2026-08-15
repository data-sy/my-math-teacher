-- CREATE 순서
	-- 단위개념, 영역 -> 단위개념_영역
    -- (단위개념 후) 지식체계, 문항
    -- 학습지 -> 학습지_문항
    -- 사용자 (-> 권한 -> 사용자_권한)
    -- 사용자_학습지 -> 답안 -> 확률


-- (단위개념이 속한) 소단원 테이블
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

-- 단위개념 테이블
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

-- (단위개념이 속한) 영역 테이블
CREATE TABLE sections (
	section_id INT,
	section_name VARCHAR(20),
	PRIMARY KEY (section_id)
);

-- 단위개념_영역 테이블
CREATE TABLE concepts_sections (
	concept_section_id INT auto_increment,
	concept_id INT,
    section_id INT,
	PRIMARY KEY (concept_section_id),
	FOREIGN KEY (concept_id) REFERENCES concepts (concept_id),
	FOREIGN KEY (section_id) REFERENCES sections (section_id)
);

-- 지식체계 테이블
CREATE TABLE knowledge_space (
	knowledge_space_id INT,
	to_concept_id INT,
	from_concept_id INT,
	PRIMARY KEY (knowledge_space_id),
	FOREIGN KEY (to_concept_id) REFERENCES concepts (concept_id),
	FOREIGN KEY (from_concept_id) REFERENCES concepts (concept_id)
);

-- 문항 테이블
CREATE TABLE items (
	item_id BIGINT auto_increment,
	item_answer VARCHAR(100),
	item_image_path VARCHAR(255),
	concept_id INT,
	PRIMARY KEY (item_id),
	FOREIGN KEY (concept_id) REFERENCES concepts (concept_id)
);

-- 학습지 테이블
CREATE TABLE tests (
	test_id BIGINT auto_increment,
	test_name VARCHAR(50),
	test_comments VARCHAR(500),
    test_school_level CHAR(2),
	test_grade_level CHAR(2),
    test_semester VARCHAR(3),
	PRIMARY KEY (test_id)
);

-- 학습지_문항 테이블
CREATE TABLE tests_items (
	test_item_id BIGINT auto_increment,
	test_id	BIGINT,
	item_id	BIGINT,
	test_item_number INT,
	PRIMARY KEY (test_item_id),
	FOREIGN KEY (test_id) REFERENCES tests (test_id),
	FOREIGN KEY (item_id) REFERENCES items (item_id)
);

-- 사용자 테이블
CREATE TABLE users (
	user_id BIGINT auto_increment,
	user_email VARCHAR(50) UNIQUE,
	user_password VARCHAR(200),
	user_name VARCHAR(20),
	user_phone VARCHAR(20),
    user_birthdate DATE,
    user_comments VARCHAR(200),
    activated TINYINT,
    oauth2id VARCHAR(200),
    auth_provider VARCHAR(20),
	PRIMARY KEY (user_id)
);

-- 권한 테이블
CREATE TABLE authority (
	authority_name VARCHAR(20),
	PRIMARY KEY (authority_name)
);

-- 사용자_권한 테이블
CREATE TABLE user_authority (
	user_autho_id BIGINT auto_increment,
	user_id BIGINT,
	authority_name VARCHAR(20),
	PRIMARY KEY (user_autho_id),
    FOREIGN KEY (user_id) REFERENCES users (user_id),
    FOREIGN KEY (authority_name) REFERENCES authority (authority_name)
);

-- 사용자_학습지 테이블
CREATE TABLE users_tests (
	user_test_id BIGINT auto_increment,
    user_id BIGINT,
    test_id BIGINT,
	user_test_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	diagnosis_id BIGINT,
	PRIMARY KEY (user_test_id),
    FOREIGN KEY (user_id) REFERENCES users (user_id),
	FOREIGN KEY (test_id) REFERENCES tests (test_id),
    FOREIGN KEY (diagnosis_id) REFERENCES users_tests (user_test_id)
);

-- 답안 테이블
CREATE TABLE answers (
	answer_id BIGINT auto_increment,
	user_test_id BIGINT,
	item_id BIGINT,
	answer_code INT,
	PRIMARY KEY (answer_id),
	FOREIGN KEY (user_test_id) REFERENCES users_tests (user_test_id),
	FOREIGN KEY (item_id) REFERENCES tests_items (item_id)
);

-- 확률 테이블
CREATE TABLE probabilities (
	probability_id BIGINT auto_increment,
	answer_id BIGINT,
    concept_id INT,
    to_concept_depth INT,
	probability_percent DOUBLE,
	probability_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (probability_id),
	FOREIGN KEY (answer_id) REFERENCES answers (answer_id),
	FOREIGN KEY (concept_id) REFERENCES concepts (concept_id)
);

-- ============================================================
-- M7 spec-01: 자가진단(self-report→DKT) — 전부 additive (ADR-0010)
-- 롤백: mmt.diagnosis.enabled=false. 구 경로 미참조라 방치 가능, 필요 시 DROP.
-- ============================================================

-- 자가진단 답안 테이블 (구 answers 무접촉 — S2=C)
CREATE TABLE self_report_answers (
	self_report_answer_id BIGINT auto_increment,
	user_test_id BIGINT NOT NULL,
	concept_id INT NOT NULL,
	known BOOLEAN NOT NULL,  -- 안다=true / 모른다=false (정오답 변환은 DKT 시퀀스 생성 시)
	created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (self_report_answer_id),
	FOREIGN KEY (user_test_id) REFERENCES users_tests (user_test_id),
	FOREIGN KEY (concept_id) REFERENCES concepts (concept_id),
	UNIQUE KEY uk_sra_session_concept (user_test_id, concept_id)  -- 세션 내 개념당 1답
);

-- probabilities 세션 스코프 컬럼 (신규 경로 전용 — 구 경로는 answer_id 로 무변경)
ALTER TABLE probabilities ADD COLUMN user_test_id BIGINT NULL;
ALTER TABLE probabilities ADD CONSTRAINT fk_prob_user_test FOREIGN KEY (user_test_id) REFERENCES users_tests (user_test_id);
ALTER TABLE probabilities ADD INDEX idx_prob_user_test (user_test_id);

-- 통합 학습 큐 (S5: 현재 위치 = 파생값, 포인터 컬럼 없음)
CREATE TABLE learning_queues (
	queue_id BIGINT auto_increment,
	user_id BIGINT NOT NULL,
	user_test_id BIGINT NOT NULL,
	created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (queue_id),
	FOREIGN KEY (user_id) REFERENCES users (user_id),
	FOREIGN KEY (user_test_id) REFERENCES users_tests (user_test_id)
);

CREATE TABLE learning_queue_items (
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

-- ============================================================
-- M8 spec-03: 개념별 외부 학습자료 링크 — additive (구 경로 미참조)
-- 롤백: 테이블 방치 가능. 링크 비노출만 원하면 alive=FALSE (배포 불요).
-- ============================================================

CREATE TABLE concept_links (
	concept_link_id BIGINT auto_increment,
	concept_id      INT          NOT NULL,
	title           VARCHAR(120) NOT NULL,   -- 노출 문구 (예: "무료 강의 (EBS)")
	url             VARCHAR(500) NOT NULL,
	provider        VARCHAR(50)  NOT NULL,   -- 'EBS' 등 — 노출·점검 그룹핑 키
	display_order   INT          NOT NULL DEFAULT 0,
	alive           BOOLEAN      NOT NULL DEFAULT TRUE,  -- R4 점검 결과 (죽은 링크 비노출)
	last_checked_at TIMESTAMP    NULL,
	created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (concept_link_id),
	CONSTRAINT fk_cl_concept FOREIGN KEY (concept_id) REFERENCES concepts (concept_id),
	INDEX idx_cl_concept (concept_id)
);
