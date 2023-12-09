package com.mmt.api.util;

import java.util.*;

public class LogicUtil {

    private static Map<Integer, List<Integer>> buildGraph(int targetId, List<Integer> integerList) {
        Map<Integer, List<Integer>> graph = new HashMap<>();
        for (int i = 0; i < integerList.size() - 1; i++) {
            int current = integerList.get(i);
            // 해당 id가 나오면 그래프 만들지 않고 다음 루프로
            if (targetId == current) continue;
            int next = integerList.get(i+1);
            graph.computeIfAbsent(current, k -> new ArrayList<>()).add(next);
            graph.computeIfAbsent(next, k -> new ArrayList<>()).add(current);
        }
        return graph;
    }

    public static Map<Integer, Integer> bfs(int start, List<Integer> integerList) {
        Map<Integer, List<Integer>> graph = buildGraph(start, integerList);
        Queue<Integer> queue = new LinkedList<>();
        Map<Integer, Integer> distances = new HashMap<>();
        Set<Integer> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);
        distances.put(start, 0);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            int distance = distances.get(current);

            for (int neighbor : graph.getOrDefault(current, Collections.emptyList())) {
                if (!visited.contains(neighbor)) {
                    queue.add(neighbor);
                    visited.add(neighbor);
                    distances.put(neighbor, distance + 1);
                }
            }
        }
        return distances;
    }

}
