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

-- 성능 테스트
-- Neo4j의 Cypher와 비교
-- 경로 길이 1~6 인 노드와 관계
-- MATCH path = (start_node)-[*1..6]->(n {concept_id: 4979})
-- RETURN nodes(path), relationships(path);
SELECT
    c1.concept_id AS level1,
    c2.concept_id AS level2,
    c3.concept_id AS level3,
    c4.concept_id AS level4,
    c5.concept_id AS level5,
    c6.concept_id AS level6,
    c7.concept_id AS level7
FROM
    concepts c1
LEFT JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
LEFT JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
LEFT JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
LEFT JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
LEFT JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
LEFT JOIN concepts c4 ON k3.to_concept_id = c4.concept_id
LEFT JOIN knowledge_space k4 ON c4.concept_id = k4.from_concept_id
LEFT JOIN concepts c5 ON k4.to_concept_id = c5.concept_id
LEFT JOIN knowledge_space k5 ON c5.concept_id = k5.from_concept_id
LEFT JOIN concepts c6 ON k5.to_concept_id = c6.concept_id
LEFT JOIN knowledge_space k6 ON c6.concept_id = k6.from_concept_id
LEFT JOIN concepts c7 ON k6.to_concept_id = c7.concept_id
WHERE
    c1.concept_id = 4979;
-- 개수를 세서 같은 값을 반환하는지 확인 : 36개 OK!
SELECT
    (SELECT COUNT(*)
     FROM concepts c1
     JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
     WHERE c1.concept_id = 4979) +
    (SELECT COUNT(*)
     FROM concepts c1
     JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
     JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
     JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
     WHERE c1.concept_id = 4979) +
    (SELECT COUNT(*)
     FROM concepts c1
     JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
     JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
     JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
     JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
     JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
     WHERE c1.concept_id = 4979) +
    (SELECT COUNT(*)
     FROM concepts c1
     JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
     JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
     JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
     JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
     JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
     JOIN concepts c4 ON k3.to_concept_id = c4.concept_id
     JOIN knowledge_space k4 ON c4.concept_id = k4.from_concept_id
     WHERE c1.concept_id = 4979) +
    (SELECT COUNT(*)
     FROM concepts c1
     JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
     JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
     JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
     JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
     JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
     JOIN concepts c4 ON k3.to_concept_id = c4.concept_id
     JOIN knowledge_space k4 ON c4.concept_id = k4.from_concept_id
     JOIN concepts c5 ON k4.to_concept_id = c5.concept_id
     JOIN knowledge_space k5 ON c5.concept_id = k5.from_concept_id
     WHERE c1.concept_id = 4979) +
    (SELECT COUNT(*)
     FROM concepts c1
     JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
     JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
     JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
     JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
     JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
     JOIN concepts c4 ON k3.to_concept_id = c4.concept_id
     JOIN knowledge_space k4 ON c4.concept_id = k4.from_concept_id
     JOIN concepts c5 ON k4.to_concept_id = c5.concept_id
     JOIN knowledge_space k5 ON c5.concept_id = k5.from_concept_id
     JOIN concepts c6 ON k5.to_concept_id = c6.concept_id
     JOIN knowledge_space k6 ON c6.concept_id = k6.from_concept_id
     WHERE c1.concept_id = 4979) AS total_path_count;

-- 경로 0~5 사이의 노드
-- MATCH (n)-[*0..5]->(m {concept_id: 4979}) 
-- RETURN (n)
SELECT DISTINCT c1.*
FROM concepts c1
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT c2.*
FROM concepts c1
INNER JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
INNER JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT c3.*
FROM concepts c1
INNER JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
INNER JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
INNER JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
INNER JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT c4.*
FROM concepts c1
INNER JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
INNER JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
INNER JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
INNER JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
INNER JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
INNER JOIN concepts c4 ON k3.to_concept_id = c4.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT c5.*
FROM concepts c1
INNER JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
INNER JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
INNER JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
INNER JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
INNER JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
INNER JOIN concepts c4 ON k3.to_concept_id = c4.concept_id
INNER JOIN knowledge_space k4 ON c4.concept_id = k4.from_concept_id
INNER JOIN concepts c5 ON k4.to_concept_id = c5.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT c6.*
FROM concepts c1
INNER JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
INNER JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
INNER JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
INNER JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
INNER JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
INNER JOIN concepts c4 ON k3.to_concept_id = c4.concept_id
INNER JOIN knowledge_space k4 ON c4.concept_id = k4.from_concept_id
INNER JOIN concepts c5 ON k4.to_concept_id = c5.concept_id
INNER JOIN knowledge_space k5 ON c5.concept_id = k5.from_concept_id
INNER JOIN concepts c6 ON k5.to_concept_id = c6.concept_id
WHERE c1.concept_id = 4979;

