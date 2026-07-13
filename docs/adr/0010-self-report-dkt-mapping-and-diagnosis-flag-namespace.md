# ADR 0010: self-report→DKT 매핑 채택 (D3-R) + `mmt.diagnosis.*` 피처 플래그 영역 등록

## Status
Proposed

*(2026-07-13, spec-01 자율주행 세션에서 작성 — Context 는 확정 스펙 [spec-01](../specs/m7/spec-01-diagnosis-self-report-dkt.md)·[milestone-7](../milestones/milestone-7-product-pivot.md) D3-R 에서 승계. 승인 시 Accepted 로 변경.)*

## Context

M7 제품 피벗은 진단 입력을 "문제 풀이·정오답 채점" → "개념 self-report OX(알아요/몰라요)"로 교체한다.
시급도 산출 엔진에 대해 밀스톤 D3 에서 두 갈래가 있었다:

- **D3-그래프 점수**: DKT 를 버리고 blockedDescendants·depth·수렴도 가중합의 순수 그래프 점수로 대체 (구 spec-01 초안, 2026-07-11 — 삭제됨).
- **D3-R (역전, 채택)**: 기존 DKT(TF Serving)를 유지하고, self-report 를 정오답(안다=맞음/모른다=틀림)으로 매핑해 기존 입력 시퀀스 형식에 흘려넣는다.

제약: DKT 모델은 실 문항 채점 시퀀스로 학습된 모델이라 self-report 매핑 입력은 학습 분포 밖이다(**R1 리스크 — 감수**). 재학습·self-report 전용 모델은 실문항 확보 후 별도 마일스톤. PRD §2.3 은 사용자 대면 문구에서 "AI 예측"을 과장하지 않는 정직 프레이밍으로 방어한다.

또한 신규 경로 전체를 게이트할 피처 플래그가 필요한데, 기존 영역(`mmt.migration.*`·`mmt.observability.*`·`mmt.benchmark.*`)에 맞지 않아 신규 영역 등록이 필요하다 (api/CLAUDE.md 컨벤션: 신규 영역은 ADR 기록 후 섹션 추가).

## Decision

1. **self-report→DKT 매핑 채택 (D3-R).**
   - answered-map → `[skill_id, answer_code]` 시퀀스 직접 생성(신규 `self_report_answers` 소스, 구 `answers`·`findAIInput`/`findBefore` 무접촉) + 기존 ×10 증폭 유지.
   - 시퀀스 순서 = 답변 입력 순서 (preview = 요청 배열 순서, 귀속 = `self_report_answer_id ASC`) — preview == 귀속 결정론의 필요조건 (property 테스트 강제).
   - DKT 입력은 현재 세션만 (S3=A 확정 — 과거 세션 병합은 preview·귀속 불일치로 F-1 위반).
   - TF Serving 결합부 신설 가드 3종: null 응답·`skill_id = -1`·`skillId-1` 범위 초과 (현행 구 경로엔 없음 — 신규 경로만).
2. **`mmt.diagnosis.*` 피처 플래그 영역 등록.**
   - `mmt.diagnosis.enabled` (기본 `false`) — 신규 `/api/v1/diagnosis/*`·`/api/v1/learning-queues/*` 활성화 게이트.
   - 롤백 = 플래그 off (구 경로 무변경 보존이므로 즉시 롤백 성립).
3. **구현 세부 (Analyze-Before-Change 산출, 아래 §분석 요약)**
   - 신규 경로의 TF Serving 클라이언트는 **타임아웃 설정된 진단 전용 인스턴스로 분리** — 공유 `RestTemplate` 빈에 타임아웃을 넣으면 구 경로 동작이 바뀌므로 금지.
   - 신규 경로의 그래프 조회는 `mmt.migration.use-mysql-cte-for-graph` 플래그와 **무관하게 MySQL 재귀 CTE 직행** (M2 이전 완료·M3 Neo4j 폐기 방향과 정합. S6 역방향 CTE 는 Neo4j 미러가 없음).
   - 신규 JPA 엔티티는 기존 테이블(users_tests·concepts)로의 연관을 **평컬럼(Long/Integer)으로 모델링**, 실 FK 제약은 `api/sql/create.sql` DDL 에만 둔다 (테스트 `ddl-auto: create-drop` 가 비엔티티 테이블 FK 를 만들 수 없음).

