# ADR 0012: 적응형 문항 선택 — 결정론적 KST 코어 (규칙 A·B·C, spec-01 §4.3 개정)

## Status
Accepted (2026-07-24)

*(2026-07-24, `feat/m7-item-selection` 브랜치 구현 세션에서 작성 — 같은 세션에서 규칙 A·B·C 구현·단위 테스트 12건 green·실그래프 라이브 검증 완료 후 사용자 사인오프로 Accepted 승격.*
*원 provenance:* 근거 = 격리 세션 컨설팅 리포트 [`docs/consulting/out/adaptive-question-selection-report.md`](../consulting/out/adaptive-question-selection-report.md) ④ 권장안 + 백로그 [`docs/backlog/m7-adaptive-traversal-question-selection.md`](../backlog/m7-adaptive-traversal-question-selection.md) 결함 3건. D1~D3 트레이드오프는 사용자 확정(2026-07-24, 아래 Decision-4).)*

## Context

spec-01 §4.3 의 현행 적응형 순회는 v2 클릭러블 프로토타입 리뷰에서 3건의 결함이 확인됐다:

- **결함① 단일 프론티어 1문 종료** — 시작 프론티어가 개념 1개뿐인 단원에서 첫 "알아요"를 누르면 선수 폐쇄 전체가 skip 되어 **질문 1개로 진단이 끝나고 "약점 없음"** 이 나온다. "프론티어 소진 = done" 정의상 동작.
- **결함② "알아요" 폐쇄 skip 과신(무검증)** — 알아요 한 번에 선수 폐쇄 전체를 안다고 추론. 과대평가 학생이면 오염이 크게 전파.
- **결함③ 다중 선수 질문 순서 무근거** — "몰라요" 시 직계 선수 전부가 후보로 들어가며 순서 = conceptId 오름차순(결정론용 임시 규칙). 정보량 근거 없음.

제약(고정 상수): 자가보고 OX · 선수관계 DAG · **stateless 결정론**(preview == 귀속, D1-A) · **3분 상한** · **콜드스타트**(실사용자 0). 이 3제약(콜드스타트·결정론·stateless)이 IRT·밴딧을 사실상 탈락시킨다 — IRT 는 문항당 수백 응답 필요, 밴딧은 랜덤성·다세션 학습 필요. 살아남는 축은 **DAG 구조 정보량**(휴리스틱↔KST 연속선)과 **DKT 역이용** 둘이며, DKT 역이용은 모델 출력 안정성에 결정론이 종속되는 리스크가 있다.

이 개정은 그 결함 3건을 닫되 아키텍처 계약 5종(stateless·결정론·API shape·③ 화면·`mmt.diagnosis.enabled` 게이트)을 하나도 건드리지 않는 범위로 한정한다.

## Decision

**「휴리스틱 강화 = 결정론적 KST 코어」를 채택**한다. 확률층(BLIM, 데이터 필요) 없이 DAG 구조 계산만으로 결함 3건을 닫는다. `DiagnosisService.next()` 순회 로직만 개정하며, 결과 경로·DTO·스키마·web-v2 는 무변경이다.

### Decision-1. 규칙 A — 종료 규칙에 최소 하한 K + 상한 N (결함① 해소)

- 종료 조건을 "후보 소진" → **"후보 소진 AND answered ≥ K"** 로 강화. `K = 8`.
- 후보 소진 + answered < K 이면 프론티어 인접·잠정-앎(inferred-known) 영역에서 프로브를 더 뽑아 계속.
- 상한 `N = 20` 하드캡: answered == N 도달 시 강제 `done`(3분 계약). K ≤ N 보장.

### Decision-2. 규칙 B — 후보 순서 = 정보량 순 (결함③ 해소)

- 후보 정렬 키를 conceptId 오름차순 → **`blockedDescendants` 내림차순**. tie-break = **DAG 깊이 내림차순 → conceptId 오름차순**(결정론 확정).
- `blockedDescendants` = 그 개념을 "몰라요" 하면 추가로 막히는 후손 수. **기존 `JdbcTemplateConceptRepository.countBlockedDescendants(conceptId, depth=3)`**(spec-01 §4.5 역방향 CTE) 재사용 — 신규 인프라 0.

### Decision-3. 규칙 C — skip-with-probe (결함② 해소)

