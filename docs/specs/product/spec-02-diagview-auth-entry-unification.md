# Spec 02: DiagView 로그인/회원가입 진입 동선 재설계 (#3)

**분류:** Product / Web UX (그래프 인프라 무의존 — M2/M3와 독립)
**정본 로드맵 항목:** `docs/roadmap.md` → Later 백로그 → [Design] 실배포 전 리디자인 → 이월 UI #3
**작업 브랜치:** `feat/pre-launch-redesign` (미푸시)
**선행 리포트:** `docs/consulting/out/design-ux-report.md` (P1·토프바 인증 빈약 / B-1 글로벌 내비 [로그인/시작하기])
**작성일:** 2026-06-24
**상태:** 합의 완료 (D1=A, D2=추출+composable, 2026-06-24) — 코드 착수 가능

---

## 1. 범위

실배포 전, **로그인/회원가입 진입 동선의 불일치를 해소하고 진입을 전역 토프바 로그인 다이얼로그로 일원화**한다.

핸드오프 범위 (a)(b)(c):
- (a) DiagView 비로그인 다운로드 다이얼로그의 **"회원가입 및 로그인" 라벨/동작 불일치** 해소
- (b) 로그인/회원가입 진입을 **전역 토프바 다이얼로그와 일원화**
- (c) DiagView 비로그인 다이얼로그에서의 진입 처리 확정

**범위 외:**
- 로그인 다이얼로그 **내용물 재디자인**(폼 표준화·인라인 검증·OAuth 버튼 비주얼)은 리포트 항목 #9 별도 작업. 여기선 다이얼로그를 **그대로 재사용**한다.
- 글로벌 내비 전면 개편(사이드바 폐기·B-1 히어로)은 리디자인 본 트랙. 여기선 인증 진입 동선만.
- 회원 탈퇴·회원정보수정 동선(UserEditView).

---

## 2. 현행 동선 (코드 확인 2026-06-24)

### 2-1. 전역 토프바 — `web/src/layout/AppTopbar.vue`
- 비로그인 시 우상단 `pi pi-user` 아이콘 버튼 → `onUserClick()` → `loginDialog = true`.
- `loginDialog`(`AppTopbar.vue:165-219`)는 **이미 통합 진입점**: 이메일/비밀번호 폼(`login()`) + "회원가입" 버튼(`goToSignup()` → signup 페이지 라우팅) + 간편로그인(google/naver/kakao OAuth 링크).
- `loginDialog` 상태는 **AppTopbar 컴포넌트 로컬 `ref`** — 외부에서 못 연다.
- 로그인 시: "로그아웃" 링크.

### 2-2. DiagView 비로그인 다운로드 다이얼로그 — `web/src/views/DiagView.vue:357-373`
- testId 선택 + 비로그인 → "다운로드" → `displayConfirmation` 다이얼로그.
- 본문: "로그인 없이도 다운로드는 가능합니다. 단, 로그인하지 않으면 진행한 내역이 기록되지 않습니다."
- footer: [아니오](닫기) / [예](`downloadTest` = PDF만, 기록 X).
- 별도 행: **[회원가입 및 로그인]** 버튼 → `goToSignup()`(`DiagView.vue:233`) → **signup 페이지 라우팅만.**

### 2-3. 불일치 (문제)
1. **라벨 ↔ 동작 불일치 (a):** 버튼은 "회원가입 **및 로그인**"인데 `goToSignup`은 회원가입 페이지로만 보낸다. 기존 회원이 이 자리에서 로그인할 길이 없다(토프바로 다시 가야 함).
2. **진입점 이원화 (b):** 통합 로그인 UI(토프바 다이얼로그)가 이미 있는데 DiagView는 그걸 못 열고 페이지 이동으로 우회 → 동선이 두 갈래.

---

## 3. 결정 (사용자 합의 2026-06-24)

### D1. DiagView 비로그인 다이얼로그의 인증 진입 처리 — **확정: A (토프바 다이얼로그 재사용)**

