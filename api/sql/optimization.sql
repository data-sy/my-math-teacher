SHOW DATABASES;

-- <<<<<<<<<<<<<<<<<<<< DB와 TABLE 셋팅 >>>>>>>>>>>>>>>>>>>> --
-- 성능 테스트용 DB : opti
-- DB DROP
SELECT DATABASE(); -- 현재 데이터베이스 확인
USE mmt;
DROP DATABASE IF EXISTS opti;
-- DB CREATE
CREATE DATABASE opti;

-- !!!!!!!!!! 성능 테스트용 DB 사용하기 !!!!!!!!!!
USE opti;
SELECT DATABASE();

-- 성능 테스트용 TABLE
  -- concepts 테이블 수정 : concept_id -> concept_raw_id로 이동하고, 기존의 PK는 오토인크리 되도록  
  -- 사용하지 않는 테이블의 FK 레퍼런스 삭제
-- 테이블 DROP
SHOW TABLES;
-- 필요한 테이블만
DROP TABLE IF EXISTS probabilities;
DROP TABLE IF EXISTS answers;
DROP TABLE IF EXISTS users_tests;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS concepts;
DROP TABLE IF EXISTS chapters;
-- 테이블 CREATE
-- 단원 테이블
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
	concept_id INT auto_increment,
	concept_name VARCHAR(70),
	concept_description TEXT,
    concept_chapter_id INT,
	concept_achievement_id INT,
	concept_achievement_name VARCHAR(120),
    concept_raw_id INT,
    skill_id INT,
	PRIMARY KEY (concept_id),
	FOREIGN KEY (concept_chapter_id) REFERENCES chapters (chapter_id)
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
-- 사용자_학습지 테이블
CREATE TABLE users_tests (
	user_test_id BIGINT auto_increment,
    user_id BIGINT,
    test_id BIGINT,
	user_test_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	diagnosis_id BIGINT,
	PRIMARY KEY (user_test_id)
--  FOREIGN KEY (user_id) REFERENCES users (user_id),
-- 	FOREIGN KEY (test_id) REFERENCES tests (test_id),
--  FOREIGN KEY (diagnosis_id) REFERENCES users_tests (user_test_id)
);
-- 답안 테이블
CREATE TABLE answers (
	answer_id BIGINT auto_increment,
	user_test_id BIGINT,
	item_id BIGINT,
	answer_code INT,
	PRIMARY KEY (answer_id),
	FOREIGN KEY (user_test_id) REFERENCES users_tests (user_test_id)
-- 	FOREIGN KEY (item_id) REFERENCES tests_items (item_id)
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

-- INSERT
-- 단원 insert_chapters.sql -> 단위개념 insert_concepts_opti.sql
SELECT * FROM concepts ORDER BY concept_id desc;
SELECT COUNT(*) FROM concepts;
-- 더미 데이터 INSERT
-- 높은 재귀(반복) 횟수를 허용
SET SESSION cte_max_recursion_depth = 2000000; -- 200만

-- 더미1 : 문항
-- concept_id 1~1631 랜덤으로 100만개 (id당 500~600문항)
INSERT INTO items (item_answer, item_image_path, concept_id)
WITH RECURSIVE cte (n) AS
(
  SELECT 1
  UNION ALL
  SELECT n + 1 FROM cte WHERE n < 1000000
)
SELECT 
    CONCAT('&#x246', CAST(FLOOR(RAND() * 5) AS CHAR)) AS item_answer,  -- 결과를 char로 바꿔줘야 에러 안 남
	CONCAT('images/items/personal/', LPAD(n, 7, '0'), '.jpg') AS item_image_path,
    FLOOR(1 + RAND() * 1631) AS concept_id
FROM cte;
-- 잘 생성됐는 지 확인
SELECT COUNT(*) FROM items;
SELECT COUNT(*) FROM items WHERE concept_id=1100;
SELECT COUNT(*) FROM items GROUP BY concept_id; -- 개념 당 500~600 문제

-- 더미2 : 유저의 학습지
-- 시나리오) 유저 100명 당 학습지 100개 => 1만개
-- BUT user_test_id를 제외한 모든 컬럼은 사용되지 않으므로 모두 랜덤으로 넣자
INSERT INTO users_tests (user_id, test_id, diagnosis_id)
WITH RECURSIVE cte (n) AS
(
  SELECT 1
  UNION ALL
  SELECT n + 1 FROM cte WHERE n < 10000
)
SELECT 
    FLOOR(1 + RAND() * 100) AS user_id,
	FLOOR(1 + RAND() * 100) AS test_id,
	FLOOR(RAND() * 10000) AS diagnosis_id
