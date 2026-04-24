import http from 'k6/http';
import { check } from 'k6';

export let options = {
    stages: [
        { duration: '1m', target: 500 }, // 1분 동안 사용자 n명까지 증가
        { duration: '3m', target: 500 }, // 3분 동안 n명의 사용자 유지
        { duration: '1m', target: 0 },   // 1분 동안 사용자 0명으로 감소
    ],
};

export default function () {
  let conceptId = 1009;

//   // 기존 API 테스트
//   let res0 = http.get(`http://host.docker.internal:8080/api/v1/perf-test/originalQuery?conceptId=${conceptId}`);
//   check(res0, {
//       'API 0: status was 200': (r) => r.status === 200,
//       'API 0: response time < 500ms': (r) => r.timings.duration < 500,
//   });

  // 개선1 API 테스트
  let res1 = http.get(`http://host.docker.internal:8080/api/v1/perf-test/javaSort?conceptId=${conceptId}`);
  check(res1, {
      'API 1: status was 200': (r) => r.status === 200,
      'API 1: response time < 500ms': (r) => r.timings.duration < 500,
  });

//   // 개선2 API 테스트
//   let res2 = http.get(`http://host.docker.internal:8080/api/v1/perf-test/javaRandomFetch?conceptId=${conceptId}`);
//   check(res2, {
//       'API 2: status was 200': (r) => r.status === 200,
//       'API 2: response time < 500ms': (r) => r.timings.duration < 500,
//   });

//   // 개선3 API 테스트
//   let res3 = http.get(`http://host.docker.internal:8080/api/v1/perf-test/dbOptimized?conceptId=${conceptId}`);
//   check(res3, {
//       'API 3: status was 200': (r) => r.status === 200,
//       'API 3: response time < 500ms': (r) => r.timings.duration < 500,
//   });

}

