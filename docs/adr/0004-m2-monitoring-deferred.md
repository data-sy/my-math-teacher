# ADR 0004: M2 production 모니터링 인프라 — M3로 분리, M2는 SimpleMeterRegistry 검증 단계만 유지

## Status

Accepted

## Context

Milestone 2 spec-03 Task 4.2의 모니터링 인프라 의사결정에서 다음 사실이 확인됐다:

- M1에서 도입한 `SimpleMeterRegistry`(ADR 0002 §4)는 in-memory 누적 구현으로 단위 테스트·로컬 검증용임. production 모니터링(p95 trends, 에러율 alerting, 캐시 hit율 dashboard 등)에는 부족.
- production 모니터링을 본격 도입하려면 Prometheus + Grafana 또는 동등 인프라 + Spring Boot Actuator 승급이 필요하며, 이는 신규 인프라 도입·보안 설정·dashboard 설계 등 M2 핵심(Neo4j 제거)과 결이 다른 작업을 동반함.
- ADR 0002 §4는 이미 "추후 Prometheus·Grafana 로드맵 진입 시 Actuator로 승급"을 명시함.

본 ADR은 M2의 모니터링 작업 범위를 단일 결정으로 고정한다.

## Decision

M2 범위에서는 **production 모니터링 인프라 도입을 수행하지 않는다.** spec-03 Task 4.2의 검증 단계는 M1에서 도입한 `SimpleMeterRegistry` + 로그 기반 측정으로 진행하며, production 모니터링 인프라(Prometheus + Grafana + Actuator 등)는 별도 마일스톤 **M3 (가칭: 운영 관측성 도입)** 으로 분리한다.

M2 spec-03 검증 범위:
- 회귀 테스트 (Task 4.1) — 단위 테스트 + Testcontainers
- 성능 체크포인트 (Task 4.2) — `SimpleMeterRegistry` 메트릭 + `QueryTimingAspect` 슬로우 쿼리 WARN 로그
- 캐시 동작 (spec-02 Task 2.1) — 단위 테스트
- 부하 테스트 (Task 4.2) — k6 신규 시나리오, 결과는 PR 설명에 첨부

M2 비범위 (M3 후행):
- production 환경의 p95 trend dashboard
- 에러율 alerting
- 캐시 hit율 실시간 모니터링
- Prometheus metrics endpoint 노출
- Grafana dashboard 구성

## Consequences

### Positive
- M2 범위가 "Neo4j 제거" 핵심에 집중되어 일정 예측 가능
- spec-03 Task 4.2의 모니터링 인프라 의사결정이 사라져 spec 실행 시 결정 부담 제거
- ADR 0002 §4의 일관성 유지 (Actuator 미도입 정책)

### Negative
- M2 운영 1개월 관찰 기간(spec-03 완료 기준)에 production 메트릭 dashboard가 없으므로, 회귀·이상 감지가 로그 기반으로만 이루어짐 → 즉각적 alerting 부재
- 사고 대응 시 측정 데이터 수집이 ad-hoc (로그 grep·수동 측정)

### Neutral
- 후속 운영 관측성 마일스톤은 본 ADR 시점에 일정·우선순위 미확정 — roadmap의 Later 섹션 "모니터링·알림 체계 구축" 항목으로 이미 등재됨 (`docs/roadmap.md:34`)

## Alternatives Considered

1. **M2에 Prometheus + Grafana 도입 포함** — 기각. M2 핵심(Neo4j 제거)과 결이 다르며 작업량·일정 위험 증가. Actuator 도입은 자동설정·HTTP 엔드포인트·보안 설정 영향 범위가 커서 ADR 0002 §4와도 충돌.
2. **로그 기반 측정만으로 운영 단계까지 진행 (M3 미신설)** — 기각. M2 이후 다른 마이그레이션·기능에서도 dashboard·alerting이 반복적으로 필요해질 가능성 높음. M3로 명시적으로 분리하여 후속 작업의 진입점으로 둔다.

## References

- 선행 ADR: ADR 0002 §4 (M1 MeterRegistry 결정 — `SimpleMeterRegistry` 수동 등록, Actuator 미도입)
- 적용 spec: `docs/specs/m2/spec-03-validation-and-rollout.md` Task 4.2
- 후속 마일스톤 후보: 운영 관측성 도입 — roadmap의 Later 섹션 "모니터링·알림 체계 구축" 항목으로 등재됨 (`docs/roadmap.md:34`). M2 종료 후 마일스톤 번호 부여 시점에 별도 ADR로 이관
- 관련 자료: `api/src/main/java/com/mmt/api/observability/` (현재 가용 인프라)
