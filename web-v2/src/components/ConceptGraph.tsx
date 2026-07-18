// 그래프 렌더 — ⑥ 정본 조작 규약(탭 선택·핀치 줌·팬·호버 의존 0·depth≤2 선명/흐림)을
// 컴포넌트로 고정하고, ④-B 는 pathIds 하이라이트만 추가한다 (06 규칙 "같은 컴포넌트").
import { useEffect, useRef } from 'react'
import cytoscape, { type Core } from 'cytoscape'
import type { ConceptEdge, ConceptNode } from '../api/types'

interface Props {
  concepts: ConceptNode[]
  edges: ConceptEdge[]
  selectedId: string | null
  onSelect?: (conceptId: string | null) => void
  /** ④-B 학습 경로 하이라이트 — 위상순 계단의 conceptId 목록 */
  pathIds?: string[]
  height: number | string
}

// 틸 그로스 토큰 (index.css :root 와 동일 값 — Cytoscape 는 CSS 변수를 못 읽어 상수 복제)
const INK = '#14231f'
const SUB = '#5f6e67'
const LINE = '#cdd8d2'
const PAPER = '#fbfbf7'
const PRIMARY = '#0e7a6c'
const ACCENT_SOFT = '#dff2ec'

export default function ConceptGraph({ concepts, edges, selectedId, onSelect, pathIds, height }: Props) {
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
          selector: 'node',
          style: {
            width: 46,
            height: 46,
            shape: 'ellipse',
            'background-color': PAPER,
            'border-width': 1.5,
            'border-color': SUB,
            label: 'data(label)',
            'font-size': 8,
            color: SUB,
            'text-valign': 'center',
            'text-halign': 'center',
            'text-wrap': 'wrap',
            'text-max-width': '40',
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
        {
          selector: 'node.path',
          style: { 'background-color': ACCENT_SOFT, 'border-width': 2.5, 'border-color': PRIMARY, color: INK },
        },
        { selector: 'edge.path', style: { 'line-color': PRIMARY, 'target-arrow-color': PRIMARY, width: 2.5 } },
        // 선택 = 잉크 링 — 경로(초록 계열)와 분리, path 뒤에 둬서 path+sel 겹침에서도 선택이 이김
        {
          selector: 'node.sel',
          style: { 'border-width': 3, 'border-color': INK, color: INK, 'font-weight': 'bold' },
        },
        // 뎁스 링별 페이드 — 덩어리로 안 뭉치게 (2026-07-18 실기기 라운드: depth1/2 분리감)
        { selector: '.d1', style: { opacity: 0.75 } },
        { selector: '.d2', style: { opacity: 0.45 } },
        { selector: '.dim', style: { opacity: 0.2 } }, // 숨김 아님 — 지형은 보이되 시선만 모음
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
  }, [concepts, edges])

  // 선택·경로 스타일 — depth≤2 선명, 너머 흐림 (06 ●4 확정)
  useEffect(() => {
    const cy = cyRef.current
    if (!cy) return
    cy.batch(() => {
      cy.elements().removeClass('sel path dim d1 d2')
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
          sel.addClass('sel')
          // 무방향 BFS depth — 선택 중심 이웃 2단까지 선명
          const dist = new Map<string, number>()
          dist.set(selectedId, 0)
          let frontier = [selectedId]
          for (let d = 1; d <= 2; d++) {
            const nxt: string[] = []
            for (const id of frontier) {
              cy.getElementById(id)
                .connectedEdges()
                .forEach((e) => {
                  for (const nb of [e.source().id(), e.target().id()]) {
                    if (!dist.has(nb)) {
                      dist.set(nb, d)
                      nxt.push(nb)
                    }
                  }
                })
            }
            frontier = nxt
          }
          cy.nodes().forEach((n) => {
            const d = dist.get(n.id())
            if (d === undefined) n.addClass('dim')
            else if (d === 1) n.addClass('d1')
            else if (d === 2) n.addClass('d2')
          })
          cy.edges().forEach((e) => {
            const du = dist.get(e.source().id())
            const dv = dist.get(e.target().id())
            if (du === undefined || dv === undefined) e.addClass('dim')
            else {
              const maxd = Math.max(du, dv)
              if (maxd === 1) e.addClass('d1')
              else if (maxd === 2) e.addClass('d2')
            }
          })
          // 선택 노드로 부드럽게 재포커스
          cy.animate({ center: { eles: sel }, duration: 250 })
        }
      }
    })
  }, [selectedId, pathIds, concepts, edges])

  return <div ref={containerRef} style={{ width: '100%', height, touchAction: 'none' }} />
}