FROM cte;
-- 잘 생성됐는 지 확인
SELECT COUNT(*) FROM users_tests;
SELECT COUNT(*) FROM users_tests WHERE user_id=1;
SELECT COUNT(*) FROM users_tests GROUP BY test_id;
SELECT COUNT(*) FROM users_tests GROUP BY user_id;

-- 더미3 : 답안
-- 이전 시나리오는 양이 많아서 커넥션이 끊겨 (유저 1000명*학습지 1000개 = 유저의 학습지 100만개 => 답안 100만개*20문항=2000만개)
-- INSERT INTO answers (user_test_id, item_id, answer_code)
-- WITH RECURSIVE cte (n) AS
-- (
--   SELECT 1
--   UNION ALL
--   SELECT n + 1 FROM cte WHERE n < 20000000
-- )
-- SELECT 
--     FLOOR((n - 1)/1000000) + 1 AS user_test_id, -- n을 100만으로 나눈 나머지를 넣으면 각각이 20개씩 생겨
-- 	FLOOR(1 + RAND() * 1000000) AS item_id, -- 100만개에서 랜덤
-- 	FLOOR(RAND() * 2) AS answer_code  -- 0, 1 랜덤
-- FROM cte;
-- 시나리오) 유저의 학습지 1만개 당 20개 문항(1번~20번) => 20만개
INSERT INTO answers (user_test_id, item_id, answer_code)
WITH RECURSIVE cte (n) AS
(
  SELECT 1
  UNION ALL
  SELECT n + 1 FROM cte WHERE n < 200000
)
SELECT 
    FLOOR((n - 1)%10000) + 1 AS user_test_id, -- n을 10000으로 나눈 나머지를 넣으면 각각이 20개씩 생겨
	FLOOR(1 + RAND() * 1000000) AS item_id, -- 100만개에서 랜덤
	FLOOR(RAND() * 2) AS answer_code  -- 0, 1 랜덤
FROM cte;
-- 잘 생성됐는 지 확인
SELECT COUNT(*) FROM answers;
SELECT COUNT(*) FROM answers GROUP BY user_test_id;
SELECT COUNT(*) FROM answers GROUP BY item_id;
SELECT COUNT(*) FROM answers GROUP BY answer_code;

-- 더미4 : 확률
-- 시나리오) 20만개의 문항 당 10개의 개념 저장 (절반이 정답, 절반이 오답)
INSERT INTO probabilities (answer_id, concept_id, to_concept_depth, probability_percent)
WITH RECURSIVE cte (n) AS
(
  SELECT 1
  UNION ALL
  SELECT n + 1 FROM cte WHERE n < 2000000
)
SELECT 
    FLOOR((n - 1)%200000) + 1 AS answer_id, -- n을 200000으로 나눈 나머지를 넣으면 각각이 10개씩 생겨
	FLOOR(1 + RAND() * 1631) AS concept_id, -- 1~1631에서 랜덤
	FLOOR(RAND() * 4) AS to_concept_depth,  -- 0~3 랜덤
	RAND() AS probability_percent -- 0~1 사이의 소수 랜덤
FROM cte;
-- 잘 생성됐는 지 확인
SELECT COUNT(*) FROM probabilities;
SELECT COUNT(*) FROM probabilities GROUP BY answer_id;
SELECT COUNT(*) FROM probabilities GROUP BY concept_id;
SELECT COUNT(*) FROM probabilities GROUP BY to_concept_depth;


-- <<<<<<<<<<<<<<<<<<<< 케이스 분류 Style Guide >>>>>>>>>>>>>>>>>>>> --
-- 1. [ 대분류 ] : 개선할 API
  -- 1) { 중분류 } : 비즈니스 로직 구현 방법
    -- (1) ( 소분류 ) : 개선할 쿼리에 대한 로직 구현 방법 
	  -- ① case : 테스트 케이스 ( ⓪은 최초 쿼리 )
-- ⓪ ① ② ③ ④ ⑤ ⑥ ⑦ ⑧ ⑨ ⑩ 

-- <<<<<<<<<<<<<<<<<<<< 성능 테스트 1 >>>>>>>>>>>>>>>>>>>> --
-- 1. [ 맞춤 API 성능 개선 ]
  -- 1) { 필요한 쿼리가 모두 분리된 상황 }  ∴ C 쿼리에 대한 개선
		-- A : u_t_id 에 따른 answer_id
		-- B : answer_id에 따른 concept_id
		-- C : concept_id에 따른 문항 (문항, 개념, 단원 테이블 JOIN)
    -- (1) ( for문 돌려서 각각의 결과물을 리스트에 add하는 방법 )
      -- ⓪	case 0

SELECT concept_id from concepts where concept_raw_id = 1009; -- 943
SELECT COUNT(*) FROM items WHERE concept_id=943; -- 636