- R1 "알아요 → 선수 폐쇄 전체 skip" → **"폐쇄를 inferred-known 마킹하되 결정론적 검증 프로브를 후보로 남긴다"**.
- 프로브 밀도(D2): 폐쇄 크기 < 임계 m(=4) 이면 프로브 1개, ≥ m 이면 √(폐쇄크기)개(내림). 프로브 선정 = **blockedDescendants 최대 우선**(tie-break 규칙 B 동일 — 결정론).
- 프로브가 "몰라요" 시(D3): 그 개념의 **직계 선수 서브트리만** Undetermined 로 복원(재개방). 원래 skip 한 폐쇄 전체가 아니라 국소 복원 — 3분 계약 안전.
- 프로브가 "몰라요"면 그 답은 일반 `known=false` 답으로 answered[] 에 들어가 **결과 경로에 자연 반영**(결과 계약 무변경으로 결함② 오염이 카드에 노출됨).

### Decision-4. D1~D3 확정값 (사용자 결정 2026-07-24)

| 결정 | 확정값 | 근거 |
|---|---|---|
| D1 최소 질문 K / 상한 N | **K=8, N=20** | 정확도·체감 균형(권장 default). 실데이터로 튜닝 가능 |
| D2 프로브 밀도 | **임계 m=4 + √n** (폐쇄<4 → 1개, ≥4 → √개) | 작은 폐쇄 가볍게·큰 폐쇄만 촘촘. blockedDescendants 최대 우선 선정 |
| D3 복원 범위 | **직계 선수 서브트리만** | 질문 수 절약·3분 계약 안전, 국소 오염만 복구 |

K·N·m 은 상수/정책이라 실데이터 축적 후 재튜닝 가능(런치 후 트랙). 지금은 확정값을 spec-01 §4.3·본 ADR 에 명기하고 진행.

### Decision-5. 신규 피처 플래그 없음 — 기존 게이트 내 기본 동작

- 규칙 A·B·C 는 **기존 `mmt.diagnosis.enabled`(ADR-0010) 게이트 안에서 동작하는 미런치 경로의 기본 동작**으로 얹는다. 알고리즘 서브플래그(구 KST-less 순회로 되돌리는 스위치)는 **추가하지 않는다** — 해당 경로는 아직 라이브가 아니라(deploy-hold) 되돌릴 라이브 트래픽이 없기 때문. `mmt.diagnosis.*` 영역은 ADR-0010 에서 이미 등록됨.

## Decision — 제외 범위 (이번 ADR 밖, 명시적)

컨설팅 리포트 ④ 단계 경로 중 **2·3단계는 제외**한다:

- **DKT 역이용**(안 물어본 개념의 예측 확률 0.5 근접 우선) — 결정론이 모델 출력 안정성에 종속되는 리스크. 리포트 Q1 이 "절대 불변"이면 탈락. 실데이터·양자화·모델 버전 핀 후 실험 브랜치로만.
- **전역 KST 반분**(현재 답변과 양립하는 지식상태 후보 집합을 가장 균등하게 가르는 개념) — 상태공간 계산 비용, 런치 후 순서 규칙 정교화 트랙.
- **IRT / CAT 정통** — 콜드스타트에서 모수 추정 불가.
- **밴딧** — 세션 엔진 부적합(랜덤성·다세션 학습). 정책 A/B 메타 선택으로만 후일.

## Analyze-Before-Change 요약 (2026-07-24 실측)

- **참조 지점:** `DiagnosisService.next()` 유일 호출자 = `DiagnosisController:55`(`POST /diagnosis/next`). `frontier()`·`toValidatedAnsweredMap()` 는 재사용하되 시그니처 무변경. `countBlockedDescendants` 는 이미 존재(`JdbcTemplateConceptRepository:248`, `DiagnosisAnalysisService:176` 에서 카드 근거로 사용 중) — 순회에서도 재사용.
- **결과 경로 독립성(핵심):** `DiagnosisAnalysisService.compute()` 는 최종 `answered[]` 의 `known=false` 개념(depth0)에서만 결과를 파생 — 질문 순서·선택에 무관한 순수 함수. 규칙 A·B·C 는 "무엇을 묻느냐"만 바꾸므로 **결과 계약(§4.4)·DTO shape·결정론(preview==귀속) 자동 보존**, `DiagnosisAnalysisService` 무변경.
- **스키마·DB·마이그레이션:** 없음. `next()` 내부 인메모리 알고리즘 변경뿐.
- **워크스페이스 간(api→web-v2):** API req/res shape 불변. `progress.estimatedRemaining` 은 **값 산식만** 바뀌고 필드 유지 — `Quiz.tsx` 는 렌더링만, 코드 변경 불요. 프로브도 같은 카드+2버튼 화면(③ 불변).
- **영향 테스트:** `DiagnosisServiceTraversalTest`(구 순회 동작에 핀 — 신규 규칙으로 갱신). 신규: 결정론·결함①③②·상한/하한 property 테스트. `DiagnosisAnalysisServiceTest`·`DiagnosisOldPathNoTouchTest` 무변경 green 이 회귀 오라클.
- **알려진 divergence(비차단):** `web-v2/src/mocks/traversal.ts` 는 mock 모드용 구 순회 복제 — 백엔드 KST 규칙과 어긋남. mock 모드에선 프로브·K하한 미노출. 런치 블로커 아님(mock 충실도) → 백로그.

