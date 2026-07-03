# spec-09 · ConceptView 진입 단순화 — 검색 기반 2-pane 단일 캔버스 (B-2, P1)

> 트랙: [Design] 실배포 전 리디자인 — ConceptView 진입 단순화(리포트 P0 §53-60·B-2 §161-174·§234 #8). 정본 진행상태 = `docs/roadmap.md` + `design-redesign-handoff.md`
> 작업 브랜치: `feat/conceptview-entry-simplification` (main 분기). Task 단위 커밋.
> 상태: spec 합의 + analyze-before-change 완료 → 코드 단계 승인됨(2026-06-24)
> 무게: **헤비** — 핵심 화면 전면 재작성 + **백엔드 신규 엔드포인트**(디자인 트랙이 api/ 로 확장). 풀 워크플로 + `/analyze-before-change`(§4).

## 1. 배경 · 문제

리포트(`docs/consulting/out/design-ux-report.md`):
- **§53-60 (P0):** 첫 진입에 그래프가 비어 있고 도달까지 **4단계**(학교급→학년→대/중/소단원 Tree→개념 목록→[선수지식 확인] 버튼). "서비스의 얼굴"이 첫 화면에선 텅 빈 카드 4개. 줌·미니맵·검색·"현재 위치" 표식 없음.
- **§161-174 (B-2 비전):** "지도+사이드 디테일" 단일 캔버스. 상단 한 줄(학교급▾·단원 검색🔍·현재위치 breadcrumb) / 좌 큰 그래프 캔버스(상시·줌·전체보기·리셋·범례) / 우 선택개념 디테일 + [이 개념 진단받기].

코드 조사(2026-06-24):
- 현 `ConceptView.vue`: 4개 카드(SelectButton 학교급 / Listbox 학년 / Tree 단원 / Listbox 개념) 캐스케이드 watch 체인 → 개념 선택 → **별도 "선수지식 확인" 버튼** 클릭 → `useConceptGraph.initGraph`로 그래프 렌더.
- **전역 개념 검색 API 부재** — `ConceptController`는 chapterId 캐스케이드(`""`, `/{id}`, `/nodes`, `/edges`, `/prerequisite`)뿐. 자유텍스트 검색은 신규 엔드포인트 필요.
- 그래프 렌더링은 spec-03에서 `composables/useConceptGraph.js`로 추출 완료(ConceptView·ResultView 공유). 색(GRADE_COLORS 3계열×명도)·클릭 선택유지 이미 처리됨.

## 2. 결정 (사용자 확정 2026-06-24)

- **D0 · 후보안 = "후보 2"**(2-pane 단일 캔버스 + **자유텍스트 개념 검색**). 시안 비교 후 확정. 리포트 비전에 가장 충실하며 백엔드 신규 엔드포인트를 수반.
- **D1 · 진입 = 검색 1차.** 자유텍스트 개념명 검색(디바운스 자동완성 드롭다운)이 기본 진입. **학교급 드롭다운만 선택적 필터로 유지**(검색 범위 좁히기). 학년/단원/개념 캐스케이드(Tree·Listbox 체인)는 **제거**.
- **D2 · 그래프 상시 + 자동 렌더.** "선수지식 확인" 버튼 제거 → 검색 결과/노드 선택 즉시 그래프 렌더. 첫 로드는 안내 placeholder("개념을 검색하면 지도가 그려져요"). 기본 개념 자동표시는 이번 범위 밖.
- **D3 · 그래프 컨트롤** — 줌(＋/－)·전체보기(fit)·리셋 상시 노출 + "현재: 중3 > 이차방정식" breadcrumb. `useConceptGraph`에 zoom/fit/reset 헬퍼 **additive** 추가.
- **D4 · 진단 CTA** — 우측 디테일 패널에 "이 개념 진단받기" → 기존 `/diagnosis` 일반 진입으로 라우팅. **개념별 맞춤 진단은 백엔드 부재로 Out**.
- **D5 · 리포지토리 = JdbcTemplate** 메서드 추가(기존 `findOneByConceptId`와 동일 `concepts JOIN chapters` 패턴 — 구조적 변경 아님, ADR 불필요).
- **D6 · 검색 DTO = 신규 경량** `ConceptSearchResponse`(id·name·schoolLevel·gradeLevel·semester·chapterName — `concept_description` 제외).

## 3. 범위

### In
**백엔드 (api/) — 신규 추가만, 기존 계약 무변경**
1. `dto/concept/ConceptSearchResponse.java` — 경량 DTO(D6).
2. `JdbcTemplateConceptRepository.searchByName(String q, String schoolLevel, int limit)` — `concept_name LIKE ?` 검색(접두 우선 정렬), 학교급 선택 필터, LIMIT. RowMapper 재사용/경량.
3. `ConceptService.searchConcepts(String q, String schoolLevel, int limit)` — 위임 + 변환. 빈/짧은 q 가드.
4. `ConceptController` `GET /api/v1/concepts/search?q=&schoolLevel=&limit=` — permitAll 경로(`/concepts/**`) 자동 커버.
5. 최소 단위 테스트(검색 리포지토리/서비스).

**프론트 (web/)**
6. `composables/useConceptGraph.js` — `zoomIn/zoomOut/fit/reset` 헬퍼 **additive** 추가(cy 인스턴스 위임). 반환 객체 확장만 → 기존 소비자 무영향.
7. `views/ConceptView.vue` — **전면 재작성**: 상단 검색바(디바운스 자동완성)+학교급 필터+breadcrumb / 좌 상시 그래프 캔버스(자동 렌더·줌·전체보기·리셋·범례) / 우 선택개념 디테일 + 진단 CTA. 노드 클릭 → 우측 디테일 갱신(기존 거동 유지). 토큰(spec-06 역할 클래스·의미 토큰)·셸(spec-07 글로벌 내비) 채택.

### Out
- 개념별 맞춤 진단 백엔드(D4) · 전체 그래프 벌크 조회 · 미니맵 · 게이미피케이션(리포트 §239) · 기본 개념 자동표시 · Pretendard 웹폰트 · 검색 인덱스 튜닝(1631행 trivial).

## 4. analyze-before-change — 참조 스캔 (완료 2026-06-24)

- **useConceptGraph 소비자**: `ConceptView.vue`(주체, 재작성)·`ResultView.vue`. ResultView는 `{ initGraph, destroy, GRADE_COLORS }`만 구조분해 → zoom/fit/reset **additive 추가는 무영향**. `initGraph` 시그니처·색·렌더 동치성 유지(spec-03 그대로).
- **concepts API 호출 지점**: ConceptView(`?chapterId`,`/{id}`,`/nodes`,`/edges`)·ResultView(`/nodes`,`/edges`,`/{id}`). 기존 엔드포인트 전부 보존(ResultView 의존). 신규 `/search`는 순수 추가, 계약 파괴 0.
- **ConceptView 라우트 링크**: `AppTopbar`(개념 탐색)·`HomeView`(2곳)·router `/concept`. 라우트 불변 → 무영향. `/concept` 인증 가드 없음(공개) — 검색 엔드포인트도 permitAll 정합.
- **백엔드 참조**: `ConceptService`/`ConceptController` 생성자 의존성 추가 없음(기존 `jdbcTemplateConceptRepository` 위임). 실제 ctor = `(ConceptRepository, KnowledgeSpaceRepository, JdbcTemplateConceptRepository, RedisUtil)` — api/CLAUDE.md M1 노트의 `Optional<MysqlConceptRepository>` 기술은 M2 이후 stale(디스크 신뢰).
- **테스트**: 백엔드 concept 테스트 다수 존재(`ConceptServiceTest` 등) — 검색 메서드용 최소 테스트 신규. 웹 테스트 프레임워크 없음 → 빌드·lint·dev 배선(어시스턴트) + 사람 시각/클릭 검증.
- **스키마·마이그레이션**: 없음(읽기 전용 추가, 무 스키마 변경). `concepts` ~1631행 → LIKE 풀스캔 trivial, 인덱스 불필요.
- **ADR**: concept 검색/그래프 진입 관련 기존 ADR 없음. 패턴 재사용이라 신규 ADR 불필요.

**위험도: 중간** (데이터/스키마 리스크 낮음·additive·무마이그레이션 / 중간 사유는 핵심 화면 전면 재작성 회귀 가능성 하나).

## 5. 변경 단계 (Task 단위 커밋)

1. spec-09 작성(본 문서).
2. 백엔드 검색 엔드포인트(DTO+Repository+Service+Controller+테스트). `./gradlew test` PASS.
3. `useConceptGraph` zoom/fit/reset additive. ResultView 동치성 확인.
4. ConceptView 2-pane 전면 재작성 + 토큰·셸 채택. 빌드·lint PASS.
5. roadmap·handoff 동기화 + 사람 시각검증.

## 6. 롤백 시나리오

- 전용 브랜치 + Task별 커밋 → `git revert`/브랜치 폐기로 즉시 복원. 구 ConceptView는 git 히스토리에 보존.
- DB/스키마 상태 없음 → 롤백 부작용 0. 백엔드 엔드포인트는 additive라 단독 잔존해도 무해.
- 피처 플래그 불필요(위험도 중간 이상 *마이그레이션*이 정책 대상 — 본 건은 UI 재설계+additive 읽기, git revert로 즉시 복원 충족).

## 7. 검증

- **어시스턴트**: 백엔드 `./gradlew test`·`compileJava` / 프론트 `npm run build`·`npm run lint` / dev 배선 + 검색→그래프→노드클릭 흐름 배선 확인.
- **사람**(시각/클릭, [[feedback_visual_verification_handoff]] — dev 기동까지 어시스턴트 몫): 검색 자동완성→선택→그래프 렌더, 줌/전체보기/리셋, 노드 클릭→우측 디테일, breadcrumb, 토큰 위계, 반응형(모바일 세로 스택). 로컬 풀스택·시드 전제 [[project_personalview_local_verification]].
</content>
</invoke>
