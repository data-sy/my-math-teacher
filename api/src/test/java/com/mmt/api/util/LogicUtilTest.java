package com.mmt.api.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogicUtilTest {

    // LogicUtil.bfs 는 integerList 의 "연속한 두 원소를 엣지로" buildGraph 함.
    // index 1 ~ size-2 까지 순회하며 current→next 엣지 생성. targetId 와 current 가
    // 같으면 해당 스텝 건너뜀. addedEdges Set 으로 정·역방향 중복을 막음.
    // 시작 노드는 distance 0 으로 시드됨.

    @Test
    void bfsReturnsOnlyStartWhenInputEmpty() {
        Map<Integer, Integer> result = LogicUtil.bfs(1, Collections.emptyList());

        assertThat(result).containsOnly(Map.entry(1, 0));
    }

    @Test
    void bfsSingleElementListStillReturnsOnlyStart() {
        // size 1 이면 for 루프 자체가 돌지 않음 → 엣지 없음 → start 만 존재.
        Map<Integer, Integer> result = LogicUtil.bfs(5, List.of(5));

        assertThat(result).containsOnly(Map.entry(5, 0));
    }

    @Test
    void bfsBuildsLinearChainAndComputesDistances() {
        // 리스트: [99, 2, 3, 4, 5] (size=5, 루프 i = 1..3)
        // i=1: current=2, next=3  → 엣지 2-3
        // i=2: current=3, next=4  → 엣지 3-4
        // i=3: current=4, next=5  → 엣지 4-5
        // start=5 기준 BFS: 5(0), 4(1), 3(2), 2(3).
        // 인덱스 0(=99) 은 엣지에 포함되지 않으므로 결과에도 없음.
        List<Integer> input = List.of(99, 2, 3, 4, 5);

        Map<Integer, Integer> result = LogicUtil.bfs(5, input);

        assertThat(result).containsOnly(
            Map.entry(5, 0),
            Map.entry(4, 1),
            Map.entry(3, 2),
            Map.entry(2, 3)
        );
    }

    @Test
    void bfsSkipsEdgesWhereCurrentEqualsTargetId() {
        // 리스트: [10, 1, 7, 2, 3]
        // 루프 i=1: current=1, targetId=7 → 1!=7, 엣지 1-7 생성
        // 루프 i=2: current=7, targetId=7 → skip (엣지 7-2 없음)
        // 루프 i=3: current=2, next=3 → 엣지 2-3 생성
        // 그래프: 1-7, 2-3. start=7 기준 BFS: 7(0), 1(1). 2, 3 은 연결 안 됨.
        List<Integer> input = List.of(10, 1, 7, 2, 3);

        Map<Integer, Integer> result = LogicUtil.bfs(7, input);

        assertThat(result).containsOnly(
            Map.entry(7, 0),
            Map.entry(1, 1)
        );
        assertThat(result).doesNotContainKeys(2, 3);
    }

    @Test
    void bfsDeduplicatesReverseEdgeSoDoesNotInfiniteLoop() {
        // 리스트: [10, 1, 2, 1]
        // 루프 i=1: 엣지 1-2 생성. addedEdges 에 "1->2", "2->1" 기록.
        // 루프 i=2: current=2, next=1. "2->1" 이미 있음 → 엣지 추가 안 됨.
        // 그래프: 1-2 만. 사이클 없음.
        List<Integer> input = List.of(10, 1, 2, 1);

        Map<Integer, Integer> result = LogicUtil.bfs(1, input);

        assertThat(result).containsOnly(
            Map.entry(1, 0),
            Map.entry(2, 1)
        );
    }
}
