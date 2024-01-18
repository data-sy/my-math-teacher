UPDATE items AS i1
JOIN (
  SELECT concept_id, MIN(item_id) AS min_item_id
  FROM items
  WHERE concept_id IN (6800, 8882, 8893, 9796, 4662, 4668, 4672, 2666, 4785, 4786, 4803, 4961, 4972, 4975, 1110, 8420, 78, 4699, 4709, 5261, 5308, 9728, 971, 1009, 1010, 1011, 2643)
  GROUP BY concept_id
) AS i2
ON i1.concept_id = i2.concept_id AND i1.item_id = i2.min_item_id
SET 
  i1.item_answer = CASE 
                    WHEN i1.concept_id = 6800 THEN '4'
                    WHEN i1.concept_id = 8882 THEN '예각'
                    WHEN i1.concept_id = 8893 THEN '3'
                    WHEN i1.concept_id = 9796 THEN '60분 이상 90분 미만'
                    WHEN i1.concept_id = 4662 THEN '4'
                    WHEN i1.concept_id = 4668 THEN '1'
                    WHEN i1.concept_id = 4672 THEN '-6'
                    WHEN i1.concept_id = 2666 THEN '3'
                    WHEN i1.concept_id = 4785 THEN '3'
                    WHEN i1.concept_id = 4786 THEN '3'
                    WHEN i1.concept_id = 4803 THEN 'x+y=5, 1500x+1200y=6900'
                    WHEN i1.concept_id = 4961 THEN '4'
                    WHEN i1.concept_id = 4972 THEN 'y=0.2x+20'
                    WHEN i1.concept_id = 4975 THEN 'x=2, y=1'
                    WHEN i1.concept_id = 1110 THEN '2'
                    WHEN i1.concept_id = 8420 THEN '4'
                    WHEN i1.concept_id = 78 THEN '5'
                    WHEN i1.concept_id = 4699 THEN '-10'
                    WHEN i1.concept_id = 4709 THEN '2'
                    WHEN i1.concept_id = 5261 THEN '1'
                    WHEN i1.concept_id = 5308 THEN '5'
                    WHEN i1.concept_id = 9728 THEN '5'
                    WHEN i1.concept_id = 971 THEN '2a^2+ab-6b^2+a+2b'
                    WHEN i1.concept_id = 1009 THEN '3'
                    WHEN i1.concept_id = 1010 THEN 'x=4±3sqrt{2}'
                    WHEN i1.concept_id = 1011 THEN 'x=(3±sqrt{5})/2'
                    WHEN i1.concept_id = 2643 THEN '(1)8 (2)7.5 (3)7,8'
                  END,
  i1.item_image_path = CASE 
                          WHEN i1.concept_id = 6800 THEN 'https://ibb.co/xzVb9yZ'
                          WHEN i1.concept_id = 8882 THEN 'https://ibb.co/D9WdV9K'
                          WHEN i1.concept_id = 8893 THEN 'https://ibb.co/C6ZCVyV'
                          WHEN i1.concept_id = 9796 THEN 'https://ibb.co/yWpySc8'
                          WHEN i1.concept_id = 4662 THEN 'https://ibb.co/99jMRcD'
                          WHEN i1.concept_id = 4668 THEN 'https://ibb.co/ssqgrXW'
                          WHEN i1.concept_id = 4672 THEN 'https://ibb.co/sP6dtLG'
                          WHEN i1.concept_id = 2666 THEN 'https://ibb.co/KjMyVtP'
                          WHEN i1.concept_id = 4785 THEN 'https://ibb.co/Wf2LPYx'
                          WHEN i1.concept_id = 4786 THEN 'https://ibb.co/zFm4zwQ'
                          WHEN i1.concept_id = 4803 THEN 'https://ibb.co/SJWFXhc'
                          WHEN i1.concept_id = 4961 THEN 'https://ibb.co/84b1cpY'
                          WHEN i1.concept_id = 4972 THEN 'https://ibb.co/HCwZ6HW'
                          WHEN i1.concept_id = 4975 THEN 'https://ibb.co/HpKK5g9'
                          WHEN i1.concept_id = 1110 THEN 'https://ibb.co/cgHX3d6'
                          WHEN i1.concept_id = 8420 THEN 'https://ibb.co/qRcR9LJ'
                          WHEN i1.concept_id = 78 THEN 'https://ibb.co/Nx47BQ6'
                          WHEN i1.concept_id = 4699 THEN 'https://ibb.co/jTvMNtn'
                          WHEN i1.concept_id = 4709 THEN 'https://ibb.co/8d7WXZz'
                          WHEN i1.concept_id = 5261 THEN 'https://ibb.co/X4WDVdB'
                          WHEN i1.concept_id = 5308 THEN 'https://ibb.co/jD19MS4'
                          WHEN i1.concept_id = 9728 THEN 'https://ibb.co/9gLmRQS'
                          WHEN i1.concept_id = 971 THEN 'https://ibb.co/6JzTHhG'
                          WHEN i1.concept_id = 1009 THEN 'https://ibb.co/s5WL4cG'
                          WHEN i1.concept_id = 1010 THEN 'https://ibb.co/wQchRtx'
                          WHEN i1.concept_id = 1011 THEN 'https://ibb.co/s9bzV43'
                          WHEN i1.concept_id = 2643 THEN 'https://ibb.co/z5M1jPP'
                        END;