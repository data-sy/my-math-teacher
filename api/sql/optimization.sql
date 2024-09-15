SHOW DATABASES;

-- < DB와 TABLE 셋팅 > --
-- 성능 테스트용 DB : opti
-- DB DROP
SELECT DATABASE(); -- 현재 데이터베이스 확인
USE mmt;
DROP DATABASE opti;
DROP DATABASE IF EXISTS opti;
-- DB CREATE
CREATE DATABASE opti;
-- !!!!!!!!!! 성능 테스트용 DB 사용 !!!!!!!!!!
USE opti;
SELECT DATABASE();

-- 성능 테스트용 TABLE : 우선은 개념, 단원, 문항만 
  -- concepts 테이블 수정 : concept_id -> concept_raw_id로 이동하고, 기존의 PK는 오토인크리 되도록
-- 테이블 DROP
SHOW TABLES;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS concepts;
DROP TABLE IF EXISTS chapters;
-- 테이블 CREATE
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
CREATE TABLE items (
	item_id BIGINT auto_increment,
	item_answer VARCHAR(100),
	item_image_path VARCHAR(255),
	concept_id INT,
	PRIMARY KEY (item_id),
	FOREIGN KEY (concept_id) REFERENCES concepts (concept_id)
);
-- 테이블 INSERT
-- 소단원 insert_chapters.sql -> 단위개념 insert_concepts_opti.sql
SELECT * FROM concepts;
SELECT COUNT(*) FROM concepts;
-- 문항 : 더미데이터 INSERT
여기에 더미 쿼리



-- < 쿼리 성능 테스트 분류(?) Guide > --
-- 대분류 : 개선할 API
  -- 중분류 : 비즈니스 로직 구현 방법
    -- 소분류 : 개선할 쿼리에 대한 로직 구현 방법 
	  -- case : 테스트 케이스
		-- 0 : 최초 쿼리
  -- 넘버링은 '중분류-소분류-case'

-- SELECT
SELECT * from items;
SELECT count(item_id) from items;
SELECT count(concept_id) from concepts;
SELECT concept_id from concepts;





-- < 대분류 : 맞춤 API 성능 개선 > --
  -- 중분류1 : 필요한 쿼리가 모두 분리된 상황  ∴ C 쿼리에 대한 개선
			-- A : u_t_id 에 따른 answer_id
			-- B : answer_id에 따른 concept_id
			-- C : concept_id에 따른 문항 (문항, 개념, 단원 테이블 JOIN)
    -- 소분류1 : for문 돌려서 각각의 결과물을 리스트에 add하는 방법
      -- case 0 : 인덱스 X, 조인을 사용한 기본 쿼리, FROM 절이 items
      -- case 1 : 인덱스 X, 조인을 사용한 기본 쿼리, FROM 절을 concepts로 수정 (where 조건문에서 concept_id 사용하니까, from절이 컨셉이면 한거나 다름 없지 않을까?)
      -- 랜덤함수가 ref인 걸 고려해서 고민해보자), 섹션은 어떻게 활용할 수 없을까?



-- case 1-1-0
EXPLAIN SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM items i
JOIN concepts c ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id 
WHERE c.concept_id = 1009
ORDER BY RAND() LIMIT 1;
-- '1','SIMPLE','c',NULL,'const','PRIMARY,concept_chapter_id','PRIMARY','4','const','1','100.00','Using temporary; Using filesort'
-- '1','SIMPLE','ch',NULL,'const','PRIMARY','PRIMARY','4','const','1','100.00',NULL
-- '1','SIMPLE','i',NULL,'ref','concept_id','concept_id','5','const','5','100.00',NULL
EXPLAIN ANALYZE SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM items i
JOIN concepts c ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id 
WHERE c.concept_id = 1009
ORDER BY RAND() LIMIT 1;
-- -> Limit: 1 row(s)  (actual time=0.0786..0.0787 rows=1 loops=1)
--     -> Sort: rand(), limit input to 1 row(s) per chunk  (actual time=0.0776..0.0776 rows=1 loops=1)
--         -> Stream results  (cost=4.04 rows=5) (actual time=0.0512..0.058 rows=5 loops=1)
--             -> Index lookup on i using concept_id (concept_id=1009)  (cost=4.04 rows=5) (actual time=0.0441..0.0482 rows=5 loops=1)
EXPLAIN SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM concepts c
JOIN items i ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id 
WHERE c.concept_id = 1009
ORDER BY RAND() LIMIT 1;
-- '1','SIMPLE','c',NULL,'const','PRIMARY,concept_chapter_id','PRIMARY','4','const','1','100.00','Using temporary; Using filesort'
-- '1','SIMPLE','ch',NULL,'const','PRIMARY','PRIMARY','4','const','1','100.00',NULL
-- '1','SIMPLE','i',NULL,'ref','concept_id','concept_id','5','const','5','100.00',NULL
EXPLAIN ANALYZE SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM concepts c
JOIN items i ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id 
WHERE c.concept_id = 1009
ORDER BY RAND() LIMIT 1;
-- -> Limit: 1 row(s)  (actual time=0.0748..0.0748 rows=1 loops=1)
--     -> Sort: rand(), limit input to 1 row(s) per chunk  (actual time=0.0739..0.0739 rows=1 loops=1)
--         -> Stream results  (cost=4.04 rows=5) (actual time=0.0488..0.0529 rows=5 loops=1)
--             -> Index lookup on i using concept_id (concept_id=1009)  (cost=4.04 rows=5) (actual time=0.041..0.0436 rows=5 loops=1)









