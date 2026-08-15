// mock 지식그래프 — 중등 수학 교육과정을 단순화한 가상 DAG (가정 A-8)
// frontier/next/preview/queue 가 전부 이 데이터에서 결정론적으로 파생된다 (결정론 계약 보존).
import type { Chapter, ConceptNode, ExternalLink } from '../api/types'

export const CHAPTERS: Chapter[] = [
  { chapterId: 'ch01', name: '정수와 유리수', grade: '중1', semester: 1, order: 1 },
  { chapterId: 'ch02', name: '문자와 식', grade: '중1', semester: 1, order: 2 },
  { chapterId: 'ch03', name: '일차방정식', grade: '중1', semester: 2, order: 3 },
  { chapterId: 'ch04', name: '유리수와 순환소수', grade: '중2', semester: 1, order: 4 },
  { chapterId: 'ch05', name: '식의 계산', grade: '중2', semester: 1, order: 5 },
  { chapterId: 'ch06', name: '일차함수', grade: '중2', semester: 2, order: 6 },
  { chapterId: 'ch07', name: '제곱근과 실수', grade: '중3', semester: 1, order: 7 },
  { chapterId: 'ch08', name: '인수분해', grade: '중3', semester: 1, order: 8 },
  { chapterId: 'ch09', name: '이차방정식', grade: '중3', semester: 1, order: 9 },
  { chapterId: 'ch10', name: '이차함수', grade: '중3', semester: 2, order: 10 },
]

interface MockConcept extends ConceptNode {
  /** 직계 선수 conceptId 들 */
  prereqs: string[]
}

export const CONCEPTS: MockConcept[] = [
  { conceptId: 'c00', conceptName: '정수와 유리수', description: '예: −3, 1/2 같은 수의 사칙계산', chapterId: 'ch01', prereqs: [] },
  { conceptId: 'c01', conceptName: '문자와 식', description: '예: 2a+3b처럼 문자로 식 나타내기', chapterId: 'ch02', prereqs: ['c00'] },
  { conceptId: 'c02', conceptName: '일차식의 계산', description: '예: 3x+2x = 5x', chapterId: 'ch02', prereqs: ['c01'] },
  { conceptId: 'c03', conceptName: '일차방정식', description: '예: 2x+1=5 → x=2', chapterId: 'ch03', prereqs: ['c02'] },
  { conceptId: 'c04', conceptName: '유리수와 순환소수', description: '예: 1/3 = 0.333…', chapterId: 'ch04', prereqs: ['c00'] },
  { conceptId: 'c05', conceptName: '지수법칙', description: '예: x²×x³ = x⁵', chapterId: 'ch05', prereqs: ['c01'] },
  { conceptId: 'c06', conceptName: '다항식의 곱셈', description: '예: (x+1)(x+2) = x²+3x+2', chapterId: 'ch05', prereqs: ['c02', 'c05'] },
  { conceptId: 'c07', conceptName: '일차함수', description: '예: y = 2x+1의 그래프는 직선', chapterId: 'ch06', prereqs: ['c03'] },
  { conceptId: 'c08', conceptName: '일차함수의 그래프', description: '예: 기울기 2, y절편 1', chapterId: 'ch06', prereqs: ['c07'] },
  { conceptId: 'c09', conceptName: '제곱근과 실수', description: '예: √9 = 3', chapterId: 'ch07', prereqs: ['c04'] },
  { conceptId: 'c10', conceptName: '근호를 포함한 식의 계산', description: '예: √2×√8 = 4', chapterId: 'ch07', prereqs: ['c09', 'c05'] },
  { conceptId: 'c11', conceptName: '인수분해', description: '예: x²−5x+6 → (x−2)(x−3)', chapterId: 'ch08', prereqs: ['c06'] },
  { conceptId: 'c12', conceptName: '인수분해 공식의 활용', description: '예: 99² = (100−1)²을 공식으로', chapterId: 'ch08', prereqs: ['c11'] },
  { conceptId: 'c13', conceptName: '이차방정식의 뜻', description: '예: x²−5x+6=0은 이차방정식', chapterId: 'ch09', prereqs: ['c03', 'c11'] },
  { conceptId: 'c14', conceptName: '이차방정식의 풀이', description: '예: x²−5x+6=0 → x=2 또는 3', chapterId: 'ch09', prereqs: ['c13', 'c10'] },
  { conceptId: 'c15', conceptName: '이차방정식의 활용', description: '예: 넓이 조건으로 변의 길이 구하기', chapterId: 'ch09', prereqs: ['c14'] },
  { conceptId: 'c16', conceptName: '이차함수의 뜻', description: '예: y = x²−4x+3', chapterId: 'ch10', prereqs: ['c14', 'c08'] },
  { conceptId: 'c17', conceptName: '이차함수의 그래프', description: '예: 꼭짓점 (2, −1)인 포물선', chapterId: 'ch10', prereqs: ['c16'] },
]

