-- CREATE 순서
	-- 단위개념, 영역 -> 단위개념_영역
    -- (단위개념 후) 지식체계, 문항
    -- 학습지 -> 학습지_문항
    -- 사용자 (-> 권한 -> 사용자_권한)
    -- 사용자_학습지 -> 답안 -> 확률

-- 단위개념 테이블
CREATE TABLE concepts (
	concept_id INT,
	concept_name VARCHAR(70),
	concept_description TEXT,
	concept_school_level CHAR(2),
	concept_grade_level CHAR(2),
	concept_semester VARCHAR(3),
	concept_chapter_id INT,
	concept_chapter_main VARCHAR(50),
	concept_chapter_sub VARCHAR(50),
	concept_chapter_subsub VARCHAR(50),
	concept_achievement_id INT,
	concept_achievement_name VARCHAR(120),
    skill_id INT,
	PRIMARY KEY (concept_id)
);

CREATE TABLE sections (
	section_id INT,
	section_name VARCHAR(20),
	PRIMARY KEY (section_id)
);

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
	test_comments VARCHAR(200),
	PRIMARY KEY (test_id)
);

-- 학습지-문항 테이블
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
	user_email VARCHAR(50),
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
