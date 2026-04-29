# Spec 03: 검증 + 점진적 출시 + 폐기

**상위 마일스톤:** Milestone 2 (Neo4j → MySQL CTE 마이그레이션)
**대상 Phase:** Phase 4 + Phase 5
**예상 소요:** 3일 (검증) + 운영 관찰 기간 (기본 1개월)
**선행 spec:** spec-02

---

## 범위

spec-01·02 산출물의 정확성·성능·호환성을 검증하고, 피처 플래그 기반 점진적 전환과 Neo4j 인프라 폐기를 수행한다.

---

## 사전 조건

- spec-01·02 완료
- M1 산출물 활용:
  - 성능 기준선: `docs/benchmark/`
  - Neo4j 결과 스냅샷 (sha256 해시 포함): `shared/benchmark/`
  - QueryTimingAspect (Hibernate Statistics + SimpleMeterRegistry)

---

## Task 4.1 — 정확성 검증 (회귀)

M1 스냅샷을 ground truth로 사용한 회귀 테스트:

```java
@Test
void cteMatchesNeo4jSnapshot() {
    // 실제 스냅샷 구조: 단일 파일 + JSON 키 "conceptId=N,depth=D"
    var snapshot = loadSnapshotEntry(
        "shared/benchmark/neo4j-snapshot-20260424.json",
        "conceptId=6646,depth=5");
    var actual = mysqlConceptRepository.get()
        .findPrerequisiteConceptIds(6646, 5);
    assertThat(actual)
        .containsExactlyInAnyOrderElementsOf(snapshot.conceptIds);
    assertSha256Match(actual, snapshot.sha256);
}
```

검증된 사실 (audit 결과):
- ✓ 스냅샷 위치/명명: `shared/benchmark/neo4j-snapshot-20260424.json` (단일 파일, 날짜 stamp 포함). JSON 최상위 키 = `"conceptId=N,depth=D"`, 값 = `{count, concept_ids[], sha256}`.
- ✓ sha256 계산 로직 (`api/src/test/java/com/mmt/api/performance/GraphQueryPerformanceTest.java:202, 232`): `MessageDigest.getInstance("SHA-256").digest(results.toString().getBytes())`. ⚠ `getBytes()`가 default charset에 의존하므로 환경 차이 가능 — CTE 비교 시 동일 charset 사용 보장.
- ✓ 케이스 커버리지: 실제 스냅샷은 **깊이 2/3/5 × 다수 conceptId**(6646/7595/6420/6784/...) 9+ 케이스. 깊이 0/1 부재. 학교 수준별 분류 아님(특정 conceptId 선정).

추가 검증:
- ConceptService 분기 양쪽(use-mysql-cte true/false)이 동일 스냅샷에 일치하는지 (spec-02 통합 테스트와 중복 가능 — 위치 통합 검토).
- 스냅샷에 conceptId=4979는 부재. select.sql:285 프로토타입의 하드코딩값과 다르므로, 회귀 테스트는 스냅샷 키에 존재하는 conceptId(예: 6646, 7595)를 사용한다.

---

## Task 4.2 — 성능 체크포인트

| 항목 | 허용 기준 | 측정 방법 |
|------|----------|----------|
| CTE 깊이 3 (캐시 미스) | <30ms (p95) | SimpleMeterRegistry + `QueryTimingAspect` |
| CTE 깊이 5 (캐시 미스) | <100ms (p95) | 동상 |
| 캐시 히트 응답 시간 | <5ms | 동상 |
| 캐시 히트율 (warm-up 후 1시간) | >90% | `RedisUtil` 카운터 추가 또는 로그 grep 집계 (ADR 0005) |

> **측정 conceptId 일관성:** M1 baseline은 `conceptId=6646` 단일 ID로 측정됨(`docs/benchmark/milestone-1-baseline.md:40,52-56`). 본 task의 CTE 측정·부하 테스트도 **동일 conceptId(6646)** 를 사용하여 깊이별·경로별 비교가 의미를 가지도록 한다. 추가 ID(7595/6420/6784)는 회귀 보강 케이스로 활용.

### 부하 테스트 시나리오

