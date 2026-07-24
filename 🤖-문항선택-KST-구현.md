# 🤖 [MMT] 적응형 문항 선택 — 결정론적 KST 코어 구현 (spec-01 §4.3 개정)

> **이 문서는 새 세션(리포 접근 O)에 붙여넣어 바로 착수하는 실행 프롬프트다.** 컨설팅·설계는 끝났고, 이 세션의 목표는 **실제 코드 구현**이다.
> 관련 정본을 먼저 읽고 시작하라 — 이 프롬프트는 그 위의 실행 지시서다.

## 0. 시작 전에 읽을 정본 (순서대로)

1. **권장안 정본** — `docs/consulting/out/adaptive-question-selection-report.md` (④ 권장안 + 단계 경로가 이 구현의 근거)
2. **결함 정본** — `docs/backlog/m7-adaptive-traversal-question-selection.md` (풀어야 할 결함 3건)
3. **현행 알고리즘 정본** — `docs/specs/m7/spec-01-diagnosis-self-report-dkt.md` §4.2~§4.5 (개정 대상 규칙)
4. **이론 참고(선택)** — `docs/consulting/out/kst-learning-material.html` (KST 개념·왜 결함②는 KST 코어로 안 풀리는지)
5. 워크스페이스 규칙 — `api/CLAUDE.md` (Analyze-Before-Change·피처 플래그·테스트·ADR 컨벤션)

**로컬 진단 백엔드 기동**(표준 포트, `CLAUDE.local.md` 정본):
```
docker compose up -d mmt-mysql mmt-neo4j mmt-redis mmt-ai
cd api && MMT_DIAGNOSIS_ENABLED=true MMT_MIGRATION_USE_MYSQL_CTE_FOR_GRAPH=true \
  MMT_DIAGNOSIS_SERVING_URL=http://localhost:8501/v1/models/my_model:predict ./gradlew bootRun
```

---

## 1. 한 줄 목표

현행 spec-01 §4.3 순회 규칙을 **결정론적 KST 코어**로 개정한다 — "남은 지식상태를 가장 크게 가르는 개념을 묻고(정보량 순서), 최소·최대 질문 수 하한/상한을 두며, '알아요' 폐쇄 skip 을 검증 프로브로 완화"한다. **결함 3건을 닫되 아키텍처 계약 4종은 하나도 건드리지 않는다.**

> **정직한 범위 한정:** "지금 KST" = 확률층(BLIM, 데이터 필요) 없이 **결정론 코어만**. 그래서 결함①·③은 KST 원리로 해소되지만 **결함②는 별도 프로브 패치**로 닫는다. DKT 역이용·전역 상태공간 반분(KST 2단계)은 **이번 범위 아님**(런치 후 트랙). 자세한 이유는 KST 학습자료 7절.

---

## 2. 구현 범위 = 컨설팅 리포트의 **1단계(0단계+프로브)만**

리포트 ④의 단계 중 **지금 넣는 것**은 아래 3개 규칙 변경뿐이다. 2·3단계(전역 KST 반분·DKT 역이용·IRT)는 **명시적으로 제외**한다.

| # | 규칙 변경 | 닫는 결함 | 근거 |
|---|---|---|---|
| A | **종료 규칙**에 최소 질문 수 하한 K + 최대 상한 N 추가 | ① 1문 종료 | 리포트 ④ 0단계 |
| B | **순서 규칙** conceptId 오름차순 → **정보량 순**(1차: blockedDescendants 내림차순, tie-break: 깊이 → conceptId) | ③ 순서 무근거 | 리포트 ④ 0단계 |
| C | **R1(알아요→선수 폐쇄 전체 skip)** → **skip-with-probe**(폐쇄를 잠정-앎으로 두고 결정론적 검증 프로브 남김, 프로브 실패 시 국소 복원) | ② skip 과신 | 리포트 ④ 1단계 |

---

## 3. 불변 계약 (깨면 구현 실패 — 테스트로 강제)