## Consequences

### Positive
- 콜드스타트·결정론·stateless 3제약을 **무비용**으로 만족(DAG 구조 계산만). 신규 인프라·모델·마이그레이션 0.
- 결과 경로가 순회와 독립이라 결함② 프로브가 결과 계약을 건드리지 않고 자연 반영 — API/화면/스키마 계약 전부 불변.
- KST 전역 정보량(2단계)·DKT(3단계)로의 업그레이드가 파괴적이지 않음(같은 구조 위 순서 규칙 정교화).

### Negative
- 이론적 최적성 없음 — K·N·m 은 휴리스틱 상수(실데이터 보정 백로그).
- 알고리즘 복잡도 상승(프로브 상태·복원). 결정론을 프로브 선정·복원·정렬 전 구간에서 유지해야 함(HashSet/HashMap 순회 누수 = preview≠귀속 계약 위반) → property 테스트로 강제.
- 규칙 B 가 요청당 후보 개념별 `countBlockedDescendants` CTE 를 호출 — 후보 수만큼 쿼리. 현 규모(1,631 개념·3,446 간선)에서 무해 판단이나 캐시(`graph:v2:blocked:` 네임스페이스) 도입은 성능 실측 후.

### Neutral
- 별도 알고리즘 서브플래그 없음(미런치 경로). 롤백 = `mmt.diagnosis.enabled=false`(제품 레벨) + 규칙 A·B·C Task 단위 커밋 개별 revert(코드 레벨).
- spec-01 §4.3 이 신규 규칙으로 개정됨. §4.2(프론티어)·§4.5(blockedDescendants CTE)는 참조만, 무변경.

## Alternatives Considered

1. **DKT 역이용** — 결정론이 모델 출력 안정성에 종속(버전·부동소수 흔들림 → preview≠귀속). 매 스텝 TF Serving 왕복 지연. 도메인 시프트(자가보고→정오답 매핑). 실데이터·양자화·버전 핀 후 실험 브랜치로 보류.
2. **전역 KST 상태공간 반분(2단계)** — 이론적으로 더 옳으나 상태공간 계산 비용. 0/1단계 하한·프로브가 결함을 이미 닫으므로 런치 후로.
3. **IRT/CAT 정통** — 콜드스타트에서 모수(a,b) 추정 불가. 단일 θ 와 개념별 이진 상태 부정합.
4. **밴딧/탐색-활용** — 랜덤성(결정론 위반)·다세션 보상 학습(데이터 필요). 세션 엔진 부적합.
5. **알고리즘 서브플래그 추가**(구 순회 병행) — 미런치 경로라 되돌릴 라이브 트래픽 없음. 불필요한 분기·정합 부담으로 기각(Decision-5).

## References
- 관련 ADR: ADR-0010 (self-report→DKT 매핑 + `mmt.diagnosis.*` 영역 — 본 개정이 그 게이트 안에서 동작), ADR-0003 (M2 캐싱 `graph:v2:` 네임스페이스 — 규칙 B 캐시 도입 시 승계)
- 컨설팅: `docs/consulting/out/adaptive-question-selection-report.md` (④ 권장안·단계 경로), `docs/consulting/out/kst-learning-material.html` (KST 코어 vs 확률층)
- 백로그: `docs/backlog/m7-adaptive-traversal-question-selection.md` (결함 3건 발견 맥락)
- 명세: `docs/specs/m7/spec-01-diagnosis-self-report-dkt.md` §4.2~§4.5 (개정 대상/참조)
- 구현 프롬프트: `docs/handoff/m7-item-selection-kst-impl-prompt.md`
