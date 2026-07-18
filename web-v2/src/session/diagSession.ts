// 진단 세션 (answered-map) — F-2 익명 진척 복구의 정본 저장소.
// 매 답변 직후 저장 · done=false 만 ② 이어가기 대상(S1-A) · done=true 는 ④ 새로고침 복원용.
import type { Answer, DiagnosisEntry } from '../api/types'

export interface DiagSession {
  entry: DiagnosisEntry
  /** scope=chapter 일 때 단원 표기용 (② 이어가기 노트 "'{단원}' N개 답변까지") */
  chapterId: string | null
  chapterName: string | null
  answered: Answer[]
  done: boolean
  savedAt: number
}

const KEY = 'mmt.diagSession'
const PENDING_KEY = 'mmt.pendingAttribution'
const GRADE_KEY = 'mmt.gradeSemester'

export function loadSession(): DiagSession | null {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as DiagSession) : null
  } catch {
    return null
  }
}

export function saveSession(s: DiagSession): void {
  localStorage.setItem(KEY, JSON.stringify(s))
}

export function clearSession(): void {
  localStorage.removeItem(KEY)
}

export function startSession(entry: DiagnosisEntry, chapterId: string | null, chapterName: string | null): DiagSession {
  const s: DiagSession = { entry, chapterId, chapterName, answered: [], done: false, savedAt: Date.now() }
  saveSession(s)
  return s
}

/** ② 이어가기 대상 = 미완주(done=false)·답변 1개 이상 세션만 */
export function resumableSession(): DiagSession | null {
  const s = loadSession()
  return s && !s.done && s.answered.length > 0 ? s : null
}

/** ④ 도착 시 읽는 완주 세션 */
export function completedSession(): DiagSession | null {
  const s = loadSession()
  return s && s.done ? s : null
}

// ── 게이트 CTA → OAuth 왕복 사이 "복귀 후 귀속하라" 1비트 플래그 ──
export function setPendingAttribution(): void {
  localStorage.setItem(PENDING_KEY, '1')
}
export function consumePendingAttribution(): boolean {
  const v = localStorage.getItem(PENDING_KEY) === '1'
  localStorage.removeItem(PENDING_KEY)
  return v
}

// ── 학년·학기 기억 (02 ●3 확정 — 최초 방문 선택 → localStorage, 서버 저장 없음) ──
export interface GradeSemester {
  grade: string
  semester: 1 | 2
}
export function loadGradeSemester(): GradeSemester | null {
  try {
    const raw = localStorage.getItem(GRADE_KEY)
    return raw ? (JSON.parse(raw) as GradeSemester) : null
  } catch {
    return null
  }
}
export function saveGradeSemester(g: GradeSemester): void {
  localStorage.setItem(GRADE_KEY, JSON.stringify(g))
}
