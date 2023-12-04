show databases;
create database mmt;

use mmt;

show tables;

-- CREATE 순서
	-- 소단원 -> 단위개념, 영역 -> 단위개념_영역
    -- (단위개념 후) 지식체계, 문항
    -- 학습지 -> 학습지_문항
    -- 사용자 (-> 권한 -> 사용자_권한)
    -- 사용자_학습지 -> 답안 -> 확률

-- DROP 순서 (CREATE의 역순)
	-- 확률 -> 답안 -> 사용자_학습지
    -- (사용자_권한 -> 권한 DROP 후) 사용자
    -- 학습지_문항 -> 학습지
    -- 문항, 지식체계 -> 단위개념_영역 -> 영역, 단위개념 -> 소단원

DROP TABLE IF EXISTS probabilities;
DROP TABLE IF EXISTS answers;
DROP TABLE IF EXISTS users_tests;

DROP TABLE IF EXISTS user_authority;
DROP TABLE IF EXISTS authority;
DROP TABLE IF EXISTS users;

DROP TABLE IF EXISTS tests_items;
DROP TABLE IF EXISTS tests;

DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS knowledge_space;
DROP TABLE IF EXISTS concepts_sections;
DROP TABLE IF EXISTS sections;
DROP TABLE IF EXISTS concepts;
DROP TABLE IF EXISTS chapters;	

-- insert 순서
-- 소단원 insert_chapters.sql -> 단위개념 insert_concepts_escape.sql -> 섹션과 단위개념_섹션 insert_concepts_sections.sql
-- 지식 체계 : insert_knowledge_space.sql
-- 문항 insert_items.sql
-- 진단 : 학습지 insert_diag_tests.sql -> 문항 insert_diag_items.sql -> 학습지_문항 insert_diag_testsitems.sql

-- user
-- insert.sql 파일에서 테스트용 계정
