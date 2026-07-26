# [M7] 그래프 노드 시트 "다음(후수)" 칸이 거의 항상 비어 있음 — 채우지 않기로 결정(discard)

**등록:** 2026-07-26 (그래프 legend 색 작업 중 사용자 발견) · **상태:** ✅ **결정 = 채우지 않음(discard, 2026-07-26 사용자)** · **기존 갭**(KST 변경과 무관)

## 결정 (2026-07-26)

**후수 텍스트를 채우지 않는다 — 오히려 노이즈가 될 우려(사용자 판단).** 시트는 현행대로: 후수 있으면(단원 스코프 비-center 선택 시 등 우연히 로드된 경우) 보이고, 없으면 "—" placeholder 유지. **아래 "수정 옵션"은 채택하지 않음(기록 보존용).** 렌더 로직은 먼저/다음 대칭이라 코드 변경 없음. 나중에 방향이 바뀌면 이 결정부터 뒤집는다.

## 증상

GraphExplore 노드 정보 시트(●5)의 **다음(후수)** 칸이 대부분의 노드에서 "—"(빈 placeholder)로 뜬다. **먼저(선수)** 칸은 정상(2개 + "+N"). 렌더 로직은 `stackedPills`로 **완전 대칭** — 일부러 다음을 뺀 게 아니다.

## 근본 원인

`fetchConceptDetail(cid)`가 후수를 `/api/v1/concepts/nodes/{cid}` + `/edges/{cid}`에서 유도하는데, 이 두 엔드포인트는 **그 개념의 선수(prerequisite) 서브그래프만** 반환한다(실측: 7123 기준 `source==7123`인 후수 엣지 0개, `target==7123`인 선수 엣지만 존재).

서브그래프가 **cid 자신을 최상위로 센터링**하므로 cid는 항상 top → 나가는(후수) 엣지가 0 → `successors` 항상 빈 배열.

`fetchConceptGraph`도 같은 엔드포인트를 써서 **그래프 캔버스도 center의 선수 폐쇄만 로드**한다(후수=보라 색은 center가 아닌 *선택 노드* 기준 상대색이라, 단원 스코프에서 deeper 노드를 선택할 때만 보임).

## 수정 옵션

1. **부분/값싼 수정 (프론트만):** 단원 스코프에서 시트의 후수를 `fetchConceptDetail` 대신 **이미 로드된 `graphData.edges`** 에서 유도(`edges.filter(from===selectedId).map(to)`). 단원 스코프·비-center 선택에선 채워지나, **"모두 보기" 스코프는 선택 노드가 곧 center라 여전히 빈다**(선택 시 그 노드 중심으로 재로드).
2. **완전한 수정 (백엔드 필요):** 후수 방향 서브그래프/노드 엔드포인트 신설(역방향 = `blockedDescendants` CTE 방향, `to_concept_id=cid → from_concept_id`). 시트·그래프 both 정확. 스코프 큼.

권장 = 런치 후 2번(백엔드 후수 엔드포인트). 급하면 1번으로 단원 스코프만 우선 완화.

## 참고

- `web-v2/src/api/endpoints.ts` `fetchConceptDetail`/`fetchConceptGraph`
- `web-v2/src/screens/GraphExplore/GraphExplore.tsx` ●5 노드 시트 (pre/succ = `stackedPills`)
- 백엔드 `/api/v1/concepts/nodes|edges/{id}` (선수 서브그래프)
