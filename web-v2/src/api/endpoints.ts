// flow-map API 표의 전 엔드포인트 — 타입드 함수 한 벌.
// 실서버 wire shape(2026-07-18 실측)을 이 레이어에서 도메인 타입으로 흡수한다:
//   · id 는 서버가 number, 도메인은 string — 응답에서 String() 정규화, 요청은 문자열 그대로(Jackson 숫자 문자열 코어션 실측 확인)
//   · chapters 실응답 = PrimeVue 트리(구 Vue 소비용) → 학기별 fan-out + leaf 평탄화 (mock 은 flat — 모드 분기)
//   · 그래프 = nodes/{id}·edges/{id} 중심 서브그래프 (전체 그래프 엔드포인트 없음)
import { apiFetch, mockEnabled } from './client'
import type {
  Answer,
  Chapter,
  ConceptDetail,
  ConceptGraphResponse,
  ConceptNode,
  ConceptSearchHit,
  DiagnosisEntry,
  FrontierResponse,
  Grade,
  LearningQueue,
  NextResponse,
  PreviewResponse,
  SaveDiagnosisResponse,
  Semester,
} from './types'

// ── 실서버 wire 타입 (이 파일 밖으로 내보내지 않음) ──

interface ChapterTreeNode {
  key: string
  label: string
  children: ChapterTreeNode[]
}

interface ConceptWire {
  conceptId: number | string
  conceptName: string
  conceptDescription?: string | null
  conceptChapterId?: number | string
  conceptChapterName?: string
}

interface EdgeWire {
  data: { id: string; source: number | string; target: number | string }
}

const sid = (v: number | string) => String(v)

const toNode = (w: ConceptWire): ConceptNode => ({
  conceptId: sid(w.conceptId),
  conceptName: w.conceptName,
  description: w.conceptDescription ?? '',
  chapterId: w.conceptChapterId != null ? sid(w.conceptChapterId) : '',
})

// ── ② 영역 진입 ──

/** 실서버 조회 단위 = (학년, 학기) — MVP 커버리지는 중등 6개 학기 (고등 커리큘럼 매핑은 백로그) */
const REAL_TERMS: { grade: Grade; semester: Semester }[] = [
  { grade: '중1', semester: 1 },
  { grade: '중1', semester: 2 },
  { grade: '중2', semester: 1 },
  { grade: '중2', semester: 2 },
  { grade: '중3', semester: 1 },
  { grade: '중3', semester: 2 },
]

function flattenChapterTree(tree: ChapterTreeNode[], grade: Grade, semester: Semester, orderBase: number): Chapter[] {
  // leaf(children 없음) = chapters.chapter_id — 트리 순회 순서가 계단순 (2026-07-18 실측)
  const out: Chapter[] = []
  const walk = (nodes: ChapterTreeNode[]) => {
    for (const n of nodes) {
      if (!n.children || n.children.length === 0) {
        out.push({ chapterId: n.key, name: n.label, grade, semester, order: orderBase + out.length + 1 })
      } else {
        walk(n.children)
      }
    }
  }
  walk(tree)
  return out
}

export async function fetchChapters(_grade?: string, _semester?: number): Promise<Chapter[]> {
  if (mockEnabled()) {
    // mock = 계단순 flat 전체 (A-13 원 가정 유지 — mock 세계는 자체 데이터)
    return apiFetch<Chapter[]>('/api/v1/chapters')
  }
  // 실서버: ±윈도가 학기 경계를 넘으므로 중등 전 학기 fan-out 후 병합 (permitAll·Query 캐시)
  const trees = await Promise.all(
    REAL_TERMS.map((t) =>
      apiFetch<ChapterTreeNode[]>(
        `/api/v1/chapters?grade=${encodeURIComponent(t.grade)}&semester=${encodeURIComponent(`${t.semester}학기`)}`,
      ),
    ),
  )
  return trees.flatMap((tree, i) => flattenChapterTree(tree, REAL_TERMS[i].grade, REAL_TERMS[i].semester, i * 100))
}

export async function fetchFrontier(chapterId: string): Promise<FrontierResponse> {
  const r = await apiFetch<{ concepts: { conceptId: number | string; conceptName: string }[] }>(
    '/api/v1/diagnosis/frontier',
    { method: 'POST', body: { chapterId } },
  )
  return { concepts: r.concepts.map((c) => ({ conceptId: sid(c.conceptId), conceptName: c.conceptName })) }
}

// ── ③ 문답 (stateless — 매 요청 answered 전체) ──
export async function fetchNext(entry: DiagnosisEntry, answered: Answer[]): Promise<NextResponse> {
  const r = await apiFetch<NextResponse>('/api/v1/diagnosis/next', {
    method: 'POST',
    body: { entry, answered },
  })
  if ('next' in r && r.next) return { ...r, next: { ...r.next, conceptId: sid(r.next.conceptId) } }
  return r
}

const normalizeResult = (p: PreviewResponse): PreviewResponse => ({
  ...p,
  cards: p.cards.map((c) => ({ ...c, conceptId: sid(c.conceptId) })),
  more: p.more.map((c) => ({ ...c, conceptId: sid(c.conceptId) })),
})

