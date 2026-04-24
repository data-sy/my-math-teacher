package com.mmt.api.performance;

import java.util.Arrays;

/**
 * 벤치마크 결과 출력 헬퍼.
 *
 * - avg/p95/p99 를 nanos 배열에서 계산해 ms 단위로 리포트.
 * - 표본 100 기준 p95 인덱스는 94 (0-based), p99 인덱스는 98.
 * - 기록 포맷은 docs/benchmark/milestone-1-baseline.md 표에 그대로 옮길 수 있도록 탭/공백 정렬 대신 키=값 형태 유지.
 */
final class BenchmarkStats {

    private BenchmarkStats() {}

    static void report(String label, long[] nanos) {
        long[] copy = nanos.clone();
        Arrays.sort(copy);
        int n = copy.length;
        long sum = 0;
        for (long v : copy) sum += v;
        double avgMs = (sum / (double) n) / 1_000_000.0;
        double p95Ms = copy[(int) Math.ceil(n * 0.95) - 1] / 1_000_000.0;
        double p99Ms = copy[(int) Math.ceil(n * 0.99) - 1] / 1_000_000.0;
        double minMs = copy[0] / 1_000_000.0;
        double maxMs = copy[n - 1] / 1_000_000.0;
        System.out.printf(
            "[Benchmark] %s: n=%d avg=%.3fms p95=%.3fms p99=%.3fms min=%.3fms max=%.3fms%n",
            label, n, avgMs, p95Ms, p99Ms, minMs, maxMs);
    }
}
