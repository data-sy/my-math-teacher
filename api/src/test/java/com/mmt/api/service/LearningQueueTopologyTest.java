package com.mmt.api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spec-01 §8 계단 불변식 property: 생성된 큐의 모든 (선수, 후수) 쌍에서
 * 선수 position < 후수 position. 시급도는 진입 순서에만 반영 (위상 제약 불가침).
 */
class LearningQueueTopologyTest {

    @Test
    @DisplayName("계단 불변식 property: 무작위 DAG ×20 시드에서 선수 position < 후수 position")
    void topologicalInvariantOnRandomDags() {
        for (int seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            int n = 2 + random.nextInt(20);
            // DAG 생성: id 큰 노드가 id 작은 노드를 선수로 가질 수 있음 (i > j 만 허용 → 무사이클)
            Map<Integer, List<Integer>> prerequisitesOf = new HashMap<>();
            Set<Integer> nodes = new LinkedHashSet<>();
            for (int i = 0; i < n; i++) {
                nodes.add(i);
            }
            for (int i = 1; i < n; i++) {
                for (int j = 0; j < i; j++) {
                    if (random.nextDouble() < 0.3) {
                        prerequisitesOf.computeIfAbsent(i, k -> new ArrayList<>()).add(j);
                    }
                }
            }
            Map<Integer, Double> urgency = new HashMap<>();
            nodes.forEach(id -> urgency.put(id, random.nextDouble() * 100));

            List<Integer> order = LearningQueueService.topologicalOrder(nodes, prerequisitesOf, urgency);

            assertThat(order).as("seed=" + seed).containsExactlyInAnyOrderElementsOf(nodes);
            Map<Integer, Integer> position = new HashMap<>();
            for (int i = 0; i < order.size(); i++) {
                position.put(order.get(i), i);
            }
            for (int dependent : nodes) {
                for (int prerequisite : prerequisitesOf.getOrDefault(dependent, List.of())) {
                    assertThat(position.get(prerequisite))
                            .as("seed=%d: 선수 %d 가 후수 %d 보다 먼저여야 함", seed, prerequisite, dependent)
                            .isLessThan(position.get(dependent));
                }
            }
        }
    }

    @Test
    @DisplayName("시급도는 진입차수-0 후보 사이의 우선순위로만 — 급한 가지 먼저, 계단은 불가침")
    void urgencyOnlyBreaksTiesAmongReady() {
        // 독립 두 사슬: A(1←2) urgency 90(느긋), B(11←12) urgency 10(급함)
        Set<Integer> nodes = new LinkedHashSet<>(List.of(1, 2, 11, 12));
        Map<Integer, List<Integer>> prereqs = Map.of(2, List.of(1), 12, List.of(11));
        Map<Integer, Double> urgency = Map.of(1, 90.0, 2, 90.0, 11, 10.0, 12, 10.0);
        List<Integer> order = LearningQueueService.topologicalOrder(nodes, prereqs, urgency);
        // 급한 B 가지 먼저 통째로, 그다음 A — 단 사슬 내부 순서(선수 먼저)는 항상 유지
        assertThat(order).containsExactly(11, 12, 1, 2);
    }

    @Test
    @DisplayName("순수 시급도순 정렬 금지: 급한 후수가 자기 선수를 건너뛰지 못한다")
    void urgentDependentCannotSkipPrerequisite() {
        // 사슬 5←6: 후수 6 이 percent 5(가장 급함), 선수 5 는 80 — 그래도 5 먼저
        Set<Integer> nodes = new LinkedHashSet<>(List.of(5, 6));
        Map<Integer, List<Integer>> prereqs = Map.of(6, List.of(5));
        List<Integer> order = LearningQueueService.topologicalOrder(nodes, prereqs,
                Map.of(5, 80.0, 6, 5.0));
        assertThat(order).containsExactly(5, 6);
    }

    @Test
    @DisplayName("사이클 데이터 degrade: 죽지 않고 전 노드 포함 완주 (§4.6-4)")
    void cycleDegradesWithoutFailure() {
        Set<Integer> nodes = new LinkedHashSet<>(List.of(1, 2, 3));
        Map<Integer, List<Integer>> prereqs = Map.of(
                1, List.of(3), 2, List.of(1), 3, List.of(2)); // 1←3←2←1 사이클
        List<Integer> order = LearningQueueService.topologicalOrder(nodes, prereqs,
                Map.of(1, 10.0, 2, 20.0, 3, 30.0));
        assertThat(order).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(order.get(0)).isEqualTo(1); // 강제 진입 = 우선순위(최저 percent) 최상
    }

    @Test
    @DisplayName("결정론: 동일 입력 → 항상 동일 순서 (urgency 결측 포함, conceptId 타이브레이크)")
    void deterministicWithMissingUrgency() {
        Set<Integer> nodes = new LinkedHashSet<>(List.of(7, 3, 9));
        Map<Integer, List<Integer>> prereqs = Map.of();
        List<Integer> expected = LearningQueueService.topologicalOrder(nodes, prereqs, Map.of());
        assertThat(expected).containsExactly(3, 7, 9); // 전부 결측 → conceptId 오름차순
        for (int i = 0; i < 5; i++) {
            assertThat(LearningQueueService.topologicalOrder(nodes, prereqs, Map.of()))
                    .isEqualTo(expected);
        }
    }
}
