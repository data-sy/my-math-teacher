-- M2 spec-01 Task 1.5: CTE 의도 케이스 시드.
-- 의미: from_concept_id = 후수, to_concept_id = 선수.
-- "X 의 선수" = (from = X 인 행의 to).

-- 100: 고립 노드. depth N 호출 시 자기 자신만 반환되는 케이스.
INSERT INTO concepts VALUES (100);

-- 200, 210: 1단계 선수. 200 의 선수 = 210.
INSERT INTO concepts VALUES (200), (210);
INSERT INTO knowledge_space VALUES (1, 210, 200);

-- 300 - 330: 3단계 체인. 300 의 선수 체인 = 310 → 320 → 330.
INSERT INTO concepts VALUES (300), (310), (320), (330);
INSERT INTO knowledge_space VALUES (2, 310, 300);
INSERT INTO knowledge_space VALUES (3, 320, 310);
INSERT INTO knowledge_space VALUES (4, 330, 320);

-- 400 - 430: 다중 경로. 400 → {410, 420}, 410 → 430, 420 → 430.
-- 430 은 400 에서 두 경로 모두 depth 2 로 도달.
INSERT INTO concepts VALUES (400), (410), (420), (430);
INSERT INTO knowledge_space VALUES (5, 410, 400);
INSERT INTO knowledge_space VALUES (6, 420, 400);
INSERT INTO knowledge_space VALUES (7, 430, 410);
INSERT INTO knowledge_space VALUES (8, 430, 420);

-- 600, 601: 2-cycle. 600 ↔ 601 양방향.
-- 600 의 선수 탐색 시 back-edge 로 600 이 depth 2 에 재등장하지만 MIN(depth) 로 흡수.
INSERT INTO concepts VALUES (600), (601);
INSERT INTO knowledge_space VALUES (9, 601, 600);
INSERT INTO knowledge_space VALUES (10, 600, 601);
