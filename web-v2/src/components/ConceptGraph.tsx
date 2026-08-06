// 그래프 렌더 — ⑥ 정본 조작 규약(탭 선택·핀치 줌·팬·호버 의존 0)을 컴포넌트로 고정하고,
// ④-B 는 pathIds 하이라이트만 추가한다 (06 규칙 "같은 컴포넌트").
// 선택 시 시각 위계 = A안 "색 층위" (2026-07-19 컨설팅 확정, 🤖-그래프-위계-A안-핸드오프):
//   hue = 방향(선수 틸 / 후수 보라), 명도 = 거리(직접/원거리), 계보 밖 = 무채색 축소.
//   상태 공간은 노드 6 + 엣지 3 고정 — 뎁스별 색 세분화 금지, 크기만 뎁스 따라 감소 허용.
import { useEffect, useRef } from 'react'
import cytoscape, { type Core } from 'cytoscape'
import type { ConceptEdge, ConceptNode } from '../api/types'

// 틸 그로스 토큰 (index.css :root 와 동일 값 — Cytoscape 는 CSS 변수를 못 읽어 상수 복제)
const INK = '#14231f'
const SUB = '#5f6e67'
const LINE = '#cdd8d2'
const PAPER = '#fbfbf7'
const PRIMARY = '#0e7a6c' // 선수-직접
const ACCENT_SOFT = '#dff2ec'
const PRE_FAR = '#2fb5a6' // --graph-pre-far
const SUCC = '#7c3aed' // --graph-succ
const SUCC_FAR = '#a78bfa' // --graph-succ-far
const OUT_NODE = '#dce1e8' // --graph-out
// 엣지 전용 틴트 (컨설팅 기준 스펙 원본값)
const PRE_EDGE_FAR = '#a5d8d2'
const SUCC_EDGE_FAR = '#d4c6fd'
const OUT_EDGE = '#e8ebef'

/** 그래프가 그리는 데 필요한 최소 노드 — 큐 항목(설명 없음)도 그대로 넘길 수 있게 폭을 좁혔다 */
type GraphNode = Pick<ConceptNode, 'conceptId' | 'conceptName'>

interface Props {
  concepts: GraphNode[]
  edges: ConceptEdge[]
  selectedId: string | null
  onSelect?: (conceptId: string | null) => void
  /** ④-B 학습 경로 하이라이트 — 위상순 계단의 conceptId 목록 */
  pathIds?: string[]
  /** ④-B 완료 표시 계단 — 물러난 위계(점선 테두리·중립 채움) */
  doneIds?: string[]
  /** ④-B "여기부터" (서버 파생 current) — 굵은 PRIMARY 링 */
  currentId?: string | null
  /**
   * 축소 개요 모드(④-B). 노드 집합이 큐 항목뿐이라(보통 5~30개) **라벨을 전부 노출**한다.
   * 겹침은 이름 축약(compactLabel)으로 막는다 — 구 정책(선택 노드만 노출)은 "아무것도 못 읽는
   * 개요"라 폐기(2026-08-05 사용자 결정 D). 전체 이름은 그래프 아래 nodeInfo 행이 담당.
   */
  compact?: boolean
  height: number | string
}

const STATE_CLASSES =
  'sel preD preF succD succF far3 out eDpre eDsucc eFpre eFsucc eOut path done current'

/** compact 라벨 축약 — cytoscape 에 ellipsis 옵션이 없어 데이터 단계에서 자른다 */
const compactLabel = (name: string) => (name.length > 10 ? `${name.slice(0, 9)}…` : name)

/**
 * compact 초기 줌 하한. fit 은 노드가 늘수록 무한히 축소해 라벨을 읽을 수 없게 만든다
 * (13개 = 실효 4px). 하한 아래로 fit 되면 하한으로 올리고 "여기부터"로 중심을 옮긴다 —
 * 전체를 보고 싶으면 핀치로 줌아웃(minZoom 은 낮게 열어둠).
 */
const COMPACT_MIN_FIT = 0.85

/**
 * 스크롤 컨테이너처럼 콘텐츠 경계 밖으로 패닝되지 않게 되돌린다. 중심 정렬(초기 포커스·선택
 * 재중심)은 대상이 계단 첫머리·끝머리면 화면 절반을 빈 채로 남기기 때문에 그 뒤에 붙인다.
 */
