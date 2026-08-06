// 프로덕션 로그인·게이트 경로 진단 (검증용, 임시).
// A4 원증상이 "로그인 후 저장 → 401 → 토큰 삭제 → 재로그인 강요" 였으므로 이 경로를 집중 관측한다.
// OAuth 제공자 화면까지는 갈 수 있으나 실제 로그인은 사람만 가능 → 리다이렉트 URL 정합까지 검증.
import { test, expect } from '@playwright/test'

test('라이브: 로그인 진입 + OAuth 리다이렉트 정합', async ({ page }) => {
  test.setTimeout(120_000)

  const apiCalls: { url: string; status: number }[] = []
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(`${e.name}: ${e.message}`))
  page.on('console', (m) => m.type() === 'error' && errors.push(`[console] ${m.text()}`))
  page.on('response', (r) => {
    if (r.url().includes('/api/')) apiCalls.push({ url: r.url().replace(/^https?:\/\/[^/]+/, ''), status: r.status() })
  })

  // ── /login 직접 진입 ──
  await page.goto('/login', { waitUntil: 'networkidle' })
  console.log('\n===== /login =====')
  console.log('URL   :', page.url())
  console.log('본문  :', (await page.locator('body').innerText()).replace(/\n+/g, ' | ').slice(0, 400))
  const btns = await page.locator('button, a').allInnerTexts()
  console.log('클릭요소:', JSON.stringify(btns.filter(Boolean).slice(0, 15)))
  await page.screenshot({ path: 'test-results/prod-login-01.png', fullPage: true })

  // ── OAuth 링크의 href 검증 (실제 이동 대신 URL 정합만) ──
  console.log('\n===== OAuth 진입점 =====')
  const links = page.locator('a[href*="oauth"], a[href*="login"], button')
  const n = await links.count()
  for (let i = 0; i < n; i++) {
    const el = links.nth(i)
    const txt = (await el.innerText()).trim().replace(/\n+/g, ' ')
    const href = await el.getAttribute('href')
    if (txt) console.log(`  "${txt.slice(0, 30)}" → href=${href ?? '(button)'}`)
  }

  // ── OAuth 버튼 1종 클릭해 실제 리다이렉트 관측 ──
  const oauthBtn = page.getByRole('button', { name: /구글|Google|네이버|Naver|카카오|Kakao/ }).first()
  const oauthLink = page.locator('a').filter({ hasText: /구글|Google|네이버|Naver|카카오|Kakao/ }).first()
  const target = (await oauthBtn.count()) ? oauthBtn : (await oauthLink.count()) ? oauthLink : null

  if (target) {
    const label = (await target.innerText()).trim()
    console.log(`\n"${label}" 클릭 → 리다이렉트 추적`)
    const chain: string[] = []
    page.on('framenavigated', (f) => f === page.mainFrame() && chain.push(f.url()))
    await target.click().catch(() => {})
    await page.waitForTimeout(6000)
    console.log('  최종 URL:', page.url().slice(0, 160))
    console.log('  이동 체인:')
    chain.forEach((u) => console.log('    ' + u.slice(0, 150)))
    const u = page.url()
    if (/accounts\.google|nid\.naver|kauth\.kakao/.test(u)) {
      console.log('  ✅ OAuth 제공자 화면 도달 — redirect_uri 배선 정상')
      const m = u.match(/redirect_uri=([^&]+)/)
      if (m) console.log('  redirect_uri =', decodeURIComponent(m[1]))
    } else {
      console.log('  ⚠️ 제공자 화면에 도달 못함')
    }
    await page.screenshot({ path: 'test-results/prod-login-02-oauth.png', fullPage: true })
  } else {
    console.log('⚠️ OAuth 버튼/링크를 못 찾음')
  }

  // ── 결과 게이트: 무토큰으로 저장 시도하면 로그인으로 가는가 ──
  console.log('\n===== 게이트 동작 (무토큰 저장 시도) =====')
  await page.goto('/result', { waitUntil: 'networkidle' })
  console.log('  /result 직접 진입 URL:', page.url())
  console.log('  본문:', (await page.locator('body').innerText()).replace(/\n+/g, ' | ').slice(0, 300))

  console.log('\n===== API 호출 =====')
  apiCalls.forEach((c) => console.log(`  ${c.status}  ${c.url}`))
  console.log(`\n에러 (${errors.length}건):`)
  errors.slice(0, 15).forEach((e) => console.log('  ' + e))

  expect(true).toBe(true)
})
