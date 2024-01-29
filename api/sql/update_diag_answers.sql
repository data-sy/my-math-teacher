-- 주의) where 조건을 만족하는 모든 데이터를 긁어오므로, SET에 없는 애들은 null이 됨
	-- ELSE 조건 추가해서 혹시 놓친 데이터 있으면 현재 값 유지되도록 하기
-- 원문자 ① &#9312 ② &#9313 ③ &#9314 ④ &#9315 ⑤ &#9316
UPDATE items i
JOIN tests_items ti ON i.item_id = ti.item_id
SET 
	i.item_answer = CASE
				WHEN ti.test_item_number = 1 THEN '(1) $-1$, (2) $-1$'
                WHEN ti.test_item_number = 2 THEN '실수부분, 허수부분'
                WHEN ti.test_item_number = 3 THEN '&#9315'
				WHEN ti.test_item_number = 4 THEN '$6$'
                WHEN ti.test_item_number = 5 THEN '(1) $\\sqrt{a}i$, (2) $\\pm \\sqrt{a}i$'
                WHEN ti.test_item_number = 6 THEN '(1) $13-5i$, (2) $7-i$'
				WHEN ti.test_item_number = 7 THEN '&#9316'
                WHEN ti.test_item_number = 8 THEN '&#9316'
                WHEN ti.test_item_number = 9 THEN '&#9312'
				WHEN ti.test_item_number = 10 THEN '&#9313'
                WHEN ti.test_item_number = 11 THEN '(1) 교환법칙, (2) 결합법칙, (3) 분배법칙'
				WHEN ti.test_item_number = 12 THEN '(1) 한 개, (2) 무수히 많다., (3) 해가 없다.'
                WHEN ti.test_item_number = 13 THEN '(1)$-2\\pm\\sqrt{3}$, 실근, (2)$\\frac{3\\pm i}{2}$, 허근'
				WHEN ti.test_item_number = 14 THEN '$k<9$'
                WHEN ti.test_item_number = 15 THEN '(1) $x=\\frac{3}{2}, \\frac{5}{4}$, (2) $x=\\pm2$'
                WHEN ti.test_item_number = 16 THEN '(1) $\\frac{-1\\pm3i}{2}$, (2) $\\sqrt{6}\\pm\\sqrt{3}$'
				WHEN ti.test_item_number = 17 THEN '$D=-3k^2+2k+1, k=1, -\\frac{1}{3}$'
                WHEN ti.test_item_number = 18 THEN '$3$'
                WHEN ti.test_item_number = 19 THEN '$16$'
                WHEN ti.test_item_number = 20 THEN '(1) $(x+4)^2=16+9, x+4=\\pm5, x=1, -9$,  (2) $(x-1)^2=1-3, x-1=\\pm2i, x=1\\pm2i$'
				WHEN ti.test_item_number = 21 THEN '(1) $36$, (2) $14$'
                WHEN ti.test_item_number = 22 THEN '&#9316'
                WHEN ti.test_item_number = 23 THEN '&#9314'
				WHEN ti.test_item_number = 24 THEN '$40$'
                WHEN ti.test_item_number = 25 THEN '&#9314'
--                 ELSE i.item_answer 
			  END
WHERE ti.test_id = 491;

UPDATE items i
JOIN tests_items ti ON i.item_id = ti.item_id
SET 
	i.item_answer = CASE
				WHEN ti.test_item_number = 1 THEN '(1) $i$, (2) $-1$'
                WHEN ti.test_item_number = 2 THEN '&#9315'
                WHEN ti.test_item_number = 3 THEN '&#9314'
				WHEN ti.test_item_number = 4 THEN '켤레복소수'
                WHEN ti.test_item_number = 5 THEN '(1) $\\sqrt{a}i$, (2) 제곱근'
                WHEN ti.test_item_number = 6 THEN '(1) $42$, (2) $1+7i$'
				WHEN ti.test_item_number = 7 THEN '&#9315'
                WHEN ti.test_item_number = 8 THEN '&#9314'
                WHEN ti.test_item_number = 9 THEN '&#9315'
				WHEN ti.test_item_number = 10 THEN '(1) $5+i$, (2) $1$, (3) $-1$'
                WHEN ti.test_item_number = 11 THEN '(1) 교환법칙, (2) 결합법칙, (3) 분배법칙'
				WHEN ti.test_item_number = 12 THEN '(1) 한 개, (2) 무수히 많다., (3) 해가 없다.'
                WHEN ti.test_item_number = 13 THEN '(1)$-2\\pm\\sqrt{3}$, 실근, (2)$\\frac{3\\pm i}{2}$, 허근'
				WHEN ti.test_item_number = 14 THEN '$k<9$'
                WHEN ti.test_item_number = 15 THEN '(1) $x=\\frac{3}{2}, \\frac{5}{4}$, (2) $x=\\pm2$'
                WHEN ti.test_item_number = 16 THEN '(1) $\\frac{-1\\pm3i}{2}$, (2) $\\sqrt{6}\\pm\\sqrt{3}$'
				WHEN ti.test_item_number = 17 THEN '$D=-3k^2+2k+1, k=1, -\\frac{1}{3}$'
                WHEN ti.test_item_number = 18 THEN '$3$'
                WHEN ti.test_item_number = 19 THEN '$16$'
                WHEN ti.test_item_number = 20 THEN '(1) $(x+4)^2=16+9, x+4=\\pm5, x=1, -9$,  (2) $(x-1)^2=1-3, x-1=\\pm2i, x=1\\pm2i$'
				WHEN ti.test_item_number = 21 THEN '(1) $36$, (2) $14$'
                WHEN ti.test_item_number = 22 THEN '$5$'
                WHEN ti.test_item_number = 23 THEN '&#9312'
				WHEN ti.test_item_number = 24 THEN '$40$'
                WHEN ti.test_item_number = 25 THEN '&#9314'
