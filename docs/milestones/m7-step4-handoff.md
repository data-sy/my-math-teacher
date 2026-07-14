# M7 Step 4(구현) 핸드오프 — 콜드스타트 복귀점

> **작성:** 2026-07-13, 브랜치 `feat/m7-product-pivot` (⚠️ **로컬 전용, origin 미푸시**).
> **정본 위계:** 방향 = [milestone-7](milestone-7-product-pivot.md) · 제품 = [PRD](../prd/m7-prd.md) · UX = `docs/design/m7-*.html` · 기술 = `docs/specs/m7/spec-01~03` · 진행 상태 = [ROADMAP](../../ROADMAP.md). **본 문서는 착수 절차 체크리스트일 뿐 — 내용이 충돌하면 위 정본을 신뢰.**

## 현재 상태 (한 줄)

Step 1 기획·Step 2 UX·Step 3 기술 설계 **완료**(spec-01·02·03 확정, 각각 분리 세션 리뷰 실체 대조 통과). **spec-01 백엔드 구현 완료(2026-07-13, 자율주행 — 브랜치 `feat/m7-spec-01-diagnosis`, 로컬 전용)** — 진행 상태·사람 게이트 정본 = [ROADMAP](../../ROADMAP.md) [M7] 항목. 다음 = 사람 게이트 처리 후 spec-02(React)·spec-03 층.

> **실행 모드 확정(2026-07-13)**: spec-01 구현은 자율주행으로 — 착수 프롬프트·가드레일·사람 게이트 = [m7-spec01-autonomous-handoff](m7-spec01-autonomous-handoff.md) (새 세션에서 그 문서 첨부 + "실행").

## Step 4 착수 절차 (이 순서대로)

1. **브랜치**: `feat/m7-product-pivot` 에서 분기 → `feat/m7-spec-01-diagnosis`. (스펙 3편이 로컬 전용 브랜치에만 있음 — 필요시 origin 푸시 여부 먼저 결정.)
2. **`/analyze-before-change`** (spec-01 §6이 입력):
   - 참조 지점: `AIController`·`ProbabilityService`·`AnswerService`·`SecurityConfig` permitAll·`ItemService` + 신규 DDL 2건(`self_report_answers`·`probabilities.user_test_id`).
   - **실측 확인 3건(추측 금지)**: ① `users_tests` PK 컬럼명·타입 ② `concepts.concept_id` 타입(INT 가정) ③ 결과 조회의 `user_test_id` 스코프 전환 가능성.
   - ADR 작성: **self-report→DKT 매핑**(D3-R 트레이드오프·R1 감수 + `mmt.diagnosis.*` 플래그 영역 등록 → api/CLAUDE.md 피처 플래그 섹션에 영역 추가).
3. **spec-01 구현 순서(제안)**: 플래그 `mmt.diagnosis.enabled` → DDL(전부 additive) → 적응형 순회(`/diagnosis/frontier`·`/next`) → DKT 매핑(`/diagnosis/preview`·귀속 `/diagnosis`) → 결과 계약 → 학습 큐(위상정렬+provenance). 신규 리포지토리는 **JPA**.
4. **병렬 갈래(선택)**: spec-02 React 는 계약 mock 으로 선개발 가능 — `web-react/` 신규 스캐폴딩 + **ADR: React 도입**(T1~T5 확정 스택 기록). T1 선결(§8) 중 잔여 실측 1건 = M6 배포 스크립트의 프론트 이미지 전환 경로.
5. **spec-03 층**은 spec-01 큐·결과 위에 얹힘 — 링크 시드 큐레이션(콘텐츠 작업)은 코드와 커밋 분리.

## 구현 중 잊으면 안 되는 것

- **결정론 계약**: preview == 귀속(순서 포함) — DKT 시퀀스 순서 = 답변 입력 순서(`self_report_answer_id ASC` == 요청 `answered[]`). property 테스트 필수(spec-01 §8).
- **구 경로 무접촉**: 신규 경로는 `answers`·`tests_items`·`findAIInput`/`findBefore` 를 읽지도 쓰지도 않음(S2=C, R1 소멸의 근거). 위반 감지 property 테스트(spec-01 §8).
- **구현 감시 2건**(spec-03 §6, 리뷰 지정): ① provenance 가 병합 경로에서 NULL 로 새지 않는지(goal "항상 정의" 불변식의 실질 관문) ② `source/goal_concept_id` = 스키마 nullable + 앱 non-null 강제(의도된 설계).
- **잠정 채택 재검토 목록**(착수 시 `/analyze-before-change` 에서 한 번씩 재확인): spec-01 S1(무영속 preview)·S4(40/65 컷 — R2 실측 분포와 연동)·S5(파생 현재위치)·S6(역방향 CTE depth 3).
- **R2 실측 4 시나리오**: 1답만 / 전부 알아요(DKT 생략 확인) / 전부 몰라요 / 혼합 — TF Serving 실응답 분포 기록(S4 컷 보정 입력). 로컬 TF Serving 부재 시 [[project_personalview_local_verification]] 의 우회(합성) 관행 참고하되, 이 실측 자체는 실서빙 응답이 필요.
- **리뷰 핸드오프 절차**: 산출물 전달 시 **파일 지문(md5·바이트) + 커밋 해시 + `git show` diff 번들** 동봉(이번 세션에서 stale export 갭으로 확립된 절차).
- **커밋 컨벤션**: 영문 제목(Conventional Commits, 마침표 없음) + 국문 본문, AI attribution 트레일러 없음. Task 단위 분리.

## 미결·대기 (현재 없음에 가까움)

- 문서 stale 정정 4건 — ✅ 전부 처리 완료(2026-07-13, 사용자 승인).
- 브랜치 origin 푸시 여부 — 미결정(로컬 전용 상태 유지 중).
- fast-follow 백로그(오픈 후): 문답 B/C안, 결과 C안 경로 맵, 홈 B 저커밋 진입로, 다중 goal 노출, SSOT codegen(TS 타입 생성), 링크 점검 크론화.