-- 재귀 CTE
WITH RECURSIVE path AS (
    -- 초기 상태, 시작 노드를 선택
    SELECT 
        c1.concept_id,
        c1.concept_name,
        c1.concept_description,
        c1.concept_chapter_id,
        c1.concept_achievement_id,
        c1.concept_achievement_name,
        c1.skill_id,
        0 AS depth
    FROM 
        concepts c1
    WHERE 
        c1.concept_id = 4979
    
    UNION ALL
    
    -- 재귀 상태, 각 단계를 따라가면서 노드를 선택
    SELECT 
        c2.concept_id,
        c2.concept_name,
        c2.concept_description,
        c2.concept_chapter_id,
        c2.concept_achievement_id,
        c2.concept_achievement_name,
        c2.skill_id,
        p.depth + 1
    FROM 
        path p
    JOIN 
        knowledge_space ks ON p.concept_id = ks.from_concept_id
    JOIN 
        concepts c2 ON ks.to_concept_id = c2.concept_id
    WHERE 
        p.depth < 5
)
SELECT DISTINCT 
    concept_id,
    concept_name,
    concept_description,
    concept_chapter_id,
    concept_achievement_id,
    concept_achievement_name,
    skill_id
FROM 
    path;

-- 경로 0~6 사이의 엣지
-- MATCH path = (start_node)-[*0..6]->(n {concept_id: 4979})
-- WITH nodes(path) AS connected_nodes
-- UNWIND connected_nodes AS node 
-- RETURN [id IN node.concept_id] AS concept_ids
SELECT DISTINCT c1.concept_id
FROM concepts c1
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT c2.concept_id
FROM concepts c1
INNER JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
INNER JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT c3.concept_id
FROM concepts c1
INNER JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
INNER JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
INNER JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
INNER JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT c4.concept_id
FROM concepts c1
INNER JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
INNER JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
INNER JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
INNER JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
INNER JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
INNER JOIN concepts c4 ON k3.to_concept_id = c4.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT c5.concept_id
FROM concepts c1
INNER JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
INNER JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
INNER JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
INNER JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
INNER JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
INNER JOIN concepts c4 ON k3.to_concept_id = c4.concept_id
INNER JOIN knowledge_space k4 ON c4.concept_id = k4.from_concept_id
INNER JOIN concepts c5 ON k4.to_concept_id = c5.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT c6.concept_id
FROM concepts c1
INNER JOIN knowledge_space k1 ON c1.concept_id = k1.from_concept_id
INNER JOIN concepts c2 ON k1.to_concept_id = c2.concept_id
INNER JOIN knowledge_space k2 ON c2.concept_id = k2.from_concept_id
INNER JOIN concepts c3 ON k2.to_concept_id = c3.concept_id
INNER JOIN knowledge_space k3 ON c3.concept_id = k3.from_concept_id
INNER JOIN concepts c4 ON k3.to_concept_id = c4.concept_id
INNER JOIN knowledge_space k4 ON c4.concept_id = k4.from_concept_id
INNER JOIN concepts c5 ON k4.to_concept_id = c5.concept_id
INNER JOIN knowledge_space k5 ON c5.concept_id = k5.from_concept_id
INNER JOIN concepts c6 ON k5.to_concept_id = c6.concept_id
WHERE c1.concept_id = 4979;

