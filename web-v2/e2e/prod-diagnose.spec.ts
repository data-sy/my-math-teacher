// 프로덕션 진단 — 라이브 사이트에서 무엇이 깨지는지 관측한다 (검증용, 임시).
// 실행: npx playwright test e2e/prod-diagnose.spec.ts --config=playwright.prod.config.ts
//
// 목적: 콘솔 에러·실패 네트워크·렌더 상태를 한 번에 수집해 "작동 안 하는 느낌"의 정체를 특정.
// 단정하지 않고 관측만 한다 — 실패해도 테스트를 죽이지 않고 전부 기록한다.
import { test, expect } from '@playwright/test'

type NetFail = { url: string; status: number | string; method: string }

test('라이브 진단: 홈 → 진입 → 문답 시도, 콘솔·네트워크 전수 수집', async ({ page }) => {
  test.setTimeout(180_000)

  const consoleErrors: string[] = []
  const pageErrors: string[] = []
  const netFails: NetFail[] = []
  const apiCalls: { url: string; status: number }[] = []

  page.on('console', (m) => {
    if (m.type() === 'error' || m.type() === 'warning') {
      consoleErrors.push(`[${m.type()}] ${m.text()}`)
    }
  })
  page.on('pageerror', (e) => pageErrors.push(`${e.name}: ${e.message}\n${e.stack?.slice(0, 400) ?? ''}`))
  page.on('requestfailed', (r) =>
    netFails.push({ url: r.url(), status: r.failure()?.errorText ?? 'failed', method: r.method() }),
  )
  page.on('response', async (r) => {
    const u = r.url()
    if (u.includes('/api/')) {
      apiCalls.push({ url: u.replace(/^https?:\/\/[^/]+/, ''), status: r.status() })
      if (r.status() >= 400) netFails.push({ url: u, status: r.status(), method: r.request().method() })
    }
  })

  // ── ① 홈 ──────────────────────────────────────────────
  await page.goto('/', { waitUntil: 'networkidle' })
  await page.screenshot({ path: 'test-results/prod-01-home.png', fullPage: true })

  const rootHtml = await page.locator('#root').innerHTML()
  console.log('\n===== ① 홈 =====')
  console.log('URL          :', page.url())
  console.log('title        :', await page.title())
  console.log('#root 길이   :', rootHtml.length, rootHtml.length < 50 ? '⚠️ 비어 있음 = 렌더 실패' : '(렌더됨)')
  console.log('본문 텍스트  :', (await page.locator('body').innerText()).slice(0, 300).replace(/\n+/g, ' | '))

  const buttons = await page.locator('button').allInnerTexts()
  console.log('버튼 목록    :', JSON.stringify(buttons))

  // ── ② 진입 시도 ────────────────────────────────────────
  console.log('\n===== ② 진입 시도 =====')
  const startBtn = page.getByRole('button', { name: /진단|시작/ }).first()
  if (await startBtn.count()) {
    await startBtn.click()
    await page.waitForTimeout(3000)
    console.log('클릭 후 URL  :', page.url())
    await page.screenshot({ path: 'test-results/prod-02-entry.png', fullPage: true })
    console.log('본문         :', (await page.locator('body').innerText()).slice(0, 400).replace(/\n+/g, ' | '))
    console.log('버튼         :', JSON.stringify(await page.locator('button').allInnerTexts()))

    // 학년 → 학기 (실제 라벨은 "1학기 (지금)" — 정확일치 금지, 정규식으로)
    for (const re of [/^중1$/, /^1학기/]) {
      const b = page.getByRole('button', { name: re }).first()
      if (await b.count()) {
        const t = await b.innerText()
        await b.click()
        await page.waitForTimeout(2500)
        console.log(`"${t}" 클릭 후 URL:`, page.url())
        console.log(`  버튼:`, JSON.stringify((await page.locator('button').allInnerTexts()).slice(0, 20)))
      } else {
        console.log(`⚠️ ${re} 버튼 없음`)
      }
    }
    await page.screenshot({ path: 'test-results/prod-03-picklist.png', fullPage: true })
    console.log('pick-list 화면 본문:', (await page.locator('body').innerText()).slice(0, 500).replace(/\n+/g, ' | '))

    // 단원 pick — 학년/학기/네비 버튼을 제외한 나머지 중 첫 항목
    const EXCLUDE = /^(중[123]|고[123]|[12]학기|‹ 홈|여기 없음|모르겠어|로그인|중등|고등)/
    const all = page.locator('button')
    const n = await all.count()
    let picked = false
    for (let i = 0; i < n; i++) {
      const t = (await all.nth(i).innerText()).trim()
      if (!t || EXCLUDE.test(t)) continue
      console.log(`단원 후보 클릭: "${t.slice(0, 40)}"`)
      await all.nth(i).click()
      picked = true
      break
    }
    if (!picked) console.log('⚠️ 단원 후보 버튼을 못 찾음')
    await page.waitForTimeout(5000)
    console.log('단원 클릭 후 URL:', page.url())
    await page.screenshot({ path: 'test-results/prod-04-quiz.png', fullPage: true })
    console.log('본문:', (await page.locator('body').innerText()).slice(0, 400).replace(/\n+/g, ' | '))

    // ── 문답 진행 ────────────────────────────────────────
    console.log('\n===== ③ 문답 =====')
    let answered = 0
    for (let i = 0; i < 30; i++) {
      if (page.url().includes('/result')) break
      const dont = page.getByRole('button', { name: '몰라요' })
      const know = page.getByRole('button', { name: '알아요' })
      if (!(await dont.count()) || !(await know.count())) {
        console.log(`  [${i}] 알아요/몰라요 버튼 없음 — URL=${page.url()}`)
        console.log('      본문:', (await page.locator('body').innerText()).slice(0, 250).replace(/\n+/g, ' | '))
        break
      }
      const q = (await page.locator('body').innerText()).split('\n').filter(Boolean).slice(0, 6).join(' | ')
      await (i === 0 ? dont : know).click()
      answered++
      await page.waitForTimeout(1800)
      if (i < 3) console.log(`  [${i}] ${i === 0 ? '몰라요' : '알아요'} — ${q.slice(0, 120)}`)
    }
    console.log(`답변 수: ${answered} · 최종 URL: ${page.url()}`)
    await page.screenshot({ path: 'test-results/prod-05-result.png', fullPage: true })
    console.log('결과 화면 본문:', (await page.locator('body').innerText()).slice(0, 600).replace(/\n+/g, ' | '))
  } else {
    console.log('⚠️ 진단 시작 버튼을 못 찾음')
  }

  // ── ③ 수집 결과 ────────────────────────────────────────
  console.log('\n===== ③ 수집 결과 =====')
  console.log(`API 호출 (${apiCalls.length}건):`)
  apiCalls.forEach((c) => console.log(`  ${c.status}  ${c.url}`))
  console.log(`\n네트워크 실패 (${netFails.length}건):`)
  netFails.forEach((f) => console.log(`  ${f.status}  ${f.method} ${f.url}`))
  console.log(`\npage error (${pageErrors.length}건):`)
  pageErrors.forEach((e) => console.log('  ' + e))
  console.log(`\n콘솔 error/warn (${consoleErrors.length}건):`)
  consoleErrors.slice(0, 25).forEach((e) => console.log('  ' + e))

  expect(true).toBe(true) // 관측 전용 — 실패시키지 않는다
})
