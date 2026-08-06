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
  /** 알아요 = true / 몰라요 = false — 실계약 필드명 = known (spec-01 §4.3, 2026-07-18 실서버 확인) */
  known: boolean
}

export interface FrontierResponse {
  /** 시작 프론티어 = 단원 내 후수-최상위 개념들 (실서버 shape: {concepts:[{conceptId, conceptName}]}) */
  concepts: { conceptId: string; conceptName: string }[]
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

/**
 * M8 spec-03 §3.1 — 개념별 외부 자료 링크. 백엔드 concept_links 의 응답 shape 그대로.
 * provider('EBS' 등)는 렌더하지 않지만 R4 점검·provider 단위 일괄 비노출의 그룹핑 키라 계약에 있다.
 * 링크 결측은 계약이다 — 빈 배열이면 섹션을 통째로 생략한다(§2.2, "준비 중" 자리표시 금지).
 */
export interface ExternalLink {
  title: string
  url: string
  provider: string
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

/** 귀속 응답 — 실서버 shape: result 중첩 (2026-07-18 실측, preview 와 result 동일 = 결정론 계약) */
export interface SaveDiagnosisResponse {
  userTestId: string
  result: PreviewResponse
}

export interface QueueItem {
  /** 실계약 필드명 = queueItemId (2026-07-18 실측 — 가정 itemId 정정) */
  queueItemId: string
  conceptId: string
  conceptName: string
  position: number
  done: boolean
  /** 서버 파생 "여기부터" — position 순 첫 done=false (클라 계산 금지, spec-02 §4.4) */
  current: boolean
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

/** ⑥ 검색 결과 — 실서버 응답에 chapterId 없음(단원명만) → 스코프 점프는 단원명 매칭 (2026-07-18 실측) */
export interface ConceptSearchHit {
  conceptId: string
  conceptName: string
  chapterName: string
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
