# [M7] 적응형 문답 질문 선택 알고리즘 고찰 (spec-01 §4.3 보완)

**등록:** 2026-07-17 (v2 클릭러블 프로토타입 리뷰 중 사용자 발견) · **상태:** ✅ **구현 완료(2026-07-24, 브랜치 `feat/m7-item-selection`)** — 결정론적 KST 코어(규칙 A·B·C) 분리 커밋 + 결정론 단위 테스트 12건 green. D1=K8/N20·D2=임계4+√n·D3=서브트리 복원 확정. 정본 = [ADR-0012](../adr/0012-adaptive-question-selection-deterministic-kst-core.md)(Proposed, 사인오프 대기) + spec-01 §4.3. **잔여 = ADR Accepted 승격 · 라이브 `/diagnosis/next` 눈검증(Docker 필요) · 배포 재개.**

## 발견된 보완 지점 (프로토타입 리뷰)

1. **단일 프론티어 1문 종료** — 시작 프론티어가 개념 1개뿐인 단원에서 첫 질문에 "알아요"를 누르면 선수 폐쇄 전체가 skip 되어 **질문 1개로 진단이 끝나고 "약점 없음"이 나온다.** 현행 spec-01 §4.3 규칙(프론티어 소진 = done)의 정의상 동작이며 목업 한정 문제가 아님. 실데이터는 단원당 프론티어가 보통 복수라 완화되지만, 근본적으로 "진단이 너무 얇게 끝나는" 케이스의 하한(최소 질문 수·확인 질문 등) 보완 필요.
2. **"알아요" 폐쇄 skip 의 과신(R1) 무검증** — 알아요 한 번에 선수 폐쇄 전체를 안다고 추론. 과대평가 학생이면 오염이 크게 전파. 폐쇄 내 샘플링 검증 질문 등 검토.
3. **다중 선수 질문 순서** — "몰라요" 시 직계 선수 전부가 후보로 들어가며 현행 순서 = conceptId 오름차순(결정론용 임시 규칙). 어떤 선수부터 묻는 게 정보량이 큰지(예: blockedDescendants 큰 것부터, DKT 불확실성 큰 것부터) 미검토.

## 진행 방식 (사용자 결정 2026-07-17)

- **지금 풀지 않는다.** 별도 **전문가 프롬프트**(`🤖-` 파일)를 만들어 기존 알고리즘 서베이 → 고찰 → 선택.
- 후보 서베이 대상: **KST**(Knowledge Space Theory — ALEKS 가 이 계열, 본 서비스 `knowledge_space` 와 개념적 동형) · **CAT**(Computerized Adaptive Testing, 정보량 기반 문항 선택) · **IRT** · DKT 출력(불확실성)을 질문 선택에 역이용하는 변형 · 추천시스템 exploration 계열.
- 제약: 서버 주도 stateless 순회(spec-01 D1-A)·결정론(preview==귀속)·"3분 진단" 상한과 양립할 것.

## 영향 범위 판정

- **프론트 화면 계약과 독립** — ③ 문답 화면(카드+2버튼+진척)·④ 결과 구조는 질문 선택 정책과 무관 → **와이어프레임/재설계 비차단.** (알고리즘 결과가 "확인 질문" 같은 UX 요소를 낳으면 ③ 에 미세 반영될 수 있으나 구조 동일.)
- **백엔드** — 채택 시 spec-01 §4.3 개정 + `/diagnosis/next` 내부 로직 변경(API 계약은 유지 가능 전망). 플래그 뒤라 라이브 영향 없음.

## 진행 경과 (2026-07-24 — 컨설팅·설계 완료)

- **페르소나 컨설팅 실행 완료** (격리 세션, 2026-07-19 최초 + 2026-07-24 재실행 — 두 실행 수렴). 프롬프트·결과·학습자료를 `docs/consulting/` 로 이관:
  - 프롬프트: [`docs/consulting/03-adaptive-question-selection.md`](../consulting/03-adaptive-question-selection.md)
  - 권장안 정본: [`docs/consulting/out/adaptive-question-selection-report.md`](../consulting/out/adaptive-question-selection-report.md) (+ `-2026-07-19.md` provenance)
  - 학습자료: `out/question-selection-learning.html`(후보 5계열)·`out/question-selection-explainer.html`(쉬운 설명)·`out/kst-learning-material.html`(KST 심화+링크)
- **권장안 = 「휴리스틱 강화(=결정론적 KST 코어)」 즉시 채택, KST 전역 반분·DKT 역이용·IRT 는 실데이터 이후로 보류.** 콜드스타트·결정론·stateless 3제약이 IRT·밴딧을 탈락시키고, DAG 구조 계산만으로 결함 3건이 닫힘.
- **결함별 해소** — ①(1문 종료)·③(순서 무근거)는 KST 원리(최소질문 하한 + blockedDescendants 정보량 순서)로 해소. ②(알아요 과신)는 **결정론 KST 코어로는 안 풀림** → 별도 검증 프로브 패치로 닫음(확률층 BLIM 은 데이터 필요라 보류).
- **구현 착수 프롬프트 준비 완료:** 최상위 [`🤖-문항선택-KST-구현.md`](../../🤖-문항선택-KST-구현.md) — 범위 = 규칙 A(하한/상한)·B(순서)·C(skip-with-probe), 새 세션에서 실행. 착수 전 사용자 확정 필요 = D1 최소질문 K·D2 프로브 밀도·D3 복원 범위.
