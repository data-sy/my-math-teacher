# spec-03 · 맞춤 — 그래프 학습 경로 노출 + 개념별 외부 링크 큐레이션

**상위 마일스톤:** [Milestone 7](../../milestones/milestone-7-product-pivot.md) — 제품 피벗. 밀스톤 D4(맞춤 = 그래프 학습 경로 + 외부 링크)·PRD §4.3(결과 액션)·와이어프레임 ④ 상태 2(게이트 뒤 학습 큐)를 기술 설계로 구체화한다.
**대상:** 링크 데이터 모델·시드·API 부착(백엔드) + 경로 노출 배선(프론트, spec-02 컴포넌트 계약 위). 큐 생성·위상정렬 자체는 [spec-01 §4.6](spec-01-diagnosis-self-report-dkt.md)(확정) 소관 — 본 spec 은 그 **출력을 채우고 보여주는** 층.
**작업 브랜치:** 착수 시 `feat/m7-spec-03-links-path` (본 설계 문서는 `feat/m7-product-pivot` 에 동반)
**상태:** 📝 **spec 합의 대기** — §7 결정(U1~U5) 사인오프 후 코드 단계
**선행:** spec-01 확정(결과 계약 `cards[].links: []` 예약·학습 큐 스키마·S6 역방향 CTE) · spec-02 확정(④ 2상태 컴포넌트·⑥ Cytoscape hook·계약 SSOT).

---

## 1. 범위

학습지 출제(구 PersonalView·html2pdf)를 대체하는 맞춤 출력 = **① 개념별 무료 외부 자료 링크**(무료 존, 비로그인 노출 — F-1 "공짜 공공자료는 게이트하지 않음") + **② 그래프 학습 경로**(게이트 자산 — 학습 큐를 ⑥ 그래프 위에 강조).

### In
1. **링크 데이터 모델** — 개념별 외부 자료(EBS 등) 테이블 + JPA 리포지토리 + 시드 전략(U1·U2).
2. **결과 계약 links 채움** — spec-01 `cards[].links: []` 예약 지점을 `{title, url, provider}` 로 부착. preview(익명)에도 포함 — 링크는 concept 조인 조회일 뿐이라 무영속 원칙과 무충돌.
3. **학습 큐 응답 계약** — `GET /learning-queues/me` 항목별 `links[]` + **"급한 이유" 사람 언어 문구**(와이어프레임: "인수분해로 가는 바로 아래 칸") 소스 정의(U3).
4. **그래프 경로 노출** — ④ 게이트 뒤 "그래프 경로 비주얼 1장"(탭 → ⑥) + ⑥ **내 경로 강조 모드**. 신규 백엔드 없이 프론트 조립(§4.3).
5. **R4 링크 유지보수** — dead link 점검 방식·주기(U5).

### Out (이번 범위 밖)
- **큐 생성·위상정렬·재진입 저장** — spec-01 §4.6 확정분. 본 spec 은 additive 확장 1건(U3-A)만 제안하며 확정 결정 재오픈 아님.
- **경로 맵 C안 미리보기·홈 저커밋 진입로 B안** — PRD §6 fast-follow 백로그.
- **유료 콘텐츠 큐레이션** — F-1 원칙(공짜 공공자료 비게이트)은 무료 한정. 유료는 훗날 별개 판단.
- **링크 관리 admin UI** — 시드·점검은 스크립트/시드 파일로. admin 은 오픈 후.
- **LLM 자동 큐레이션** — 링크는 수동 큐레이션(품질 우선). 자동화는 후속.

---

## 2. 데이터 모델

### 2.1 신규 테이블 `concept_links` (U1 = DB 권장안 기준)

```sql
CREATE TABLE concept_links (
  concept_link_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  concept_id      INT          NOT NULL,       -- FK concepts
  title           VARCHAR(120) NOT NULL,       -- 노출 문구 (예: "무료 강의 (EBS)")
  url             VARCHAR(500) NOT NULL,
  provider        VARCHAR(50)  NOT NULL,       -- 'EBS' 등 — 노출·점검 그룹핑 키
  display_order   INT          NOT NULL DEFAULT 0,
  alive           BOOLEAN      NOT NULL DEFAULT TRUE,   -- R4 점검 결과 (죽은 링크 비노출)
  last_checked_at TIMESTAMP    NULL,
  created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cl_concept FOREIGN KEY (concept_id) REFERENCES concepts(concept_id),
  INDEX idx_cl_concept (concept_id)
);
```

