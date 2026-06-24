# spec-06 · 디자인 토큰 1벌 + 타이포 스케일 (P1, C안 착수)

> 트랙: [Design] 실배포 전 리디자인 — 디자인 시스템 토큰 레이어(C 권장). 정본 진행상태 = `docs/roadmap.md` + `design-redesign-handoff.md`
> 작업 브랜치: `feat/design-tokens-typography` (main 분기) · Task 단위 커밋
> 상태: spec 합의 대기 → 합의 후 코드 단계(구현)
> 무게: 토큰 레이어 신설은 전역 영향이나, **정의 + 단일 파일럿(HomeView)** 으로 범위를 좁혀 경량 spec. 전역 sweep·글꼴·base scale 변경은 Out(아래). 데이터/라우팅/마이그레이션 배선 없음 → `/analyze-before-change` 불요.

## 1. 배경 · 문제

리포트(`docs/consulting/out/design-ux-report.md`)의 디자인 시스템 결손:

- **P1 · 컬러 토큰 부재(§114).** 인디고 테마 디폴트 + Cytoscape 하드코딩 색 + 카피별 빨강/파랑 인라인. 브랜드/의미색 한 벌이 없음.
- **타이포 스케일 부재(§127).** 본문 기준 모듈러 스케일 없이 PrimeFlex `text-2xl/3xl/4xl`을 라벨에 남발. 위계가 자의적.
- **권장(§216, §233):** "Tailwind+토큰" 풀 레이어는 React 이주 범위(02번)와 얽힘 → 이번엔 **CSS 변수 토큰(`tokens.scss`) + 의미 역할 클래스**만 Vue/SCSS 안에서 가볍게 도입하고, 컴포넌트는 점진 채택.

### 1.1 사실확인 (코드 레벨, 2026-06-24 조사)

- 스타일 스택: SCSS Sakai 템플릿(`src/assets/layout/*`) + PrimeFlex `^3.3.1` + PrimeVue `3.39.0` 테마 `lara-light-indigo`(index.html `<link id="theme-css">`로 로드). **Tailwind 없음.**
- 전역 SCSS 엔트리 = `src/assets/styles.scss`(`main.js`가 import) → 토큰 파트셜은 여기에 `@import`.
- 테마가 런타임 CSS 변수 제공: `--primary-color`/`--primary-color-text`, `--surface-0..900`/`--surface-card`/`--surface-border`/`--surface-ground`, `--text-color`/`--text-color-secondary`, 팔레트 `--green-*`/`--yellow-*`/`--red-*`. → **컬러 의미 토큰은 대부분 이 위 별칭(alias)** 이라 가벼움.
- 타이포 클래스 실태: `text-2xl` 59회, `text-3xl` 43회, `text-4xl` 38회, `text-lg` 67회 등 ~300곳(`*.vue`). 하드코딩 hex 9곳(DiagView 3·ResultView 2·LoginDialog 2·HomeView 1·ConceptView 1).
- 기존 토큰 자산: 그래프 학년 3색은 이미 토큰화됨(`composables/useConceptGraph.js` `GRADE_COLORS`, spec-03). 본 spec과 정합(노란색 퇴출 기조 유지).
- 기본 폰트: `_variables.scss` `$scale:14px`. 리포트 권장 16px와 불일치하나 **전역 변경은 Out**(아래).

## 2. 범위

### In
1. **`src/assets/_tokens.scss` 신설** — `:root` CSS 변수 1벌:
   - **타이포 모듈러 스케일**(본문 16px 기준 1.25): display/title/heading/subheading/body/caption + line-height + font-weight 토큰.
   - **컬러 의미 토큰**: 브랜드 1색 + 텍스트/뮤트/표면/보더 + 의미 3색(성공/주의/위험). 기존 테마 변수 위 별칭.
