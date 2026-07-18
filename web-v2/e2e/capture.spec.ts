// 390px 시각 검증용 캡처 — 산출물은 스크래치 디렉토리 (리포 밖)
import { test } from '@playwright/test'

const OUT = process.env.SHOT_DIR ?? 'test-results/shots'

test('캡처: 전 화면', async ({ page }) => {
  await page.goto('/')
  await page.getByText('수학은 계단이다.').waitFor()
  await page.screenshot({ path: `${OUT}/01-home.png`, fullPage: true })

  await page.goto('/entry')
  await page.screenshot({ path: `${OUT}/02a-entry-first-visit.png`, fullPage: true })
  await page.getByRole('button', { name: '중3', exact: true }).click()
  await page.screenshot({ path: `${OUT}/02a2-entry-semester.png`, fullPage: true })
  await page.getByRole('button', { name: /^1학기/ }).click()
  await page.screenshot({ path: `${OUT}/02b-entry-picklist.png`, fullPage: true })

  await page.getByRole('button', { name: /이차방정식 / }).click()
  await page.waitForURL(/\/quiz/)
  await page.getByText('이차방정식의 활용').waitFor()
  await page.screenshot({ path: `${OUT}/03-quiz.png`, fullPage: true })

  await page.getByRole('button', { name: '몰라요' }).click()
  await page.getByRole('button', { name: '몰라요' }).click()
  await page.getByRole('button', { name: '알아요' }).click()
  await page.getByRole('button', { name: '알아요' }).click()
  await page.waitForURL(/\/result/)
  await page.getByText('확인한 4개 중 2개 약점').waitFor()
  await page.screenshot({ path: `${OUT}/04a-result-free.png`, fullPage: true })

  await page.getByRole('button', { name: '저장하고 학습 경로 시작하기' }).click()
  await page.waitForURL(/\/login/)
  await page.getByText('결과를 저장하고').waitFor()
  await page.screenshot({ path: `${OUT}/05a-login-gate.png`, fullPage: true })

  await page.getByRole('button', { name: '구글로 계속하기' }).click()
  await page.waitForURL(/\/result\?view=saved/)
  await page.getByText('나의 학습 계단 — 체크리스트').waitFor()
  await page.waitForTimeout(600) // 그래프 레이아웃 안정
  await page.screenshot({ path: `${OUT}/04b-result-gated.png`, fullPage: true })

  await page.goto('/')
  await page.getByText(/이어서:/).waitFor()
  await page.screenshot({ path: `${OUT}/01b-home-banner.png`, fullPage: true })

  await page.goto('/graph')
  await page.locator('canvas').first().waitFor()
  await page.waitForTimeout(600)
  await page.screenshot({ path: `${OUT}/06a-graph.png`, fullPage: true })
  await page.getByPlaceholder('🔍 개념 검색 (예: 인수분해)').fill('인수분해')
  await page.getByRole('button', { name: /인수분해 · 인수분해/ }).click()
  await page.waitForTimeout(600)
  await page.screenshot({ path: `${OUT}/06b-graph-sheet.png`, fullPage: true })

  await page.goto('/no-such-page')
  await page.screenshot({ path: `${OUT}/05b-notfound.png`, fullPage: true })
})

test('캡처: ④ 약점 0', async ({ page }) => {
  await page.goto('/entry')
  await page.getByRole('button', { name: '중3', exact: true }).click()
  await page.getByRole('button', { name: /^1학기/ }).click()
  await page.getByRole('button', { name: /^인수분해/ }).click()
  await page.waitForURL(/\/quiz/)
  await page.getByRole('button', { name: '알아요' }).click()
  await page.waitForURL(/\/result/)
  await page.getByText('약점이 안 보여요').waitFor()
  await page.screenshot({ path: `${OUT}/04c-result-empty.png`, fullPage: true })
})
