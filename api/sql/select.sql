select count(*) from concepts;
select count(*) from knowledge_space;
select count(*) from items;

select count(*) from tests;
select * from items where item_id>4890;

select count(*) from tests_items;

select * from concepts where concept_school_level = '고등';

select * from users;

-- 테이블 수정으로 사용x (99, 448, 245, 647)
-- select count(distinct concept_chapter_main) from concepts;
-- select count(distinct concept_chapter_sub) from concepts;
-- select count(distinct concept_chapter_subsub) from concepts;
-- select count(distinct concept_chapter_id) from concepts; 

select * from chapters;
select * from chapters where school_level = '고등';

select * from tests;
select * from items;

select * from concepts where concept_chapter_id in
 (select chapter_id from chapters where grade_level = '미적');

select * from users;
select * from users_tests;

select * from tests_items where test_id=1;
select * from concepts where concept_chapter_id IN (select chapter_id from chapters where grade_level = '중2');
select * from chapters where chapter_id IN (494, 495);
select * from tests where test_grade_level = '중2';
select * from tests_items where test_id in (386, 387);
select i.item_id, i.concept_id, c.concept_name from concepts c 
join items i on i.concept_id=c.concept_id
where i.item_id in (4001, 4006, 4011, 4016, 4021, 4026, 4031);
select * from concepts where skill_id in (728, 725, 724);

SELECT ut.user_test_id, t.test_id, t.test_name, 
CASE WHEN EXISTS (SELECT 1 FROM answers a WHERE a.user_test_id = ut.user_test_id) 
THEN TRUE ELSE FALSE END AS is_record 
FROM users_tests ut JOIN tests t ON ut.test_id = t.test_id 
WHERE ut.user_id = 1;
      
select * from tests_items where test_id = 100;

select * from answers;
delete from answers where user_test_id in (4, 5, 6);
select * from probabilities;

SELECT user_test_id FROM users_tests ut 
WHERE user_id = (SELECT user_id FROM users_tests WHERE user_test_id=1)
AND EXISTS (SELECT 1 FROM answers a WHERE a.user_test_id = ut.user_test_id);

SELECT * FROM knowledge_space WHERE from_concept_id = 4979;

SELECT a.answer_id, i.concept_id, c.skill_id 
FROM items i 
JOIN answers a ON a.item_id=i.item_id 
JOIN concepts c ON c.concept_id=i.concept_id 
WHERE a.user_test_id = 5
AND a.answer_code = 0;

select * from probabilities;

-- select * from concepts where concept_id in (select i.concept_id from items i join tests_items ti on i.item_id=ti.item_id where ti.test_id in (386, 387));
-- select * from concepts where concept_id in (select i.concept_id from items i join tests_items ti on i.item_id=ti.item_id where ti.test_id in (381, 382));

select * from tests where test_semester = '상';
select * from tests_items where test_id in (386, 387);
select * from concepts where concept_id in (select i.concept_id from items i join tests_items ti on i.item_id=ti.item_id where ti.test_id in (491));
select * from tests_items where item_id in (select item_id from items where concept_id=4222);
select * from concepts where concept_id in (select i.concept_id from items i join tests_items ti on i.item_id=ti.item_id where ti.test_id in (501));

select * from items where item_id = 1000;

select * from tests_items where test_id=491;

select * from user_authority;
delete from user_authority where user_id>2;
delete from users where user_id>2;
select * from users;

SELECT ut.user_test_id, t.test_id, t.test_name FROM users_tests ut JOIN tests t ON ut.test_id = t.test_id
WHERE ut.user_id = 3 AND EXISTS (SELECT 1 FROM answers a WHERE a.user_test_id = ut.user_test_id);

select * from probabilities;

SELECT p.probability_id, ti.test_item_number, p.concept_id, p.to_concept_depth, p.probability_percent, c.concept_name, ch.school_level, ch.grade_level, ch.semester, ch.chapter_main, ch.chapter_sub, ch.chapter_name
FROM chapters ch JOIN concepts c ON c.concept_chapter_id = ch.chapter_id
JOIN probabilities p ON p.concept_id = c.concept_id JOIN answers a ON a.answer_id = p.answer_id JOIN tests_items ti ON ti.item_id = a.item_id
WHERE a.user_test_id = 8;

select * from probabilities where answer_id=340;

select * from chapters;

select max(test_item_number) from tests_items;

SELECT user_name, user_birthdate FROM users WHERE user_id = 3;

SELECT school_level, grade_level, semester, chapter_main, chapter_sub FROM chapters WHERE chapter_name  = '여러 가지 모양을 찾아볼까요';
SELECT school_level, grade_level, semester, chapter_main, chapter_sub FROM chapters WHERE chapter_name  = '복소수와 이차방정식';

-- 중복된 chapter_name 존재
SELECT chapter_name, COUNT(*)
FROM chapters
GROUP BY chapter_name
HAVING COUNT(*) > 1;

SELECT ut.user_test_id, ut.user_test_timestamp, t.test_id, t.test_name, t.test_school_level, t.test_grade_level, t.test_semester,
CASE WHEN EXISTS (SELECT 1 FROM answers a WHERE a.user_test_id = ut.user_test_id)
THEN TRUE ELSE FALSE END AS is_record
FROM users_tests ut JOIN tests t ON ut.test_id = t.test_id
WHERE ut.user_id = 3;

select * from tests where test_id = 492;

SELECT i.item_id, i.item_answer, i.item_image_path, ti.test_item_number, c.concept_name
FROM items i 
JOIN tests_items ti ON ti.item_id = i.item_id 
JOIN concepts c ON c.concept_id = i.concept_id
WHERE ti.test_id = 491;

select test_item_number from tests_items where test_id = 491;

select * from items where item_id in (select item_id from tests_items where test_id = 491);

select * from items where item_id in (select item_id from tests_items where test_id = 492);

select * from answers;

select * from probabilities p join answers a on p.answer_id = a.answer_id where user_test_id = 8;

-- MySQL 버전 정보 확인
SELECT VERSION();

