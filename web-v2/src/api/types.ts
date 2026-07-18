// 계약 타입 — 정본 = docs/design/v2/00-flow-map.html "화면 ↔ 백엔드 계약 매핑" 표.
// 표가 침묵하는 세부 shape 는 docs/assumptions.md (A-2, A-9~A-11) 가정.

export type Grade = '중1' | '중2' | '중3' | '고1' | '고2' | '고3'
export type Semester = 1 | 2

export interface Chapter {
  chapterId: string
  name: string
  grade: Grade
  semester: Semester
  /** 계단순(선후) 전역 정렬 키 — 오름차순 = 아래 계단 → 위 계단 */
  order: number
}

/** ② escape (b) "전체 훑기" = scope full */
export type DiagnosisEntry =
  | { scope: 'chapter'; chapterId: string }
  | { scope: 'full'; schoolLevel: '중등' | '고등' }

export interface Answer {
  conceptId: string
  /** 알아요 = true / 몰라요 = false */
  know: boolean
}

export interface FrontierResponse {
  /** 시작 프론티어 = 단원 내 후수-최상위 개념 id들 */
  frontier: string[]
}

export interface NextQuestion {
  conceptId: string
  conceptName: string
  /** D5 대표 예시 — ③ 카드·⑥ 시트 공용 소스 */
  description: string
}

export interface DiagnosisProgress {
  asked: number
  estimatedRemaining: number
}

export type NextResponse =
  | { done: true }
  | { done?: false; next: NextQuestion; progress: DiagnosisProgress }

export type Urgency = 'HIGH' | 'MID' | 'LOW'

export interface ExternalLink {
  label: string
  url: string
}

export interface ResultCard {
  conceptId: string
  conceptName: string
  level: string
  chapter: string
  /** fail-soft 시 결측(null) — 배지 "—" 표시 (04 ●3) */
  urgency: Urgency | null
  urgencyBasis: { blockedDescendants: number }
  links: ExternalLink[]
}

export interface PreviewResponse {
  headline: {
    totalAsked: number
    weakCount: number
    topConceptName: string | null
  }
  /** top-N = HIGH 전부(바닥 3·캡 5) */
  cards: ResultCard[]
  /** "더 보기" 접힘분 (중/하) */
  more: ResultCard[]
  /** fail-soft 여부 — urgency 결측 시 true (A-2 가정) */
  failSoft?: boolean
}

/** 귀속 응답 = preview 와 동일 shape + userTestId (결정론 계약) */
export interface DiagnosisSavedResponse extends PreviewResponse {
  userTestId: string
}

export interface QueueItem {
  itemId: string
  conceptId: string
  conceptName: string
  position: number
  done: boolean
}

export interface LearningQueue {
  queueId: string
  userTestId: string
  items: QueueItem[]
}

export interface ConceptNode {
  conceptId: string
  conceptName: string
  description: string
  chapterId: string
}

export interface ConceptEdge {
  /** 선수 → 후수 (06 ●4 엣지 방향) */
  from: string
  to: string
}

export interface ConceptGraphResponse {
  concepts: ConceptNode[]
  edges: ConceptEdge[]
}

export interface ConceptDetail {
  concept: ConceptNode
  /** 직계 선수 (⑥ 시트 "먼저") */
  prerequisites: ConceptNode[]
  /** 직계 후수 (⑥ 시트 "다음") */
  successors: ConceptNode[]
}

/** 에러 바디 — "학생 친화 메시지 동반" (A-2 가정) */
export interface ApiErrorBody {
  message?: string
}

export interface ReissueResponse {
  accessToken: string
}