## Analyze-Before-Change 요약 (2026-07-13 실측)

- **실측 3건**: ① `users_tests` PK = `user_test_id BIGINT AUTO_INCREMENT` ② `concepts.concept_id` = `INT` ③ `probabilities.answer_id` 이미 nullable → `user_test_id BIGINT NULL` additive + 직조회 전환 가능.
- **구 경로 구조적 격리 증명**: 구 `findResults`·`findProbability` 는 answer_id 경유 → answer_id NULL 인 신규 행 미노출. 구 users_tests 조회 3종은 `JOIN tests`·`EXISTS(answers)` 조건 → test_id NULL·무answers 진단 세션 행 미노출.
- **기존 코드 수정 최소 3점**: SecurityConfig permitAll 2줄, ProbabilityService 서빙 호출부 additive 분리(구 시그니처 무변경), UserTestRepository 생성 키 반환 save 추가.
- **스키마**: 신규 4건 전부 additive — `self_report_answers`·`probabilities.user_test_id`·`learning_queues`·`learning_queue_items`. 롤백 시나리오 = 플래그 off + 테이블 방치(구 경로 미참조) 또는 DROP.
- **영향 테스트**: 기존 스위트는 구 경로 검증이므로 무변경 green 이 회귀 오라클. 신규 = spec-01 §8 property 테스트군.

## Consequences

### Positive
- 검증된 서빙 인프라·`probabilities` 파이프라인 재사용 — 신규 모델 훈련 없이 M7 런치 가능.
- 플래그 off + 구 경로 무변경으로 즉시 롤백 성립 (피처 플래그 정책 충족).
- 실문항 데이터가 쌓이면 동일 계약 위에서 모델만 교체 가능 (재학습 마일스톤 예약).

### Negative
- **R1 감수**: self-report 매핑 입력은 DKT 학습 분포 밖 — 확률 출력의 절대값 신뢰도 미보장. 완화 = R2 대표 4시나리오 실측(사람 게이트)·S4 등급 컷 실데이터 보정(백로그)·PRD 정직 프레이밍·fail-soft(서빙 실패 시 "몰라요" 목록 기반 결과는 성립).
- TF Serving 가용성이 preview 경로(익명·permitAll)에 노출 — rate limit + 타임아웃 + 학생 친화 에러로 방어.

### Neutral
- `mmt.diagnosis.*` 영역이 api/CLAUDE.md 피처 플래그 섹션에 추가됨.
- 구 채점 진단은 "제2 진단 모드"로 부활 여지 보존 (무변경 보존이므로).

## Alternatives Considered

1. **그래프 점수로 DKT 대체** (구 spec-01 초안) — 검증 안 된 자체 휴리스틱으로 기존 검증 자산(DKT·probabilities 파이프라인)을 버림. 점수 튜닝 비용이 매핑 비용보다 큼. 밀스톤 D3-R 역전으로 기각.
2. **answers 테이블 재사용** (item_id NOT NULL 완화) — 리뷰 R1 확정 기각: 롤백용으로 살아있는 `findBefore` 가 NULL item_id 행을 전체 이력으로 긁어 구 경로 오염. 임시답/가짜 item 매핑도 '조용한 오염'이라 금지 (S2=C).
3. **과거 self-report 세션 병합 입력** (S3=B) — preview 와 귀속 결과 불일치 → F-1 "본 결과를 저장" 위반으로 기각.
4. **공유 RestTemplate 에 타임아웃 추가** — 구 경로 동작 변경(무접촉 가드레일 위반)으로 기각, 진단 전용 클라이언트 분리.

## References
- 관련 ADR: ADR-0003 (M2 캐싱 패턴 — `graph:v2:` 네임스페이스 승계), ADR-0005 (CTE 객체 매핑)
- 명세: `docs/specs/m7/spec-01-diagnosis-self-report-dkt.md` (S1~S6 결정 표), `docs/milestones/milestone-7-product-pivot.md` (D3-R), `docs/prd/m7-prd.md` (F-1~F-4)
- 착수 핸드오프: `docs/milestones/m7-spec01-autonomous-handoff.md`
