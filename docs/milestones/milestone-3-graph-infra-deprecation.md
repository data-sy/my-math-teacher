# Milestone 3: 그래프 인프라 폐기 + 운영 출시 정책

**브랜치 정책:** spec 단위로 분할 (M2 와 동일)
- `feat/m3-spec-01-gradual-rollout` — Phase 1 (점진 전환 정책 ADR + 플래그 ON)
- `feat/m3-spec-02-deprecation` — Phase 2 (관찰 완료 후 Neo4j 인프라·코드 폐기)

**예상 소요:** 1일 (출시) + 관찰 기간 (기본 1개월) + 2일 (폐기)
**의존성:** Milestone 2 (Neo4j → MySQL CTE 마이그레이션) 의 검증 단계(spec-03 Task 4.x) 완료
**위험 수준:** 낮음 — 검증·롤백 안전망이 M2 에서 모두 확보됨
**선행 조건:** M2 spec-03 의 회귀·성능·시각화·거리 맵 검증 모두 통과, 피처 플래그 OFF 상태에서 정상 동작 확인

---

## 목표

M2 에서 검증된 MySQL 재귀 CTE 경로를 운영 환경에 점진 적용하고, 무사고 관찰 기간 후 Neo4j 인프라와 관련 코드를 일괄 제거한다. 본 마일스톤이 완료되면 그래프 탐색 책임은 MySQL 단일 RDB 로 완전 이전된다.

---

## 왜 M2 에서 분리됐는가

원래 M2 spec-03 의 Phase 5 로 묶여 있었으나 다음 사유로 분리한다:

- **시간 척도가 다르다.** Phase 1~4(검증)는 코드 작업으로 며칠 단위, Phase 5 는 관찰 1개월 + 폐기 작업이라 한 마일스톤·한 PR 로 묶기에 부적합.
- **결정 시점이 다르다.** 폐기는 관찰 결과를 입력으로 받는다 — "관찰 완료" 라는 사건이 트리거. 검증 단계와 같은 PR 단위로 갈 수 없다.
- **포트폴리오 컨텍스트.** 본 프로젝트는 운영 서비스가 아니므로 출시·관찰·폐기는 실제로는 수행되지 않을 수 있다. M2 를 "검증된 마이그레이션" 으로 닫고 M3 를 운영 단계로 분리하면 각 마일스톤이 closed scope 을 가진다.

---

## 구성 spec

### spec-01: 점진 출시 + ADR 0006 (Phase 1)

- 배포 환경 최종 확인 (단일 인스턴스 가정 검증) — 멀티 인스턴스로 판명 시 채택안 (A) LB 분할로 변경
- ADR 0006 작성: 점진 전환 옵션 (C) "즉시 전환 + 관찰 연장" 채택 정책 기록
- 피처 플래그 `mmt.migration.use-mysql-cte-for-graph=true` 로 전환
- 즉시 롤백 경로 확인 (플래그 `false` 복귀)

### spec-02: 관찰 후 폐기 + ADR 0007 (Phase 2)

관찰 기간(기본 1개월, 단축 기준은 ADR 0006 에 동반 기록) 무사고 통과 후 진행. 각 항목은 별도 커밋.

**코드 정리**
- 피처 플래그 분기 코드 단순화 (CTE 직접 호출만 남김)
- `.block()` 잔존 호출 제거 — 현행 위치: `KnowledgeSpaceService.java:39`, `ConceptService.java:164`
- Neo4j 그래프 탐색 리포지토리 삭제 (`ConceptRepository` — `ReactiveNeo4jRepository`)
- 도메인 엔티티의 Neo4j 어노테이션 제거 (`Concept.java:9` `@Node`)
- `application.yml` / `application-test.yml` 의 Neo4j 관련 logging 제거
- 피처 플래그 자체 제거 (분기 사라졌으므로)
- `LogicUtil.bfs` + 테스트 삭제 (Neo4j 분기 의존이 사라지므로 함께 제거 — spec-02 task 3.4 결정 후속)

**의존성 / 빌드**
- `org.springframework.boot:spring-boot-starter-data-neo4j` 제거
- `org.testcontainers:neo4j` 제거
- M1 Testcontainers Neo4j 설정 제거 (테스트 시간 단축)

**인프라**
- `docker-compose.yml` 에서 `mmt-neo4j` 컨테이너 제거 — **ADR 0007 동반 필수** (루트 CLAUDE.md "docker-compose.yml 의 서비스 구성은 ADR 없이 변경 금지")
- AWS Neo4j 인스턴스 종료 (운영자 작업)
- Neo4j 커스텀 Docker 이미지(`mymathteacher/mmt-neo4j:1.0.0`) 정리 정책 결정

**문서**
- ADR 0007 통합본 작성: Neo4j 도입 배경(v1) + 폐기 사유(M2) + docker-compose 변경을 한 ADR 에
- 루트 CLAUDE.md / api/CLAUDE.md 갱신 (Neo4j 언급 제거)
- `docs/roadmap.md` 에 M2 / M3 완료 표시

---

## 롤백 안전망

| 단계 | 롤백 방법 | 소요 |
|------|----------|------|
| 컨테이너 제거 전 | 피처 플래그 `false` 로 즉시 복귀 | 1분 |
| 컨테이너 제거 후, 코드 잔존 | docker-compose 주석 해제 + 코드 revert | 10분 |
| 코드 제거 후 | git revert 커밋 + 재빌드·재배포 | ~30분 |

> 컨테이너 제거 전 1 릴리즈 주기 동안 docker-compose 의 `mmt-neo4j` 를 주석 처리만 해두고 코드는 살려두는 것을 권장 — 만일에 대비한 회복 경로 보존.

---

## 완료 기준

- [ ] ADR 0006 작성 — 점진 전환 정책
- [ ] 피처 플래그 ON 후 관찰 기간 무사고 (캐시 히트율 >90%, 에러율 0%, 응답 시간 회귀 없음)
- [ ] 그래프 탐색 코드에서 Neo4j 분기 완전 제거
- [ ] Neo4j 관련 빌드 의존성·설정·도메인 어노테이션 제거
- [ ] `docker-compose.yml` 에서 `mmt-neo4j` 제거
- [ ] AWS Neo4j 인스턴스 종료
- [ ] ADR 0007 통합본 작성
- [ ] 루트 CLAUDE.md / api/CLAUDE.md / roadmap.md 갱신

---

## 비범위 (다른 마일스톤)

- CTE 메서드 자체 → M2 spec-01
- 캐싱 / 분기 / `.block()` 일부 정리 → M2 spec-02
- 정확성·성능·호환성 검증 → M2 spec-03
- production 모니터링 인프라 (Prometheus, Grafana, Actuator, dashboard, alerting) → 별도 후속 마일스톤 (ADR 0004 — 본래 M3 로 분리 예정이었으나 본 마일스톤이 그래프 폐기로 좁혀짐에 따라 후속 마일스톤으로 이관)

---

## 참조

- M2: Neo4j → MySQL CTE 마이그레이션 (검증 단계까지)
- M2 spec-03: 검증 단계 spec (본 마일스톤의 직접 선행)
- ADR 0003: M2 캐싱 패턴
- ADR 0004: 운영 모니터링 인프라 분리 정책
- ADR 0006 (예정): 점진 전환 옵션 (C) 채택
- ADR 0007 (예정): Neo4j 폐기 통합본
