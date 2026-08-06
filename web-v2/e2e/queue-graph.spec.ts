// ④-B ●8 학습 큐 DAG 회귀 — 그래프가 "큐 그 자체"를 그리는지 (2026-08-05 결정 D).
// 구현 이전에는 중심 개념의 선수 폐쇄를 그려 큐에 없는 개념이 대부분이었고 라벨은 전부 숨겨져 있었다.
import { expect, test } from '@playwright/test'

/** 전부 "몰라요" → drill-down 이 선수지식으로 파고들어 13계단짜리 큐가 생긴다 */
async function saveBigQueue(page: import('@playwright/test').Page) {
  await page.goto('/entry')
  await page.getByRole('button', { name: '중3', exact: true }).click()
  await page.getByRole('button', { name: /^1학기/ }).click()
  await page.getByRole('button', { name: /이차방정식 / }).click()
  await expect(page).toHaveURL(/\/quiz/)
  for (let i = 0; i < 30; i++) {
    if (page.url().includes('/result')) break
    const btn = page.getByRole('button', { name: '몰라요' })
    if (!(await btn.isEnabled().catch(() => false))) {
      await page.waitForTimeout(100)
      continue
    }
    await btn.click().catch(() => {})
  }
  await page.getByRole('button', { name: '저장하고 학습 경로 시작하기' }).click()
  await page.getByRole('button', { name: '구글로 계속하기' }).click()
  await expect(page).toHaveURL(/\/result\?view=saved/)
  await page.getByRole('button', { name: /학습 계단 \d+\/\d+/ }).waitFor()
  await page.waitForTimeout(600) // 레이아웃·줌 클램프 안정
}

test('④-B 그래프 = 큐 DAG — 카드별 edges 합집합만 요청하고 노드 탭이 계단 번호를 답한다', async ({
  page,
}) => {
  const edgeReqs: string[] = []
  const nodeReqs: string[] = []
  page.on('request', (r) => {
    if (r.url().includes('/concepts/edges/')) edgeReqs.push(r.url())
    if (r.url().includes('/concepts/nodes/')) nodeReqs.push(r.url())
  })

  await saveBigQueue(page)

  // 라벨 렌더에 nodes/{id} 는 불필요 — 큐 항목이 conceptName 을 이미 갖고 있다
  expect(nodeReqs).toHaveLength(0)
  expect(edgeReqs.length).toBeGreaterThan(0)

  // 노드 탭 → 몇 번째 계단인지 (구 구현은 개념 설명을 띄웠고 큐 위치는 알 수 없었다)
  const info = page.locator('[class*="nodeInfo"]')
  await expect(info).toHaveText('노드를 탭하면 몇 번째 계단인지 보여줘요')
  const canvas = page.locator('canvas').last()
  const box = (await canvas.boundingBox())!
  await page.mouse.click(box.x + box.width / 2, box.y + 24) // 최상단 = 계단 1
  await expect(info).toHaveText(/^1\. .+ — 여기부터$/)
})

test('④-B 완료 체크 — 그래프 노드 집합은 그대로, "여기부터"만 다음 계단으로 이동', async ({
  page,
}) => {
  await saveBigQueue(page)
  const info = page.locator('[class*="nodeInfo"]')
  const canvas = page.locator('canvas').last()
  const box = (await canvas.boundingBox())!

  await page.mouse.click(box.x + box.width / 2, box.y + 24)
  const firstStep = (await info.textContent())!.trim()
  expect(firstStep).toMatch(/^1\. .+ — 여기부터$/)

  await page.getByRole('button', { name: '학습 계단 0/13' }).click()
  await page.getByRole('button', { name: /^1\./ }).click()
  await expect(page.getByRole('button', { name: '학습 계단 1/13' })).toBeVisible()
  await page.mouse.click(10, 10) // 시트 닫기

  // 선택은 유지된 채 상태만 갱신된다 — 노드가 사라지거나 큐에서 빠지면 이 단언이 깨진다
  // (좌표 재클릭은 선택 시 재중심 때문에 플래키해서 쓰지 않는다)
  await expect(info).toHaveText(firstStep.replace('여기부터', '완료함'))
})