- ✓ k6 인프라 가용 (`shared/performance-tests/test_concepts_by_chapter.js`, `test_item_by_concept.js`, `k6_docker_commands.sh`). 단 **그래프 쿼리 전용 시나리오는 부재** → 본 spec에서 신규 작성 (예: `test_graph_prerequisite_by_depth.js`).
- conceptId=6646 고정, 깊이 3·5 각각 1,000회 호출, p50/p95/p99 측정
- 캐시 비활성 / 활성 두 조건 비교

### 모니터링 인프라 결정 (ADR 0005에 따라 확정)

ADR 0005에 따라 production 모니터링 인프라(Prometheus + Grafana + Actuator)는 **M3로 분리**한다. 본 spec은 다음만 수행:

- M1의 `SimpleMeterRegistry` + `QueryTimingAspect` 슬로우 쿼리 WARN 로그로 검증 단계 측정
- 부하 테스트 결과·메트릭 dump를 PR 설명에 첨부
- 캐시 히트율은 `RedisUtil`에 카운터 추가 또는 로그 기반 집계로 우회

dashboard·alerting은 비범위.

---

## Task 4.3 — Cytoscape.js 호환

- **대상:** `web/`의 그래프 시각화 컴포넌트가 받는 백엔드 응답
- **검증:** 분기 양쪽에서 응답 DTO의 직렬화 결과가 동일한지 비교

```java
@Test
void cytoscapeResponseDtoIsIdentical() {
    var neo4jResponse = serviceWithFlag(false).getGraphForVisualization(...);
    var cteResponse   = serviceWithFlag(true).getGraphForVisualization(...);
    assertThat(toJson(cteResponse)).isEqualTo(toJson(neo4jResponse));
}
```

검증 결과:
- ✓ 활성 응답 DTO: `ConceptResponse` (`ConceptConverter.convertToFluxConceptResponse` 경유, `findNodesByConceptId`/`findToConcepts`가 `Flux<ConceptResponse>` 반환). `NodeResponse`/`NetworkResponse`는 `ConceptService.java:98-109`의 주석 처리 코드에만 존재 (활성 미사용).
- DTO 구조: `Concept` 도메인을 그대로 변환 (concept_id 등 노드 정보). **엣지 정보는 별도 조회**가 필요 — 본 spec에서 응답 동등성 비교 시 엣지 조회 경로도 검증 대상에 포함.
- 프론트 컴포넌트: `web/src/views/ResultView.vue`, `web/src/views/ConceptView.vue`에서 `cytoscape` + `cytoscape-klay`로 그래프 렌더링. cytoscape 변환 로직은 프론트 측에 위치하므로 백엔드 응답이 동일하면 시각화 동작 보장.

---

## Task 4.4 — BFS 호환

- **대상:** `LogicUtil.bfs(int start, List<Integer> integerList) → Map<Integer, Integer>` (거리 맵)
- **검증:** 분기 양쪽에서 동일한 거리 맵 산출

```java
@Test
void bfsResultIsIdenticalAcrossBranches() {
    // 실제 BFS 호출처(ProbabilityService.java:65-68)와 동일하게 깊이 3 사용
    int conceptId = 6646;  // 스냅샷 키에 존재
    var neo4jIds = serviceWithFlag(false).findNodesIdByConceptIdDepth3(conceptId);
    var cteIds   = serviceWithFlag(true).findNodesIdByConceptIdDepth3(conceptId);

    var neo4jBfs = LogicUtil.bfs(conceptId, neo4jIds.collectList().block());
    var cteBfs   = LogicUtil.bfs(conceptId, cteIds.collectList().block());

    assertThat(cteBfs).isEqualTo(neo4jBfs);
}
```

검증된 사실:
- ✓ BFS 입력 출처: `ProbabilityService.java:65-68`에서 `conceptService.findNodesIdByConceptIdDepth3(conceptId).collectList().block()` 결과를 BFS에 투입. 즉 **깊이 3 탐색 결과 ID 리스트**가 표준 입력. 깊이 5는 BFS에 사용되지 않음.
- BFS 결과 거리 맵: key = 선수 conceptId, value = 시작 노드로부터의 최단 거리. 빠진 노드가 있을 경우 동작은 `LogicUtil.bfs` 구현 확인 후 동등성 검증 케이스에 추가 (입력 리스트가 같다면 동일한 거리 맵이 보장됨).