// ── ④-A 무영속 프리뷰 ──
export async function fetchPreview(entry: DiagnosisEntry, answered: Answer[]): Promise<PreviewResponse> {
  return normalizeResult(
    await apiFetch<PreviewResponse>('/api/v1/diagnosis/preview', { method: 'POST', body: { entry, answered } }),
  )
}

// ── ④-B 귀속 + 큐 (인증) ──
export async function saveDiagnosis(entry: DiagnosisEntry, answered: Answer[]): Promise<SaveDiagnosisResponse> {
  const r = await apiFetch<{ userTestId: number | string; result: PreviewResponse }>('/api/v1/diagnosis', {
    method: 'POST',
    body: { entry, answered },
    auth: true,
  })
  return { userTestId: sid(r.userTestId), result: normalizeResult(r.result) }
}

interface QueueWire {
  queueId: number | string
  userTestId: number | string
  items: {
    queueItemId: number | string
    conceptId: number | string
    conceptName: string
    position: number
    done: boolean
    current: boolean
  }[]
}

const normalizeQueue = (q: QueueWire | null): LearningQueue | null =>
  q && {
    queueId: sid(q.queueId),
    userTestId: sid(q.userTestId),
    items: q.items.map((i) => ({ ...i, queueItemId: sid(i.queueItemId), conceptId: sid(i.conceptId) })),
  }

export async function createQueue(userTestId: string): Promise<LearningQueue> {
  const q = await apiFetch<QueueWire>('/api/v1/learning-queues', {
    method: 'POST',
    body: { userTestId },
    auth: true,
  })
  return normalizeQueue(q)!
}

export async function fetchSavedDiagnosis(userTestId: string): Promise<PreviewResponse> {
  return normalizeResult(await apiFetch<PreviewResponse>(`/api/v1/diagnosis/${userTestId}`, { auth: true }))
}

export async function fetchMyQueue(): Promise<LearningQueue | null> {
  return normalizeQueue(await apiFetch<QueueWire | null>('/api/v1/learning-queues/me', { auth: true }))
}

/** 응답 = 갱신된 큐 전체 (2026-07-18 실측 — 화면은 invalidate 재조회를 쓰므로 반환값은 참고용) */
export async function toggleQueueItemDone(queueId: string, queueItemId: string): Promise<LearningQueue> {
  const q = await apiFetch<QueueWire>(`/api/v1/learning-queues/${queueId}/items/${queueItemId}/done`, {
    method: 'PATCH',
    auth: true,
  })
  return normalizeQueue(q)!
}

// ── ⑥ 그래프 탐색 (permitAll) — 중심 개념 기준 서브그래프 ──
export async function fetchConceptGraph(centerConceptId: string): Promise<ConceptGraphResponse> {
  const [nodes, edges] = await Promise.all([
    apiFetch<ConceptWire[]>(`/api/v1/concepts/nodes/${centerConceptId}`),
    apiFetch<EdgeWire[]>(`/api/v1/concepts/edges/${centerConceptId}`),
  ])
  const concepts = nodes.map(toNode)
  const ids = new Set(concepts.map((c) => c.conceptId))
  return {
    concepts,
    // source→target = 선수→후수 (knowledge_space 대조 실측) · 로드된 노드 밖 간선은 제거
    edges: edges
      .map((e) => ({ from: sid(e.data.source), to: sid(e.data.target) }))
      .filter((e) => ids.has(e.from) && ids.has(e.to)),
  }
}

export async function searchConcepts(query: string): Promise<ConceptSearchHit[]> {
  const hits = await apiFetch<(ConceptWire & { conceptChapterName?: string })[]>(
    `/api/v1/concepts/search?q=${encodeURIComponent(query)}`,
  )
  return hits.map((h) => ({
    conceptId: sid(h.conceptId),
    conceptName: h.conceptName,
    chapterName: h.conceptChapterName ?? '',
  }))
}

export async function fetchConceptDetail(conceptId: string): Promise<ConceptDetail> {
  // 실서버에 통짜 detail 엔드포인트 없음 — 상세 + 직계 선수 + (edges/nodes 로 직계 후수) 조립
  const [me, prereqs, nodes, edges] = await Promise.all([
    apiFetch<ConceptWire>(`/api/v1/concepts/${conceptId}`),
    apiFetch<ConceptWire[]>(`/api/v1/concepts/prerequisite/${conceptId}`),
    apiFetch<ConceptWire[]>(`/api/v1/concepts/nodes/${conceptId}`),
    apiFetch<EdgeWire[]>(`/api/v1/concepts/edges/${conceptId}`),
  ])
  const byId = new Map(nodes.map((n) => [sid(n.conceptId), n]))
  const successors = edges
    .filter((e) => sid(e.data.source) === conceptId)
    .map((e) => byId.get(sid(e.data.target)))
    .filter((n): n is ConceptWire => !!n)
  return {
    concept: toNode(me),
    prerequisites: prereqs.filter((p) => sid(p.conceptId) !== conceptId).map(toNode),
    successors: successors.map(toNode),
  }
}

// ── 인증 (접점 시트) ──
export function logout() {
  return apiFetch<void>('/api/v1/auth/authentication', { method: 'DELETE', auth: true })
}
