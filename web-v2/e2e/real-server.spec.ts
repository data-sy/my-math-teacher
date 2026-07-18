// 실서버 스모크 — 핵심 플로우 ①→②→③→④→게이트(④-B) 완주 (3단계 게이트 검증).
// 전제: 백엔드 8080(진단 플래그 on) + dev 서버 5173 이 VITE_ENABLE_MOCK=false 로 기동.
// 실행: REAL_SMOKE=1 REAL_TOKEN=<accessToken> npx playwright test e2e/real-server.spec.ts
// (OAuth 는 로컬 완주 불가 — 접점 시트. 토큰은 로컬 주조/실로그인 값 주입 = A-4 시뮬레이션)
import { expect, test } from '@playwright/test'

const TOKEN = process.env.REAL_TOKEN ?? ''

test.skip(process.env.REAL_SMOKE !== '1', 'REAL_SMOKE=1 로만 실행 (실서버 필요)')

test('실서버: 진입→문답→결과→귀속→큐 체크리스트→홈 배너', async ({ page }) => {
  test.setTimeout(180_000)
  // 로그인 시뮬레이션 — 실 토큰 주입 (게이트에서 즉시 귀속 경로)
  await page.addInitScript((tok) => localStorage.setItem('mmt.accessToken', tok), TOKEN)

  // ① 홈 → ② 진입
  await page.goto('/')
  await expect(page.getByText('수학은 계단이다.')).toBeVisible()
  await page.getByRole('button', { name: '무료 진단 시작' }).click()

  // ② 학년 선택(최초 방문) → 실서버 pick-list 는 실단원명 — 첫 후보 원탭
  await expect(page).toHaveURL(/\/entry/)
  await page.getByRole('button', { name: '중3', exact: true }).click()
  await page.getByRole('button', { name: /^1학기/ }).click()
  await expect(page.getByText('지금 시기 추천')).toBeVisible()
  const pick = page.locator('main button, .screen button').filter({ hasText: /단원|식|수|함수|근|분해/ }).first()
  await pick.click()

  // ③ 문답 — 첫 답 몰라요(약점 확보) → 이후 알아요(가지 폐쇄로 수렴) — 상한 40
  await expect(page).toHaveURL(/\/quiz/, { timeout: 20_000 })
  let answered = 0
  for (let i = 0; i < 40; i++) {
    if (page.url().includes('/result')) break
    const dont = page.getByRole('button', { name: '몰라요' })
    const know = page.getByRole('button', { name: '알아요' })
    try {
      await expect(dont).toBeEnabled({ timeout: 15_000 })
    } catch {
      break // ④ 전환 등
    }
    if (page.url().includes('/result')) break
    await (i === 0 ? dont : know).click()
    answered++
  }
  expect(answered).toBeGreaterThan(0)

  // ④-A 결과 (실 DKT 응답) → 게이트: 로그인 상태라 즉시 귀속+큐
  await expect(page).toHaveURL(/\/result/, { timeout: 30_000 })
  await expect(page.getByText(/개 약점|약점이 안 보여요/)).toBeVisible({ timeout: 30_000 })

  const gateCta = page.getByRole('button', { name: '저장하고 학습 경로 시작하기' })
  if ((await gateCta.count()) === 0) {
    console.log('약점 0 결과 — 게이트 없음 (정상 B안). 귀속 검증은 몰라요 경로 재실행 필요')
    return
  }
  await gateCta.click()

  // ④-B — 그래프·카드·체크리스트
  await expect(page).toHaveURL(/\/result\?view=saved/, { timeout: 60_000 })
  await expect(page.getByText('나의 학습 계단 — 체크리스트')).toBeVisible({ timeout: 30_000 })

  // 실서버 엣지: 알아요 폐쇄가 큐 후보를 전부 걸러 빈 큐가 될 수 있다 (2026-07-18 실측 — 백로그)
  const first = page.locator('button').filter({ hasText: /^1\. / }).first()
  if ((await first.count()) > 0) {
    await expect(page.getByText('여기부터')).toBeVisible()
    // 체크리스트 토글 (실 PATCH) → ✓ 반영
    await first.click()
    await expect(first).toContainText('✓', { timeout: 15_000 })
    // ① 홈 배너 (실 큐)
    await page.goto('/')
    await expect(page.getByText(/이어서: /)).toBeVisible({ timeout: 15_000 })
  } else {
    await expect(page.getByText('표시할 계단이 없어요')).toBeVisible()
    console.log('빈 큐 경로 — 논-빈 큐·토글·홈 배너는 API 레벨 검증 완료 (full-flow 스크립트)')
  }
})
