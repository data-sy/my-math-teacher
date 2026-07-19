// MSW 핸들러 — flow-map API 표 전 엔드포인트의 mock 구현.
// 에러 시뮬레이션: localStorage 'mmt.mockError' = '429' | '400' | 'failsoft' | 'neterr' (개발용 스위치)
import { HttpResponse, http } from 'msw'
import type {
  Answer,
  Chapter,
  DiagnosisEntry,
  LearningQueue,
  PreviewResponse,
  ResultCard,
  Urgency,
} from '../api/types'
import { CHAPTERS, CONCEPTS, chapterOf, concept, directPrereqs, linksFor } from './graph-data'
import { buildQueueConcepts, resolveFrontier, traverse, validateAnswers, weakConcepts } from './traversal'

// ── mock 영속 상태 (localStorage — 새로고침 넘어 ④-B 재열람·홈 배너 검증용) ──
interface MockDb {
  seq: number
  tests: Record<string, { entry: DiagnosisEntry; answered: Answer[] }>
  queue: LearningQueue | null
}

// v2: 실서버 wire shape 미러(queueItemId·current·귀속 중첩 등) — 구 키의 stale 상태와 격리 (2026-07-18)
const DB_KEY = 'mmt.mockdb.v2'

function loadDb(): MockDb {
  try {
    const raw = localStorage.getItem(DB_KEY)
    if (raw) return JSON.parse(raw) as MockDb
  } catch {
    /* 깨진 상태 — 초기화 */
  }
  return { seq: 0, tests: {}, queue: null }
}

function saveDb(db: MockDb) {
  localStorage.setItem(DB_KEY, JSON.stringify(db))
}

function mockErrorSwitch(): string | null {
  return localStorage.getItem('mmt.mockError')
}

function isAuthed(request: Request): boolean {
  return (request.headers.get('Authorization') ?? '').startsWith('Bearer mock-token')
}

// 실서버 미러: 에러 바디 = plain text (2026-07-18 실측), 401 은 빈 바디
const err = (status: number, message: string) => new HttpResponse(message, { status })
const unauthorized = () => new HttpResponse(null, { status: 401 })

// ── preview/귀속 공용 결과 산출 (결정론 계약: 같은 입력 → 같은 결과) ──
function buildResult(answered: Answer[]): PreviewResponse {
  const failSoft = mockErrorSwitch() === 'failsoft'
  const weak = weakConcepts(answered)

  const toCard = (w: { conceptId: string; blockedDescendants: number }): ResultCard => {
    const ch = chapterOf(w.conceptId)
    const urgency: Urgency = w.blockedDescendants >= 3 ? 'HIGH' : w.blockedDescendants >= 1 ? 'MID' : 'LOW'
    return {
      conceptId: w.conceptId,
      conceptName: concept(w.conceptId).conceptName,
      level: ch.grade,
      chapter: ch.name,
      urgency: failSoft ? null : urgency,
      urgencyBasis: { blockedDescendants: w.blockedDescendants },
      links: linksFor(w.conceptId),
    }
  }

  const all = weak.map(toCard) // 이미 blocked 내림차순 = 시급도순 (fail-soft 시에도 이 순서 유지 — 04 ●3)
  let cut: number
  if (failSoft) {
    cut = Math.min(all.length, 5) // 등급 결측 — blocked 순 상위로 컷만 유지
  } else {
    const high = all.filter((c) => c.urgency === 'HIGH').length
    cut = Math.min(Math.max(high, Math.min(3, all.length)), 5) // '상' 전부, 바닥 3(부족분 중/하 보충), 캡 5
  }
  const cards = all.slice(0, cut)
  const more = all.slice(cut)

  return {
    headline: {
      totalAsked: answered.length,
      weakCount: weak.length,
      topConceptName: cards[0]?.conceptName ?? null,
    },
    cards,
    more,
    failSoft,
  }
}

function buildQueue(db: MockDb, userTestId: string): LearningQueue {
  const test = db.tests[userTestId]
  const ids = buildQueueConcepts(test.answered)
  return withCurrent({
    queueId: `q${userTestId}`,
    userTestId,
    items: ids.map((conceptId, i) => ({
      queueItemId: `qi-${userTestId}-${i + 1}`,
      conceptId,
      conceptName: concept(conceptId).conceptName,
      position: i + 1,
      done: false,
      current: false,
    })),
  })
}

