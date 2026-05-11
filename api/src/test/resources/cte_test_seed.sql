-- M2 spec-01 Task 1.5 + spec-02 Task 3.1a: CTE 의도 케이스 시드.
-- 의미: from_concept_id = 후수, to_concept_id = 선수.
-- "X 의 선수" = (from = X 인 행의 to).
-- spec-02 객체 반환 메서드 검증을 위해 chapters + concepts 풀 컬럼을 시드한다.

-- chapters: 단일 단원(chapter_id=1)에 모든 concept 을 매핑하여 시드 단순화.
INSERT INTO chapters VALUES (1, '테스트 단원', '초등', '1', '1', '수와 연산', '자연수');

-- 100: 고립 노드. depth N 호출 시 자기 자신만 반환되는 케이스.
INSERT INTO concepts VALUES (100, '고립 개념', '고립 노드 설명', 1, 1001, '성취기준-고립');

-- 200, 210: 1단계 선수. 200 의 선수 = 210.
INSERT INTO concepts VALUES (200, '개념 200', '설명 200', 1, 1200, '성취기준-200');
INSERT INTO concepts VALUES (210, '개념 210', '설명 210', 1, 1210, '성취기준-210');
INSERT INTO knowledge_space VALUES (1, 210, 200);

-- 300 - 330: 3단계 체인. 300 의 선수 체인 = 310 → 320 → 330.
INSERT INTO concepts VALUES (300, '개념 300', '설명 300', 1, 1300, '성취기준-300');
INSERT INTO concepts VALUES (310, '개념 310', '설명 310', 1, 1310, '성취기준-310');
INSERT INTO concepts VALUES (320, '개념 320', '설명 320', 1, 1320, '성취기준-320');
INSERT INTO concepts VALUES (330, '개념 330', '설명 330', 1, 1330, '성취기준-330');
INSERT INTO knowledge_space VALUES (2, 310, 300);
INSERT INTO knowledge_space VALUES (3, 320, 310);
INSERT INTO knowledge_space VALUES (4, 330, 320);

-- 400 - 430: 다중 경로. 400 → {410, 420}, 410 → 430, 420 → 430.
-- 430 은 400 에서 두 경로 모두 depth 2 로 도달. 객체 결과에서 DISTINCT 검증.
INSERT INTO concepts VALUES (400, '개념 400', '설명 400', 1, 1400, '성취기준-400');
INSERT INTO concepts VALUES (410, '개념 410', '설명 410', 1, 1410, '성취기준-410');
INSERT INTO concepts VALUES (420, '개념 420', '설명 420', 1, 1420, '성취기준-420');
INSERT INTO concepts VALUES (430, '개념 430', '설명 430', 1, 1430, '성취기준-430');
INSERT INTO knowledge_space VALUES (5, 410, 400);
INSERT INTO knowledge_space VALUES (6, 420, 400);
INSERT INTO knowledge_space VALUES (7, 430, 410);
INSERT INTO knowledge_space VALUES (8, 430, 420);

-- 600, 601: 2-cycle. 600 ↔ 601 양방향.
-- 600 의 선수 탐색 시 back-edge 로 600 이 depth 2 에 재등장하지만 MIN(depth) 로 흡수.
INSERT INTO concepts VALUES (600, '개념 600', '설명 600', 1, 1600, '성취기준-600');
INSERT INTO concepts VALUES (601, '개념 601', '설명 601', 1, 1601, '성취기준-601');
INSERT INTO knowledge_space VALUES (9, 601, 600);
INSERT INTO knowledge_space VALUES (10, 600, 601);
