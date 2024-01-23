-- 주의) where 조건을 만족하는 모든 데이터를 긁어오므로, SET에 없는 애들은 null이 됨
	-- ELSE 조건 추가해서 혹시 놓친 데이터 있으면 현재 값 유지되도록 하기
-- 원문자 ① &#9312 ② &#9313 ③ &#9314 ④ &#9315 ⑤ &#9316
UPDATE items i
JOIN tests_items ti ON i.item_id = ti.item_id
SET 
	i.item_answer = CASE
				WHEN ti.test_item_number = 1 THEN ''
                WHEN ti.test_item_number = 2 THEN ''
                WHEN ti.test_item_number = 3 THEN ''
				WHEN ti.test_item_number = 4 THEN ''
                WHEN ti.test_item_number = 5 THEN ''
                WHEN ti.test_item_number = 6 THEN ''
				WHEN ti.test_item_number = 7 THEN ''
                WHEN ti.test_item_number = 8 THEN ''
                WHEN ti.test_item_number = 9 THEN ''
				WHEN ti.test_item_number = 10 THEN ''
                WHEN ti.test_item_number = 11 THEN ''
				WHEN ti.test_item_number = 12 THEN ''
                WHEN ti.test_item_number = 13 THEN ''
				WHEN ti.test_item_number = 14 THEN ''
                WHEN ti.test_item_number = 15 THEN ''
                WHEN ti.test_item_number = 16 THEN ''
				WHEN ti.test_item_number = 17 THEN ''
                WHEN ti.test_item_number = 18 THEN ''
                WHEN ti.test_item_number = 19 THEN ''
                WHEN ti.test_item_number = 20 THEN ''
				WHEN ti.test_item_number = 21 THEN ''
                WHEN ti.test_item_number = 22 THEN ''
                WHEN ti.test_item_number = 23 THEN ''
				WHEN ti.test_item_number = 24 THEN ''
                WHEN ti.test_item_number = 25 THEN ''
--                 ELSE i.item_answer 
			  END
WHERE ti.test_id = 491;


