# M7 spec-01 자율주행 핸드오프 — 새 세션 착수 프롬프트 (2026-07-13)

> **성격: 다음 세션이 읽고 바로 착수하는 forward 실행 계획.** 진행 상태 정본 = [ROADMAP](../../ROADMAP.md),
> 착수 체크리스트 정본 = [m7-step4-handoff](m7-step4-handoff.md), 명세 정본 = [spec-01](../specs/m7/spec-01-diagnosis-self-report-dkt.md).
> 이 문서는 그 위에 **자율주행 모드 규칙 + 실행 순서**만 얹는다 — 내용이 충돌하면 위 정본을 신뢰.
>
> **착수법**: 새 세션에서 `@docs/milestones/m7-spec01-autonomous-handoff.md` 첨부 + **"실행"**.

## 0. 모드 — 왜 자율주행인가 (devetym 전례)

- spec-01~03은 분리 세션 리뷰(실체 대조)를 이미 통과한 **확정 스펙** — 구현 전 사람 비준 게이트 불요.
- 자율주행의 실체 = **확정 스펙 + 이 핸드오프의 가드레일 + green 오라클 + 스텝 경계 격리 리뷰**.
  별도 하네스 기계장치 없음(devetym M0~M9 전례). Workflow형 구현 하네스는 agent-harnesses BACKLOG의
  장기 과제로 남김 — 이번 주행과 무관.
- 사람 체크포인트 = **완성물 리뷰** + §5 사람 게이트. 중간 판단은 eyes-open으로 스스로 결정하되
  §5 항목에서는 멈추고 대기.

## 1. 착수 절차 (이 순서대로)

0. **green 기준선**: `docker compose up -d mmt-mysql mmt-neo4j mmt-redis` → `cd api && ./gradlew test`
   — 착수 전 회귀 0 확인(Testcontainers라 Docker 필요). 기준선이 안 서면 여기서 멈추고 보고.
1. **브랜치**: `feat/m7-product-pivot` → `feat/m7-spec-01-diagnosis` 분기. origin 푸시 안 함(미결정 유지).
2. **`/analyze-before-change`** — 입력 = spec-01 §6.
   - 참조 지점: `AIController`·`ProbabilityService`·`AnswerService`·`SecurityConfig` permitAll·`ItemService` + 신규 DDL 2건(`self_report_answers`·`probabilities.user_test_id`).
   - **실측 3건(추측 금지)**: ① `users_tests` PK 컬럼명·타입 ② `concepts.concept_id` 타입 ③ 결과 조회의 `user_test_id` 스코프 전환 가능성.
   - ADR 작성: self-report→DKT 매핑(D3-R 트레이드오프·R1 감수) + `mmt.diagnosis.*` 플래그 영역 등록(api/CLAUDE.md 피처 플래그 섹션에 영역 추가).
   - 잠정 채택 4건 재확인: S1(무영속 preview)·S4(40/65 컷)·S5(파생 현재위치)·S6(역방향 CTE depth 3).
3. **구현 순서**: 플래그 `mmt.diagnosis.enabled` → DDL(전부 additive) → 적응형 순회(`/diagnosis/frontier`·`/next`)
   → DKT 매핑(`/diagnosis/preview`·귀속 `/diagnosis`) → 결과 계약 → 학습 큐(위상정렬+provenance). 신규 리포지토리는 **JPA**.
4. **매 스텝 끝**: `./gradlew test` green 회귀 0 확인 → Task 단위 커밋(영문 제목 Conventional Commits·마침표 없음 + 국문 본문, AI attribution 트레일러 없음).

## 2. 🔒 가드레일 (리뷰가 지정한 함정 — 자율로 우회 금지)

- **결정론 계약**: preview == 귀속(순서 포함) — DKT 시퀀스 순서 = 답변 입력 순서(`self_report_answer_id ASC` == 요청 `answered[]`). **property 테스트 필수**(spec-01 §8).
- **구 경로 무접촉**: 신규 경로는 `answers`·`tests_items`·`findAIInput`/`findBefore`를 읽지도 쓰지도 않음. **위반 감지 property 테스트**(spec-01 §8).
- **provenance NULL 누수 금지**(병합 경로) / `source`·`goal_concept_id` = 스키마 nullable + 앱 non-null 강제(의도된 설계 — 스키마만 보고 "결함"으로 되돌리지 말 것).
- **플래그 off = 구 동작 그대로**(즉시 롤백 가능성). 구 Vue `web/`은 무변경 보존 — diff 발생 자체가 결함.
- **시크릿**: `application-secure*.yml` 계열 변형 전수 gitignore 확인, 자격증명 커밋 금지.

## 3. 격리 리뷰 케이던스

- 구현 스텝 경계(최소: DDL 후 / DKT 매핑 후 / 학습 큐 후)에서 `/audit-doc <대상> mmt` · `/design-review <대상> mmt`.
  **프로파일 인자 `mmt` 명시 필수**(커맨드 자동 추론 규칙에 MMT 없음). 렌즈 정본 = `~/dev/agent-harnesses/profiles/mmt.md`.
- 리뷰/산출물 전달 시 **파일 지문(md5·바이트) + 커밋 해시 + `git show` diff 번들** 동봉.

## 4. 범위

- 이번 주행 = **spec-01만**. spec-02(React 스캐폴딩)·spec-03(링크 시드 콘텐츠)은 다음 갈래 — 착수 금지.

## 5. 사람 게이트 (자율 금지 — 여기서 멈추고 대기)

- push·머지·PR 생성.
- **R2 실측 4시나리오**(1답만 / 전부 알아요 / 전부 몰라요 / 혼합) — 실 TF Serving 응답 필요.
  로컬 서빙 가능하면 실측 후 S4 컷 보정 입력으로 기록, 불가하면 **명시적 이월**(합성 우회로 실측을 대체하지 말 것).
- 스키마 **파괴적** 변경이 필요해지는 경우(스펙상 전부 additive — 아니게 되는 순간 멈춤).
- 완성물 리뷰: 수용한 residual(eyes-open 결정 목록)과 함께 제시.

## 6. 안전선 (불변)

- 진행 상태는 `ROADMAP.md`(디스크)에 기록 — 장기 메모리에 status 복제 금지.
- 브랜치 보존, 젠더중립 네이밍.