깊이 5 회귀 보강은 별도 케이스로 추가하되 주 검증 깊이는 3으로 한다.

---

## Task 5.1 — 점진적 트래픽 전환

### ⚠ [검증 필요 — 핵심] 배포 구조 확인

원본 M2 자료의 "10% → 50% → 100% 전환"은 멀티 인스턴스 + 로드 밸런서 환경 가정. 현재 MMT 배포 구조 확인 후 다음 중 채택:

| 옵션 | 조건 | 방법 |
|------|------|------|
| (A) LB 분할 | 멀티 인스턴스 환경 | 인스턴스별로 피처 플래그를 다르게 설정해 트래픽 비율 조절 |
| (B) 서비스 레이어 % 분할 | 단일 인스턴스 | conceptId 또는 사용자 ID 해싱(`hash % 100 < threshold`)으로 % 기반 분기. 피처 플래그를 boolean에서 percentage로 확장 |
| (C) 즉시 전환 + 관찰 연장 | 단일 인스턴스 | 카나리 없이 ON/OFF만, 대신 관찰 기간 1개월 → 2주 + 빠른 롤백 준비 |

권장: 단일 인스턴스 환경이라면 (C). 정적 데이터 + 즉시 롤백 가능 + 영향 범위 제한적이므로 가장 단순하고 충분.

[검증 필요] 현재 배포 환경: AWS 인스턴스 수, LB 사용 여부, 배포 자동화 도구.

### 채택 후 실행 단계

채택안에 따라 본 task의 세부 단계를 ADR로 기록 후 실행. 결정 자체를 ADR로 남기는 이유: 향후 다른 마이그레이션에서 재사용 가능한 정책이 됨.

---

## Task 5.2 — 모니터링 항목

| 지표 | 목표 | 수집 위치 |
|------|------|----------|
| CTE 경로 에러율 | 0% | [검증 필요] 에러 로그 집계 |
| CTE 응답 시간 (p95) | Task 4.2 기준 충족 | QueryTimingAspect 로그 |
| 캐시 히트율 | >90% | `RedisUtil` 카운터 또는 로그 집계 (ADR 0004 + ADR 0005) |
| 진단 결과 페이지 로드 시간 | 회귀 없음 (M1 기준선 대비) | 프론트 측 측정 — [검증 필요: 운영자] 측정 도구·환경 확인 |
| 그래프 시각화 렌더링 정상 여부 | 100% | 프론트 에러 로그 — [검증 필요: 운영자] 로그 인프라 확인 |

ADR 0005에 따라 production 메트릭 dashboard·alerting은 비범위. 본 task의 지표 수집은 모두 로그 기반 + `RedisUtil` 카운터로 우회.

---

## Task 5.3 — Neo4j 폐기 체크리스트

관찰 기간 무사고 후 다음 항목 순차 진행. 각 항목은 별도 커밋(루트 CLAUDE.md "커밋은 Task 단위" 규칙).

### 코드 / 설정 정리

- [ ] 피처 플래그 분기 코드 단순화 (CTE 직접 호출, `if (useMysqlCte && mysqlConceptRepository.isPresent())` 분기 제거)
- [ ] `.block()` 잔존 호출 제거 (spec-02 Task 3.2 후속) — 위치: `ProbabilityService.java:66`, `KnowledgeSpaceService.java:36`
- [ ] ✓ 삭제 대상 클래스: **`ConceptRepository`** (`api/src/main/java/com/mmt/api/repository/concept/ConceptRepository.java`, `extends ReactiveNeo4jRepository<Concept, Integer>`)
- [ ] Neo4j 도메인 엔티티 삭제: `Concept.java:9`의 `@Node("concept")` 어노테이션. M2 이후 MySQL 전용 엔티티로 단순화 (또는 신규 JPA 엔티티 도입)
- [ ] ✓ 의존성 제거: `org.springframework.boot:spring-boot-starter-data-neo4j` (`build.gradle:31`) **+ `org.testcontainers:neo4j` 테스트 의존성 (`build.gradle:62`)**
- [ ] 공용 `application.yml`·`application-test.yml`의 Neo4j 관련 logging level 제거 (`logging.level...neo4j: DEBUG`, `org.springframework.data.neo4j.cypher: DEBUG`). `application-securelocal.yml`은 gitignored이므로 운영자가 수동으로 Neo4j 연결 설정 제거. **공용 yml에 `spring.neo4j.*` 블록은 부재**(이미 정리된 상태) — 별도 작업 불필요
- [ ] 피처 플래그 `mmt.migration.use-mysql-cte-for-graph` 제거 (분기 사라졌으므로) + `MysqlConceptRepository`의 `@ConditionalOnProperty` 제거

