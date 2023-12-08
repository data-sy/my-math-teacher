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

SELECT ut.user_test_id, t.test_id, t.test_name, 
CASE WHEN EXISTS (SELECT 1 FROM answers a WHERE a.user_test_id = ut.user_test_id) 
THEN TRUE ELSE FALSE END AS is_record 
FROM users_tests ut JOIN tests t ON ut.test_id = t.test_id 
WHERE ut.user_id = 1;
      
select * from tests_items where test_id = 100;
                
select * from answers;

SELECT user_test_id FROM users_tests ut 
WHERE user_id = (SELECT user_id FROM users_tests WHERE user_test_id=1)
AND EXISTS (SELECT 1 FROM answers a WHERE a.user_test_id = ut.user_test_id);

SELECT * FROM knowledge_space WHERE from_concept_id = 4979;