1. **stateless** — 서버 세션 없음. 다음 질문은 매 요청의 `answered[]` 전체에서 **재계산**. (spec-01 D1-A)
2. **결정론** — 같은 `answered[]` → 같은 다음 질문·같은 결과. **preview 와 귀속 저장 결과의 동치가 계약**(F-1). 모든 tie-break 는 conceptId 로 확정적. **랜덤·시각·해시맵 순회 순서 의존 금지.**
3. **API 계약 유지** — `POST /api/v1/diagnosis/next` 의 req/res **shape 불변**(`DiagnosisNextRequest`/`DiagnosisNextResponse`). `progress.estimatedRemaining` 산식은 바뀌어도 필드는 유지.
4. **③ 화면 불변** — 카드+2버튼 구조 그대로. (프로브도 같은 화면에 일반 질문으로 노출 — 프론트 변경 불요가 목표)
5. **피처 플래그 뒤** — `mmt.diagnosis.enabled`(ADR-0010) 게이트 안에서만 동작. 라이브 무영향.
6. **3분 상한** — 상한 N 은 하드캡. 하한 K 가 상한을 넘지 않게.

---

## 4. ⚠️ 착수 전 사용자에게 확인할 결정 3건 (임의 확정 금지)

리포트 ③ 판별 질문에서 **사용자 몫으로 남은 트레이드오프**다. 코드 작성 전에 아래를 옵션+권장으로 제시하고 **답을 받은 뒤** 진행하라. (권장 default 는 제시하되 확정은 사용자.)

- **D1. 최소 질문 수 K** — 하한을 몇으로? *권장 default = 8* (3분 상한 N=20 과 함께). 정확도 우선이면 ↑, 체감 우선이면 ↓.
- **D2. 프로브 밀도** — "알아요" 폐쇄당 검증 프로브 몇 개? *권장 default = 폐쇄 크기 임계 m 미만이면 1개, 이상이면 √(폐쇄크기) 개, 선정은 blockedDescendants 최대 우선(결정론)*. 결함② 심각도(리포트 Q3)에 따라 조정.
- **D3. 프로브 실패 시 복원 범위** — 프로브 "몰라요" 시 그 개념의 **직계 선수 서브트리만**(질문 수 절약) vs **원래 skip 한 폐쇄 전체**(오염 최소). *권장 default = 서브트리만(3분 계약 안전)*. 리포트 Q3.

> **D1·D2·D3 는 상수/정책이라 나중에 실데이터로 튜닝 가능**(리포트 3단계). 지금은 사용자 확정값을 spec 에 명기하고 진행.

---

## 5. 규칙 변경 명세 (현행 → 신규)

현행 §4.3 순회 규칙(1~5)은 아래로 개정한다. **매 요청 재계산 구조는 유지**하고 세 집합을 명시적으로 만든다:

- `Known` = `answered` 중 known=true 개념 ∪ **그 선수 폐쇄 전체**(현행 3항의 visited-set BFS 재사용 — 사이클 면역·무캐시 간선 직로드 유지)
- `Unknown` = `answered` 중 known=false 개념 (그 후손은 답 못 하므로 자연히 후보에서 배제)
- `Undetermined` = 도메인 − Known − Unknown

**신규 순회 규칙:**

1. 초기 프론티어 = §4.2 시작 프론티어 (**불변**).
2. **"몰라요"** → 직계 선수를 후보에 push (**불변**), 단 후보 **정렬만 변경**(규칙 B).
3. **"알아요"** → 폐쇄를 **inferred-known 마킹하되(현행 유지)** + 규칙 C 대로 폐쇄 내 **검증 프로브 개념을 후보에 남긴다**. 프로브가 "몰라요"면 D3 범위대로 해당 영역을 Undetermined 로 **복원**(마킹 해제).
4. **다음 질문** = (Undetermined ∩ 후보) 중 **규칙 B 순서 1위**. 후보가 비고 **answered 수 ≥ K** 이면 `done`. answered 수 < K 이면 잠정-앎/프론티어 인접에서 프로브를 더 뽑아 계속(규칙 A). **answered 수 = N 도달 시 강제 `done`**(규칙 A 상한).
5. `description` 소스 (**불변**).

**규칙 B 순서 산정** — `blockedDescendants`(그 개념을 "몰라요" 시 추가로 파야 할 후손 수)는 **spec-01 §4.5 가 이미 CTE(depth 3, `findPrerequisitesAsDepthMap`)로 계산**한다. 이를 순서 키로 재사용하라(신규 인프라 0). 내림차순, tie-break = DAG 깊이 내림차순 → conceptId 오름차순(결정론).

