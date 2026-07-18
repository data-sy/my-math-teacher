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

const INK = '#1a1a1a'
const SUB = '#666'
const LINE = '#c8c8c8'
const SOFT = '#f5f5f5'

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
            'background-color': '#fff',
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
          selector: 'node.sel',
          style: { 'border-width': 2.5, 'border-color': INK, color: INK, 'font-weight': 'bold' },
        },
        {
          selector: 'node.path',
          style: { 'background-color': SOFT, 'border-width': 2.5, 'border-color': INK, color: INK },
        },
        { selector: 'edge.path', style: { 'line-color': INK, 'target-arrow-color': INK, width: 2.5 } },
        { selector: '.dim', style: { opacity: 0.25 } }, // 숨김 아님 — 지형은 보이되 시선만 모음
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
      cy.elements().removeClass('sel path dim')
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
            if (!dist.has(n.id())) n.addClass('dim')
          })
          cy.edges().forEach((e) => {
            if (!dist.has(e.source().id()) || !dist.has(e.target().id())) e.addClass('dim')
          })
          // 선택 노드로 부드럽게 재포커스
          cy.animate({ center: { eles: sel }, duration: 250 })
        }
      }
    })
  }, [selectedId, pathIds, concepts, edges])

  return <div ref={containerRef} style={{ width: '100%', height, touchAction: 'none' }} />
}
