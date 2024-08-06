import http from 'k6/http';
import { check } from 'k6';

export let options = {
    stages: [
        { duration: '1m', target: 500 }, // 1분 동안 사용자 200명까지 증가
        { duration: '3m', target: 500 }, // 3분 동안 200명의 사용자 유지
        { duration: '1m', target: 0 },   // 1분 동안 사용자 0명으로 감소
    ],
};

export default function () {
  let chapterId = 500;
  let res = http.get(`http://host.docker.internal:8080/api/v1/concepts?chapterId=${chapterId}`);
  check(res, {
    'is status 200': (r) => r.status === 200, // 응답 상태 코드가 200인지 확인
    'response time < 500ms': (r) => r.timings.duration < 500, // 응답 시간이 500ms 미만인지 확인
  });
}

