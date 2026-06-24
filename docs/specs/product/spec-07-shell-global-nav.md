# spec-07 · 셸 전환 Phase 1 — 좌측 사이드바 → 상단 글로벌 내비 (P1)

> 트랙: [Design] 실배포 전 리디자인 — 셸 전환(리포트 P1 #5, §129·§231·§248 #2). 정본 진행상태 = `docs/roadmap.md` + `design-redesign-handoff.md`
> 작업 브랜치: `feat/shell-global-nav` (토큰 브랜치 `feat/design-tokens-typography` 위 스택 — 토큰 채택 위함). Task 단위 커밋.
> 상태: spec 합의 대기 → 합의 후 코드 단계
> 무게: 전역 프레임·다층(레이아웃/CSS/컴포넌트) 변경·가역성 낮음 → **정식 spec + `/analyze-before-change`**(§3.1 참조 스캔 완료). 단 PrimeVue `Menubar` 재사용으로 신규 위젯 자작 없이 범위 축소.

## 1. 배경 · 문제

리포트(`docs/consulting/out/design-ux-report.md`) 총평 §23·§31, 방향1 §129, 로드맵 P1 §231, 열린질문 §248 #2:

- 현재 셸 = **관리자 사이드바(Sakai 템플릿)** + 토프바. "B2C 학습 서비스가 아니라 사내 어드민"처럼 보이는 가장 큰 원인. 인상 개선 폭이 가장 크나 라우팅/레이아웃 손이 가장 많이 감 → P1, 의사결정 필요(§248 #2 = "정말 갈아엎는다" 사용자 승인).
- 좌측 세로 메뉴(홈/선수지식/맞춤학습 4종/마이페이지)는 화면을 좁히고, 학습 서비스의 수평적 정보구조와 맞지 않음.

### 1.1 사실확인 (코드 레벨, 2026-06-24 조사)

- **현재 셸 구성**: `AppLayout.vue`(토프바 + `.layout-sidebar`(`AppSidebar`→`AppMenu`→`AppMenuItem`) + 메인 + 푸터 + 로그인 다이얼로그). 사이드바 토글 = `AppTopbar` 햄버거 → `useLayout.onMenuToggle` → `layoutState`(static/overlay/mobile).
- **메뉴 모델**(`AppMenu.vue`): 홈(/) · 선수지식(/concept) · 맞춤학습(진단 /diagnosis · 채점 /record · 결과 /result · 맞춤출제 /personal) · 마이페이지(회원수정 /user-edit).
- **라우트**(`router/index.js`): 위 + /login(OauthLogin) · /signup · /error. 전부 `AppLayout` children(셸 안). 라우트 가드 거의 없음(비로그인 샘플 허용 — 예: /result).
- **PrimeVue `Menubar` 전역 등록됨**(`main.js:174`; `Menu`·`Avatar`·`TieredMenu`·`Button`도) → 가로 내비+서브메뉴 드롭다운+**모바일 햄버거 반응형 내장** 컴포넌트를 자작 없이 사용 가능.
- **토큰**: spec-06 `_tokens.scss`(브랜치 스택) 사용 가능 → 신규 내비 텍스트/색은 역할 클래스·의미 토큰 채택.

## 2. 범위

### In (Phase 1)
1. **상단 글로벌 내비 도입** — `AppLayout`의 좌측 사이드바를 폐기하고 상단에 PrimeVue `Menubar` 기반 글로벌 내비를 둔다. IA(사용자 결정):
   - `start` 슬롯: **로고**(→ `/`).
   - 내비 모델: **개념 탐색**(/concept) · **진단**(/diagnosis) · **내 학습 ▾**(드롭다운: 채점하기 /record · AI 분석 결과 /result · 맞춤 학습지 출제 /personal).
   - `end` 슬롯: **로그인**(비로그인) / **로그아웃 + 회원정보 수정**(로그인) — 기존 토프바 로그인 동선(`useLoginDialog`) 재사용.
2. **메인 풀폭화** — 사이드바 좌측 오프셋 제거, 콘텐츠 영역이 전체 폭 사용(상단 내비 아래).
3. **은퇴 정리** — `AppSidebar.vue`·`AppMenu.vue`·`AppMenuItem.vue` 제거(닫힌 묶음, §3.1). `AppLayout`의 사이드바 outside-click 로직(`.layout-sidebar`/`.layout-menu-button` querySelector watch) 제거. `AppTopbar` 햄버거 제거.
4. **토큰 채택** — 신규 내비 카피/색은 spec-06 역할 클래스·`--mmt-*` 의미 토큰 사용.
5. **모바일 비파손** — `Menubar` 내장 반응형(햄버거 토글)로 모바일에서 내비 접힘. 전용 하단 탭은 Phase 3(Out).

### Out (후속 Phase / 별도)
- **학습 스텝 인디케이터**(진단→채점→분석→맞춤) — **Phase 2 별도 spec**. 흐름 페이지(diag/record/result/personal)에 맥락적 표시.
- **모바일 하단 탭 바** — **Phase 3 별도**. 본 Phase는 `Menubar` 반응형 햄버거로 모바일 대응만.
- **라우트 가드/권한 재설계** — 비로그인 접근 정책은 현행 유지(내비 노출만 로그인 상태로 분기, 하드 가드 신설 안 함).
- **`useLayout` 컴포저블 정리/삭제** — 사장된 sidebar-toggle 익스포트는 남겨둠(타 영향 0, 과도 정리 회피). `layoutConfig` 테마 클래스는 유지.
- **테마/다크모드·`AppConfig`·스케일 토글** — 본 범위 밖.
- **푸터·로그인 다이얼로그 동작 변경** — 없음(위치만 풀폭 메인 하단 유지).

## 3. 결정 (사용자 승인 2026-06-24)

- **D1 · 범위 = Phase 1(상단 내비)만.** 스텝 인디케이터·모바일 하단탭은 후속 Phase(단계적 리스크 분산). §248 #2 "갈아엎기"의 핵심(사이드바 폐기)만 이번에.
- **D2 · 내비 IA = 로고 / 개념 탐색 / 진단 / 내 학습 ▾(채점·결과·맞춤출제) / 로그인.** 맞춤학습 4경로 중 진단은 1차 진입점으로 평면 노출, 나머지 3은 "내 학습" 드롭다운으로 묶어 상단을 깔끔히.
- **D3 · 구현 = PrimeVue `Menubar` 재사용.** 자작 내비/모바일 토글 대신 테마 일관·반응형 내장 컴포넌트 활용(footprint·리스크 축소).

### 3.1 analyze-before-change — 참조 스캔(완료)

- **은퇴 3종은 닫힌 묶음**: `AppMenuItem`←`AppMenu`←`AppSidebar`←`AppLayout`만. 외부 참조 없음 → 삭제 안전.
- **sidebar-toggle 심볼 사용처**: `onMenuToggle`/`isSidebarActive`/`setActiveMenuItem` = `AppLayout`(watch+querySelector) · `AppTopbar`(햄버거) · `AppMenuItem`(은퇴). 셸 교체 후 전부 비소비 → `layout.js`엔 남기되(무영향) 소비처 제거.
- **CSS**: Sakai 레이아웃이 `.layout-main-container`에 사이드바 폭만큼 오프셋 부여(`layout-static` body class). 풀폭화는 사이드바 엘리먼트·상태 클래스 제거 + 메인 오프셋 0 오버라이드로 처리(신규 `_shell.scss` 또는 AppLayout scoped).
- **영향 테스트**: web 테스트 프레임워크 없음 → 런타임 수동 검증(빌드·dev 배선 어시스턴트 + 사람 시각/클릭 검증).
- **롤백**: 셸 파일 한정 변경 → `git revert` 또는 파일 복원으로 즉시 원복(§7).

## 4. 설계

### 4.1 레이아웃 재편 (`AppLayout.vue`)
```
┌ 상단 글로벌 내비 (Menubar, sticky) ───────────────────────────┐
│ [로고]  개념 탐색  진단  내 학습 ▾            [로그인/로그아웃]  │
└───────────────────────────────────────────────────────────────┘
┌ 메인 (router-view, 풀폭) ─────────────────────────────────────┐
│                                                               │
└───────────────────────────────────────────────────────────────┘
┌ 푸터 ─────────────────────────────────────────────────────────┘
  + <login-dialog/> (전역, 변경 없음)
```
- `.layout-sidebar` 제거. `<app-topbar>`(→ 글로벌 내비 호스트) + `.layout-main-container`(메인+푸터) 유지하되 좌측 오프셋 0.
- `containerClass`에서 사이드바 상태 클래스(`layout-static`/`layout-static-inactive`/`layout-overlay`/mobile-active) 제거, 테마 클래스만 유지. outside-click watch/리스너 삭제.

### 4.2 글로벌 내비 (`AppTopbar.vue` 재작성)
- PrimeVue `<Menubar :model="navModel">` + `#start`(로고 router-link `/`) + `#end`(로그인/로그아웃 영역).
- 내비 모델(SPA 이동은 `command: () => router.push(...)` 또는 `#item` 슬롯 router-link — 구현 시 active 하이라이트 위해 `#item` 슬롯+`router-link` 우선):
  ```
  [
    { label: '개념 탐색', icon: 'pi pi-sitemap', to: '/concept' },
    { label: '진단', icon: 'pi pi-file', to: '/diagnosis' },
    { label: '내 학습', icon: 'pi pi-compass', items: [
      { label: '채점하기', icon: 'pi pi-check-square', to: '/record' },
      { label: 'AI 분석 결과', icon: 'pi pi-chart-line', to: '/result' },
      { label: '맞춤 학습지 출제', icon: 'pi pi-book', to: '/personal' },
    ]},
  ]
  ```
- `#end`: 기존 로그인 상태 로직(`localStorage.accessToken` + `store.state.accessToken` watch) 보존. 비로그인 → `Button`("로그인", `useLoginDialog().open()`). 로그인 → "로그아웃"(기존 `logout()`) + 회원정보 수정(/user-edit) 진입(작은 `Menu`/`Avatar` 드롭다운 또는 링크).
- 로고/카피/색은 spec-06 토큰(`--mmt-brand` 등) 채택.

### 4.3 스타일
- `Menubar`는 테마(lara-light-indigo) 스킨 → 룩앤필 자동 일관. sticky top.
- 메인 풀폭 오버라이드: `.layout-main-container { margin-left: 0; }` + 사이드바 폭 padding 제거(Sakai `_main.scss`/layout grid 상쇄). 신규 `assets/layout/_shell.scss`로 격리 후 `styles.scss` import(토큰 뒤).
- 반응형: `Menubar`가 좁은 폭에서 햄버거로 접힘(내장). 별도 미디어쿼리 최소화.

## 5. Task 분해 (Task 단위 커밋)
1. **Task 1 · 글로벌 내비(`AppTopbar` 재작성)** — `Menubar` 내비 모델 + 로고 start + 로그인/로그아웃·회원수정 end. 햄버거·사이드바 토글 의존 제거. (이 시점 사이드바 아직 존재 가능 — 단, Task2와 한 흐름으로 검증.)
2. **Task 2 · `AppLayout` 재편 + 풀폭 CSS** — 사이드바 div·`AppSidebar` import·outside-click watch 제거, `containerClass` 정리, `_shell.scss` 풀폭 오버라이드 + import.
3. **Task 3 · 은퇴 파일 삭제** — `AppSidebar.vue`·`AppMenu.vue`·`AppMenuItem.vue` 제거(참조 0 확인됨).
4. **Task 4 · docs** — roadmap [Design] 셸 Phase 1 + 핸드오프 갱신, Phase 2/3 백로그 명시.

## 6. 검증
- 빌드(`npm run build`) PASS · lint 신규 에러 0 · `npm run dev` 배선 PASS — 어시스턴트.
- 코드 레벨: 내비 각 항목/드롭다운/로고/로그인 동선이 올바른 라우트·다이얼로그로 가는지, 사이드바 잔재(빈 좌측 오프셋·깨진 햄버거) 없는지 — 어시스턴트.
- **최종 시각/클릭 검증(데스크톱: 내비·드롭다운·로그인 상태 전환·풀폭 / 모바일: 햄버거 접힘) = 사람**([[workflow_pattern]] 분담, web 자동화 없음, [[feedback_visual_verification_handoff]] — 어시스턴트가 dev 기동까지).

## 7. 롤백
- 변경 한정: `AppLayout.vue`·`AppTopbar.vue`·신규 `_shell.scss`·`styles.scss`(1줄 import) + 은퇴 3파일 삭제. 라우터/스토어/백엔드/마이그레이션 무변경. `git revert`(삭제 파일 포함 복원) 또는 브랜치 폐기로 즉시 원복. 데이터 리스크 0.
