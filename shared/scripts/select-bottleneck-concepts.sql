-- ============================================================
-- M8 spec-03 §2.2 — 링크 시드 대상 고르기: "그래프 구조상 병목" 상위 개념
--
-- 왜 이 목록인가: 시급도는 유저별이라 사전 선별이 불가능하다. 대신 그래프 구조상
-- "이걸 모르면 위로 많이 막히는" 개념 = blockedDescendants 가 큰 개념부터 시드한다.
-- 전 개념(1,631) 커버는 불필요하고, 링크 결측은 계약이다(§2.2).
--
-- 재귀 형태는 앱의 JdbcTemplateConceptRepository.countBlockedDescendants 와 동일하게 맞췄다
-- (같은 depth 3, 같은 조인 방향, 자기 자신 제외) — 화면의 "N개가 막혀요" 와 같은 수를 본다.
--
-- 실행:
--   mysql -h <HOST> -P <PORT> -u <USER> -p mmt < shared/scripts/select-bottleneck-concepts.sql
-- 읽기 전용. 결과 상위 30~50 개념을 concept-links-seed.csv 큐레이션 대상으로 삼는다.
-- ============================================================

WITH RECURSIVE blocked_path AS (
    SELECT concept_id AS root, concept_id, 0 AS depth
    FROM concepts

    UNION ALL

    SELECT bp.root, c.concept_id, bp.depth + 1
    FROM blocked_path bp
    JOIN knowledge_space ks ON bp.concept_id = ks.to_concept_id
    JOIN concepts c          ON ks.from_concept_id = c.concept_id
    WHERE bp.depth < 3
)
SELECT
    bp.root                                   AS concept_id,
    c.concept_name,
    COUNT(DISTINCT bp.concept_id) - 1         AS blocked_descendants,
    (SELECT COUNT(*) FROM concept_links cl
      WHERE cl.concept_id = bp.root AND cl.alive = TRUE) AS existing_links
FROM blocked_path bp
JOIN concepts c ON c.concept_id = bp.root
GROUP BY bp.root, c.concept_name
HAVING blocked_descendants > 0
ORDER BY blocked_descendants DESC, concept_id ASC
LIMIT 50;
