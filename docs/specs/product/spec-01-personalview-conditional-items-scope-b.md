# Spec 01: 맞춤학습지 조건부 출제 (Scope B)

**분류:** Product (그래프 인프라 무의존 — M2/M3와 독립)
**정본 로드맵 항목:** `docs/roadmap.md` → Later 백로그 → [Product] 맞춤학습지 조건부 출제 (Scope B)
**선행:** Scope A(기본 정책 자동 출제) 출시 완료 (PR #24, `5623ffc`)
**작성일:** 2026-06-23

---

## 범위

PersonalView 의 **비활성 플레이스홀더 라디오**(맞춤 유형·재출제)와 문항 수 입력을 실제로 백엔드 `ItemService.findPersonalItems` 출제 결과에 반영한다. 기존 무조건 "오답 → 선수지식 depth≤2 → 개념당 1문항" 로직을 **조건 파라미터화**한다.

**범위 외:** 그래프 라이브 탐색(없음 — 사전계산 `probabilities.to_concept_depth` 사용), 스키마 변경(없음), 비율 가중 출제, N+1 근본 해소(별도 백로그).

---

## 시퀀싱 근거 (M3 비차단)

로드맵 원문은 "M2 검증된 CTE 그래프 경로(M3 출시 후) 위에서 설계"라고 명시했으나, **`findPersonalItems` 는 라이브 그래프 탐색을 하지 않는다.** 선수지식 깊이는 `probabilities` 테이블의 사전계산 컬럼 `to_concept_depth`(0=오답 개념 자체, 1~2=선수지식 깊이)에서 읽는다. Neo4j/CTE(`mmt.migration.use-mysql-cte-for-graph`) 직접 의존이 0이므로 **M3 미출시여도 Scope B 착수는 비차단**이다 (사용자 결정, 2026-06-23). 로드맵 항목에 본 근거를 반영한다.

---

## 데이터 모델 (M1/조사에서 확인됨)

- `answers(answer_id, user_test_id, item_id, answer_code)` — 오답 = `answer_code = 0`
- `probabilities(answer_id, concept_id, to_concept_depth, ...)` — answer_id 1개당 복수 row. `to_concept_depth` 분포 0~3, 출제는 0~2 사용
- `items(item_id, item_answer, item_image_path, concept_id)` → `concepts` → `chapters` 조인으로 출제 응답 구성
- 기존 조회: `ItemRepository.findByConceptId(conceptId)` = `ORDER BY RAND() LIMIT 1` (개념당 랜덤 1문항)

---

## 결정 사항 (사용자 확정, 2026-06-23)

### D1. 맞춤 유형 → depth 필터 (단순 분할)
| 라디오 값 | 의미 | depth 필터 |
|---|---|---|
| `wrong` (오답 문항 위주) | 오답 개념 자체 | `to_concept_depth == 0` |
| `prerequisite` (선수 지식 위주) | 선수지식 | `1 <= to_concept_depth <= 2` |

두 옵션은 레거시 `depth ≤ 2` 집합을 **분할**한다(개념 유실 없음). 가중치·폴백·비율 혼합 없음.

### D2. 재출제 → 원본 문항 재포함 (additive)
"재출제" = 학생이 원래 푼 문항(`answers.item_id`)을 학습지에 다시 포함. **신규 문항(D1 기반)과 더해진다(additive).**

| 라디오 값 | 의미 |
|---|---|
| `nothing` (없음) | 원본 문항 미포함. D1 기반 신규 문항만 |
| `wrong` (오답 문항) | 학생의 **원래 오답 문항**(`answer_code=0`) 포함 **+ 신규 문항도 포함** |
| `all` (전체 문항) | 학생의 **원래 응시 문항 전체** 포함 **+ 신규 문항도 포함** |

### D3. 문항 수(count) — 목표 총 문항수, 같은 범위에서 채움 (개정 2026-06-23, 사용자 결정)
- `count` = **목표 총 문항수**(재출제 원본 + 신규 합산). 범위 6~30, 서버에서 clamp.
- 채우기 순서:
  1. 재출제 원본 문항(선택 시) — `item_id` dedup 후 **전부 포함**.
  2. 원본 수 ≥ count → 원본 전부만(초과 허용), 신규·fill 생략.
  3. 원본 수 < count → 남은 자리(`count − 원본수`)를 맞춤유형 범위(D1 depth 필터된 개념 집합)에서 채운다:
     - (a) 개념당 1문항씩(다양성 우선, 개념 등장 순서),
     - (b) 자리가 남으면 **같은 범위 개념들에서 추가 문항을 round-robin** 으로 더 추출,
     - (c) 범위 내 가용 문항이 소진되면 중단(count 미달 가능 — 없는 문항을 생성하지 않음).
- 이전 안(“count=신규 상한, 재출제 additive on top”)은 폐기. 사용자 의도 = 내가 정한 문항 수가 곧 학습지 크기.

### D4. 정렬·중복 제거
- 학습지 순서: **재출제 원본 문항 먼저 → 신규 문항**(범위 채우기 순서대로). `testItemNumber` 는 최종 합쳐진 순서대로 1부터.
- 중복 제거: `item_id` 기준. 재출제 원본·신규·fill 이 같은 item 이면 1회만(원본 우선).
- 신규 채우기는 첫 패스(개념당 1문항)로 다양성을 확보한 뒤 추가 패스로 같은 개념의 다른 문항을 채운다(D3-3).

### D5. 하위호환 / 롤백
- 컨트롤러 파라미터 `category`·`reExam`·`count` 는 **옵셔널**. **전부 미지정 시 레거시 Scope A 동작**(depth ≤ 2 혼합, 재출제 없음, count 무제한, distinct 미적용) 그대로 재현.
- 프론트가 파라미터 전송을 멈추면(= revert) 즉시 Scope A 로 롤백. 백엔드는 additive 라 그대로 둬도 안전.

---

## API 계약

`GET /api/v1/items/personal`

| 파라미터 | 타입 | 필수 | 기본 | 비고 |
|---|---|---|---|---|
| `userTestId` | Long | Y | — | 기존. IDOR 소유권 검사(security spec-01) 유지 |
| `category` | String | N | (미지정=레거시) | `wrong` \| `prerequisite` |
| `reExam` | String | N | `nothing` | `nothing` \| `wrong` \| `all` |
| `count` | int | N | 10 | clamp 6~30 |

응답 스키마(`PersonalItemsResponse`) 변경 없음.

---

## 구현 단계 (Task 단위 커밋)

1. **T1 — 리포지토리**: ① 원본 응시 문항 조회 `ItemRepository.findOriginalItemsByUserTestId(Long userTestId, boolean onlyWrong)` → `List<Item>` (answers⋈items⋈concepts⋈chapters, onlyWrong 시 `answer_code=0`). ② 범위 채우기용 `ItemRepository.findItemsByConceptIds(List<Integer> conceptIds)` → `List<Item>` (해당 개념들의 전체 문항, `ORDER BY RAND()`). 둘 다 단일 쿼리(N+1 없음).
2. **T2 — 서비스**: `findPersonalItems(userTestId, userEmail, category, reExam, count)` 분기. D1~D5 구현. 레거시 경로 보존.
3. **T3 — 컨트롤러**: 옵셔널 쿼리파라미터 추가, 서비스 위임.
4. **T4 — 백엔드 테스트**: `ItemService` 단위 테스트(depth 필터·재출제 머지·count 캡·dedup·하위호환).
5. **T5 — 프론트**: `PersonalView.vue` disabled 해제, 라벨 버그(`전체 문항` 의 `value="prerequisite"` → `value="all"`) 수정, 선택값 쿼리스트링 전달, 출제 전 조건(맞춤유형 미선택 시) 안내.

---

## 향후 개선 — 비율 배분 출제 (deferred, 2026-06-23)

사용자 원래 의도는 단순 "채우기"가 아니라 **count 를 비율로 배분**하는 것:
- 예: count=14, 선수지식 위주 + 오답 문항 → `선수지식 : 일반지식(오답) ≈ 7:3` 으로 자리 배분(비율값 미확정).
- **선수지식 자리(≈70%)** ← 취약 선수지식 문항(depth 1~2).
- **일반지식 자리(≈30%)** ← 재출제 오답 문항. 그 자리가 부족하면 같은 범주 문항으로 보충.
- 즉 재출제 오답이 많아도 30% 자리로 **상한**되고, 선수지식이 본 비중을 차지.

현 구현(D3)은 이 비율 배분이 아니라 **"원본 전부 먼저 → 같은 범주로 count 까지 채움"** 의 우회안이다(사용자 승인 2026-06-23: "지금 조건으로 우선 채운 다음 부족분은 같은 범주에서 추가"). 차이:
- 현 구현은 재출제 오답을 30% 로 상한하지 않고 전부 포함(많으면 count 초과 가능, D3-2).
- 비율 배분은 카테고리별 목표치 계산 + 버킷별 채우기/상한 로직이 추가로 필요.

비율 배분은 별도 후속(roadmap Later 백로그)으로 다룬다.

## 영향·위험 (Analyze-Before-Change 요약)

- 호출 지점: 서비스=`ItemController` 1곳, 엔드포인트 소비자=`PersonalView.vue` 1곳. 격리됨.
- 영향 테스트: 기존 0개 → 신규 단위 테스트 동반(api/CLAUDE.md 규칙).
- 스키마·마이그레이션: 없음(기존 컬럼만).
- ADR: 불필요(신규 아키텍처 결정 없음, 기존 로직 파라미터화).
- 위험도: **중간**. 폭발 반경 작음(단일 소비자·스키마 무변경·하위호환).

## 롤백 시나리오

- 파라미터 옵셔널·기본=레거시 → **프론트 revert 만으로 즉시 Scope A 복귀**.
- 커밋 백엔드/프론트 분리 → 부분 롤백.
- 스키마 무변경 → DB 롤백 불필요.

---

## 검증 (web 테스트 프레임워크 부재 → 런타임 수동)

메모리 `project_personalview_local_verification` 셋업(TF Serving 부재→probabilities 합성, signup→POST /tests→answers/probabilities, 토큰 localStorage 주입, /personal IDOR=소유자만) 위에서:

- [ ] `category=wrong` → depth 0 개념 문항만
- [ ] `category=prerequisite` → depth 1~2 개념 문항만
- [ ] `reExam=wrong` → 원래 오답 문항 + 신규 문항, dedup 확인
- [ ] `reExam=all` → 원래 응시 문항 전체 + 신규 문항
- [ ] `count` 채우기 동작(총 문항수 = count, 범위 내에서 round-robin fill), 6~30 clamp, 범위 소진 시 미달 허용
- [ ] 파라미터 전부 생략 → 레거시 Scope A 동치
- [ ] IDOR: 타인 userTestId → 403 유지
