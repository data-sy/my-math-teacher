// MSW 핸들러 — flow-map API 표 전 엔드포인트의 mock 구현.
// 에러 시뮬레이션: localStorage 'mmt.mockError' = '429' | '400' | 'failsoft' | 'neterr' (개발용 스위치)
import { HttpResponse, http } from 'msw'
import type {
  Answer,
  Chapter,
  ConceptNode,
  DiagnosisEntry,
  LearningQueue,
  PreviewResponse,
  ResultCard,
  Urgency,
} from '../api/types'
import {
  CHAPTERS,
  CONCEPTS,
  chapterOf,
  concept,
  directPrereqs,
  directSuccessors,
  linksFor,
} from './graph-data'
import { buildQueueConcepts, resolveFrontier, traverse, validateAnswers, weakConcepts } from './traversal'

// ── mock 영속 상태 (localStorage — 새로고침 넘어 ④-B 재열람·홈 배너 검증용) ──
interface MockDb {
  seq: number
  tests: Record<string, { entry: DiagnosisEntry; answered: Answer[] }>
  queue: LearningQueue | null
}

const DB_KEY = 'mmt.mockdb'

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

const err = (status: number, message: string) => HttpResponse.json({ message }, { status })
const unauthorized = () => err(401, '다시 로그인해주세요')

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
  return {
    queueId: `q${userTestId}`,
    userTestId,
    items: ids.map((conceptId, i) => ({
      itemId: `qi-${userTestId}-${i + 1}`,
      conceptId,
      conceptName: concept(conceptId).conceptName,
      position: i + 1,
      done: false,
    })),
  }
}

const node = (id: string): ConceptNode => {
  const c = concept(id)
  return { conceptId: c.conceptId, conceptName: c.conceptName, description: c.description, chapterId: c.chapterId }
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
    return HttpResponse.json({ frontier: resolveFrontier({ scope: 'chapter', chapterId }) })
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
    return HttpResponse.json({
      next: node(t.next),
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
    if (!isAuthed(request)) return unauthorized()
    const { entry, answered } = (await request.json()) as { entry: DiagnosisEntry; answered: Answer[] }
    const invalid = validateAnswers(answered)
    if (invalid) return err(400, invalid)
    const db = loadDb()
    db.seq += 1
    const userTestId = `t${db.seq}`
    db.tests[userTestId] = { entry, answered }
    saveDb(db)
    return HttpResponse.json({ ...buildResult(answered), userTestId })
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
    return HttpResponse.json({ ...buildResult(test.answered), userTestId: params.userTestId })
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
    const item = db.queue.items.find((i) => i.itemId === params.itemId)
    if (!item) return err(400, '없는 항목이에요')
    item.done = !item.done // self-mark 토글 (해제도 가능 — 04 ●10)
    saveDb(db)
    return HttpResponse.json({ itemId: item.itemId, done: item.done })
  }),

  // ── ⑥ permitAll — search 를 :id 보다 먼저 등록 ──
  http.get('/api/v1/concepts/search', ({ request }) => {
    const q = new URL(request.url).searchParams.get('q')?.trim() ?? ''
    if (!q) return HttpResponse.json([])
    const hits = CONCEPTS.filter((c) => c.conceptName.includes(q)).map((c) => node(c.conceptId))
    return HttpResponse.json(hits)
  }),

  http.get('/api/v1/concepts/:id', ({ params }) => {
    const id = params.id as string
    if (!CONCEPTS.some((c) => c.conceptId === id)) return err(400, '없는 개념이에요')
    return HttpResponse.json({
      concept: node(id),
      prerequisites: directPrereqs(id).map(node),
      successors: directSuccessors(id).map(node),
    })
  }),

  http.get('/api/v1/concepts', ({ request }) => {
    const chapterId = new URL(request.url).searchParams.get('chapterId')
    let ids: Set<string>
    if (chapterId) {
      // 단원 개념 + 직계 경계 이웃(1-hop) — focus+context 의 "지형" 제공 (가정 A-9)
      ids = new Set(CONCEPTS.filter((c) => c.chapterId === chapterId).map((c) => c.conceptId))
      for (const id of [...ids]) {
        directPrereqs(id).forEach((p) => ids.add(p))
        directSuccessors(id).forEach((s) => ids.add(s))
      }
    } else {
      ids = new Set(CONCEPTS.map((c) => c.conceptId)) // 모두 보기
    }
    const edges = CONCEPTS.flatMap((c) =>
      c.prereqs.filter((p) => ids.has(p) && ids.has(c.conceptId)).map((p) => ({ from: p, to: c.conceptId })),
    )
    return HttpResponse.json({ concepts: [...ids].map(node), edges })
  }),

  // ── 인증 역학 (접점 시트) ──
  http.post('/api/v1/auth/reissue', async ({ request }) => {
    const { accessToken } = (await request.json()) as { accessToken: string }
    if (!accessToken?.startsWith('mock-token')) return unauthorized()
    return HttpResponse.json({ accessToken: 'mock-token-reissued' })
  }),

  http.delete('/api/v1/auth/authentication', () => new HttpResponse(null, { status: 204 })),
]
