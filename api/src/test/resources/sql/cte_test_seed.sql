-- M2 Spec 01 Task 1.5 / Spec 02 Task 1.1: CTE 단위 테스트 전용 시드
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
--
-- chapters 시드: spec-02 findPrerequisiteConcepts (concepts JOIN chapters) 검증용.
-- 모든 concept 은 단일 chapter_id=1 을 참조하여 JOIN 결과를 단순화.

INSERT INTO chapters (chapter_id, chapter_name, school_level, grade_level, semester, chapter_main, chapter_sub) VALUES
    (1, 'cte-test-chapter', '초등', '초1', '1학기', '', '9까지의 수');

INSERT INTO concepts (concept_id, concept_name, concept_description, concept_chapter_id, concept_achievement_id, concept_achievement_name) VALUES
    (1,  'depth-1 prerequisite',             'desc 1',  1, 1, 'achievement 1'),
    (2,  'depth-1 prerequisite (alt)',       'desc 2',  1, 1, 'achievement 1'),
    (3,  'depth-2 prerequisite (multi-path)','desc 3',  1, 1, 'achievement 1'),
    (4,  'depth-3 prerequisite',             'desc 4',  1, 1, 'achievement 1'),
    (5,  'depth-4 prerequisite',             'desc 5',  1, 1, 'achievement 1'),
    (6,  'depth-5 prerequisite',             'desc 6',  1, 1, 'achievement 1'),
    (7,  'isolated node',                    'desc 7',  1, 1, 'achievement 1'),
    (10, 'start node',                       'desc 10', 1, 1, 'achievement 1');

INSERT INTO knowledge_space (knowledge_space_id, to_concept_id, from_concept_id) VALUES
    (1, 10, 1),  -- 1 → 10
    (2, 10, 2),  -- 2 → 10
    (3, 1,  3),  -- 3 → 1
    (4, 2,  3),  -- 3 → 2  (다중 경로: 3 이 1 과 2 양쪽으로 10 의 depth 2 선수)
    (5, 3,  4),  -- 4 → 3
    (6, 4,  5),  -- 5 → 4
    (7, 5,  6);  -- 6 → 5