> **결정론 필수 조건:** 프로브 선정·복원·정렬 모두 **구조 순위로 확정**할 것. `HashSet`/`HashMap` 순회로 순서가 새면 preview≠귀속 → 계약 위반. 정렬 후 리스트로만 다뤄라.

---

## 6. 작업 절차 (바이브코딩 2단계 워크플로우 준수)

1. **브랜치** — spec 단위 브랜치에서 작업. (현재 `feat/m7-item-selection` 이 이 작업용이면 계속, 아니면 main 에서 새로 분기.)
2. **`/analyze-before-change`** (필수 — 쿼리 구조·알고리즘 변경) — 참조 지점(`DiagnosisService`의 순회 메서드, `DiagnosisNextResponse` 조립, `findPrerequisitesAsDepthMap` 호출부), 영향받는 테스트, 롤백 시나리오(플래그 off = 현행 규칙)를 조사해 **ADR 초안**에 넣는다.
3. **ADR** (`/write-adr`) — "적응형 문항 선택: 결정론적 KST 코어" 결정 기록. 후보 비교(리포트 요약)·D1~D3 확정값·제외 범위(DKT/전역 KST)·롤백을 담는다. `mmt.diagnosis.*` 영역이므로 새 플래그 필요 시 api/CLAUDE.md 피처 플래그 섹션도 갱신 제안.
4. **spec-01 §4.3 개정** — 위 신규 규칙으로 문서 갱신(§4.2·§4.5 는 참조만, 손대지 않음).
5. **구현** — `DiagnosisService` 순회 로직 개정. Task 단위 커밋(규칙 A / B / C 를 분리 커밋 권장). 커밋 메시지 = 영문 제목 + 국문 body, Conventional Commits, 트레일러 없음.
6. **테스트** (테스트 없이 리포지토리·순회 로직 변경 금지):
   - **결정론 테스트** — 같은 `answered[]` 를 순서 섞어/반복 입력해도 다음 질문·결과 동일. preview==귀속 동치.
   - **결함① 회귀** — 단일 프론티어 단원 첫 "알아요" 가 1문 종료되지 않고 K 하한까지 프로브를 뽑는지.
   - **결함③** — 다중 선수에서 blockedDescendants 큰 개념이 먼저 나오는지.
   - **결함② 프로브** — "알아요" 폐쇄에 프로브가 남고, 프로브 "몰라요" 시 D3 범위로 복원되는지.
   - **상한/하한** — K 미만 조기종료 없음, N 초과 없음.
7. **로컬 검증** — §0 기동 후 실제 `/diagnosis/next` 순회를 몇 케이스 돌려 눈으로 확인.

---

## 7. 참조 파일

- `api/src/main/java/com/mmt/api/service/DiagnosisService.java` — 순회 엔진 (개정 핵심)
- `api/src/main/java/com/mmt/api/controller/DiagnosisController.java` — `/diagnosis/next` 엔드포인트
- `api/src/main/java/com/mmt/api/dto/diagnosis/DiagnosisNextRequest.java` / `DiagnosisNextResponse.java` — **shape 불변**
- `api/src/main/java/com/mmt/api/service/DiagnosisResultAssembler.java` — 결과(outer fringe) 조립
- `docs/specs/m7/spec-01-diagnosis-self-report-dkt.md` §4.2~§4.5 — 개정 대상/참조
- `docs/adr/_template.md` — ADR 템플릿

---

## 8. 완료 기준 (DoD)

- [ ] D1~D3 사용자 확정값이 ADR·spec 에 명기됨
- [ ] 규칙 A·B·C 구현 + Task 단위 커밋
- [ ] 결함 3건 각각에 대응하는 테스트 통과 (결정론 테스트 포함)
- [ ] `mmt.diagnosis.enabled` off 시 **현행 동작으로 롤백**됨을 확인 (또는 신규 로직이 기존 계약과 호환)
- [ ] API req/res shape·③ 화면 계약 불변 확인
- [ ] spec-01 §4.3 갱신 + ADR 작성 + `ROADMAP.md` M7 진행 상태 반영
- [ ] **범위 밖(DKT 역이용·전역 KST 반분·IRT)은 손대지 않음** 확인
