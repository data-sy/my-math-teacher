import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import {
  fetchChapters,
  fetchConceptDetail,
  fetchConceptGraph,
  searchConcepts,
} from '../../api/endpoints'
import type { Chapter, ConceptNode } from '../../api/types'
import BottomSheet from '../../components/BottomSheet'
import ConceptGraph from '../../components/ConceptGraph'
import { currentSemester, pickDefaultChapter } from '../../lib/curriculum'
import { loadGradeSemester } from '../../session/diagSession'
import s from './GraphExplore.module.css'

// 스코프 = 무엇을 로드하나 (06 ●3 확정) — 판독성은 depth 선명/흐림이 담당
type Scope = { type: 'chapter'; chapterId: string; name: string } | { type: 'all' }

export default function GraphExplore() {
  const nav = useNavigate()
  const [params] = useSearchParams()
  const initialConceptId = params.get('conceptId')

  const [scope, setScope] = useState<Scope | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(initialConceptId)
  const [scopeSheetOpen, setScopeSheetOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<ConceptNode[] | null>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const { data: chapters } = useQuery({ queryKey: ['chapters'], queryFn: () => fetchChapters() })

  // 진입 컨텍스트의 개념 — 스코프를 그 단원으로 (④ 약점 0 링크 · 06 ●4 초기 선택)
  const { data: initialDetail } = useQuery({
    queryKey: ['concept', initialConceptId],
    queryFn: () => fetchConceptDetail(initialConceptId!),
    enabled: !!initialConceptId,
  })

  // 초기 스코프 — ②와 동일 시기 추정 로직 재사용, 기억값 없으면 대표 단원 (06 ●3)
  useEffect(() => {
    if (scope || !chapters) return
    if (initialConceptId) {
      if (!initialDetail) return
      const ch = chapters.find((c) => c.chapterId === initialDetail.concept.chapterId)
      if (ch) setScope({ type: 'chapter', chapterId: ch.chapterId, name: ch.name })
      else setScope({ type: 'all' })
      return
    }
    const gs = loadGradeSemester()
    const month = new Date().getMonth() + 1
    const def = gs
      ? pickDefaultChapter(chapters, gs.grade, gs.semester, month)
      : pickDefaultChapter(chapters, '중3', currentSemester(month), month)
    if (def) setScope({ type: 'chapter', chapterId: def.chapterId, name: def.name })
    else setScope({ type: 'all' })
  }, [scope, chapters, initialConceptId, initialDetail])

  const graphQ = useQuery({
    queryKey: ['concepts', scope?.type === 'chapter' ? scope.chapterId : 'all'],
    queryFn: () => fetchConceptGraph(scope?.type === 'chapter' ? scope.chapterId : undefined),
    enabled: !!scope,
  })

  // 초기 선택 — 컨텍스트 없으면 스코프 대표 노드(단원 내 후수-최상위 = ② 프론티어와 동일 개념)
  useEffect(() => {
    if (selectedId || !graphQ.data || !scope) return
    const { concepts, edges } = graphQ.data
    const inScope =
      scope.type === 'chapter' ? concepts.filter((c) => c.chapterId === scope.chapterId) : concepts
    const ids = new Set(inScope.map((c) => c.conceptId))
    const top = inScope.find(
      (c) => !edges.some((e) => e.from === c.conceptId && ids.has(e.to)),
    )
    if (top) setSelectedId(top.conceptId)
  }, [selectedId, graphQ.data, scope])

  // ●5 노드 정보 시트 데이터
  const { data: detail } = useQuery({
    queryKey: ['concept', selectedId],
    queryFn: () => fetchConceptDetail(selectedId!),
    enabled: !!selectedId,
  })

  // ●2 검색 — 입력 중 자동완성
  function onQueryChange(v: string) {
    setQuery(v)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (!v.trim()) {
      setResults(null)
      return
    }
    debounceRef.current = setTimeout(async () => {
      try {
        setResults(await searchConcepts(v.trim()))
      } catch {
        setResults([])
      }
    }, 250)
  }

  // 검색 결과·체인 pill 탭 — 경계 개념이면 스코프 자동 전환 (06 ●2·●3)
  function jumpTo(c: ConceptNode) {
    if (scope?.type === 'chapter' && c.chapterId !== scope.chapterId && chapters) {
      const ch = chapters.find((x) => x.chapterId === c.chapterId)
      if (ch) setScope({ type: 'chapter', chapterId: ch.chapterId, name: ch.name })
    }
    setSelectedId(c.conceptId)
    setQuery('')
    setResults(null)
  }

  const chapterName = (c: ConceptNode) =>
    chapters?.find((x) => x.chapterId === c.chapterId)?.name ?? ''

  const graphData = graphQ.data
  const stackedPills = (nodes: ConceptNode[]) => {
    const shown = nodes.slice(0, 2) // 세로 스택, 많으면 "+N" (06 ●5)
    const extra = nodes.length - shown.length
    return { shown, extra }
  }
  const pre = useMemo(() => stackedPills(detail?.prerequisites ?? []), [detail])
  const succ = useMemo(() => stackedPills(detail?.successors ?? []), [detail])

  return (
    <div className="screen">
      {/* ●1 ‹ 홈 */}
      <Link className="nav-back" to="/">‹ 홈</Link>

      {/* ●2 검색 한 줄 */}
      <div className={s.search}>
        <input
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="🔍 개념 검색 (예: 인수분해)"
        />
        {results && (
          <div className={s.searchList}>
            {results.length === 0 && (
              <button disabled>
                <small>검색 결과가 없어요</small>
              </button>
            )}
            {results.map((r) => (
              <button key={r.conceptId} onClick={() => jumpTo(r)}>
                {r.conceptName} <small>· {chapterName(r)}</small>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* ●3 스코프 칩 = 로드 범위 */}
      <div className={s.scopeRow}>
        <button className={s.scopeChip} onClick={() => setScopeSheetOpen(true)}>
          {scope?.type === 'chapter' ? scope.name : '모두 보기'} ▾
        </button>
      </div>

      {/* ●4 그래프 캔버스 — 탭 선택 · 핀치 줌 · 팬 · 호버 의존 0 */}
      <div className={s.canvas}>
        {graphData && (
          <div style={{ position: 'absolute', inset: 0 }}>
            <ConceptGraph
              concepts={graphData.concepts}
              edges={graphData.edges}
              selectedId={selectedId}
              onSelect={setSelectedId}
              height="100%"
            />
          </div>
        )}
        {graphQ.isError && (
          <div className="err-card" style={{ margin: 12 }}>
            연결이 잠깐 끊겼어요.
            <br />
            <button className="retry" onClick={() => graphQ.refetch()}>다시 시도</button>
          </div>
        )}
      </div>

      {/* ●5 노드 정보 시트 — 3열 스택 체인, CTA 를 가리지 않는 인플로우 패널 */}
      {selectedId && detail && (
        <div className={s.nodeSheet}>
          <div className={s.grab} />
          <div className={s.sheetName}>{detail.concept.conceptName}</div>
          <div className={s.sheetDesc}>{detail.concept.description}</div>
          <div className={s.chain}>
            <div className={s.chainCol}>
              <span className={s.chainLabel}>먼저</span>
              {pre.shown.map((p) => (
                <button key={p.conceptId} className={s.pill} onClick={() => jumpTo(p)}>
                  {p.conceptName}
                </button>
              ))}
              {pre.extra > 0 && <span className={s.pill}>+{pre.extra}</span>}
              {pre.shown.length === 0 && <span className={s.pill}>—</span>}
            </div>
            <span className={s.chainArr}>→</span>
            <div className={s.chainCol}>
              <span className={s.chainLabel}>지금</span>
              <span className={`${s.pill} ${s.pillNow}`}>{detail.concept.conceptName}</span>
            </div>
            <span className={s.chainArr}>→</span>
            <div className={s.chainCol}>
              <span className={s.chainLabel}>다음</span>
              {succ.shown.map((p) => (
                <button key={p.conceptId} className={s.pill} onClick={() => jumpTo(p)}>
                  {p.conceptName}
                </button>
              ))}
              {succ.extra > 0 && <span className={s.pill}>+{succ.extra}</span>}
              {succ.shown.length === 0 && <span className={s.pill}>—</span>}
            </div>
          </div>
        </div>
      )}

      {/* ●6 상시 1차 CTA — 탐색→진단 루프 */}
      <button className="btn-primary" onClick={() => nav('/entry')}>
        내 약점 진단하기
      </button>

      {/* 스코프 선택 시트 — 단원 목록(②와 동일 데이터) + "모두 보기" */}
      <BottomSheet open={scopeSheetOpen} onClose={() => setScopeSheetOpen(false)}>
        <div className={s.sheetTitle}>로드 범위</div>
        <button
          className={s.sheetItem}
          onClick={() => {
            setScope({ type: 'all' })
            setSelectedId(null)
            setScopeSheetOpen(false)
          }}
        >
          모두 보기
        </button>
        {['중1', '중2', '중3', '고1', '고2', '고3']
          .filter((g) => chapters?.some((c) => c.grade === g))
          .map((g) => (
            <div key={g}>
              <div className={s.sheetGroup}>{g}</div>
              {chapters
                ?.filter((c) => c.grade === g)
                .map((c: Chapter) => (
                  <button
                    key={c.chapterId}
                    className={s.sheetItem}
                    onClick={() => {
                      setScope({ type: 'chapter', chapterId: c.chapterId, name: c.name })
                      setSelectedId(null)
                      setScopeSheetOpen(false)
                    }}
                  >
                    {c.name} <small style={{ color: 'var(--sub)' }}>· {c.semester}학기</small>
                  </button>
                ))}
            </div>
          ))}
      </BottomSheet>
    </div>
  )
}