/** 서버 파생값 미러 — current = position 순 첫 done=false (실서버 shape 2026-07-18 실측) */
function withCurrent(q: LearningQueue): LearningQueue {
  const sorted = [...q.items].sort((a, b) => a.position - b.position)
  const firstUndone = sorted.find((i) => !i.done)
  return { ...q, items: q.items.map((i) => ({ ...i, current: i === firstUndone })) }
}

// 실서버 ConceptResponse wire shape 미러
const wireNode = (id: string) => {
  const c = concept(id)
  return {
    conceptId: c.conceptId,
    conceptName: c.conceptName,
    conceptDescription: c.description,
    conceptChapterId: c.chapterId,
    conceptChapterName: chapterOf(id).name,
  }
}

export const handlers = [
  // ── ② 후보 데이터 — 계단순 전체 반환, 윈도잉은 프론트 로직 (가정 A-13) ──
  http.get('/api/v1/chapters', () => {
    const sorted: Chapter[] = [...CHAPTERS].sort((a, b) => a.order - b.order)
    return HttpResponse.json(sorted)
  }),

  http.post('/api/v1/diagnosis/frontier', async ({ request }) => {
    const { chapterId } = (await request.json()) as { chapterId: string }
    if (!CHAPTERS.some((c) => c.chapterId === chapterId)) return err(400, '없는 단원이에요')
    // 실서버 shape: {concepts:[{conceptId, conceptName}]}
    const ids = resolveFrontier({ scope: 'chapter', chapterId })
    return HttpResponse.json({
      concepts: ids.map((id) => ({ conceptId: id, conceptName: concept(id).conceptName })),
    })
  }),

  // ── ③ stateless next ──
  http.post('/api/v1/diagnosis/next', async ({ request }) => {
    const sw = mockErrorSwitch()
    if (sw === 'neterr') return HttpResponse.error()
    const { entry, answered } = (await request.json()) as { entry: DiagnosisEntry; answered: Answer[] }
    const invalid = sw === '400' ? '뭔가 꼬였어요' : validateAnswers(answered)
    if (invalid) return err(400, invalid)
    const t = traverse(entry, answered)
    if (!t.next) return HttpResponse.json({ done: true })
    const c = concept(t.next)
    return HttpResponse.json({
      next: { conceptId: c.conceptId, conceptName: c.conceptName, description: c.description },
      progress: { asked: answered.length, estimatedRemaining: t.estimatedRemaining },
    })
  }),

  // ── ④-A 무영속 preview (rate limit 10회/분/IP → 429) ──
  http.post('/api/v1/diagnosis/preview', async ({ request }) => {
    if (mockErrorSwitch() === '429') return err(429, '잠깐 쉬었다 다시 해요 — 1분 뒤면 돼요')
    const { answered } = (await request.json()) as { entry: DiagnosisEntry; answered: Answer[] }
    const invalid = validateAnswers(answered)
    if (invalid) return err(400, invalid)
    return HttpResponse.json(buildResult(answered))
  }),

  // ── ④-B 귀속 (인증) — 응답 = preview 와 동일 + userTestId ──
  http.post('/api/v1/diagnosis', async ({ request }) => {
    if (mockErrorSwitch() === 'neterr') return HttpResponse.error()
    if (!isAuthed(request)) return unauthorized()
    const { entry, answered } = (await request.json()) as { entry: DiagnosisEntry; answered: Answer[] }
    const invalid = validateAnswers(answered)
    if (invalid) return err(400, invalid)
    const db = loadDb()
    db.seq += 1
    const userTestId = `t${db.seq}`
    db.tests[userTestId] = { entry, answered }
    saveDb(db)
    // 실서버 shape: result 중첩 (2026-07-18 실측)
    return HttpResponse.json({ userTestId, result: buildResult(answered) })
  }),

  http.post('/api/v1/learning-queues', async ({ request }) => {
    if (!isAuthed(request)) return unauthorized()
    const { userTestId } = (await request.json()) as { userTestId: string }
    const db = loadDb()
    if (!db.tests[userTestId]) return err(400, '저장된 진단이 없어요')
    db.queue = buildQueue(db, userTestId) // 유저당 활성 큐 1개 — 새 큐가 대체
    saveDb(db)
    return HttpResponse.json(db.queue)
  }),

  http.get('/api/v1/diagnosis/:userTestId', ({ request, params }) => {
    if (!isAuthed(request)) return unauthorized()
    const db = loadDb()
    const test = db.tests[params.userTestId as string]
    if (!test) return err(403, '찾을 수 없어요') // 존재 숨김 톤 (05 ●6)
    return HttpResponse.json(buildResult(test.answered)) // 실서버 shape: flat (userTestId 없음)
  }),

  http.get('/api/v1/learning-queues/me', ({ request }) => {
    if (!isAuthed(request)) return unauthorized()
    const db = loadDb()
    return HttpResponse.json(db.queue) // 없으면 null
  }),

  http.patch('/api/v1/learning-queues/:qId/items/:itemId/done', ({ request, params }) => {
    if (mockErrorSwitch() === 'neterr') return HttpResponse.error()
    if (!isAuthed(request)) return unauthorized()
    const db = loadDb()
    if (!db.queue || db.queue.queueId !== params.qId) return err(403, '찾을 수 없어요')
    const item = db.queue.items.find((i) => i.queueItemId === params.itemId)
    if (!item) return err(400, '없는 항목이에요')
    item.done = !item.done // self-mark 토글 (해제도 가능 — 04 ●10)
    db.queue = withCurrent(db.queue)
    saveDb(db)
    return HttpResponse.json(db.queue) // 실서버 shape: 갱신된 큐 전체 (2026-07-18 실측)
  }),

  // ── ⑥ permitAll — 실서버 wire shape 미러 (2026-07-18 실측). search 를 :id 보다 먼저 등록 ──
  http.get('/api/v1/concepts/search', ({ request }) => {
    const q = new URL(request.url).searchParams.get('q')?.trim() ?? ''
    if (!q) return HttpResponse.json([])
    const hits = CONCEPTS.filter((c) => c.conceptName.includes(q)).map((c) => ({
      conceptId: c.conceptId,
      conceptName: c.conceptName,
      conceptChapterName: chapterOf(c.conceptId).name,
    }))
    return HttpResponse.json(hits)
  }),

  // 직계 선수 목록 (실서버는 자기 자신 포함 — 미러)
  http.get('/api/v1/concepts/prerequisite/:id', ({ params }) => {
    const id = params.id as string
    if (!CONCEPTS.some((c) => c.conceptId === id)) return err(400, '없는 개념이에요')
    return HttpResponse.json([id, ...directPrereqs(id)].map(wireNode))
  }),

  // 중심 개념 서브그래프 — mock 그래프는 소규모라 전체 = 이웃
  http.get('/api/v1/concepts/nodes/:id', ({ params }) => {
    if (!CONCEPTS.some((c) => c.conceptId === (params.id as string))) return err(400, '없는 개념이에요')
    return HttpResponse.json(CONCEPTS.map((c) => wireNode(c.conceptId)))
  }),

  // 엣지 — source→target = 선수→후수 (knowledge_space 대조 실측과 동일 방향)
  http.get('/api/v1/concepts/edges/:id', ({ params }) => {
    if (!CONCEPTS.some((c) => c.conceptId === (params.id as string))) return err(400, '없는 개념이에요')
    const edges = CONCEPTS.flatMap((c, ci) =>
      c.prereqs.map((p, pi) => ({ data: { id: `e${ci}-${pi}`, source: p, target: c.conceptId } })),
    )
    return HttpResponse.json(edges)
  }),

  http.get('/api/v1/concepts/:id', ({ params }) => {
    const id = params.id as string
    if (!CONCEPTS.some((c) => c.conceptId === id)) return err(400, '없는 개념이에요')
    return HttpResponse.json(wireNode(id)) // 실서버 shape: flat 상세 (통짜 detail 아님)
  }),

  // ── 인증 역학 (접점 시트) ──
  http.post('/api/v1/auth/reissue', async ({ request }) => {
    const { accessToken } = (await request.json()) as { accessToken: string }
    if (!accessToken?.startsWith('mock-token')) return unauthorized()
    return HttpResponse.json({ accessToken: 'mock-token-reissued' })
  }),

  http.delete('/api/v1/auth/authentication', () => new HttpResponse(null, { status: 204 })),
]
