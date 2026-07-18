// flow-map API 표의 전 엔드포인트 — 타입드 함수 한 벌
import { apiFetch } from './client'
import type {
  Answer,
  Chapter,
  ConceptDetail,
  ConceptGraphResponse,
  ConceptNode,
  DiagnosisEntry,
  DiagnosisSavedResponse,
  FrontierResponse,
  LearningQueue,
  NextResponse,
  PreviewResponse,
} from './types'

// ── ② 영역 진입 ──
export function fetchChapters(grade?: string, semester?: number) {
  const q = new URLSearchParams()
  if (grade) q.set('grade', grade)
  if (semester) q.set('semester', String(semester))
  const qs = q.toString()
  return apiFetch<Chapter[]>(`/api/v1/chapters${qs ? `?${qs}` : ''}`)
}

export function fetchFrontier(chapterId: string) {
  return apiFetch<FrontierResponse>('/api/v1/diagnosis/frontier', {
    method: 'POST',
    body: { chapterId },
  })
}

// ── ③ 문답 (stateless — 매 요청 answered 전체) ──
export function fetchNext(entry: DiagnosisEntry, answered: Answer[]) {
  return apiFetch<NextResponse>('/api/v1/diagnosis/next', {
    method: 'POST',
    body: { entry, answered },
  })
}

// ── ④-A 무영속 프리뷰 ──
export function fetchPreview(entry: DiagnosisEntry, answered: Answer[]) {
  return apiFetch<PreviewResponse>('/api/v1/diagnosis/preview', {
    method: 'POST',
    body: { entry, answered },
  })
}

// ── ④-B 귀속 + 큐 (인증) ──
export function saveDiagnosis(entry: DiagnosisEntry, answered: Answer[]) {
  return apiFetch<DiagnosisSavedResponse>('/api/v1/diagnosis', {
    method: 'POST',
    body: { entry, answered },
    auth: true,
  })
}

export function createQueue(userTestId: string) {
  return apiFetch<LearningQueue>('/api/v1/learning-queues', {
    method: 'POST',
    body: { userTestId },
    auth: true,
  })
}

export function fetchSavedDiagnosis(userTestId: string) {
  return apiFetch<DiagnosisSavedResponse>(`/api/v1/diagnosis/${userTestId}`, { auth: true })
}

export function fetchMyQueue() {
  return apiFetch<LearningQueue | null>('/api/v1/learning-queues/me', { auth: true })
}

export function toggleQueueItemDone(queueId: string, itemId: string) {
  return apiFetch<QueueItemToggleResponse>(
    `/api/v1/learning-queues/${queueId}/items/${itemId}/done`,
    { method: 'PATCH', auth: true },
  )
}
export interface QueueItemToggleResponse {
  itemId: string
  done: boolean
}

// ── ⑥ 그래프 탐색 (permitAll) ──
export function fetchConceptGraph(chapterId?: string) {
  const qs = chapterId ? `?chapterId=${encodeURIComponent(chapterId)}` : ''
  return apiFetch<ConceptGraphResponse>(`/api/v1/concepts${qs}`)
}

export function searchConcepts(query: string) {
  return apiFetch<ConceptNode[]>(`/api/v1/concepts/search?q=${encodeURIComponent(query)}`)
}

export function fetchConceptDetail(conceptId: string) {
  return apiFetch<ConceptDetail>(`/api/v1/concepts/${conceptId}`)
}

// ── 인증 (접점 시트) ──
export function logout() {
  return apiFetch<void>('/api/v1/auth/authentication', { method: 'DELETE', auth: true })
}