- 조회는 항상 `alive = TRUE` 필터 — 죽은 링크는 삭제가 아니라 비노출(점검 이력 보존).
- 개념당 노출 상한 **3개**(`display_order` 순) — 카드·큐 항목의 모바일 폭 기준(U4).
- 리포지토리는 **JPA**(api/CLAUDE.md — 신규 JdbcTemplate 금지).

### 2.2 시드 전략 (U2)

- **전 개념(1,631) 커버 불요**(D4 원문 — "상위 취약 개념부터, 전 개념 채울 필요 없음"). 시급도는 유저별이라 사전 선별 불가 → **그래프 구조상 병목**(blockedDescendants 상위 = S6 역방향 CTE 재사용) **상위 30~50 개념**부터 시드.
- **링크 결측 허용이 계약**: 링크 0개 개념의 카드·큐 항목은 링크 섹션 자체를 생략(빈 껍데기·"준비 중" 문구 없음 — 학생 신뢰 톤).
- 시드 파일: `shared/data/concept-links-seed.csv`(concept_id, title, url, provider, display_order) → 삽입 스크립트로 적재. 큐레이션 자체(EBS 검색·선별)는 콘텐츠 작업 — 착수 시 별도 Task 로 분리(코드와 커밋 분리).

---

## 3. API 계약 부착

### 3.1 결과 카드 links (무료 존)

spec-01 결과 계약의 예약 지점을 채운다 — shape 는 계약 SSOT(spec-02 §5)에 함께 등재:

```
cards: [ { …spec-01 확정 필드…,
           links: [ { title, url, provider } ] } ]   // alive=TRUE, display_order 순, 최대 3
```

- `preview`(익명)·`GET /diagnosis/{userTestId}`(영속) **둘 다 동일 부착**(F-1: 무료 자료는 로그인 전 충족 — §5.1 게이트 검증 항목과 연동).
- N+1 주의: 카드 top-N(≤5) + more 목록의 links 는 **concept_id IN 일괄 조회 1쿼리**로 부착(`*N1Test` 대상).

### 3.2 학습 큐 응답 (게이트 뒤)

```
GET /api/v1/learning-queues/me
{ queueId, userTestId, createdAt,
  items: [ { position, conceptId, conceptName, done, doneAt,
             links: [ {title, url, provider} ],
             reason: "인수분해로 가는 바로 아래 계단" } ],   // U3 — 사람 언어 근거
  current: { position, conceptId },                          // 파생값(첫 done=false, spec-01 S5)
  goal: { conceptId, conceptName } }                         // 계단 꼭대기(와이어프레임 "목표")
```

- **`reason` 문구 소스 = U3.** 권장 A: 큐 생성 시 `learning_queue_items` 에 **`source_concept_id INT NULL`**(이 항목이 어느 취약 카드 가지에서 왔나) additive 컬럼으로 저장 → 조회 시 템플릿 조합("{source 개념}로 가는 아래 계단" / 카드 본인이면 "가장 급했던 약점"). spec-01 §4.6 확정 스키마의 **additive 확장이며 재오픈 아님**(위상정렬·파생 현재위치·2테이블 구조 불변).
- `goal` = 큐 마지막 position 항목이 아니라 **top-N 카드들의 최상위 후수 개념**(와이어프레임 "이차방정식까지") — 큐 생성 시 결정, additive 컬럼(`learning_queues.goal_concept_id INT NULL`) 저장.

### 3.3 ⑥ 그래프 경로 강조 (신규 백엔드 없음)

- ④ 게이트 뒤 "인터랙티브 그래프 경로" 비주얼 탭 → `/concepts?highlight=queue` 진입.
- 프론트가 `GET /learning-queues/me` 의 conceptId 목록 + **기존** nodes/edges 엔드포인트로 그래프 조립 후, spec-02 Cytoscape hook 에 **경로 강조 모드** 추가: 큐 소속 노드 = 강조(순번 배지), 완료 노드 = 체크 스타일, 현재 위치 = 활성 스타일, 비소속 = dim(기존 focus/dim 스타일 체계 재사용).
- 백엔드 신규 없음 — spec-02 hook 의 옵션 확장(`highlightPath: {orderedIds, doneIds, currentId}`)만. 계약 SSOT 에 타입 등재.

---

## 4. R4 — 링크 유지보수 운영 (U5)

