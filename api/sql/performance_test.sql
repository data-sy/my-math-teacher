SHOW VARIABLES LIKE 'performance_schema';
UPDATE performance_schema.setup_consumers SET ENABLED = 'YES' WHERE NAME = 'events_statements_current';
UPDATE performance_schema.setup_consumers SET ENABLED = 'YES' WHERE NAME = 'events_statements_history';
UPDATE performance_schema.setup_consumers SET ENABLED = 'YES' WHERE NAME = 'events_statements_history_long';

-- 첫 번째 쿼리 실행
SELECT *
FROM items
WHERE concept_id = 4979
ORDER BY RAND()
LIMIT 1;

-- 두 번째 쿼리 실행
SELECT *
FROM items
WHERE item_id = (
    SELECT item_id
    FROM items
    WHERE concept_id = 4979
    ORDER BY RAND()
    LIMIT 1
);

-- 세 번째 쿼리 실행
SELECT *
FROM items AS t1
JOIN (
    SELECT CEIL(RAND() * (
        SELECT MAX(item_id)
        FROM items
        WHERE concept_id = 4979
) AS t2
ON t1.item_id = t2.random_id
WHERE t1.concept_id = 123
LIMIT 1;

-- 성능 데이터 조회
SELECT 
    event_id, 
    event_name, 
    timer_start, 
    timer_end, 
    timer_wait, -- 비교할 값
    sql_text
FROM 
    performance_schema.events_statements_history
ORDER BY 
    event_id DESC
LIMIT 1;

