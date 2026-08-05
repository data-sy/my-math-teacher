// 인증 상태 행 회귀 테스트 — 로그인/로그아웃 표시가 세 조합에서 모두 보이는가.
// 배경: "로그인 + 큐 없음" 조합에서 이 자리가 통째로 비어 사용자가 자기 로그인 여부를
// 알 수 없었고, 로그아웃 진입점이 전 화면에 없어 AuthContext.logout() 이 죽은 코드였다
// (2026-08-05 사용자 보고 → 상시 노출로 변경).
//
// ⚠️ 토큰은 반드시 'mock-token' 으로 시작해야 한다 — mocks/handlers.ts:45 의 isAuthed 규약.
//    아무 문자열이나 넣으면 401 → client.ts 가 토큰을 비워(정상 동작) 버튼이 사라진다.
//    MSW 는 브라우저 안에서 가로채므로 page.route 스텁으로는 우회되지 않는다.
import { test, expect } from '@playwright/test'

const MOCK_TOKEN = 'mock-token-e2e-authrow'

test('비로그인 → "로그인" 링크가 보인다', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('link', { name: '로그인' })).toBeVisible()
  await expect(page.getByRole('button', { name: '로그아웃' })).toHaveCount(0)
})

test('로그인 → "로그아웃" 버튼이 보인다 (큐 유무와 무관)', async ({ page }) => {
  await page.addInitScript((t) => localStorage.setItem('mmt.accessToken', t), MOCK_TOKEN)
  await page.goto('/')
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()
  await expect(page.getByRole('link', { name: '로그인' })).toHaveCount(0)
})

test('로그아웃 클릭 → 토큰이 비워지고 "로그인" 링크로 돌아온다', async ({ page }) => {
  await page.addInitScript((t) => localStorage.setItem('mmt.accessToken', t), MOCK_TOKEN)
  await page.goto('/')
  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page.getByRole('link', { name: '로그인' })).toBeVisible({ timeout: 10_000 })
  expect(await page.evaluate(() => localStorage.getItem('mmt.accessToken'))).toBeNull()
})
