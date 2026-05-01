# Milestone 2: Neo4j → MySQL CTE 마이그레이션

**브랜치:** `feat/migrate-neo4j-to-mysql-cte`
**예상 소요:** 8일 (구현) + 운영 관찰 기간 (기본 1개월)
**의존성:** Milestone 1 (테스트 인프라 및 기준선 구축) 완료
**위험 수준:** 중간
**선행 조건:** Milestone 1의 모든 완료 기준 충족, M1 산출물(기준선·결과 스냅샷·피처 플래그 구조) 가용

---

## 목표

Neo4j 그래프 데이터베이스를 제거하고 MySQL Recursive CTE로 대체한다. 이를 통해:

- AWS 비용 절감 (~월 $50-100) — [검증 필요] 실제 인스턴스 비용
- 데이터 중복(MySQL ↔ Neo4j) 근본 해소
- 운영 복잡도 감소 (관리 DB 1개 감소, docker-compose 단순화)
- 리액티브 안티패턴(`.block()` 호출) 자연 해소

## 왜 이 마일스톤이 최우선인가

- 가장 높은 AWS 비용 절감 효과
- 변경 범위가 좁음 (리포지토리 1개, 그래프 메서드 군 한정)
- 데이터 중복 문제의 근본 해결
- 배포 단순화 (docker-compose에서 Neo4j 컨테이너 제거)

---

## 현재 상태 요약

### Neo4j 사용 범위

**단일 유즈케이스:** 맞춤형 학습 경로를 위한 선수 개념 그래프 탐색

```
전체 리포지토리 클래스 22개 (도메인 11개; 대부분 JPA + JdbcTemplate 페어) 중
└── Neo4j (Reactive): 1개 — ConceptRepository (그래프 탐색 Cypher 쿼리)
```

- **데이터 규모:** 1,631 노드 / 3,446 엣지 — [검증 필요] 실제 production 카운트
- **호출 빈도:** 진단 결과 페이지 로드 시에만 (낮음) — [검증 필요]
- **데이터 특성:** 읽기 전용, CSV 초기 로드 후 런타임 변경 없음
- **핵심 문제:** `Concept` 데이터가 MySQL과 Neo4j에 중복 저장

### M1에서 준비된 자산 (활용 대상)

- 성능 기준선: `docs/benchmark/`
- Neo4j 결과 스냅샷 (sha256 해시 포함): `shared/benchmark/`
- 피처 플래그: `mmt.migration.use-mysql-cte-for-graph` (ADR 0002 §1 네임스페이스 준수)
- ConceptService.findNodesIdByConceptIdDepth3 분기 시범 적용
- QueryTimingAspect (Micrometer Timer + AOP) — 리포지토리 쿼리 시간 측정·슬로우 쿼리 WARN
- Hibernate Statistics 기반 N+1 검증 인프라 (JPA 한정, ADR 0002 §3)
- SimpleMeterRegistry 수동 등록 (ADR 0002 §4)

---

## 성능 허용 기준

| 항목 | M1 기준선 (Neo4j) | CTE 허용치 | 비고 |
|------|-------------------|-----------|------|
| 깊이 3 (초등학교) | p95 14.034 ms (M1 baseline) | <30ms (p95) | 약 2.1배 허용 |
| 깊이 5 (고등학교) | p95 12.898 ms (M1 baseline) | <100ms (p95) | 약 7.8배 허용 |
| 캐시 히트율 | — | >90% (warm-up 후) | [검증 필요] 90% 산출 근거 |
| 캐시 히트 시간 | — | <5ms | Redis 기준 |

> 비교 일관성: M1 baseline은 **conceptId=6646 단일 ID**로 측정됐다(`docs/benchmark/milestone-1-baseline.md:40,52-56`). spec-03 Task 4.2의 CTE 회귀·부하 측정도 동일 conceptId를 사용해야 깊이 비교가 의미를 가진다. 깊이 5의 p95(12.898ms)가 깊이 3의 p95(14.034ms)보다 작은 것도 이 단일 ID 측정에 따른 서브그래프 특성 + JIT warmup 영향이며, 동일 ID로만 회귀를 비교하면 영향 상쇄.

성능 데이터 원본은 `docs/benchmark/`(M1 산출물)을 참조한다.

---

## 구성 spec

본 마일스톤은 다음 3개 spec으로 분해된다. 각 spec은 별도 세션에서 실행한다.