| 옵션 | 동작 | 일원화 | 비용/가역성 | 비고 |
|---|---|---|---|---|
| **A (권장)** | "회원가입 및 로그인" 버튼 → **전역 토프바 로그인 다이얼로그를 그 자리에서 연다**(로그인 폼+회원가입+OAuth 한 곳). | ✅ 진짜 일원화 (b 충족) | 중 — 다이얼로그를 공유 컴포넌트로 추출 + 공유 트리거 필요. 가역(되돌리기 쉬움) | 기존 회원·신규 모두 이 자리에서 처리. 라벨/동작 일치 |
| **B (최소 우회)** | 버튼 라벨을 **"회원가입"으로 정정**하고 현행 signup 페이지 라우팅 유지. | ❌ 일원화 보류 | 소 — 1~2줄 | (a)만 해소. 기존 회원은 여전히 토프바로 가야 로그인 |
| **C (안내형)** | 다이얼로그에서 인증 버튼 제거 + "기록을 남기려면 우측 상단에서 로그인하세요" 안내. | △ 토프바로 단일화하나 인라인 트리거 없음 | 소 | 진입을 토프바로만 모음. 클릭 한 번 더 |

**확정 = A** (사용자 결정 2026-06-24). 핸드오프 (b) "전역 토프바와 일원화" 방향과 정합. 통합 다이얼로그가 이미 존재하므로 재사용이 자연스럽다. (B/C는 폐기 — 일원화를 미루지 않고 이번에 처리.)

### D2. 공유 트리거 구현 방식 — **확정: 추출 + composable** (사용자 결정 2026-06-24)

로그인 다이얼로그를 양쪽에서 열려면 상태를 컴포넌트 밖으로 빼야 한다. 채택:
- 로그인 다이얼로그 마크업을 `web/src/components/LoginDialog.vue`로 **추출**(내용 동일, 비주얼 변경 없음).
- 가시성 트리거는 **싱글톤 composable `useLoginDialog()`**(`open()`/`close()` + 공유 `ref`) — Vuex 불필요, web/CLAUDE.md의 "api.js 훅 경유" 패턴과 결.
- `LoginDialog`는 `AppLayout`에 1회 마운트(현재 AppTopbar 내부 → 레이아웃 레벨로). AppTopbar 아이콘·DiagView 버튼은 `open()` 호출만.
- 이는 **레이어 간 리팩토링** → 착수 전 `/analyze-before-change` 필수(AppTopbar 로컬 상태 `login`/`loginErrorMessage` 참조 지점, ResultView 등 복붙 다이얼로그 영향 — 리포트 16번 줄 "DiagView·ResultView 복붙 로그인 다이얼로그" 정리와 연계 가능).

> D2 대안(폐기): 추출 없이 Vuex flag `loginDialogVisible`만 추가하는 최소안 — 변경은 작으나 다이얼로그가 AppTopbar에 잔류, ResultView 복붙 정리 불가. 사용자가 추출안 채택.

### D3. PR 타이밍
- #3까지 묶어 한 번에 올릴지, 중간 푸시할지는 핸드오프 §5대로 **사람 결정** — 본 spec 범위 밖.

---

## 4. 작업 순서 (D1·D2 합의 후 확정)

1. (D1=A & D2=추출 채택 시) `/analyze-before-change` — AppTopbar 로그인 다이얼로그 추출 영향 범위.
2. `LoginDialog.vue` 추출 + `useLoginDialog()` composable. → Task 1 커밋.
3. AppTopbar 아이콘이 `useLoginDialog().open()` 호출하도록 전환. → Task 2 커밋.
4. DiagView 버튼이 `open()` 호출 + 라벨 정합화. → Task 3 커밋.
5. 런타임 수동 검증(웹 테스트 프레임워크 없음): 토프바·DiagView 양쪽에서 다이얼로그 오픈/로그인/회원가입/OAuth 링크 동작. 풀스택 배선 확인은 어시스턴트, 최종 시각 클릭 검증은 사람([[project_redesign_track]] 분담).

---

## 5. 결정 사항 (사용자 확정 2026-06-24)

- **D1 = A** — DiagView 비로그인 버튼이 전역 토프바 로그인 다이얼로그를 그 자리에서 연다(로그인 폼+회원가입+OAuth 재사용). 라벨/동작 불일치 해소 + 진입 일원화.
- **D2 = 추출 + composable** — `LoginDialog.vue` 추출 + 싱글톤 `useLoginDialog()` 트리거 + `AppLayout` 1회 마운트. Vuex 미사용.
  - **정정(analyze-before-change 2026-06-24):** 리포트 L16의 "DiagView·ResultView 복붙 로그인 다이얼로그"는 **stale**. 실제 로그인 다이얼로그 정의처는 `AppTopbar.vue` **단 한 곳**(ResultView는 Toast 안내만, SignUpView Dialog는 가입확인 컨펌으로 별개). 따라서 추출의 명분은 "복붙 제거"가 아니라 **양쪽(토프바·DiagView) 공유 진입**이다.
- D3(PR 타이밍) = 사람 결정, 범위 밖.