-- knowledge_space 자체로 select ====> 이거임!
SELECT DISTINCT ks.knowledge_space_id, ks.to_concept_id, ks.from_concept_id
FROM knowledge_space ks
INNER JOIN concepts c1 ON ks.from_concept_id = c1.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT ks1.knowledge_space_id, ks1.to_concept_id, ks1.from_concept_id
FROM knowledge_space ks1
INNER JOIN knowledge_space ks2 ON ks1.from_concept_id = ks2.to_concept_id
INNER JOIN concepts c1 ON ks2.from_concept_id = c1.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT ks1.knowledge_space_id, ks1.to_concept_id, ks1.from_concept_id
FROM knowledge_space ks1
INNER JOIN knowledge_space ks2 ON ks1.from_concept_id = ks2.to_concept_id
INNER JOIN knowledge_space ks3 ON ks2.from_concept_id = ks3.to_concept_id
INNER JOIN concepts c1 ON ks3.from_concept_id = c1.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT ks1.knowledge_space_id, ks1.to_concept_id, ks1.from_concept_id
FROM knowledge_space ks1
INNER JOIN knowledge_space ks2 ON ks1.from_concept_id = ks2.to_concept_id
INNER JOIN knowledge_space ks3 ON ks2.from_concept_id = ks3.to_concept_id
INNER JOIN knowledge_space ks4 ON ks3.from_concept_id = ks4.to_concept_id
INNER JOIN concepts c1 ON ks4.from_concept_id = c1.concept_id
WHERE c1.concept_id = 4979
UNION
SELECT DISTINCT ks1.knowledge_space_id, ks1.to_concept_id, ks1.from_concept_id
FROM knowledge_space ks1
INNER JOIN knowledge_space ks2 ON ks1.from_concept_id = ks2.to_concept_id
INNER JOIN knowledge_space ks3 ON ks2.from_concept_id = ks3.to_concept_id
INNER JOIN knowledge_space ks4 ON ks3.from_concept_id = ks4.to_concept_id
INNER JOIN knowledge_space ks5 ON ks4.from_concept_id = ks5.to_concept_id
INNER JOIN concepts c1 ON ks5.from_concept_id = c1.concept_id
WHERE c1.concept_id = 4979;

-- 재귀 CTE
WITH RECURSIVE edge_path AS (
    -- 초기 상태, 시작 노드로부터 직접 연결된 관계를 선택
    SELECT
        ks.knowledge_space_id,
        ks.from_concept_id,
        ks.to_concept_id,
        1 AS depth
    FROM
        knowledge_space ks
    WHERE
        ks.from_concept_id = 4979
    
    UNION ALL
    
    -- 재귀 상태, 각 단계를 따라가면서 관계를 선택
    SELECT
        ks.knowledge_space_id,
        ks.from_concept_id,
        ks.to_concept_id,
        ep.depth + 1
    FROM
        knowledge_space ks
    JOIN
        edge_path ep ON ks.from_concept_id = ep.to_concept_id
    WHERE
        ep.depth < 5
)
SELECT DISTINCT
    knowledge_space_id,
    from_concept_id,
    to_concept_id
FROM
    edge_path;

SELECT COUNT(DISTINCT chapter_id) AS cardinality FROM chapters;


SELECT p.probability_id, p.concept_id, p.to_concept_depth, p.probability_percent, 
c.concept_name, 
ch.school_level, ch.grade_level, ch.semester, ch.chapter_main, ch.chapter_sub, ch.chapter_name,
ti.test_item_number
FROM chapters ch 
JOIN concepts c ON c.concept_chapter_id = ch.chapter_id
JOIN probabilities p ON p.concept_id = c.concept_id 
JOIN answers a ON a.answer_id = p.answer_id 
JOIN tests_items ti ON ti.item_id = a.item_id
WHERE a.user_test_id = ?;


SELECT ch.school_level FROM chapters ch JOIN concepts c ON ch.chapter_id = c.concept_chapter_id WHERE c.concept_id = 10;

SELECT * FROM probabilities;

SELECT p.probability_id, ti.test_item_number, p.concept_id, p.to_concept_depth, p.probability_percent, c.concept_name, ch.school_level, ch.grade_level, ch.semester, ch.chapter_main, ch.chapter_sub, ch.chapter_name
FROM chapters ch JOIN concepts c ON c.concept_chapter_id = ch.chapter_id
JOIN probabilities p ON p.concept_id = c.concept_id JOIN answers a ON a.answer_id = p.answer_id JOIN tests_items ti ON ti.item_id = a.item_id
WHERE a.user_test_id = 2
AND p.to_concept_depth < 2;

SELECT answer_id FROM answers WHERE user_test_id = 3 AND answer_code = 0;

SELECT answer_id, concept_id, to_concept_depth, probability_percent FROM probabilities WHERE answer_id IN (29, 30, 32);

SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester 
FROM items i 
JOIN concepts c ON i.concept_id = c.concept_id 
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id 
WHERE c.concept_id = 1009
ORDER BY RAND() 
LIMIT 1;
SELECT i.item_id, i.item_answer, i.item_image_path, c.concept_name, ch.school_level, ch.grade_level, ch.semester FROM items i 
JOIN concepts c ON i.concept_id = c.concept_id JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id WHERE c.concept_id = 1009 ORDER BY RAND() LIMIT 1;
