// M2 spec-03 Task 4.2b: 그래프 선수개념 조회 endpoint 의 부하·지연 측정.
//
// spec-03 L83/L87:
//   - conceptId 는 M1 baseline 과 동일한 6646 (스냅샷 representative_id) 고정.
//   - 1,000 회 호출 후 p50/p95/p99 측정.
//   - 캐시 비활성 / 활성 두 조건 비교.
//
// endpoint:
//   GET /api/v1/concepts/nodes/{conceptId}
//     → ConceptService.findNodesByConceptId(conceptId)
//        conceptId 의 school_level 이 "초등" 이면 depth=3, 그 외엔 depth=5 로
//        내부에서 결정 (ConceptService.java:96). depth 별 직접 측정은
//        CteGraphQueryPerformanceTest 에서 수행 — k6 는 endpoint 레벨 부하만.
//
// 모드 분기 (env 변수):
//   CACHE_MODE=warm (기본)
//     첫 호출이 miss, 이후 호출은 Redis cache hit. spec-03 L80 "캐시 히트
//     응답 시간 <5ms" 검증.
//   CACHE_MODE=cold
//     매 iteration 직전에 admin endpoint 로 graph:* prefix 캐시 무효화 →
//     모든 응답이 cache miss. spec-03 L78 "CTE 깊이 N (캐시 미스)" 와 비교
//     가능하나 endpoint 레벨이므로 service 오버헤드 포함.
//     ADMIN_TOKEN env 필수 (SecurityConfig 의 /admin/** → hasRole("ADMIN")).
//
// 실행:
//   docker run --rm -v $(pwd):/scripts -v $(pwd)/results:/results \
//     -e CACHE_MODE=warm \
//     -i grafana/k6 run /scripts/test_graph_prerequisite_by_depth.js
//
//   docker run --rm -v $(pwd):/scripts -v $(pwd)/results:/results \
//     -e CACHE_MODE=cold -e ADMIN_TOKEN='Bearer eyJ...' \
//     -i grafana/k6 run /scripts/test_graph_prerequisite_by_depth.js
//
// 결과 첨부:
//   PR 설명에 두 모드의 p50/p95/p99 표 + http_req_duration trend 첨부 (ADR 0004 —
//   production dashboard 비범위, PR 본문이 1차 기록).

import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';

const CONCEPT_ID = 6646;
const BASE_URL = 'http://host.docker.internal:8080';
const CACHE_MODE = (__ENV.CACHE_MODE || 'warm').toLowerCase();
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN || '';

// 단일 VU, 1000 회 직렬 호출 — 캐시 모드 비교가 목적이며 동시성은 부가 관심사가
// 아니므로 변동 요인을 최소화. 동시성 부하는 별도 시나리오에서 다룬다.
export const options = {
    scenarios: {
        prerequisite_load: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1000,
            maxDuration: '5m',
        },
    },
    thresholds: {
        http_req_failed:   ['rate<0.01'],
        // warm 모드의 p95 는 spec-03 L80 "캐시 히트 응답 시간 <5ms" 기준.
        // cold 모드는 endpoint 레벨이라 service 오버헤드 포함 — 별도 기준 없음.
        // 두 모드를 한 시나리오에서 단정하기 어려우니 threshold 는 가벼운 가드만.
        http_req_duration: ['p(95)<200'],
    },
};

const prerequisiteLatency = new Trend('prerequisite_latency_ms');

export function setup() {
    if (CACHE_MODE === 'cold' && !ADMIN_TOKEN) {
        fail('CACHE_MODE=cold requires ADMIN_TOKEN env (e.g. "Bearer eyJ...")');
    }
    return { mode: CACHE_MODE };
}

export default function (data) {
    if (data.mode === 'cold') {
        // 매 iteration 직전 graph:* 캐시 무효화. invalidate 호출 자체의 시간은
        // prerequisite_latency_ms trend 에 포함되지 않도록 별도 호출.
        const invalidate = http.post(
            `${BASE_URL}/admin/cache/graph/invalidate`,
            null,
            { headers: { Authorization: ADMIN_TOKEN } }
        );
        check(invalidate, { 'invalidate 200': (r) => r.status === 200 });
    }

    const res = http.get(`${BASE_URL}/api/v1/concepts/nodes/${CONCEPT_ID}`);
    prerequisiteLatency.add(res.timings.duration);

    check(res, {
        'status 200':       (r) => r.status === 200,
        'response not empty': (r) => r.body && r.body.length > 0,
    });
}
