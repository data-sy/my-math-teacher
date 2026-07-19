// 학년×시기 → 시기 추정 단원(soft default) — 프론트 로직 (flow-map API 표 ② 명시 · 가정 A-5)
// R6 확정: 확률적 guess 여도 됨 — soft 제안일 뿐, pick-list ± 후보와 escape 가 오추정을 흡수.
import type { Chapter } from '../api/types'

export function currentSemester(month: number): 1 | 2 {
  // 1학기 = 3~8월, 2학기 = 9~2월
  return month >= 3 && month <= 8 ? 1 : 2
}

/** 학년·학기 내 단원들 중 월 진행률로 시기 추정 단원 선택 */
export function pickDefaultChapter(
  chapters: Chapter[],
  grade: string,
  semester: 1 | 2,
  month: number,
): Chapter | null {
  const inTerm = chapters
    .filter((c) => c.grade === grade && c.semester === semester)
    .sort((a, b) => a.order - b.order)
  if (inTerm.length === 0) {
    // 해당 학년 데이터 없음(mock 은 중등만) — 계단 최상단으로 클램프
    const sorted = [...chapters].sort((a, b) => a.order - b.order)
    return sorted[sorted.length - 1] ?? null
  }
  // 학기 내 진행률: 1학기 3~8월 / 2학기 9~2월 을 0~1 로
  const start = semester === 1 ? 3 : 9
  const offset = (month - start + 12) % 12 // 0~5
  const frac = Math.min(offset / 5, 1)
  const idx = Math.min(Math.floor(frac * inTerm.length), inTerm.length - 1)
  return inTerm[idx]
}

/** 계단순 ±후보 윈도 (default 앞뒤 1개 — 총 3~4개, 02 ●5) */
export function ladderWindow(chapters: Chapter[], defaultId: string): Chapter[] {
  const sorted = [...chapters].sort((a, b) => a.order - b.order)
  const i = sorted.findIndex((c) => c.chapterId === defaultId)
  if (i < 0) return sorted.slice(0, 3)
  const from = Math.max(0, i - 1)
  return sorted.slice(from, Math.min(sorted.length, from + 3))
}

/** 약점 0 CTA "한 계단 위" 단원 (04 ●12 · 가정 A-6) — 없으면 null(B′ 폴백) */
export function nextChapterUp(chapters: Chapter[], currentId: string): Chapter | null {
  const sorted = [...chapters].sort((a, b) => a.order - b.order)
  const i = sorted.findIndex((c) => c.chapterId === currentId)
  return i >= 0 && i + 1 < sorted.length ? sorted[i + 1] : null
}

/** pick-item 부제: "중3 · 1학기 — 한 계단 아래" (와이어프레임 문구) */
export function ladderLabel(c: Chapter, defaultOrder: number): string {
  const base = `${c.grade} · ${c.semester}학기`
  if (c.order < defaultOrder) return `${base} — 한 계단 아래`
  if (c.order > defaultOrder) return `${base} — 한 계단 위`
  return base
}