- **점검 스크립트**: `shared/performance-tests` 관행처럼 `shared/scripts/check-concept-links.*` — `alive=TRUE` 전 행에 HTTP 상태 점검(HEAD, 실패 시 GET 폴백) → 실패 행 `alive=FALSE`·`last_checked_at` 갱신 리포트. 자동 복구 없음(수동 확인 후 갱신).
- **주기**: 월 1회 수동 실행부터(시드 30~50개 규모에 충분). 크론화·admin 은 링크 수가 늘면 후속.
- EBS 대규모 개편(R4 시나리오) 시: provider 단위 일괄 비노출 가능(`UPDATE … WHERE provider='EBS'`) — 결측 허용 계약(§2.2) 덕에 서비스는 링크 없이도 성립(fail-soft).

---

## 5. 왜 이 순서 (spec-03 이 마지막)

- spec-01 이 큐·결과 계약을, spec-02 가 화면·hook 계약을 확정한 뒤라 본 spec 은 **예약 지점을 채우는 층** — 역방향 의존 없음.
- 구현 순서상으로도 독립: 링크 테이블·시드는 백엔드(spec-01 구현)와, 경로 강조는 프론트(spec-02 구현)와 각각 병렬 가능.

---

## 6. Analyze-Before-Change 예고 (합의 후 착수 시)

- **참조 지점:** spec-01 결과 조회 쿼리(links 조인 지점)·`learning_queue_items` DDL(additive 2컬럼)·계약 SSOT 타입 모듈·spec-02 Cytoscape hook 옵션·`ItemService.findPersonalItems`(구 맞춤 경로 — 무변경 보존 확인, 구 Vue 가 롤백 대상으로 계속 소비).
- **실측 확인:** `concepts.concept_id` 타입 정렬(spec-01 §6 과 동일 항목 공유) · 시드 대상 상위 개념의 실제 EBS 자료 존재 여부(콘텐츠 리스크 — 없으면 U2 의 N 하향).
- **영향 테스트:** 결과 계약 스냅샷(links 필드 추가로 갱신), links 일괄 조회 `*N1Test`, 큐 응답 계약 테스트.
- **롤백:** `concept_links` 테이블·additive 2컬럼 전부 additive — 구 경로 미참조, 방치 가능. 링크 비노출은 `alive=FALSE` 로 즉시(배포 불요).

---

## 7. 결정 (사인오프 대기 — U1~U5)

- **U1 — 링크 저장소:** **A(권장) DB 테이블 `concept_links`** — 결과·큐 API 가 조인 부착, 재배포 없이 갱신, R4 점검 상태(alive) 관리 가능 / B 정적 JSON(프론트 번들) — 갱신마다 재배포·preview(백엔드 계산)와 이원화.
- **U2 — 시드 커버리지:** **A(권장) blockedDescendants 상위 30~50 개념 + 결측 허용 계약** — 구조적 병목부터, 링크 0 이면 섹션 생략 / B 진입 단원 프론티어 전 개념 — 커버리지 넓으나 콘텐츠 작업량 폭증.
- **U3 — 큐 항목 "급한 이유" 소스:** **A(권장) 생성 시 `source_concept_id` additive 저장** — 결정론·조회 단순(spec-01 스키마 재오픈 아님) / B 조회 시 그래프 파생 — 스키마 무변경이나 조회마다 그래프 질의 + 갱신 시점 따라 문구 흔들림.
- **U4 — 링크 노출 상한:** 개념당 **3개**(display_order 순) — 모바일 카드 폭·선택 과부하 방지. EBS 우선, provider 다양화는 후속.
- **U5 — R4 점검:** **월 1회 수동 스크립트**(HEAD 점검 → alive 갱신) — 시드 규모에 비례해 크론화 후속.

---

## 8. 검증 (착수 후)

- **무료 충족(F-1·§5.1):** 비로그인 preview 결과에 카드 links 가 실제 노출(개념·순서·자료 3요소 완성) — 게이트가 §5.1 을 막지 않음 재확인.
- **결측 허용:** 링크 0 개념 카드·큐 항목에서 링크 섹션이 깨끗이 생략(빈 배열 → UI 미렌더).
- **N+1:** 카드+more 전체 links 부착이 1쿼리(IN) — `*N1Test`.
- **reason 문구:** 큐 항목별 사람 언어 근거가 source 개념 기준으로 정확(내부용어 노출 금지 — PRD §4.2 규율 공유).
- **경로 강조:** `/concepts?highlight=queue` 에서 큐 순번·완료·현재 위치가 그래프에 표시, 비소속 dim — 모바일 탭 동작(호버 0).
- **R4 스크립트:** 죽은 URL 시뮬레이션 → alive=FALSE 전환·비노출 확인, provider 일괄 비노출 동작.
