# [M7] web-v2 mock 순회가 백엔드 KST 코어와 divergence (비차단)

**등록:** 2026-07-24 (문항선택 KST 구현 세션) · **상태:** 백로그(비차단) · **우선순위:** 낮음

## 내용

`web-v2/src/mocks/traversal.ts` 의 `traverse()` 는 **구 순회 규칙**을 복제한다:
- "알아요" → 선수 폐쇄 전체 skip (프로브 없음)
- 후보 순서 = 순회(BFS) 순서 (blockedDescendants 순 아님)
- 최소 하한 K·상한 N 없음

반면 백엔드(`DiagnosisService`)는 2026-07-24 [ADR-0012](../adr/0012-adaptive-question-selection-deterministic-kst-core.md) 로 **결정론적 KST 코어**로 개정됨:
- 규칙 B: 후보 순서 = blockedDescendants 내림차순(tie-break 깊이→id)
- 규칙 A: 최소 K=8 / 상한 N=20
- 규칙 C: skip-with-probe (√n 프로브 + D3 서브트리 복원)

→ **mock 모드(`VITE_ENABLE_MOCK=true`)에서 첫 질문 순서·프로브·질문 수가 실서버와 다르게 보인다.**

## 영향 / 왜 비차단

- mock 모드는 **오프라인 프론트 개발용**. 실기기 확인·데모는 실서버 모드(`VITE_ENABLE_MOCK=false VITE_API_BASE=http://localhost:8080`)로 검증된 백엔드에 붙으므로 정확하다.
- 결과(preview)·학습 큐 계약 shape 는 동일 — 화면 구조는 안 깨진다. 어긋나는 것은 **질문 선택 순서·프로브 노출**뿐.

## 처리 옵션 (택1, 착수 시)

1. **mock 을 KST 규칙으로 재작성** — `traverse()` 에 blocked 순 정렬 + K/N + 프로브 구현(TS 포팅). mock 충실도 ↑, 구현량 중.
2. **mock 모드에 "구 순회" 배너/주석 강화** — 저비용, mock 이 근사임을 명시.
3. **현행 유지 + 본 백로그로 추적** — 실서버 모드가 정본이라 실질 영향 최소.

기본 권장 = 3 (실질 영향 최소), 프론트 데모를 mock 으로 자주 돌리게 되면 1.
