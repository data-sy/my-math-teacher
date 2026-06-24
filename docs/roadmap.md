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

- **[Infra/Data] 로컬 DB 초기화(시드) 정의** (spec-04 검증에서 발견, 2026-06-24)
  - 로컬 `mmt` DB 가 비어 있고(fresh 볼륨), `api/sql/` 시드 26개가 v1/non-v1 혼재·순서/FK 의존이 얽혀 "한 방에 채우는" 정본 초기화 절차가 없음. **`probabilities` 는 AI 예측 생성물이라 시드에 아예 없음**(weakness-diagnosis 가 의존). 그래서 결과 화면 실데이터 검증 때마다 수동 합성이 필요.
  - 범위: ① create.sql + 필요한 insert 들을 의존 순서로 묶은 **단일 초기화 스크립트**(또는 `docker-entrypoint-initdb.d` 마운트) ② depth-0 포함 대표적 진단 1건 + probabilities 합성 시드(데모/검증용) — [Data] depth-0 백로그와 통합. **M4 의 "RDS 스키마·시드 적재(R4)" 가 이 정본 시드를 재사용**하므로 M4 진입 전 정리하면 이득.
  - 참고: spec-04 검증은 `scratchpad/seed_min.sql`(화면 필요분 최소 가짜 데이터)로 우회함 — 정본 아님, 검증 후 비움.