const byId = new Map(CONCEPTS.map((c) => [c.conceptId, c]))

export function concept(id: string): MockConcept {
  const c = byId.get(id)
  if (!c) throw new Error(`unknown concept: ${id}`)
  return c
}

export function directPrereqs(id: string): string[] {
  return concept(id).prereqs
}

export function directSuccessors(id: string): string[] {
  return CONCEPTS.filter((c) => c.prereqs.includes(id)).map((c) => c.conceptId)
}

/** 선수 폐쇄 (자신 제외) — "알아요" skip 범위 */
export function prereqClosure(id: string): Set<string> {
  const out = new Set<string>()
  const stack = [...directPrereqs(id)]
  while (stack.length) {
    const cur = stack.pop()!
    if (out.has(cur)) continue
    out.add(cur)
    stack.push(...directPrereqs(cur))
  }
  return out
}

/** 후수 폐쇄 (자신 제외) — blockedDescendants 근거 */
export function successorClosure(id: string): Set<string> {
  const out = new Set<string>()
  const stack = [...directSuccessors(id)]
  while (stack.length) {
    const cur = stack.pop()!
    if (out.has(cur)) continue
    out.add(cur)
    stack.push(...directSuccessors(cur))
  }
  return out
}

/** 단원 시작 프론티어 = 단원 내 후수-최상위(같은 단원의 다른 개념의 선수가 아닌 것) */
export function chapterFrontier(chapterId: string): string[] {
  const inChapter = CONCEPTS.filter((c) => c.chapterId === chapterId)
  const ids = new Set(inChapter.map((c) => c.conceptId))
  return inChapter
    .filter((c) => !inChapter.some((other) => other.prereqs.includes(c.conceptId) && ids.has(other.conceptId)))
    .map((c) => c.conceptId)
}

/** ② escape (b) 전체 훑기 — 학교급 대표 단원(각 학년 마지막 단원) 프론티어 합집합 */
export function fullScanFrontier(): string[] {
  const representatives = ['ch03', 'ch06', 'ch09'] // 중1·중2·중3 각 마지막 단원
  const out: string[] = []
  for (const ch of representatives) {
    for (const id of chapterFrontier(ch)) if (!out.includes(id)) out.push(id)
  }
  return out
}

/** 개념별 무료 외부자료 링크 — 카피는 와이어프레임 문구 그대로 */
export function linksFor(id: string): ExternalLink[] {
  const links: ExternalLink[] = [
    { title: 'EBS 무료 강의', url: `https://mid.ebs.co.kr/search?q=${encodeURIComponent(concept(id).conceptName)}`, provider: 'EBS' },
  ]
  // 짝수 인덱스 개념에만 두 번째 자료 — 카드별 링크 수 편차를 mock 에서도 재현
  if (Number(id.slice(1)) % 2 === 0) {
    links.push({ title: '개념 정리 자료', url: `https://www.mathfactory.net/search?q=${encodeURIComponent(concept(id).conceptName)}`, provider: 'MathFactory' })
  }
  return links
}

export function chapterOf(conceptId: string): Chapter {
  const ch = CHAPTERS.find((c) => c.chapterId === concept(conceptId).chapterId)
  if (!ch) throw new Error(`concept without chapter: ${conceptId}`)
  return ch
}
