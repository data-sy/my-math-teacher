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

interface Props {
  concepts: ConceptNode[]
  edges: ConceptEdge[]
  selectedId: string | null
  onSelect?: (conceptId: string | null) => void
  /** ④-B 학습 경로 하이라이트 — 위상순 계단의 conceptId 목록 */
  pathIds?: string[]
  /** 축소 fit 개요 모드(④-B) — 라벨은 선택 노드만 (저줌 겹침 방지. min-zoomed-font-size 는 DPR 의존이라 미사용) */
  compact?: boolean
  height: number | string
}

const STATE_CLASSES = 'sel preD preF succD succF far3 out eDpre eDsucc eFpre eFsucc eOut path'

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

export default function ConceptGraph({ concepts, edges, selectedId, onSelect, pathIds, compact, height }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const cyRef = useRef<Core | null>(null)
  const onSelectRef = useRef(onSelect)
  onSelectRef.current = onSelect

  // 그래프 데이터가 바뀔 때만 재생성
  useEffect(() => {
    if (!containerRef.current) return
    const cy = cytoscape({
      container: containerRef.current,
      elements: [
        ...concepts.map((c) => ({ data: { id: c.conceptId, label: c.conceptName } })),
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
            'text-max-width': '76',
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
        // compact(④-B 개요) — 라벨은 선택 노드만. sel 뒤에 둬 text-opacity 만 덮어씀
        ...(compact
          ? [
              { selector: 'node', style: { 'text-opacity': 0 } },
              { selector: 'node.sel', style: { 'text-opacity': 1 } },
            ]
          : []),
      ],
      layout: { name: 'breadthfirst', directed: true, spacingFactor: 1.1, padding: 12 },
      userZoomingEnabled: true, // 핀치 줌
      userPanningEnabled: true, // 드래그 팬
      boxSelectionEnabled: false,
    })

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
          // 선택 노드로 부드럽게 재포커스
          cy.animate({ center: { eles: sel }, duration: 250 })
        }
      }
    })
  }, [selectedId, pathIds, concepts, edges])

  return <div ref={containerRef} style={{ width: '100%', height, touchAction: 'none' }} />
}