2. **의미 역할 타이포 클래스**(`.t-display/.t-title/.t-heading/.t-subheading/.t-body/.t-caption`) — `text-2xl/4xl` 자의적 사이즈를 역할 어휘로 대체.
3. **`styles.scss`에 `@import '_tokens.scss'`** 배선.
4. **HomeView 파일럿 채택** — `web/src/views/HomeView.vue`의 `text-2xl/3xl/4xl` 등 자의적 타이포를 역할 클래스로, 인라인/하드코딩 색을 의미 토큰으로 전환(거동·레이아웃 동등, 시각 위계만 토큰화).
5. **사용 가이드 한 단락** — spec §4.3에 "언제 어떤 역할 클래스/토큰을 쓰나" 기록(후속 화면이 참조).

### Out (이번 범위 밖)
- **전역 sweep** — Home 외 10개 뷰의 `text-*`/색 일괄 전환. 각 화면이 트랙에서 손댈 때 자연 채택(retrofit 0 전략). 본 spec은 HomeView만.
- **Pretendard 등 한글 웹폰트 도입(§127)** — 별도 작은 작업으로 분리(자산·로딩 성능·라이선스·전역 시각 변화라 독립 검증 이득). 본 spec은 글꼴 패밀리 미변경(테마 기본 유지).
- **`$scale` 14→16px 전역 변경** — 앱 전체 시각 시프트라 리스크 큼. 역할 클래스 `.t-body`가 적용처에서 16px를 세팅하되, 전역 base는 후속 결정(별도).
- **Tailwind/PrimeVue unstyled·pass-through 프리셋(C 풀안)** — React 이주(02번)와 묶인 큰 결정 → ADR 후속.
- **그래프 색 재정의** — 이미 spec-03에서 토큰화됨. 본 spec은 의미색 vocabulary만 추가, `GRADE_COLORS`는 손대지 않음.
- **컴포넌트 라이브러리 교체·버튼/카드 등 위젯 리스킨** — 토큰 위에 얹는 후속.

## 3. 결정 (사용자 승인 2026-06-24)

- **D1 · 토큰 범위 = 타이포 + 컬러 의미토큰.** spacing/트래킹은 PrimeFlex가 커버·불만 낮아 제외.
- **D2 · Pretendard 보류.** 이번 spec은 스케일/컬러 구조만. 글꼴은 별도.
- **D3 · 채택 = 정의 + HomeView 파일럿 1화면.** 레이어를 end-to-end 검증하되 big-bang sweep 회피. 나머지는 셸·ConceptView·폼 작업 시 채택.

## 4. 설계

### 4.1 타이포 토큰 + 역할 클래스 (`_tokens.scss`)

모듈러 스케일(본문 16px, 비율 1.25, 반올림):

| 역할 클래스 | size 토큰 | 값(rem/px) | weight | line-height | 용도 |
|---|---|---|---|---|---|
| `.t-display` | `--mmt-fs-display` | 2.441rem ≈ 39px | 700 | 1.15 | 히어로 H1 |
| `.t-title` | `--mmt-fs-title` | 1.953rem ≈ 31px | 700 | 1.2 | 페이지 제목 |
| `.t-heading` | `--mmt-fs-heading` | 1.5625rem ≈ 25px | 600 | 1.25 | 섹션 헤딩 |
| `.t-subheading` | `--mmt-fs-subhead` | 1.25rem = 20px | 600 | 1.3 | 카드/스텝 소제목 |
| `.t-body` | `--mmt-fs-body` | 1rem = 16px | 400 | 1.6 | 본문 |
| `.t-caption` | `--mmt-fs-caption` | 0.875rem = 14px | 400 | 1.4 | 보조·설명(뮤트색) |

```scss
:root {
  --mmt-fs-display: 2.441rem;  --mmt-fs-title: 1.953rem;  --mmt-fs-heading: 1.5625rem;
  --mmt-fs-subhead: 1.25rem;   --mmt-fs-body: 1rem;       --mmt-fs-caption: 0.875rem;
  --mmt-lh-tight: 1.2;  --mmt-lh-snug: 1.3;  --mmt-lh-normal: 1.6;
  --mmt-fw-regular: 400; --mmt-fw-medium: 500; --mmt-fw-semibold: 600; --mmt-fw-bold: 700;
}
.t-display { font-size: var(--mmt-fs-display); font-weight: var(--mmt-fw-bold); line-height: 1.15; }
/* …title/heading/subheading/body/caption 동형… caption 은 color: var(--mmt-text-muted) */
```

