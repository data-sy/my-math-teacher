-- M2 Spec 01 Task 1.5: CTE 단위 테스트 전용 시드
--
-- 그래프 설계 (ADR 0003 backward 방향: from = 선수, to = 후수):
--
--   6 → 5 → 4 → 3 → {1, 2} → 10
--                   (1, 2 모두 10 의 직접 선수)
--                   (3 → 1, 3 → 2 두 엣지 → 3 은 depth 2 에서 두 경로로 도달)
--
--   7 = 고립 노드 (어떤 엣지에도 등장하지 않음)
--
-- 시작 노드 10 기준 깊이별 결과:
--   depth 0 → {10}
--   depth 1 → {10, 1, 2}
--   depth 2 → {10, 1, 2, 3}            (3 은 두 경로로 도달 → DISTINCT 평탄화)
--   depth 3 → {10, 1, 2, 3, 4}
--   depth 4 → {10, 1, 2, 3, 4, 5}
--   depth 5 → {10, 1, 2, 3, 4, 5, 6}

INSERT INTO concepts (concept_id, concept_name) VALUES
    (1,  'depth-1 prerequisite'),
    (2,  'depth-1 prerequisite (alt)'),
    (3,  'depth-2 prerequisite (multi-path)'),
    (4,  'depth-3 prerequisite'),
    (5,  'depth-4 prerequisite'),
    (6,  'depth-5 prerequisite'),
    (7,  'isolated node'),
    (10, 'start node');

INSERT INTO knowledge_space (knowledge_space_id, to_concept_id, from_concept_id) VALUES
    (1, 10, 1),  -- 1 → 10
    (2, 10, 2),  -- 2 → 10
    (3, 1,  3),  -- 3 → 1
    (4, 2,  3),  -- 3 → 2  (다중 경로: 3 이 1 과 2 양쪽으로 10 의 depth 2 선수)
    (5, 3,  4),  -- 4 → 3
    (6, 4,  5),  -- 5 → 4
    (7, 5,  6);  -- 6 → 5
