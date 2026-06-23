# MMT Roadmap

MMT 프로젝트의 중장기 작업 계획. 세부 실행 지시는 각 마일스톤·spec 문서를 참조.

---

## Now — 진행 중

- **[M2] Neo4j → MySQL CTE 마이그레이션 — 검증 단계 완료** (PR [#22](https://github.com/data-sy/my-math-teacher/pull/22))
  - 그래프 탐색 쿼리를 MySQL 재귀 CTE로 이전, 결과 동등성·성능·시각화·거리 맵 의미 보존 검증 완료
  - 회수: depth 3 p95 14.034ms (Neo4j) → 0.556ms (CTE), 약 25배 개선
  - 결과 보고: `docs/reports/m2-cte-migration.md`
  - 점진 출시·관찰·폐기는 **M3** 로 분리 (포트폴리오 컨텍스트로 실제 폐기 수행은 보류 가능)

---

## Next — 다음 분기 (착수 예정)

- **[M3] 그래프 인프라 폐기 + 운영 출시 정책**
  - M2 검증된 CTE 경로를 피처 플래그 ON 으로 점진 출시, 관찰 후 Neo4j 인프라·코드 일괄 폐기
  - ADR 0006 (점진 전환 정책) + ADR 0007 (Neo4j 폐기 통합본) 작성
  - 본 프로젝트는 운영 서비스가 아니므로 실제 수행은 선택적 — 마일스톤 문서로 의사결정·체크리스트만 정의 완료
  - 문서: [`docs/milestones/milestone-3-graph-infra-deprecation.md`](milestones/milestone-3-graph-infra-deprecation.md)

- **[Epic] JdbcTemplate → JPA 전환**
  - 레거시 JdbcTemplate 기반 코드를 JPA로 점진적 마이그레이션
  - 레포지토리 단위로 쪼개어 복수 마일스톤으로 분할 예정

---

## Later — 백로그 (아직 미착수, 검토 단계)

- **[Infra] RedisUtil value serializer isolation 리팩토링** (M2 spec-02 PR [#21](https://github.com/data-sy/my-math-teacher/pull/21) 에서 발견)
  - `RedisUtil.set` 이 호출마다 `RedisTemplate.setValueSerializer` 를 클래스 단위로 교체 → 다른 호출의 deserialize 와 충돌 (`ClassCastException`). M2 spec-02 에서는 통합 테스트 cleanup (`deleteByPrefix`) 으로 우회.
  - 범위: 호출 단위 serializer isolation 또는 통합 serializer 도입. ADR 작성 필요.
  - 완료 시 `ConceptServiceFeatureFlagTest.WhenFlagTrue` 의 `@BeforeEach cleanGraphCache` 우회 코드 제거 가능.
- **[Infra] Testcontainers Redis 자동 ServiceConnection** (Spring Boot 3.2+ 업그레이드 의존)
  - M2 spec-02 에서 G2 로 검토했으나 Spring Boot 3.1.6 의 Redis `@ServiceConnection` 미지원으로 보류. 3.2+ 에서 자동 지원.
  - 범위: Spring Boot 업그레이드 후 `TestcontainersConfig` 에 Redis 컨테이너 추가, `application-test.yml` 동적 주입. 로컬 `mmt-redis` 의존 없이 CI 친화 통합 테스트.
- **[Infra] 프로파일·로깅 위생 정리** (M1 Spec 03 에서 관찰됨)
  - `application.yml` 의 `spring.profiles.include: securelocal` 을 `!test` 프로파일 조건부로 전환 → 현재 `@ActiveProfiles("test")` 만으로는 securelocal 이 배제되지 않아 테스트가 `@Import(TestcontainersConfig.class)` 강제됨 (Spec 03 작업 규칙 2 "프로파일 독립성" 미완)
  - `application-prod.yml` · `application-local.yml` 도입으로 4 프로파일 (default/local/test/prod) 체계 완성
  - `application.yml` 의 공통 `com.mmt` · `springframework.data.neo4j` · `springframework.security` DEBUG 로거를 프로덕션에서 INFO 로 하향 (M1 Spec 03 Task 3.3 에서 관찰)
  - 완료 시 `FeatureFlagIntegrationTest` 류가 Testcontainer import 없이도 기동
- DKT 모델 서빙 파이프라인 재검토 (현재 TensorFlow Serving 고정)
- 프론트엔드(`web/`) 상태 관리·빌드 시스템 현대화
- CI/CD 파이프라인 정비 및 배포 자동화
- 모니터링·알림 체계 구축 (현재 Grafana+Prometheus 기반 확장)
- `shared/` 내부 구조 정리 (`diagrams/`, `scripts/`, `data/` 분리 — 필요시)
- **[Product] 맞춤학습지 비율 배분 출제** (Scope B 후속, 2026-06-23) — count 를 맞춤유형/재출제 비율(예 선수지식:일반 7:3)로 자리 배분하고 버킷별 상한·보충. 현재는 우선순위 tier+spill 우회안으로 출시됨. Scope B spec §향후 개선 참조.
- **[Research] 맞춤 출제 전용 알고리즘 조사·학습** (2026-06-23) — 현재 출제 로직은 휴리스틱(depth 우선순위 tier + round-robin fill). 학습자 약점 기반 문항 추천에는 전용 분야가 있을 가능성: **추천 시스템(recommender systems), 문항반응이론(IRT)·컴퓨터적응검사(CAT), 지식추적(Knowledge Tracing) 기반 문항 선택**. 당장 구현 아님 — 공부 후 적용 가능하면 비율 배분/Scope B 출제 로직을 원리 기반으로 대체 검토. (DKT 모델이 이미 있으므로 KT 계열과의 연계가 자연스러울 수 있음.)
- **[Design] 실배포 전 리디자인** (전문가 페르소나 컨설팅 2건 수렴, 2026-06-23)
  - 2년 묵은 디자인으로 실서비스 배포를 앞두고 정리. 리포트: `docs/consulting/out/design-ux-report.md`, `react-migration-scope.md`. **결론: launch 까지 Vue 유지 + 리디자인도 Vue 에서 + React 이주는 launch 후 재검토.**
  - 이월 UI 항목:
    - 카피 일제 정리 (영문·오타 → 한국어 일관)
    - 그래프 응급 접근성 (노드 크기·호버→클릭 선택·색 대비)
    - DiagView 비로그인 다운로드 다이얼로그 **"회원가입 및 로그인" 버튼 라벨/동선 불일치** — 현재 `goToSignup`(회원가입만) 라우팅, 로그인 폼 없음. 로그인 기능 자체는 정상(전역 토프바 AppTopbar 다이얼로그: 폼 + OAuth). 로그인/회원가입 진입 동선 재설계 시 라벨 정리.
    - ~~`listboxTest` 무가드 역참조 잔여 (DiagView 다운로드 다이얼로그 ~355·371행)~~ — ✅ `fa991d0` 처리 완료(브랜치 `feat/pre-launch-redesign`). PersonalView `d15f6aa` 와 동일 패턴: watch 해제 시 게이트 초기화 + 두 Dialog 옵셔널 체이닝. **런타임 풀스택 수동검증 PASS**(비로그인 /diagnosis 선택→해제 회귀, 크래시 없음). PR 대기.

---

## Done — 완료

- **[Product] 맞춤학습지 조건부 출제 (Scope B)** — 2026-06-23 완료 (브랜치 `feat/personalview-conditional-items-scope-b`, PR 진행)
  - PersonalView 맞춤 유형(오답/선수지식 위주)·재출제(없음/오답/전체)·문항수 라디오가 `ItemService.findPersonalItems` 를 제어. (Scope A 기본 정책은 PR #24 `5623ffc` 출시 완료.)
  - 알고리즘: 맞춤유형="위주"=**우선순위 tier**(오답위주 depth0→depth1~2 spill / 선수지식위주 depth1~2→depth0 spill), 재출제=원본 응시문항 재포함, **count=목표 총 문항수**(6~30) — 원본 → tier 순서 round-robin 으로 count 까지 채움(depth≤2 전체 소진 시 미달 허용), 파라미터 옵셔널=레거시 하위호환.
  - **그래프 무의존**: `probabilities.to_concept_depth`(사전계산)만 사용 → Neo4j/CTE 직접 의존 0, M3 비차단.
  - 검증: 단위테스트(tier·spill·dedup·clamp·원본 산입·하위호환·IDOR) + 풀스택 런타임(브라우저 UI 포함) 통과.
  - spec: [`docs/specs/product/spec-01-personalview-conditional-items-scope-b.md`](specs/product/spec-01-personalview-conditional-items-scope-b.md). 후속: 비율 배분 출제·맞춤 출제 알고리즘 조사(Later 백로그).
- **[M1] 테스트 인프라 및 기준선 구축** — 2026-04-24 완료 (PR [#11](https://github.com/data-sy/my-math-teacher/pull/11), [#12](https://github.com/data-sy/my-math-teacher/pull/12))
  - [milestone](milestones/milestone-1-test-infrastructure.md)
  - Testcontainers 기반 통합 테스트 (MySQL 8 + Neo4j 5.12), 테스트 전용 `application-test.yml` 프로파일, JPA N+1 감지 (Hibernate Statistics)
  - 성능 기준선 실측 (warmup 3 + 측정 100 회, avg/p95/p99) — 7 개 연산을 `docs/benchmark/milestone-1-baseline.md` 에 기록
  - Neo4j 그래프 결과 sha256 스냅샷 (`shared/benchmark/neo4j-snapshot-20260424.json`) — M2 동치성 비교용
  - 회귀 감지 테스트 2 종 (`shouldNotRegress*`, warmup 20 + median 30 기반)
  - 피처 플래그 구조 `mmt.<영역>.<설정>` 도입 (`mmt.migration.*`, `mmt.observability.*`, `mmt.benchmark.baseline.*`)
  - `MysqlConceptRepository` 스텁 + `ConceptService.findNodesIdByConceptIdDepth3` 조건 분기 (M2 롤백 구조)
  - `QueryTimingAspect` + Micrometer `SimpleMeterRegistry` — 리포지토리 쿼리 시간 · 슬로우 쿼리 WARN
- **[M0] Claude Code 통합 환경 구축** — 2026-04-24 완료 (머지 `710167c`)
  - [milestone](milestones/milestone-0-claude-code-integration.md)
  - 계층형 CLAUDE.md (루트 + `api/` + `web/`), 슬래시 커맨드 3종(`/analyze-before-change`, `/write-adr`, `/review-pr`), Analyze-Before-Change·피처 플래그 가드레일 명시, ADR 템플릿

---

## Epic 및 마일스톤 분할 원칙

- **Roadmap** — 하고 싶은 모든 작업의 단일 인덱스 (이 문서)
- **Epic** — 여러 마일스톤을 묶는 큰 주제 (예: JPA 전환). 커지면 `docs/epics/` 하위로 분리
- **Milestone** — 시간·완료 상태가 있는 체크포인트 (`docs/milestones/`)
- **Spec** — Claude Code 실행 지시 (`docs/specs/`)
- **ADR** — 돌이킬 수 없는 의사결정 기록 (`docs/adr/`)

## 갱신 규칙

- 마일스톤 착수 시 Now로 이동
- 마일스톤 완료 시 Done으로 이동, 커밋 해시·완료일 기록
- 새 아이디어는 Later에 먼저 추가하고, 우선순위가 올라가면 Next로 승격