### 4.2 컬러 의미 토큰 (`_tokens.scss`, 테마 위 별칭)

```scss
:root {
  --mmt-brand:        var(--primary-color);
  --mmt-brand-text:   var(--primary-color-text);
  --mmt-text:         var(--text-color);
  --mmt-text-muted:   var(--text-color-secondary);
  --mmt-surface:      var(--surface-card);
  --mmt-surface-sunken: var(--surface-ground);
  --mmt-border:       var(--surface-border);
  --mmt-success:      var(--green-500);
  --mmt-warning:      var(--yellow-700);  /* yellow-500 은 흰 배경 대비 부족 → 700 단계로 ≥대비 확보(리포트 노란색 퇴출 기조) */
  --mmt-danger:       var(--red-500);
}
```

- 원칙: 컴포넌트는 raw hex·테마 변수 직접참조 대신 `--mmt-*` 역할 토큰을 참조. 향후 브랜드 채도 조정 시 한 곳(별칭)만 수정.
- 그래프 학년 3색(`GRADE_COLORS`)은 본 토큰과 별개 도메인 팔레트로 공존(spec-03), 변경 없음.

### 4.3 사용 가이드 (후속 화면 참조)
- **제목·헤딩은 역할 클래스로**: `text-4xl`/`text-2xl` 직접 대신 `.t-display`/`.t-title`/`.t-heading`. "더 크게"가 아니라 "무슨 역할"인지로 고른다.
- **본문/보조는 `.t-body`/`.t-caption`**. 보조 설명은 `.t-caption`(뮤트색 자동).
- **색은 의미로**: 위험/경고/성공은 `--mmt-danger/-warning/-success`, 강조는 `--mmt-brand`, 보조 텍스트는 `--mmt-text-muted`. 인라인 hex 금지.
- **간격은 기존 PrimeFlex 유틸**(`p-*`/`m-*`/`gap-*`) 유지 — spacing 토큰은 이번 범위 밖.

### 4.4 배선
- `_tokens.scss`를 `styles.scss`에서 `@import`(레이아웃/PrimeFlex 뒤). 테마 변수는 index.html `<link>`로 런타임 로드되므로 `var(--primary-color)` 별칭은 정상 해석.

## 5. Task 분해 (Task 단위 커밋)
1. **Task 1 · `_tokens.scss` 신설 + 배선** — 타이포 토큰·역할 클래스·컬러 의미 토큰 정의, `styles.scss` import. 채택 없음(정의만) → 기존 화면 거동 불변.
2. **Task 2 · HomeView 파일럿 채택** — `HomeView.vue` 자의적 `text-*`/하드코딩 색 → 역할 클래스/의미 토큰 전환(레이아웃 동등).
3. **Task 3 · docs** — roadmap [Design] 토큰 항목 + 핸드오프 갱신, 후속(전역 sweep·Pretendard·base scale) 백로그 명시.

## 6. 검증
- 빌드(`npm run build`) PASS · lint 신규 에러 0 · `npm run dev` 배선 PASS — 어시스턴트.
- Task1 후: 토큰 정의만 → 기존 화면 시각 무변경(회귀 없음) 확인 — 어시스턴트.
- Task2 후: HomeView가 토큰 적용 전후 레이아웃 동등하며 위계만 정돈됐는지 — **최종 시각 검증(데스크톱/모바일) = 사람**([[workflow_pattern]] 분담, web 자동화 없음).

## 7. 롤백
- `_tokens.scss` 신설 + `styles.scss` 1줄 import + `HomeView.vue` 클래스 치환뿐. 라우터/스토어/백엔드/마이그레이션 무변경 → `git revert` 또는 파일 단위 복원으로 즉시 원복. 리스크 0(정의 Task는 채택 없어 무영향, 파일럿 Task는 단일 뷰).