--                 ELSE i.item_answer 
			  END
WHERE ti.test_id = 492;

UPDATE items i
JOIN tests_items ti ON i.item_id = ti.item_id
SET 
	i.item_answer = CASE
				WHEN ti.test_item_number = 1 THEN '$-1$, $1$'
                WHEN ti.test_item_number = 2 THEN '$a=0, b \\neq 0$'
                WHEN ti.test_item_number = 3 THEN '&#9316'
				WHEN ti.test_item_number = 4 THEN '$a-bi$'
                WHEN ti.test_item_number = 5 THEN '(1) $\\sqrt{a}i$, (2) $\\pm \\sqrt{a}i$'
                WHEN ti.test_item_number = 6 THEN '(1) $13-5i$, (2) $7-i$'
				WHEN ti.test_item_number = 7 THEN '&#9315'
                WHEN ti.test_item_number = 8 THEN '&#9314'
                WHEN ti.test_item_number = 9 THEN '&#9312'
				WHEN ti.test_item_number = 10 THEN '&#9314'
                WHEN ti.test_item_number = 11 THEN '(1) 교환법칙, (2) 결합법칙, (3) 분배법칙'
				WHEN ti.test_item_number = 12 THEN '(1) 한 개, (2) 무수히 많다., (3) 해가 없다.'
                WHEN ti.test_item_number = 13 THEN '(1)$-2\\pm\\sqrt{3}$, 실근, (2)$\\frac{3\\pm i}{2}$, 허근'
				WHEN ti.test_item_number = 14 THEN '$k<9$'
                WHEN ti.test_item_number = 15 THEN '(1) $x=\\frac{3}{2}, \\frac{5}{4}$, (2) $x=\\pm2$'
                WHEN ti.test_item_number = 16 THEN '(1) $\\frac{-1\\pm3i}{2}$, (2) $\\sqrt{6}\\pm\\sqrt{3}$'
				WHEN ti.test_item_number = 17 THEN '$D=-3k^2+2k+1, k=1, -\\frac{1}{3}$'
                WHEN ti.test_item_number = 18 THEN '$3$'
                WHEN ti.test_item_number = 19 THEN '$16$'
                WHEN ti.test_item_number = 20 THEN '(1) $(x+4)^2=16+9, x+4=\\pm5, x=1, -9$,  (2) $(x-1)^2=1-3, x-1=\\pm2i, x=1\\pm2i$'
				WHEN ti.test_item_number = 21 THEN '(1) $36$, (2) $14$'
                WHEN ti.test_item_number = 22 THEN '$4$'
                WHEN ti.test_item_number = 23 THEN '&#9314'
				WHEN ti.test_item_number = 24 THEN '$40$'
                WHEN ti.test_item_number = 25 THEN '&#9314'
--                 ELSE i.item_answer 
			  END
WHERE ti.test_id = 493;


