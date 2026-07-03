# spec-08 · 폼 표준화 — 가입/회원수정 토큰 채택 + 중복 검증 컴포넌트화 (P1)

> 트랙: [Design] 실배포 전 리디자인 — 폼 표준화(리포트 P1 §235 #9). 정본 진행상태 = `docs/roadmap.md` + `design-redesign-handoff.md`
> 작업 브랜치: `feat/form-standardization` (셸 브랜치 `feat/shell-global-nav` 위 스택 — 토큰+셸 채택). Task 단위 커밋.
> 상태: spec 합의 대기 → 합의 후 코드 단계
> 무게: 중복 검증 로직 추출(거동 리팩토링)을 포함하므로 경량~중간 spec + **`/analyze-before-change`**(§3.1, 참조 스캔 완료). 단 양 폼 검증자가 코드상 동일 정의라 발산 위험 0.

## 1. 배경 · 문제

리포트(`docs/consulting/out/design-ux-report.md`) §94(P1 영문/큰 타이포 폼)·§103(P2 대괄호 제목)·§235 #9. 코드 조사(2026-06-24):

- **인라인 검증은 이미 구현됨**(`validateEmail/Password/UserName/UserComments`·`confirmPassword`·중복확인). 리포트의 "인라인 검증 전환"은 사실상 완료 상태.
- **남은 실제 문제:**
  1. **타이포 과대·하드코딩 색** — 라벨 `text-2xl`(24px)·제목 `text-3xl`·에러 `text-red-600 text-base`. spec-06 토큰 미채택. 오타 클래스 `text-font-medium`(no-op) 다수.
  2. **검증 로직 ~80줄 중복** — `SignUpView.vue`·`UserEditView.vue`가 `validatePassword`/`validateUserName`/`validateUserComments`/`confirmPassword`/`formatDate` + 비번 요구사항 푸터(`#header`/`#footer` 슬롯)를 **동일하게 복붙**.
  3. **생년월일 위젯 clunky** — `Calendar` 기본 + "연도를 클릭하여 찾아보세요" 안내. 출생연도까지 수십 년 거슬러 내비.
  4. **제목 위생** — `[ 마이페이지 ]` 대괄호 표기(§103).

## 2. 범위

### In (중간 범위 — 사용자 결정 2026-06-24)
1. **공통 검증 컴포저블 추출** — `composables/useUserForm.js`: 공유 필드 ref(`password`·`passwordConfirm`·`name`·`comments`) + 검증 상태/함수(`validatePassword`·`validateUserName`·`validateUserComments`·`confirmPassword` + 비번 변경 시 확인창 리셋 watch) + `formatDate`. 양 뷰가 이를 소비.
2. **비번 요구사항 컴포넌트 추출** — `components/PasswordRequirements.vue`: `Password`의 `#header`("비밀번호 안전도") + `#footer`(요구사항 ul) 동일 마크업. 양 뷰가 슬롯에 사용.
3. **토큰 채택**(spec-06) — 제목→`.t-heading`, 라벨→`.t-subheading`, 안내→`.t-caption`, 에러→`.t-caption`+`--mmt-danger`. `text-2xl/3xl`·`text-red-600`·오타 `text-font-medium` 제거.
4. **생년월일 위젯 개선** — `Calendar`에 미래일자 차단(`:maxDate`) + 연도 내비 개선(PrimeVue 3.39 `yearNavigator`/`yearRange` 지원 시 적용, 미지원 시 안내 카피 명확화로 폴백). 출생연도 선택 마찰 완화.
5. **제목 위생** — `[ 마이페이지 ]`→`마이페이지`.

### Out (이번 범위 밖)
- **제출 버튼 disabled 체인 UX 재설계** — v-if/else-if 플레이스홀더 버튼 체인은 투박하나 동작함. 인라인 검증 단일 버튼+에러 요약으로의 교체는 "헤비" 범위라 제외(후속 백로그).
- **필드 단위 `<FormField>` 래퍼 컴포넌트화** — 라벨+입력+에러 한 묶음 추상화는 과설계 위험 → 이번엔 검증 로직·비번 요구사항만 추출.
- **이메일 정규식·비번 정책 변경** — 검증 규칙 자체는 현행 유지(추출만, 로직 동일).
- **가입 필수항목 축소(생년월일/기타사항)** — 전환율 트레이드오프(열린질문 §252 #6), 별도 결정.
- **백엔드 계약 변경** — 없음. 요청 DTO·엔드포인트 그대로.

## 3. 결정

- **D1 · 범위 = 중간**(토큰 채택 + 중복 컴포넌트화 + 생년월일 + 위생). 제출 버튼 UX 재설계는 Out(사용자 결정 2026-06-24).
- **D2 · 추출 형태 = 컴포저블(검증 로직·공유 ref) + 컴포넌트(비번 요구사항).** 필드 래퍼 추상화는 안 함(과설계 회피, [[feedback_spec_weight_calibration]]).

### 3.1 analyze-before-change — 참조 스캔(완료)

- 양 뷰의 검증 함수·ref 는 `<script setup>` **로컬**(미export) → 외부 import/참조 불가. 추출은 내부 변경, 외부 영향 0.
- 두 뷰의 `validatePassword`/`validateUserName`/`validateUserComments`/`confirmPassword`/`formatDate`·비번 푸터는 **코드상 동일**(읽고 대조) → 단일 출처로 합쳐도 거동 발산 없음.
- 차이점(추출 제외): SignUp = email·중복확인·생년월일 `requestData`; UserEdit = currentPassword·탈퇴·`userDetail` fetch·`requestData3`. → 폼 고유 로직은 뷰에 잔존, 공유분만 추출.
- 검증 수단: web 테스트 프레임워크 없음 → 빌드·lint·dev 배선(어시스턴트) + 사람 시각/입력 검증(가입·수정 폼에서 각 검증 메시지·비번 일치·생년월일·제출 흐름 동작).
- 롤백: 신규 2파일 + 양 뷰 변경 한정 → `git revert`/브랜치 폐기로 즉시 원복(§6).

## 4. 설계

### 4.1 `composables/useUserForm.js`
```js
export function useUserForm() {
  const password = ref(''); const passwordConfirm = ref('');
  const name = ref(''); const comments = ref('');
  // password: isPasswordValid, isPasswordLengthValid, passwordLengthErrorMessage, validatePassword()
  // name: isUserNameValid, userNameErrorMessage, validateUserName()
  // comments: isUserCommentsValid, userCommentsErrorMessage, validateUserComments()
  // confirm: passwordConfirmMessage, isPasswordMatch, confirmPassword()
  watch(password, () => { passwordConfirm.value = ''; });   // 기존 거동 보존
  const formatDate = (date) => `${y}년 ${m}월 ${d}일`;       // 기존 동일
  return { password, passwordConfirm, name, comments, /* …검증 상태·함수… */, formatDate };
}
```
- 양 뷰는 `const { password, name, ... , validatePassword } = useUserForm()` 로 소비. 폼 고유 ref(email·currentPassword 등)는 뷰에 그대로.

### 4.2 `components/PasswordRequirements.vue`
- `Password` 슬롯에 들어갈 헤더/푸터 마크업(요구사항 ul + Divider) 캡슐화. 양 뷰: `<Password ...><template #header><PasswordRequirements header /></template><template #footer><PasswordRequirements /></template></Password>` 또는 단일 슬롯 구성(구현 시 정리).

### 4.3 토큰 매핑
| 현재 | → |
|---|---|
| 제목 `text-3xl font-medium` | `.t-heading` (+ `[ 마이페이지 ]`→`마이페이지`) |
| 라벨 `text-2xl font-medium` | `.t-subheading` |
| 안내 `text-600 text-base font-normal` | `.t-caption` |
| 에러 `text-red-600 text-base text-font-medium` | `.t-caption` + `color: var(--mmt-danger)` (scoped `.field-error`) |

### 4.4 생년월일
- `Calendar`에 `:maxDate="new Date()"`(미래 차단). 연도 내비: `yearNavigator`/`yearRange="1950:현재"` 적용 시도 → 3.39 지원/경고 여부 구현 시 확인, 미지원이면 안내 카피만 명확화(폴백). 거동 위험 최소.

## 5. Task 분해 (Task 단위 커밋)
1. **Task 1 · 공통 추출** — `useUserForm.js` 컴포저블 + `PasswordRequirements.vue` 컴포넌트 신설.
2. **Task 2 · SignUpView 적용** — 컴포저블/컴포넌트 소비 + 토큰 채택 + 생년월일·위생. 거동 동등.
3. **Task 3 · UserEditView 적용** — 동일 적용(대괄호 제목 포함). 거동 동등.
4. **Task 4 · docs** — roadmap [Design] 폼 표준화 + 핸드오프 갱신.

## 6. 검증
- 빌드(`npm run build`) PASS · lint 신규 에러 0 · `npm run dev` 배선 PASS — 어시스턴트.
- 추출 전후 검증 거동 동일(이메일/비번/일치/이름/기타 메시지·중복확인·현재비번확인·제출/수정/탈퇴 흐름) 코드 레벨 확인 — 어시스턴트.
- **최종 시각/입력 검증(가입·회원수정 폼에서 각 검증·비번 일치·생년월일·제출 동작, 타이포 위계) = 사람**([[workflow_pattern]] 분담, [[feedback_visual_verification_handoff]]).

## 7. 롤백
- 신규 `composables/useUserForm.js`·`components/PasswordRequirements.vue` + `SignUpView.vue`·`UserEditView.vue` 변경 한정. 라우터/스토어/백엔드/마이그레이션 무변경 → `git revert` 또는 브랜치 폐기로 즉시 원복. 데이터 리스크 0.