### 인프라 정리

- [ ] `docker-compose.yml`에서 `mmt-neo4j` 컨테이너 제거 — **ADR 작성 필수** (루트 CLAUDE.md "docker-compose.yml의 서비스 구성은 ADR 없이 변경 금지")
- [ ] [검증 필요] AWS Neo4j 인스턴스 종료 — 운영 권한 보유자 확인
- [ ] Neo4j 커스텀 Docker 이미지(`mymathteacher/mmt-neo4j:1.0.0`) Docker Hub 정리 정책 결정 (즉시 삭제 / 보관)

### M1 산출물 정리

- [ ] M1에서 추가한 Testcontainers Neo4j 설정 제거 (테스트 시간 단축)
- [ ] M1 결과 스냅샷(`shared/benchmark/`)은 회귀 검증 자료로 **유지** (재마이그레이션 시 참조)

### 문서

- [ ] Neo4j 도입 배경(v1)과 폐기 사유(M2)를 함께 기록한 ADR 작성
- [ ] 루트 CLAUDE.md 업데이트: 모노레포 구조에서 `neo4j/` 디렉토리의 위상 변경 (또는 디렉토리 삭제)
- [ ] `docs/roadmap.md`에서 M2 완료 표시

### 롤백 안전망

- [ ] 컨테이너 제거 전 마지막 단계: 1 릴리즈 주기 동안 Neo4j 컨테이너를 docker-compose 주석 처리만 해두고 코드는 살려둠
- [ ] [검증 필요] "1 릴리즈 주기"의 정의. 프로젝트의 릴리즈 주기 규약이 없다면 캘린더 기반(예: 14일)으로 고정

**중간 롤백 시나리오:**
- 컨테이너 제거 전: 피처 플래그 `false`로 즉시 복귀
- 컨테이너 제거 후 코드 잔존: docker-compose 주석 해제 + 코드 revert
- 코드 제거 후: git revert 커밋 후 재빌드·재배포 (~30분)

---

## 완료 기준

- [ ] Task 4.1~4.4 모두 통과
- [ ] Task 5.1 채택안 결정 + 실행 완료 (ADR 기록)
- [ ] 관찰 기간 무사고 (기본 1개월, [검증 필요] 단축/연장 기준)
- [ ] Task 5.3 체크리스트 모두 완료
- [ ] Neo4j 폐기 ADR 작성
- [ ] 루트 CLAUDE.md / roadmap.md 업데이트

---

## 비범위 (다른 spec/마일스톤에서 처리)

- CTE 메서드 자체 → spec-01
- 캐싱 / 분기 / `.block()` 정리 → spec-02
- production 모니터링 인프라 (Prometheus·Grafana, dashboard, alerting) → **M3로 분리 (ADR 0005)**

---

## 참조

- ADR 0001: 마이그레이션 전 테스트 커버리지 선행 구축
- ADR 0002 §3: Hibernate Statistics 기반 N+1 검증 (JPA 한정)
- ADR 0003: knowledge_space 엣지 방향성 (CTE backward 단일안)
- ADR 0004: M2 캐싱 패턴 (RedisUtil 직접 호출)
- ADR 0005: M2 모니터링 인프라 M3 분리
- M1 산출물: `docs/benchmark/milestone-1-baseline.md`, `shared/benchmark/neo4j-snapshot-20260424.json`