UPDATE items i
JOIN tests_items ti ON i.item_id = ti.item_id
SET 
	i.item_answer = CASE
				WHEN ti.test_item_number = 1 THEN '(1) $-1$, (2) $-1$'
                WHEN ti.test_item_number = 2 THEN '$a=b=0$'
                WHEN ti.test_item_number = 3 THEN '$21$'
				WHEN ti.test_item_number = 4 THEN '$3-2i$'
                WHEN ti.test_item_number = 5 THEN '(1) $\\sqrt{a}i$, (2) 제곱근'
                WHEN ti.test_item_number = 6 THEN '(1) $42$, (2) $1+7i$'
				WHEN ti.test_item_number = 7 THEN '&#9312'
                WHEN ti.test_item_number = 8 THEN '(1) 참, (2) 거짓'
                WHEN ti.test_item_number = 9 THEN '&#9315'
				WHEN ti.test_item_number = 10 THEN '&#9314'
                WHEN ti.test_item_number = 11 THEN '(1) 교환법칙, (2) 결합법칙, (3) 분배법칙'
				WHEN ti.test_item_number = 12 THEN '(1) 한 개, (2) 무수히 많다., (3) 해가 없다.'
                WHEN ti.test_item_number = 13 THEN '(1)$-2\\pm\\sqrt{3}$, 실근, (2)$\\frac{3\\pm i}{2}$, 허근'
				WHEN ti.test_item_number = 14 THEN '$k<9$'
                WHEN ti.test_item_number = 15 THEN '(1) $x=\\frac{3}{2}, \\frac{5}{4}$, (2) $x=\\pm2$'
                WHEN ti.test_item_number = 16 THEN '(1) $\\frac{-1\\pm3i}{2}$, (2) $\\sqrt{6}\\pm\\sqrt{3}$'
				WHEN ti.test_item_number = 17 THEN '$D=-3k^2+2k+1, k=1, -\\frac{1}{3}$'
                WHEN ti.test_item_number = 18 THEN '$3$'
                WHEN ti.test_item_number = 19 THEN '$16$'
                WHEN ti.test_item_number = 20 THEN '(1) $(x+4)^2=16+9, x+4=\\pm5, x=1, -9$,  (2) $(x-1)^2=1-3, x-1=\\pm2i, x=1\\pm2i$'
				WHEN ti.test_item_number = 21 THEN '(1) $36$, (2) $14$'
                WHEN ti.test_item_number = 22 THEN '(1) $x^2+x-4=0$, (2) $x^2+5x+6=0$'
                WHEN ti.test_item_number = 23 THEN '&#9312'
				WHEN ti.test_item_number = 24 THEN '$40$'
                WHEN ti.test_item_number = 25 THEN '&#9314'
--                 ELSE i.item_answer 
			  END
WHERE ti.test_id = 494;

UPDATE items i
JOIN tests_items ti ON i.item_id = ti.item_id
SET 
	i.item_answer = CASE
				WHEN ti.test_item_number = 1 THEN '($i$), ($-1$)'
                WHEN ti.test_item_number = 2 THEN '$a \\neq 0, b = 0$'
                WHEN ti.test_item_number = 3 THEN '&#9315'
				WHEN ti.test_item_number = 4 THEN '$10-i$'
                WHEN ti.test_item_number = 5 THEN '(1) $\\sqrt{a}i$, (2) (2) $\\pm \\sqrt{a}i$'
                WHEN ti.test_item_number = 6 THEN '(1) $13-5i$, (2) $7-i$'
				WHEN ti.test_item_number = 7 THEN '$-1$'
                WHEN ti.test_item_number = 8 THEN '(1) 거짓, (2) 참'
                WHEN ti.test_item_number = 9 THEN '&#9315'
				WHEN ti.test_item_number = 10 THEN '&#9314'
                WHEN ti.test_item_number = 11 THEN '(1) 교환법칙, (2) 결합법칙, (3) 분배법칙'
				WHEN ti.test_item_number = 12 THEN '(1) 한 개, (2) 무수히 많다., (3) 해가 없다.'
                WHEN ti.test_item_number = 13 THEN '(1)$-2\\pm\\sqrt{3}$, 실근, (2)$\\frac{3\\pm i}{2}$, 허근'
				WHEN ti.test_item_number = 14 THEN '$k<9$'
                WHEN ti.test_item_number = 15 THEN '(1) $x=\\frac{3}{2}, \\frac{5}{4}$, (2) $x=\\pm2$'
                WHEN ti.test_item_number = 16 THEN '(1) $\\frac{-1\\pm3i}{2}$, (2) $\\sqrt{6}\\pm\\sqrt{3}$'
				WHEN ti.test_item_number = 17 THEN '$D=-3k^2+2k+1, k=1, -\\frac{1}{3}$'
                WHEN ti.test_item_number = 18 THEN '$3$'
                WHEN ti.test_item_number = 19 THEN '$16$'
                WHEN ti.test_item_number = 20 THEN '(1) $(x+4)^2=16+9, x+4=\\pm5, x=1, -9$,  (2) $(x-1)^2=1-3, x-1=\\pm2i, x=1\\pm2i$'
				WHEN ti.test_item_number = 21 THEN '(1) $36$, (2) $14$'
                WHEN ti.test_item_number = 22 THEN '$-1$'
                WHEN ti.test_item_number = 23 THEN '&#9314'
				WHEN ti.test_item_number = 24 THEN '$40$'
                WHEN ti.test_item_number = 25 THEN '&#9314'
--                 ELSE i.item_answer 
			  END
WHERE ti.test_id = 495;
