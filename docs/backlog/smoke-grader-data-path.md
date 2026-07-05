# [M4 후속] smoke grader 를 데이터경로까지 검증하도록 강화 (spec-02 G4)

## 배경

§4 1차 라이브(2026-07-05)에서 배포 smoke 는 `/api/v1/health`(200, 의존성 미검사)만 봐서
**Redis 크로스인스턴스 캐시 결함으로 데이터 엔드포인트가 401 을 응답하는데도 통과**했다.
`/health` 는 sub-ms 로 뜨지만 실제 데이터경로(CTE+캐시)는 깨져 있던 상황 — **헬스게이트가
데이터경로를 미검증**하는 사각지대다. (캐시 결함 자체는 `72d70f7` 로 수정됨.)

`switch-backend.sh` 의 컷오버 헬스폴(`HEALTH_PATH`, 기본 `/api/v1/health`)도 같은 한계:
green 이 헬시로 판정돼 flip 됐지만 데이터경로는 오염일 수 있다.

## 남은 작업

- [ ] **배포 smoke grader**: 단순 `/health` 200 이 아니라 **대표 데이터 엔드포인트**
      `GET /api/v1/concepts/nodes/7925` 가 **non-empty**(단순 200 아님, R4) 인지 검증하는
      게이트 추가. (permitAll 이라 JWT 불필요.)
- [ ] **컷오버 헬스폴 강화 검토**: `switch-backend.sh` 의 idle 색 헬스폴을 데이터 엔드포인트로
      승격할지 결정. 트레이드오프 = 부팅 직후 캐시 워밍/CTE 첫쿼리 지연으로 헬스 판정이
      늦어질 수 있음(현 HEALTH_RETRIES=30×5s 여유로 흡수 가능한지 확인).
- [ ] spec-02 §4 **G4 smoke grader** 정의와 정합 맞추기(검수자 2층 — R1 기동 + R4 데이터).

## 경계

- 라이브(재배포)로만 재검증 가능 → 다음 apply 세션에서 CPU_LIMIT 배선과 함께.
- 이 강화는 **관측/게이트 개선**이지 앱 로직 변경 아님 — switch-backend.sh·워크플로 스코프.
