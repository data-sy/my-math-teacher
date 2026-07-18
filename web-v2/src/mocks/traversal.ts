// 적응형 순회 mock — 서버 몫 로직(spec-01 D1-A)의 결정론적 흉내.
// "알아요" = 선수 폐쇄 전체 skip · "몰라요" = 직계 선수 push(drill-down) · stateless 재계산.
import type { Answer, DiagnosisEntry } from '../api/types'
import {
  CONCEPTS,
  chapterFrontier,
  directPrereqs,
  fullScanFrontier,
  prereqClosure,
  successorClosure,
} from './graph-data'

export function resolveFrontier(entry: DiagnosisEntry): string[] {
  return entry.scope === 'chapter' ? chapterFrontier(entry.chapterId) : fullScanFrontier()
}

export interface TraversalResult {
  next: string | null
  /** next 포함, 현재 답변 기준 앞으로 물을 것으로 보이는 개념 수 (추정) */
  estimatedRemaining: number
}

const knownIds = new Set(CONCEPTS.map((c) => c.conceptId))

/** 400 계약: 중복·미존재 conceptId (스테일 맵) */
export function validateAnswers(answered: Answer[]): string | null {
  const seen = new Set<string>()
  for (const a of answered) {
    if (!knownIds.has(a.conceptId)) return `모르는 개념이 섞여 있어요 (${a.conceptId})`
    if (seen.has(a.conceptId)) return '같은 개념에 답이 두 번 있어요'
    seen.add(a.conceptId)
  }
  return null
}

export function traverse(entry: DiagnosisEntry, answered: Answer[]): TraversalResult {
  const answerOf = new Map(answered.map((a) => [a.conceptId, a.know]))
  const skip = new Set<string>()
  for (const a of answered) {
    if (a.know) for (const p of prereqClosure(a.conceptId)) skip.add(p)
  }

  const queue = [...resolveFrontier(entry)]
  const seen = new Set<string>()
  const askable: string[] = []
  while (queue.length) {
    const c = queue.shift()!
    if (seen.has(c)) continue
    seen.add(c)
    const a = answerOf.get(c)
    if (a === true) continue // 알아요 — 이 가지 종료 (선수 폐쇄는 skip 셋이 처리)
    if (a === false) {
      queue.unshift(...directPrereqs(c)) // 몰라요 — 직계 선수 drill-down
      continue
    }
    if (skip.has(c)) continue
    askable.push(c) // 미답·미스킵 — 물을 후보 (순서 = 순회 순서)
  }

  return { next: askable[0] ?? null, estimatedRemaining: askable.length }
}

export interface WeakConcept {
  conceptId: string
  blockedDescendants: number
}

/** 몰라요 목록 → blocked 수 내림차순 (preview·귀속 공용 — 결정론 계약) */
export function weakConcepts(answered: Answer[]): WeakConcept[] {
  return answered
    .filter((a) => !a.know)
    .map((a) => ({ conceptId: a.conceptId, blockedDescendants: successorClosure(a.conceptId).size }))
    .sort((x, y) => y.blockedDescendants - x.blockedDescendants)
}

/** 통합 학습 큐 = 약점 ∪ 약점의 선수 폐쇄 − 아는 것(및 그 선수 폐쇄), 위상순(선수 먼저) */
export function buildQueueConcepts(answered: Answer[]): string[] {
  const weak = answered.filter((a) => !a.know).map((a) => a.conceptId)
  const known = new Set<string>()
  for (const a of answered) {
    if (a.know) {
      known.add(a.conceptId)
      for (const p of prereqClosure(a.conceptId)) known.add(p)
    }
  }
  const need = new Set<string>()
  for (const w of weak) {
    if (!known.has(w)) need.add(w)
    for (const p of prereqClosure(w)) if (!known.has(p)) need.add(p)
  }
  // 위상 정렬(선수 먼저) — 동순위는 교육과정 계단순(CONCEPTS 배열 순서)
  const ordered = CONCEPTS.filter((c) => need.has(c.conceptId)).map((c) => c.conceptId)
  const placed: string[] = []
  const placedSet = new Set<string>()
  let guard = ordered.length * ordered.length + 1
  const pending = [...ordered]
  while (pending.length && guard-- > 0) {
    const c = pending.shift()!
    const ready = directPrereqs(c).every((p) => !need.has(p) || placedSet.has(p))
    if (ready) {
      placed.push(c)
      placedSet.add(c)
    } else {
      pending.push(c)
    }
  }
  return placed
}
