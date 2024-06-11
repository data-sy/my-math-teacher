show databases;
create database mmt;
-- 테이블 비우기
TRUNCATE TABLE answers;

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
-- 소단원 insert_chapters.sql -> 단위개념 insert_concepts_latex.sql -> 섹션과 단위개념_섹션 insert_concepts_sections.sql
-- 지식 체계 : insert_knowledge_space.sql
-- 진단 : 학습지 insert_diag_tests.sql -> 문항 insert_diag_items.sql -> 학습지_문항 insert_diag_testsitems.sql
-- 진단 학습지 중 491~495 답안 업데이트 : update_diag_answers.sql
-- (맞춤 학습 때 사용될) 문항 insert_items.sql -- 초기 버전에서는 생략 가능
-- 유저 : insert_users.sql 파일에서 테스트용 계정
-- 포스트맨으로 가서 : 테스트유저(유저3) 회원가입, 진단학습지 다운로드(1, 2, 491~495), 답안입력(뒤에3개), ai분석(유저테스트5)
