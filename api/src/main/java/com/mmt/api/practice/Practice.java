//package com.mmt.api.practice;
//
//import com.mmt.api.repository.concept.ConceptRepository;
//import reactor.core.publisher.Flux;
//
//import java.util.*;
//
//public class Practice {
//    public static void main(String[] arg){
//
//        // bfs 성공
////        Flux<Integer> integerFlux = Flux.just(4975, 4979, 4961, 4975, 4979, 9184, 4961, 4975, 4979, 9181, 9184, 4961, 4975, 4979, 5788, 9181, 9184,
////                4961, 4975, 4979, 3703, 5788, 9181, 9184, 4961, 4975, 4979, 7809, 5788, 9181, 9184, 4961, 4975, 4979, 5781, 5788, 9181, 9184,
////                4961, 4975, 4979, 7810, 5788, 9181, 9184, 4961, 4975, 4979, 1256, 5788, 9181, 9184, 4961, 4975, 4979, 6784, 9181, 9184, 4961,
////                4975, 4979, 6784, 9184, 4961, 4975, 4979, 4786, 4979, 2666, 4786, 4979, 6795, 2666, 4786, 4979, 5809, 6795, 2666, 4786, 4979,
////                5781, 5809, 6795, 2666, 4786, 4979, 2616, 5781, 5809, 6795, 2666, 4786, 4979, 5619, 5781, 5809, 6795, 2666, 4786, 4979, 5762,
////                5781, 5809, 6795, 2666, 4786, 4979, 7944, 5809, 6795, 2666, 4786, 4979, 6646, 7944, 5809, 6795, 2666, 4786, 4979, 6649, 7944,
////                5809, 6795, 2666, 4786, 4979, 7942, 7944, 5809, 6795, 2666, 4786, 4979, 7943, 7944, 5809, 6795, 2666, 4786, 4979, 7945, 7944,
////                5809, 6795, 2666, 4786, 4979, 351, 5809, 6795, 2666, 4786, 4979, 5810, 6795, 2666, 4786, 4979, 5706, 5810, 6795, 2666, 4786,
////                4979, 5656, 5706, 5810, 6795, 2666, 4786, 4979, 164, 5706, 5810, 6795, 2666, 4786, 4979, 163, 5706, 5810, 6795, 2666, 4786,
////                4979, 9166, 6795, 2666, 4786, 4979, 9164, 9166, 6795, 2666, 4786, 4979, 5788, 9164, 9166, 6795, 2666, 4786, 4979, 6784, 6795,
////                2666, 4786, 4979);
////        List<Integer> integerList = integerFlux.collectList().block();
////        Map<Integer, Integer> distances = bfs(4979, integerList);
////
////        for (Map.Entry<Integer, Integer> entry : distances.entrySet()) {
////            System.out.println("Distance from 4979" + " to " + entry.getKey() + " is " + entry.getValue());
////        }
//
//    }
//
//    private static Map<Integer, List<Integer>> buildGraph(int targetId, List<Integer> integerList) {
//        Map<Integer, List<Integer>> graph = new HashMap<>();
//        for (int i = 0; i < integerList.size() - 1; i++) {
//            int current = integerList.get(i);
//            // 해당 id가 나오면 그래프 만들지 않고 다음 루프로
//            if (targetId == current) continue;
//            int next = integerList.get(i+1);
//            graph.computeIfAbsent(current, k -> new ArrayList<>()).add(next);
//            graph.computeIfAbsent(next, k -> new ArrayList<>()).add(current);
//        }
//        return graph;
//    }
//
//    private static Map<Integer, Integer> bfs(int start, List<Integer> integerList) {
//        Map<Integer, List<Integer>> graph = buildGraph(start, integerList);
//        Queue<Integer> queue = new LinkedList<>();
//        Map<Integer, Integer> distances = new HashMap<>();
//        Set<Integer> visited = new HashSet<>();
//
//        queue.add(start);
//        visited.add(start);
//        distances.put(start, 0);
//
//        while (!queue.isEmpty()) {
//            int current = queue.poll();
//            int distance = distances.get(current);
//
//            for (int neighbor : graph.getOrDefault(current, Collections.emptyList())) {
//                if (!visited.contains(neighbor)) {
//                    queue.add(neighbor);
//                    visited.add(neighbor);
//                    distances.put(neighbor, distance + 1);
//                }
//            }
//        }
//        return distances;
//    }
//
//}