- **spec-01: CTE 리포지토리 + 인덱스** (Phase 1, 2일)
  데이터 레이어 한정. JdbcTemplateConceptRepository에 재귀 CTE 메서드와 인덱스 도입.
- **spec-02: 서비스 통합 + 캐싱 + 리액티브 정리** (Phase 2 + 3, 3일)
  ConceptService 그래프 메서드 군에 분기·캐싱·`.block()` 정리 적용.
- **spec-03: 검증 + 점진적 출시 + 폐기** (Phase 4 + 5, 3일 + 관찰 기간)
  정확성·성능·호환성 검증과 Neo4j 인프라 폐기.

---

## 위험 및 완화

| 위험 | 가능성 | 영향 | 완화 |
|------|--------|------|------|
| CTE 성능 미달 (깊이 5) | 중간 | 중간 | Redis 캐싱(>90% 히트율 목표), 복합 인덱스 |
| 캐싱 레이어 신규 도입 | 낮음 | 낮음 | RedisUtil 직접 호출 채택(ADR 0003)으로 영향 최소화 — Spring Cache 인프라 미도입 |
| 단일 인스턴스 환경에서 점진 전환 한계 | 중간 | 중간 | spec-03 Task 5.1에서 3가지 안 검토 후 채택 |
| Cytoscape.js 호환성 | 낮음 | 중간 | 그래프 응답 DTO 형태 동등성 테스트 |
| BFS 알고리즘 회귀 | 낮음 | 중간 | LogicUtil.bfs 입력 동등성 테스트 |
| 데이터 정합성 (전환 기간) | 낮음 | 높음 | 정적 데이터 + 피처 플래그 즉시 롤백 |
| `.block()` 잔존 호출 누락 | 중간 | 낮음 | spec-02에서 전수 조사 후 spec-03에서 제거 |

---

## 기대 효과

- **비용:** AWS Neo4j 호스팅 제거 (~월 $50-100 절약)
- **운영:** 유지보수·백업·모니터링 대상 DB 1개 감소
- **일관성:** MySQL ↔ Neo4j 데이터 중복 문제 근본 해결
- **배포:** docker-compose 단순화 (Neo4j 컨테이너 제거)
- **기술 부채:** 리액티브 블로킹(`.block()`) 안티패턴 자연 해소

---

## 완료 기준

- [ ] `ConceptRepository`의 그래프 메서드 6개를 모두 CTE로 대체 (매핑 표는 spec-01 사전 조건 참조)
- [ ] ConceptService 그래프 메서드 5개 + KnowledgeSpaceService 호출 2지점 모두 피처 플래그 분기 적용 (spec-02 — KnowledgeSpaceService는 ConceptService 경유 리팩토링 후 분기 흡수해도 동등)
- [ ] 캐시 히트율 >90% 확인 (spec-03)
- [ ] CTE 결과와 M1 Neo4j 스냅샷 정확성 100% 일치
- [ ] 모든 CTE 쿼리가 성능 허용 기준 충족
- [ ] 그래프 시각화(Cytoscape.js) / BFS 알고리즘 호환 확인
- [ ] 피처 플래그로 즉시 롤백 가능
- [ ] 운영 1개월 무사고 — [검증 필요] 모니터링 인프라 가용성
- [ ] Neo4j 인프라 완전 제거 (코드·의존성·컨테이너·AWS 인스턴스)
- [ ] Neo4j 폐기 ADR 작성 (도입 배경과 폐기 사유 함께 기록)

---

## 참조

- ADR 0001: 마이그레이션 전 테스트 커버리지 선행 구축
- ADR 0002: M1 구현 컨벤션 묶음 (특히 §1 피처 플래그 네임스페이스 준수 필수)
- ADR 0003: M2 캐싱 패턴 (RedisUtil 직접 호출, Spring Cache 미도입)
- ADR 0004: M2 모니터링 인프라는 M3로 분리, M2는 SimpleMeterRegistry 검증 단계만
- ADR 0005: CTE 객체 반환 메서드 도메인 매핑 (`concepts JOIN chapters`)
- spec-01 데이터 모델 노트: `knowledge_space` 엣지 방향성 정의 (M2 전 SQL의 의미 기준)
- M1 산출물: `docs/benchmark/`, `shared/benchmark/`
- 루트 CLAUDE.md: Analyze-Before-Change 패턴 + 피처 플래그 정책