-- 1.1)(1)⓪
EXPLAIN SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM items i
JOIN concepts c ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id 
WHERE c.concept_id = 1009
ORDER BY RAND() LIMIT 1;
-- '1','SIMPLE','c',NULL,'const','PRIMARY,concept_chapter_id','PRIMARY','4','const','1','100.00','Using temporary; Using filesort'
-- '1','SIMPLE','ch',NULL,'const','PRIMARY','PRIMARY','4','const','1','100.00',NULL
-- '1','SIMPLE','i',NULL,'ref','concept_id','concept_id','5','const','591','100.00',NULL

EXPLAIN ANALYZE SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM items i
JOIN concepts c ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id 
WHERE c.concept_id = 1009
ORDER BY RAND() LIMIT 1;
-- -> Limit: 1 row(s)  (actual time=8.44..8.44 rows=1 loops=1)
--     -> Sort: rand(), limit input to 1 row(s) per chunk  (actual time=8.44..8.44 rows=1 loops=1)
--         -> Stream results  (cost=1117 rows=1147) (actual time=0.675..8.09 rows=1147 loops=1)
--             -> Index lookup on i using idx_concept_id (concept_id=1009)  (cost=1117 rows=1147) (actual time=0.664..7.21 rows=1147 loops=1)


-- 1.1)(1)① : 모든 데이터를 가져와서 자바에서 랜덤 추출
EXPLAIN SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM items i
JOIN concepts c ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id 
WHERE c.concept_id = 1009;
-- '1','SIMPLE','c',NULL,'const','PRIMARY,concept_chapter_id','PRIMARY','4','const','1','100.00',NULL
-- '1','SIMPLE','ch',NULL,'const','PRIMARY','PRIMARY','4','const','1','100.00',NULL
-- '1','SIMPLE','i',NULL,'ref','concept_id','concept_id','5','const','591','100.00',NULL

EXPLAIN ANALYZE SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM items i
JOIN concepts c ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id 
WHERE c.concept_id = 1009;
-- -> Index lookup on i using concept_id (concept_id=1009)  (cost=598 rows=591) (actual time=0.347..2.71 rows=591 loops=1)


-- 1.1)(1)② : 자바에서 랜덤 추출할 번호를 미리 뽑아오기
-- 쿼리1 : c_id에 따른 i_id 목록 -> 자바에서 랜덤 추출 -> i_i에 따른 리스펀스들
SELECT i.item_id
FROM items i
JOIN concepts c ON i.concept_id = c.concept_id 
WHERE c.concept_id = 1009; -- 634316
EXPLAIN ANALYZE SELECT i.item_id
FROM items i
JOIN concepts c ON i.concept_id = c.concept_id 
WHERE c.concept_id = 1009;
-- -> Covering index lookup on i using concept_id (concept_id=1009)  (cost=61 rows=591) (actual time=0.102..0.282 rows=591 loops=1)
EXPLAIN ANALYZE SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM items i
JOIN concepts c ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id 
WHERE i.item_id = 634316;
-- -> Rows fetched before execution  (cost=0..0 rows=1) (actual time=292e-6..334e-6 rows=1 loops=1)

-- 1.1)(1)③ : 랜덤 추출 부분을 DB 인라인 뷰로 해결
EXPLAIN SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM (
    SELECT item_id
    FROM items
    WHERE concept_id = 1009
    ORDER BY RAND() LIMIT 1
) AS random_item
JOIN items i ON i.item_id = random_item.item_id
JOIN concepts c ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id;
-- '1','PRIMARY','<derived2>',NULL,'system',NULL,NULL,NULL,NULL,'1','100.00',NULL
-- '1','PRIMARY','i',NULL,'const','PRIMARY,concept_id','PRIMARY','8','const','1','100.00',NULL
-- '1','PRIMARY','c',NULL,'const','PRIMARY,concept_chapter_id','PRIMARY','4','const','1','100.00',NULL
-- '1','PRIMARY','ch',NULL,'const','PRIMARY','PRIMARY','4','const','1','100.00',NULL
-- '2','DERIVED','i',NULL,'ref','concept_id','concept_id','5','const','591','100.00','Using index; Using temporary; Using filesort'

EXPLAIN ANALYZE SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM (
    SELECT item_id
    FROM items 
    WHERE concept_id = 1009
    ORDER BY RAND() LIMIT 1
) AS random_item
JOIN items i ON i.item_id = random_item.item_id
JOIN concepts c ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id;
-- -> Rows fetched before execution  (cost=0..0 rows=1) (actual time=84e-6..126e-6 rows=1 loops=1)


  -- 2) { 필요한 쿼리를 한 번에 보내기 }  ∴ A+B+C=D 쿼리
-- 		-- A : u_t_id 에 따른 answer_id
-- 		-- B : answer_id에 따른 concept_id
-- 		-- C : concept_id에 따른 문항 (문항, 개념, 단원 테이블 JOIN)