- **[Design] ResultView 선수지식 트리 "누적해서 보기" affordance** (spec-04 D5 에서 이월, 2026-06-24)
  - spec-04 에서 결과 화면 raw 표를 삭제하며 표 그룹헤더의 "선수지식 트리 누적해서 보기" 버튼도 사라짐. 카드의 "선수지식 트리 보기"가 `showTree`(=`knowledgeSpace` 누적 push)라 **동작상 누적은 이미 유지**되나(여러 카드 클릭 시 한 트리에 쌓임), "여러 약점 개념을 한 선수지식 트리에 누적해 비교/탐색한다"는 의도를 **명시적 UI 로 살리는** 건 별도 설계 필요.
  - 검토 범위: 누적 상태 시각 표시(어떤 약점들이 트리에 올라와 있는지)·개별 제거·"전체 누적 보기" 진입점. 현 `clearCy`(화면 비우기)와의 관계. 사용자 결정: "어떤 식으로 어디에 살릴지"는 열림.
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
- **[Data] 샘플 진단(sample weakness-diagnosis) depth-0 대표문항 행 누락** (2026-06-24) — 비로그인 샘플 학습지(`/weakness-diagnosis/sample/{id}`, 예 userTestId=1)의 `probabilities` 행이 전부 `to_concept_depth≥1` 이고 **depth-0(직접 출제된 대표 개념) 행이 없음**. ResultView 분석결과 표는 문항별 대표(depth-0)를 기준으로 그룹헤더·선수지식 트리 진입을 구성하므로, depth-0 부재 시 대표 개념·트리 진입 버튼이 비고 데모 가치가 떨어짐. 코드는 무가드 역참조 가드(`6098760`)로 크래시는 막아둠(그룹헤더는 행 자체 개념명 폴백, 트리 버튼은 representative 있을 때만). 근본 해결 = 샘플 answer 별 올바른 depth-0 대표 행 시드(item→concept 매핑 기반 INSERT). 실유저 진단 데이터엔 depth-0 가 있어 영향 낮음 → 샘플 품질/데모용 보강 항목. 시드 보강 시 `ProbabilityService` 생성 규칙과 정합 확인.
- **[Design/Ops] 문의·관리자 연락 메일 교체 + 하드코딩 중앙화** (2026-06-24) — 현재 문의 메일 `contact.mmt.2024@gmail.com` 이 연도(2024) 박힌 임시 계정. 실배포 전 영구 계정으로 교체 필요(관리자 계정 정리 포함, 어떤 메일·도메인은 사람 게이트 결정). 더해 이 문자열이 web src **6곳에 복붙 하드코딩**(`AppFooter.vue`×1·`DiagView.vue`×2·`RecordView.vue`×2·`PersonalView.vue`×1) → 교체 시 누락 방지 위해 단일 상수/설정(공용 constants 또는 env)으로 **중앙화 후 1곳 수정** 권장. 리디자인 트랙 P2 카피/브랜딩 위생과 함께 처리 가능.
- **[Research] 맞춤 출제 전용 알고리즘 조사·학습** (2026-06-23) — 현재 출제 로직은 휴리스틱(depth 우선순위 tier + round-robin fill). 학습자 약점 기반 문항 추천에는 전용 분야가 있을 가능성: **추천 시스템(recommender systems), 문항반응이론(IRT)·컴퓨터적응검사(CAT), 지식추적(Knowledge Tracing) 기반 문항 선택**. 당장 구현 아님 — 공부 후 적용 가능하면 비율 배분/Scope B 출제 로직을 원리 기반으로 대체 검토. (DKT 모델이 이미 있으므로 KT 계열과의 연계가 자연스러울 수 있음.)
- **[Design] 실배포 전 리디자인** (전문가 페르소나 컨설팅 2건 수렴, 2026-06-23)
  - 2년 묵은 디자인으로 실서비스 배포를 앞두고 정리. 리포트: `docs/consulting/out/design-ux-report.md`, `react-migration-scope.md`. **결론: launch 까지 Vue 유지 + 리디자인도 Vue 에서 + React 이주는 launch 후 재검토.**
  - 이월 UI 항목:
    - ~~카피 일제 정리 (영문·오타 → 한국어 일관)~~ — ✅ `c9afabf`(영문 라벨/버튼 한국어 전환) + `63a5cff`(오타: 을 오기·HOME→홈·로그아웃 줄바꿈) 처리. 브랜드/기술명/푸터 법적 문구는 영문 유지. 표기: 버튼 예/아니오.
    - ~~그래프 응급 접근성 (노드 크기·호버→클릭 선택·색 대비)~~ — ✅ spec-03 처리(브랜치 `feat/pre-launch-redesign`). spec `docs/specs/product/spec-03-graph-accessibility-emergency.md`. ConceptView·ResultView 에 복붙돼 있던 Cytoscape 렌더링 로직을 `composables/useConceptGraph.js` 로 추출(거동 불변, `4437e83`)한 뒤 한 곳에서 응급 수정: ① 학년색 12색→학교급 3색(초 green/중 violet/고 magenta)+명도 3단계, 노란색 퇴출, 전 색 흰배경 ≥3:1(WCAG 1.4.11), 색 진실원천 `GRADE_COLORS` 1곳·범례 바인딩(`2fdc0bc`) ② 호버전용→클릭 선택유지+빈배경 해제(모바일 대응)·노드 7→14·폰트 7→11(`75498ed`). 빌드 PASS, lint 신규에러 0(추출로 기존 dead-code error 2건 정리), dev 배선 PASS. **런타임 시각검증 PASS**(2026-06-24, 사람: ConceptView 포물선 그래프 — 노드 가독·노란색 제거·학교급 3색+명도·클릭 선택유지·빈배경 해제 확인. 시각리뷰로 중등 violet 명도 간격 확대 `97830e1`. ResultView 그래프는 동일 `useConceptGraph` 컴포저블이라 동치성으로 갈음 — 샘플 depth-0 누락으로 트리 직접 진입은 불가, 별도 [Data] 백로그). B-2 단일캔버스 진입 재설계·줌/검색/미니맵·전역 토큰화는 이번 범위 밖(Later).
    - ~~DiagView 비로그인 다운로드 다이얼로그 **"회원가입 및 로그인" 버튼 라벨/동선 불일치**~~ — ✅ #3 처리(3 Task, `63e284c`+`ec605da`+`01763e4`, 브랜치 `feat/pre-launch-redesign`). spec `docs/specs/product/spec-02-diagview-auth-entry-unification.md`(D1=A 토프바 다이얼로그 재사용, D2=추출+composable). 로그인 다이얼로그를 `components/LoginDialog.vue`로 추출 + 싱글톤 `useLoginDialog()` 트리거 + `AppLayout` 1회 마운트, 토프바 아이콘·DiagView 버튼이 공유. 빌드 PASS, lint 신규에러 0(기존 부채만), 헤드리스 런타임검증 PASS(useLoginDialog 싱글톤 공유 ref 계약 5/5). **런타임 시각 클릭검증 PASS**(2026-06-24, 사람: 비로그인 /diagnosis 다운로드 확인창→[회원가입 및 로그인]→로그인 다이얼로그가 그 자리에서 열림, 토프바 아이콘과 동일 다이얼로그 재사용 확인). PR 대기. (analyze-before-change 발견: 리포트의 "ResultView 복붙 로그인 다이얼로그"는 stale — 정의처는 AppTopbar 단일.)
    - ~~`listboxTest` 무가드 역참조 잔여 (DiagView 다운로드 다이얼로그 ~355·371행)~~ — ✅ `fa991d0` 처리 완료(브랜치 `feat/pre-launch-redesign`). PersonalView `d15f6aa` 와 동일 패턴: watch 해제 시 게이트 초기화 + 두 Dialog 옵셔널 체이닝. **런타임 풀스택 수동검증 PASS**(비로그인 /diagnosis 선택→해제 회귀, 크래시 없음). PR 대기.
  - 리포트 B 시리즈 화면 재설계:
    - **HomeView 가치제안 히어로 + 작동방식 스텝 (B-1)** — ✅ 코드+시각검증 완료, **PR #28 머지 완료(2026-06-24, B-3·PersonalView 묶음 1 PR, main `f93df00`)**. spec `docs/specs/product/spec-05-home-hero-redesign.md`. 리포트 A-1 §46~48·B-1 §140~159·로드맵 P0 §228 #4 의 "가치 제안 없이 기능 카드 5개 평면 나열" 홈을 "히어로(헤드라인+서브카피+1차 CTA 2개) → 작동방식 4스텝 → 그래프 미리보기·샘플" 로 재설계. **결정(사용자 승인 2026-06-24):** D1 톤=학부모(신뢰) / D2 범위=풀 B-1 한 패스(단일 정적 뷰·완전 가역, 어시스턴트 판단) / D3 셸 글로벌 내비 제외(P1 별도 트랙, 사이드바 유지) / D4 사회적 증거는 허위 지표 금지(실데이터 전). **3 Task:** ① 히어로 블록 추가+죽은 주석 코드 제거(CTA: 무료로 진단 시작→/diagnosis, 개념 그래프 둘러보기→/concept, `d5624a8`) ② 작동방식 4스텝(진단→채점→분석→맞춤, 툴팁 설명 본문화, 진단 흐름 4카드 대체, `f7ab821`) ③ 그래프 미리보기+샘플 결과 섹션(선수지식 카드 대체·순서 재배치, 샘플 결과 보기→/result, `f8e4efb`). 순수 프레젠테이션·라우터/스토어/백엔드 무변경. 빌드 PASS, lint 신규에러 0. **검증 완료: 빌드·lint 어시스턴트 / 사람 시각검증 PASS(반응형·카피 톤·CTA affordance — 시각리뷰로 히어로 서브카피 줄바꿈 반영 `d6c1e9e`).** **Out(후속):** 상단 글로벌 내비·셸 전환(P1) · 디자인 토큰/타이포 전역 정리(C안, P1) · 사회적 증거 실데이터 · 게이미피케이션(P2).
    - **ResultView 결과 재설계 (B-3)** — ✅ 코드+검증 완료, **PR #28 머지 완료(2026-06-24, B-1·PersonalView 묶음 1 PR, main `f93df00`)**. spec `docs/specs/product/spec-04-resultview-result-redesign.md`. 리포트 A-3 §65~71·B-3 §176 의 "raw 표·트리만 던지는" 결과 화면을 "헤드라인 요약 → 시급도순 우선순위 약점 카드 → 근거(표·그래프) 강등" 으로 재설계. **결정(사용자 승인 2026-06-24):** D1 약점 카드=문항 단위 / D2 헤드라인=가용 데이터만(weakness-diagnosis 응답에 정답수·점수 없음 — 정답률 게이지는 데이터 확보 후 후속) / D3 차트 미도입(숫자+CSS 막대) / D4 곁다리(뱃지색·빈상태·카피). **4 Task:** ① 카드 모델 가공(문항 그룹→대표개념·가장 약한 선수지식·mastery·시급도, `setPriority` 상대 thirds→절대구간 40/65%, `efd65f5`) ② 헤드라인 요약+우선순위 카드 UI(`0c5c90f`) ③ 표·트리·개념상세를 "근거 더보기"로 강등(progressive disclosure, Cytoscape 언마운트 방지 `v-show`+showTree 가 패널 펼친 뒤 nextTick→initGraph, `bdba58e`) ④ 시급도 뱃지 3색(죽은 'new'=success 제거·'하'=info 추가)·빈 상태 안내형·잔여 영문 토스트('Confirmed'→'안내') 정리(`c91f2be`). 빌드 PASS, lint 신규에러 0, **dev 배선 PASS**(새 식별자 트랜스폼 확인). **미해결 주의:** 시급도 절대 임계 40/65% 는 제안 출발값 — 실데이터 mastery 분포로 도메인 보정 필요. 시각검증은 샘플 depth-0 누락([Data] 백로그)으로 카드/트리가 빌 수 있어 실유저 데이터(로그인) 또는 시드 보강 전제. **개념 단위 약점 집계·정답률 차트는 후속(Out).** **⑤ 런타임 리뷰 반영(D5, `0c9536a`):** 로컬 목 서버+dev 프론트로 런타임 확인(사용자) → 카드-그래프 사이 raw 표가 노이즈로 판명 → **표 강등이 아니라 삭제**(카드 완전 대체) + 죽은 가공 코드 제거, 학습지 목록 좌측 복원, 헤딩 "진단 결과 요약"→"진단 결과". "근거 더보기"엔 선수지식 그래프+개념 상세만 잔존. **"선수지식 트리 누적해서 보기" 명시 affordance 는 Later 백로그로 이월**(아래). **⑥ 실데이터 풀스택 검증 PASS(실 백엔드 bootRun+MySQL+dev):** DB→JdbcTemplate→Spring 직렬화(UTF-8)→실 CORS→Vue 풀 경로 사용자 확인. 발견: 로컬 mmt DB 에 실시드 상존(710/1631), depth-0 포함 데모 진단(90000번대, 검증 후 DELETE·실시드 보존)으로 상중하·트리CTA 풀기능 + 기존 실데이터로 degenerate 상태 동시 확인. **남은 일: 없음(PR #28 머지 완료). 임계 40/65% 보정은 이번 스킵(백로그성).**
    - **PersonalView "개발 중" 노출 정리 (P0)** — ✅ 코드+시각검증 완료, **PR #28 머지 완료(2026-06-24, B-3·B-1 묶음 1 PR, main `f93df00`)**. 리포트 P0 §225 #1. **사실확인: 리포트가 가리킨 "개발 중" 오버레이는 이미 사라짐** — `#developing` 안은 Scope B 폼 구현으로 동작하는 출제 폼이 됐고, `isSet` 깨진 분기·복붙 로그인 다이얼로그·`<AppConfig>` 는 중간 작업(spec-02·`fa991d0` 등)에서 이미 해소됨. 남은 건 흔적 스캐폴딩·죽은 코드뿐이라 경량 가역 작업으로 처리(spec 없이 backlog+코드, [[feedback_spec_weight_calibration]] 판단). **2 Task:** ① `developing-wrapper`/`#developing` 래퍼 div·`.developing-wrapper` CSS·죽은 주석(confirm4 블록·미사용 confirm5·죽은 출제 버튼 블록·낡은 '준비중' 주석) 제거, 폼 dedent — 거동 무변경(`97303b3`) ② PDF 학습지 헤더 이름·학년 미완성 노출 해소 — `userDetail`/`userGrade` ref 가 선언만 되고 fetch 안 돼 다운로드 PDF 헤더가 늘 공백이던 것을, 로그인 시 `/api/v1/users`+`TitleService.calculateGrade` 로 채움(RecordView 동일 패턴, `c52af6c`). 빌드 PASS, lint 신규에러 0(confirm5 미사용 에러 1건 해소). **검증 완료(2026-06-24): 빌드·lint·풀스택 배선(어시스턴트 — 인프라+bootRun+dev 기동, `verifyqa01` 토큰 주입으로 `/api/v1/users`·출제 API 실응답 확인) + 사람 시각검증 PASS(로그인 후 /personal — 맞춤조건 폼 깔끔 렌더·죽은 영역 없음, 미리보기 헤더에 학생 이름 표기 확인). 학년은 시드 유저 생일 NULL → `calculateGrade(null)=''` 정상 동작.** **Out(잔여 부채, 본 작업 밖):** PersonalView 의 `renderItemAnswer`/`isLatex` 미사용 함수·`receivedData` ref `.value` 누락(line ~60)은 기존 부채로 미수정.

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