function clampPan(cy: Core, pad = 8) {
  const bb = cy.elements().renderedBoundingBox()
  const axis = (b1: number, b2: number, size: number) => {
    const len = b2 - b1
    if (len <= size - 2 * pad) return (size - len) / 2 - b1 // 다 들어가면 가운데
    if (b1 > pad) return pad - b1 // 앞쪽 빈틈 제거
    if (b2 < size - pad) return size - pad - b2 // 뒤쪽 빈틈 제거
    return 0
  }
  cy.panBy({ x: axis(bb.x1, bb.x2, cy.width()), y: axis(bb.y1, bb.y2, cy.height()) })
}

/** 방향 BFS — adj(선수 방향 또는 후수 방향)로 도달 가능한 노드의 거리 맵 (선택 제외) */
function bfsDist(start: string, adj: Map<string, string[]>): Map<string, number> {
  const dist = new Map<string, number>()
  let frontier = [start]
  for (let d = 1; frontier.length > 0; d++) {
    const next: string[] = []
    for (const id of frontier) {
      for (const nb of adj.get(id) ?? []) {
        if (nb !== start && !dist.has(nb)) {
          dist.set(nb, d)
          next.push(nb)
        }
      }
    }
    frontier = next
  }
  return dist
}

export default function ConceptGraph({
  concepts,
  edges,
  selectedId,
  onSelect,
  pathIds,
  doneIds,
  currentId,
  compact,
  height,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const cyRef = useRef<Core | null>(null)
  const onSelectRef = useRef(onSelect)
  onSelectRef.current = onSelect
  // 초기 포커스 대상 — 그래프 재생성 트리거는 아니라 ref 로만 읽는다
  const focusIdRef = useRef(currentId)
  focusIdRef.current = currentId

  // 그래프 데이터가 바뀔 때만 재생성
  useEffect(() => {
    if (!containerRef.current) return
    const cy = cytoscape({
      container: containerRef.current,
      elements: [
        ...concepts.map((c) => ({
          data: { id: c.conceptId, label: compact ? compactLabel(c.conceptName) : c.conceptName },
        })),
        ...edges.map((e) => ({ data: { id: `${e.from}->${e.to}`, source: e.from, target: e.to } })),
      ],
      style: [
        {
          // 무선택 모드 = 전 노드 중립 단일 톤·균일 크기 (A안 불변 원칙 8)
          selector: 'node',
          style: {
            width: 34,
            height: 34,
            shape: 'ellipse',
            'background-color': PAPER,
            'border-width': 1.5,
            'border-color': SUB,
            label: 'data(label)',
            'font-size': 10,
            color: SUB,
            'text-valign': 'bottom',
            'text-halign': 'center',
            'text-margin-y': 4,
            'text-wrap': 'wrap',
            'text-max-width': compact ? '72' : '76',
            // 선택 전이 1회 200~250ms — 클래스 교체 때만 발화, 지속 모션 없음 (원칙 7)
            'transition-property': 'width height background-color border-width',
            'transition-duration': 220,
          },
        },
        {
          selector: 'edge',
          style: {
            width: 1.5,
            'line-color': LINE,
            'target-arrow-shape': 'triangle', // 방향 = 선수 → 후수 (06 ●4)
            'target-arrow-color': LINE,
            'arrow-scale': 0.7,
            'curve-style': 'bezier',
          },
        },
        // ── 계보 상태 (노드 6종 중 선택 제외 5종) ──
        { selector: 'node.preD', style: { width: 30, height: 30, 'background-color': PRIMARY, 'border-width': 0, 'font-size': 10.5, 'font-weight': 700, color: INK } },
        { selector: 'node.preF', style: { width: 22, height: 22, 'background-color': PRE_FAR, 'border-width': 0, 'font-size': 9, 'font-weight': 500, color: SUB } },
        { selector: 'node.succD', style: { width: 30, height: 30, 'background-color': SUCC, 'border-width': 0, 'font-size': 10.5, 'font-weight': 700, color: INK } },
        { selector: 'node.succF', style: { width: 22, height: 22, 'background-color': SUCC_FAR, 'border-width': 0, 'font-size': 9, 'font-weight': 500, color: SUB } },
        { selector: 'node.far3', style: { width: 18, height: 18 } }, // 뎁스3+ 크기만 감소 (색 세분화 금지)
        {
          // 계보 밖 = 무채색화 + 축소 + 라벨 감쇠(저줌 생략) — 투명도 금지 (원칙 4)
          selector: 'node.out',
          style: { width: 14, height: 14, 'background-color': OUT_NODE, 'border-width': 0, 'font-size': 8, 'font-weight': 400, color: SUB, 'min-zoomed-font-size': 8 },
        },
        // ── 엣지 3종: 직접(선택 인접) / 계보 내 원거리 / 계보 밖(화살촉 생략) ──
        { selector: 'edge.eDpre', style: { width: 2.5, 'line-color': PRIMARY, 'target-arrow-color': PRIMARY } },
        { selector: 'edge.eDsucc', style: { width: 2.5, 'line-color': SUCC, 'target-arrow-color': SUCC } },
        { selector: 'edge.eFpre', style: { width: 1.5, 'line-color': PRE_EDGE_FAR, 'target-arrow-color': PRE_EDGE_FAR } },
        { selector: 'edge.eFsucc', style: { width: 1.5, 'line-color': SUCC_EDGE_FAR, 'target-arrow-color': SUCC_EDGE_FAR } },
        { selector: 'edge.eOut', style: { width: 1, 'line-color': OUT_EDGE, 'target-arrow-shape': 'none' } },
        // ④-B 학습 경로 — 계보 상태 위에서도 경로 표식 유지 (path 가 계보 클래스보다 뒤)
        {
          selector: 'node.path',
          style: { 'background-color': ACCENT_SOFT, 'border-width': 2.5, 'border-color': PRIMARY, color: INK },
        },
        { selector: 'edge.path', style: { 'line-color': PRIMARY, 'target-arrow-color': PRIMARY, width: 2.5 } },
        // ④-B 큐 상태 2종 — 새 hue 를 만들지 않고 기존 어휘(테두리 형태·PRIMARY)만 재사용해
        // A안 "노드 6 상태 고정" 팔레트를 넓히지 않는다. 계보 클래스 뒤 = 테두리 속성 승계
        {
          // 완료한 계단 = 물러남 (채움 없음 + 점선). 투명도 미사용 (원칙 4)
          selector: 'node.done',
          style: {
            'background-color': PAPER,
            'border-width': 1.5,
            'border-color': LINE,
            'border-style': 'dashed',
            color: SUB,
            'font-weight': 400,
          },
        },
        {
          // "여기부터" = 서버 파생 current. 체크리스트의 같은 표식과 한 어휘
          selector: 'node.current',
          style: {
            'background-color': ACCENT_SOFT,
            'border-width': 3,
            'border-color': PRIMARY,
            'border-style': 'solid',
            color: INK,
            'font-weight': 800,
          },
        },
        {
          // 선택 = 크기 최대 + 명도 극단(잉크) + 흰 링 + 외곽 링 + 할로 — hue 미사용 (원칙 5)
          selector: 'node.sel',
          style: {
            width: 40,
            height: 40,
            'background-color': INK,
            'border-width': 3,
            'border-color': '#fff',
            'outline-width': 1.5,
            'outline-color': INK,
            'outline-offset': 0,
            'underlay-color': INK,
            'underlay-opacity': 0.12,
            'underlay-padding': 8,
            'font-size': 12,
            'font-weight': 800,
            color: INK,
          },
        },
      ],
      // compact 은 라벨을 전부 띄우므로 간격을 넓힌다 — 좁으면 라벨(text-valign:bottom)이
      // 세로 DAG 의 간선을 그대로 덮어 "연결이 없는 것처럼" 보인다 (2026-08-05 실측)
      layout: {
        name: 'breadthfirst',
        directed: true,
        spacingFactor: compact ? 1.9 : 1.1,
        padding: 12,
      },
      userZoomingEnabled: true, // 핀치 줌
      userPanningEnabled: true, // 드래그 팬
      boxSelectionEnabled: false,
    })

    if (compact) {
      // breadthfirst 는 생성자 안에서 동기로 끝나 layoutstop 이 리스너 등록 전에 발화한다
      // → 인라인 실행 + 혹시 비동기로 도는 경우 대비해 layoutstop 에도 건다(줌 검사라 멱등)
      const clampZoom = () => {
        if (cy.zoom() >= COMPACT_MIN_FIT) return
        cy.zoom(COMPACT_MIN_FIT)
        const focus = focusIdRef.current ? cy.getElementById(focusIdRef.current) : null
        if (focus && focus.nonempty()) cy.center(focus)
        else cy.center()
        clampPan(cy)
      }
      clampZoom()
      cy.one('layoutstop', clampZoom)
    }

    cy.on('tap', 'node', (ev) => onSelectRef.current?.(ev.target.id()))
    cy.on('tap', (ev) => {
      if (ev.target === cy) onSelectRef.current?.(null) // 빈 곳 탭 = 선택 해제
    })

    cyRef.current = cy
    return () => {
      cy.destroy()
      cyRef.current = null
    }
  }, [concepts, edges, compact])

  // 선택·경로 스타일 — A안 계보 분류: 선수(조상)/후수(자손) 방향 BFS, 직접=거리1·원거리=2+(3+는 크기만 감소)
  useEffect(() => {
    const cy = cyRef.current
    if (!cy) return
    cy.batch(() => {
      cy.elements().removeClass(STATE_CLASSES)
      if (doneIds && doneIds.length > 0) {
        const doneSet = new Set(doneIds)
        cy.nodes().forEach((n) => {
          if (doneSet.has(n.id())) n.addClass('done')
        })
      }
      if (currentId) cy.getElementById(currentId).addClass('current')
      if (pathIds && pathIds.length > 0) {
        const pathSet = new Set(pathIds)
        cy.nodes().forEach((n) => {
          if (pathSet.has(n.id())) n.addClass('path')
        })
        cy.edges().forEach((e) => {
          if (pathSet.has(e.source().id()) && pathSet.has(e.target().id())) e.addClass('path')
        })
      }
      if (selectedId) {
        const sel = cy.getElementById(selectedId)
        if (sel.nonempty()) {
          // 인접 맵 — 엣지 방향: from(선수) → to(후수)
          const toPre = new Map<string, string[]>() // n 의 직계 선수들
          const toSucc = new Map<string, string[]>() // n 의 직계 후수들
          for (const e of edges) {
            toPre.set(e.to, [...(toPre.get(e.to) ?? []), e.from])
            toSucc.set(e.from, [...(toSucc.get(e.from) ?? []), e.to])
          }
          const anc = bfsDist(selectedId, toPre) // 선수 계보
          const desc = bfsDist(selectedId, toSucc) // 후수 계보

          cy.nodes().forEach((n) => {
            const id = n.id()
            if (id === selectedId) return
            const da = anc.get(id)
            const dd = desc.get(id)
            if (da !== undefined) {
              n.addClass(da === 1 ? 'preD' : 'preF')
              if (da >= 3) n.addClass('far3')
            } else if (dd !== undefined) {
              n.addClass(dd === 1 ? 'succD' : 'succF')
              if (dd >= 3) n.addClass('far3')
            } else {
              n.addClass('out')
            }
          })

          const sideOf = (id: string) =>
            id === selectedId ? 'sel' : anc.has(id) ? 'anc' : desc.has(id) ? 'desc' : 'out'
          cy.edges().forEach((e) => {
            const s = sideOf(e.source().id())
            const t = sideOf(e.target().id())
            if (s === 'anc' && t === 'sel') e.addClass('eDpre')
            else if (s === 'sel' && t === 'desc') e.addClass('eDsucc')
            else if (s === 'anc' && t === 'anc') e.addClass('eFpre')
            else if (s === 'desc' && t === 'desc') e.addClass('eFsucc')
            else if (s === 'anc' && t === 'desc') e.addClass('eFsucc') // 계보 내 우회 — 도착 쪽 틴트
            else e.addClass('eOut')
          })

          sel.addClass('sel')
          // 선택 노드로 부드럽게 재포커스 — compact 는 재중심이 빈 여백을 만들 수 있어 되돌린다
          cy.animate({
            center: { eles: sel },
            duration: 250,
            complete: compact ? () => clampPan(cy) : undefined,
          })
        }
      }
    })
  }, [selectedId, pathIds, doneIds, currentId, concepts, edges, compact])

  return <div ref={containerRef} style={{ width: '100%', height, touchAction: 'none' }} />
}
