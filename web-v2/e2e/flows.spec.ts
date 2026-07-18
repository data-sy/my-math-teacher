// 핵심 플로우 스모크 (완주 조건 ④) — ①→②→③→④ · 게이트 전환 · ⑥ 탐색 · 약점 0 · undo
// mock 데이터 전제(2026-07, 중3·1학기): default 단원 = 이차방정식(ch09), 프론티어 = 이차방정식의 활용
import { expect, test, type Page } from '@playwright/test'

async function pickGradeAndDefault(page: Page) {
  // 최초 방문 — pick-list 자리에 학년 선택 먼저 (02 ●3)
  await page.getByRole('button', { name: '중3', exact: true }).click()
  await page.getByRole('button', { name: /^1학기/ }).click()
  await expect(page.getByText('지금 시기 추천')).toBeVisible()
}

test('핵심 플로우 ①→②→③→④-A → 게이트 전환 ④-B → 홈 배너', async ({ page }) => {
  // ① 홈
  await page.goto('/')
  await expect(page.getByText('수학은 계단이다.')).toBeVisible()
  await expect(page.getByRole('link', { name: '로그인' })).toBeVisible() // 비로그인 — ●7
  await page.getByRole('button', { name: '무료 진단 시작' }).click()

  // ② 영역 진입
  await expect(page).toHaveURL(/\/entry/)
  await expect(page.getByText('지금 어디쯤이야?')).toBeVisible()
  await pickGradeAndDefault(page)
  await page.getByRole('button', { name: /이차방정식 / }).click()

  // ③ 문답 — 몰라요×2 → 알아요×2 → done (mock DAG 결정론)
  await expect(page).toHaveURL(/\/quiz/)
  await expect(page.getByText('이차방정식의 활용')).toBeVisible()
  await page.getByRole('button', { name: '몰라요' }).click()
  await expect(page.getByText('이차방정식의 풀이')).toBeVisible()
  await expect(page.getByText('1개 답변')).toBeVisible()
  await page.getByRole('button', { name: '몰라요' }).click()
  await expect(page.getByText('이차방정식의 뜻')).toBeVisible()
  await page.getByRole('button', { name: '알아요' }).click()
  // 토스트 = "알아요"에만 (03 ●5)
  await expect(page.getByText('알겠어요 — 관련 기초는 건너뛸게요')).toBeVisible()
  await page.getByRole('button', { name: '알아요' }).click()

  // ④-A 무료 결과
  await expect(page).toHaveURL(/\/result/)
  await expect(page.getByText('확인한 4개 중 2개 약점')).toBeVisible()
  await expect(page.getByText("가장 급한 건 '이차방정식의 풀이'")).toBeVisible()
  await expect(page.getByText('자가응답 기반 AI 학습 가이드예요')).toBeVisible() // 정직성 한 줄
  await expect(page.getByText('시급도 상')).toBeVisible()
  await expect(page.getByText('이걸 모르면 위로 3개 개념이 막혀요')).toBeVisible()
  await expect(page.getByText('EBS 무료 강의 ↗').first()).toBeVisible()

  // 게이트 전환 — 1차 CTA → ⑤-A → mock OAuth → ④-B
  await page.getByRole('button', { name: '저장하고 학습 경로 시작하기' }).click()
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByText('방금 받은 진단 결과는 그대로 있어요.')).toBeVisible()
  await page.getByRole('button', { name: '구글로 계속하기' }).click()

  // ④-B 게이트 상태 — 그래프 → 카드 + 플로팅 체크리스트 진입 (2026-07-18 재배치)
  await expect(page).toHaveURL(/\/result\?view=saved/)
  await expect(page.getByText('나의 학습 경로 (저장됨)')).toBeVisible()
  await expect(page.getByText('진단 카드 (재열람)')).toBeVisible()
  await page.getByRole('button', { name: /학습 계단 0\/2/ }).click() // 플로팅 pill → 시트
  await expect(page.getByText('나의 학습 계단 — 체크리스트')).toBeVisible()
  await expect(page.getByText('1. 이차방정식의 풀이')).toBeVisible() // 위상순 — 선수 먼저
  await expect(page.getByText('여기부터')).toBeVisible()

  // 토글 실패 = 무음 금지 (2026-07-18 POC 발견): 실패 주입 → 인라인 피드백, 체크 안 됨
  await page.evaluate(() => localStorage.setItem('mmt.mockError', 'neterr'))
  await page.getByRole('button', { name: /1\. 이차방정식의 풀이/ }).click()
  await expect(page.getByText('체크가 저장되지 않았어요 — 다시 탭해주세요.')).toBeVisible()
  await expect(page.getByRole('button', { name: /1\. 이차방정식의 풀이/ })).not.toContainText('✓')
  await page.evaluate(() => localStorage.removeItem('mmt.mockError'))

  // self-mark 토글 → "여기부터" 이동 (재시도 성공 시 에러 스트립 소거)
  await page.getByRole('button', { name: /1\. 이차방정식의 풀이/ }).click()
  await expect(page.getByRole('button', { name: /1\. 이차방정식의 풀이/ })).toContainText('✓')
  await expect(page.getByText('체크가 저장되지 않았어요 — 다시 탭해주세요.')).toHaveCount(0)

  // ① 홈 재진입 배너 (로그인 + 활성 큐)
  await page.goto('/')
  await expect(page.getByText(/이어서: 이차방정식의 활용/)).toBeVisible()
  await expect(page.getByRole('link', { name: '로그인' })).toHaveCount(0) // 배너와 상호 배타
})

test('④-A 게이트 CTA 실패 = 무음 금지 — 인라인 에러 + 재시도 성공 (2026-07-18 발견)', async ({ page }) => {
  // 로그인 상태(토큰 선주입 — 구 세션 잔존 토큰 시나리오와 동일)로 완주 → ④-A
  await page.goto('/entry')
  await page.evaluate(() => localStorage.setItem('mmt.accessToken', 'mock-token-google'))
  await pickGradeAndDefault(page)
  await page.getByRole('button', { name: /이차방정식 / }).click()
  await page.getByRole('button', { name: '몰라요' }).click()
  await expect(page.getByText('이차방정식의 풀이')).toBeVisible()
  await page.getByRole('button', { name: '몰라요' }).click()
  await expect(page.getByText('이차방정식의 뜻')).toBeVisible()
  await page.getByRole('button', { name: '알아요' }).click()
  await page.getByRole('button', { name: '알아요' }).click()
  await expect(page).toHaveURL(/\/result/)

  // 귀속 실패 주입 → 인라인 에러, 화면 이탈 없음 (무음 금지)
  await page.evaluate(() => localStorage.setItem('mmt.mockError', 'neterr'))
  await page.getByRole('button', { name: '저장하고 학습 경로 시작하기' }).click()
  await expect(page.getByText('저장이 잘 안 됐어요 — 다시 시도해주세요. 진단 결과는 그대로 있어요.')).toBeVisible()
  await expect(page).toHaveURL(/\/result$/)

  // 재시도 성공 → ④-B 전환 + 에러 스트립 소거
  await page.evaluate(() => localStorage.removeItem('mmt.mockError'))
  await page.getByRole('button', { name: '저장하고 학습 경로 시작하기' }).click()
  await expect(page).toHaveURL(/\/result\?view=saved/)
  await expect(page.getByText('나의 학습 경로 (저장됨)')).toBeVisible()
})

test('④-B 스테일 큐 403 — 한 번 실패 후 자가치유 재탭 성공 (2026-07-18 실기기 발견)', async ({ page }) => {
  // 로그인 + 완주 + 귀속 → ④-B
  await page.goto('/entry')
  await page.evaluate(() => localStorage.setItem('mmt.accessToken', 'mock-token-google'))
  await pickGradeAndDefault(page)
  await page.getByRole('button', { name: /이차방정식 / }).click()
  await page.getByRole('button', { name: '몰라요' }).click()
  await expect(page.getByText('이차방정식의 풀이')).toBeVisible()
  await page.getByRole('button', { name: '몰라요' }).click()
  await expect(page.getByText('이차방정식의 뜻')).toBeVisible()
  await page.getByRole('button', { name: '알아요' }).click()
  await page.getByRole('button', { name: '알아요' }).click()
  await expect(page).toHaveURL(/\/result/)
  await page.getByRole('button', { name: '저장하고 학습 경로 시작하기' }).click()
  await expect(page).toHaveURL(/\/result\?view=saved/)

  // 그 사이 다른 경로의 재귀속으로 큐가 교체된 상황 — 저장소 큐 id 바꿔치기
  await page.evaluate(() => {
    const db = JSON.parse(localStorage.getItem('mmt.mockdb.v2')!) as {
      queue: { queueId: string; items: { queueItemId: string }[] }
    }
    db.queue.queueId = 'qREPLACED'
    db.queue.items = db.queue.items.map((i, k) => ({ ...i, queueItemId: `qiR-${k}` }))
    localStorage.setItem('mmt.mockdb.v2', JSON.stringify(db))
  })

  // 스테일 화면에서 토글 → 403 스트립(원인 코드 포함) + 큐 자동 재조회
  await page.getByRole('button', { name: /학습 계단 0\/2/ }).click()
  const item = page.getByRole('button', { name: /1\. 이차방정식의 풀이/ })
  await item.click()
  await expect(page.getByText('체크가 저장되지 않았어요 — 다시 탭해주세요.')).toBeVisible()
  await expect(page.getByText('(오류 403)')).toBeVisible()

  // 자가치유 후 재탭 = 성공, 스트립 소거
  await item.click()
  await expect(item).toContainText('✓')
  await expect(page.getByText('체크가 저장되지 않았어요 — 다시 탭해주세요.')).toHaveCount(0)
})

test('③ undo — 직전 답 1개 되돌리기, 진척 후퇴는 undo 만 예외', async ({ page }) => {
  await page.goto('/entry')
  await pickGradeAndDefault(page)
  await page.getByRole('button', { name: /이차방정식 / }).click()
  await expect(page.getByText('이차방정식의 활용')).toBeVisible()
  await expect(page.getByText('0개 답변')).toBeVisible()
  // 답변 0개 — undo 숨김
  await expect(page.getByRole('button', { name: '‹ 이전 답 수정' })).toHaveCount(0)
  await page.getByRole('button', { name: '몰라요' }).click()
  await expect(page.getByText('1개 답변')).toBeVisible()
  await page.getByRole('button', { name: '‹ 이전 답 수정' }).click()
  await expect(page.getByText('0개 답변')).toBeVisible()
  await expect(page.getByText('이차방정식의 활용')).toBeVisible() // 그 개념 질문으로 복귀
})

test('④ 약점 0 (B안) — 전부 알아요 → 한 계단 위 CTA', async ({ page }) => {
  await page.goto('/entry')
  await pickGradeAndDefault(page)
  await page.getByRole('button', { name: /^인수분해/ }).click()
  await expect(page).toHaveURL(/\/quiz/)
  await page.getByRole('button', { name: '알아요' }).click()

  await expect(page).toHaveURL(/\/result/)
  await expect(page.getByText('약점이 안 보여요! 👍')).toBeVisible()
  await expect(page.getByText('여기는 탄탄해요 — 한 계단 위로 올라가볼까요?')).toBeVisible()
  // 게이트 CTA·저장 없음 — 1차 = 한 계단 위 진단 (ch08 다음 = ch09 이차방정식)
  await expect(page.getByRole('button', { name: '저장하고 학습 경로 시작하기' })).toHaveCount(0)
  await page.getByRole('button', { name: "한 계단 위 '이차방정식' 진단하기" }).click()
  await expect(page).toHaveURL(/\/quiz/)
  await expect(page.getByText('이차방정식의 활용')).toBeVisible()
})

test('② 이어가기 — 미완주 세션 복구 노트', async ({ page }) => {
  await page.goto('/entry')
  await pickGradeAndDefault(page)
  await page.getByRole('button', { name: /이차방정식 / }).click()
  await page.getByRole('button', { name: '몰라요' }).click()
  await expect(page.getByText('1개 답변')).toBeVisible()

  // ‹ 진입 으로 이탈 → 복구 노트
  await page.getByRole('link', { name: '‹ 진입' }).click()
  await expect(page.getByText(/진행 중인 진단이 있어요 — '이차방정식' 1개 답변까지/)).toBeVisible()
  await page.getByRole('button', { name: '이어서 진행' }).click()
  await expect(page.getByText('1개 답변')).toBeVisible() // 같은 지점 재개
  await expect(page.getByText('이차방정식의 풀이')).toBeVisible()
})

test('② 폐기 확인 — 진행 중 세션이 있을 때만 조건부 시트 (백로그 m7-entry-discard-confirm-dialog)', async ({ page }) => {
  // 진행 중 세션이 없으면 원탭 즉시 시작 — 모달 없음
  await page.goto('/entry')
  await pickGradeAndDefault(page)
  await page.getByRole('button', { name: /이차방정식 / }).click()
  await expect(page).toHaveURL(/\/quiz/)
  await expect(page.getByText('진행 중인 진단을 지울까요?')).toHaveCount(0)

  // 세션 생성 (1개 답변) 후 ②로 이탈
  await page.getByRole('button', { name: '몰라요' }).click()
  await page.getByRole('link', { name: '‹ 진입' }).click()
  await expect(page.getByText(/진행 중인 진단이 있어요/)).toBeVisible()

  // 다른 단원 원탭 → 확인 시트 (즉시 시작 대신 한 홉) · 취소 → 세션 유지
  await page.getByRole('button', { name: /^인수분해/ }).click()
  await expect(page.getByText('진행 중인 진단을 지울까요?')).toBeVisible()
  await expect(page.getByText(/'이차방정식' 1개 답변이 사라져요/)).toBeVisible()
  await expect(page).toHaveURL(/\/entry/)
  await page.getByRole('button', { name: '취소', exact: true }).click()
  await expect(page.getByText('진행 중인 진단을 지울까요?')).toHaveCount(0)
  await expect(page.getByText(/진행 중인 진단이 있어요/)).toBeVisible()

  // 다른 단원 원탭 → 확정 → 폐기 후 그 단원으로 새 세션 시작 (확인 후 진행)
  await page.getByRole('button', { name: /^인수분해/ }).click()
  await page.getByRole('button', { name: '지우고 새로 시작' }).click()
  await expect(page).toHaveURL(/\/quiz/)
  await expect(page.getByText('0개 답변')).toBeVisible()

  // "새로 시작" → 동일 확인 → 확정 시 노트 소거 (화면 이동 없음)
  await page.getByRole('button', { name: '몰라요' }).click()
  await page.getByRole('link', { name: '‹ 진입' }).click()
  await expect(page.getByText(/진행 중인 진단이 있어요 — '인수분해' 1개 답변까지/)).toBeVisible()
  await page.getByRole('button', { name: '새로 시작', exact: true }).click()
  await expect(page.getByText('진행 중인 진단을 지울까요?')).toBeVisible()
  await page.getByRole('button', { name: '지우고 새로 시작' }).click()
  await expect(page.getByText(/진행 중인 진단이 있어요/)).toHaveCount(0)
  await expect(page).toHaveURL(/\/entry/)

  // 세션이 사라졌으니 다시 원탭 즉시 시작 — 모달 없음 (조건부 보장)
  await page.getByRole('button', { name: /^인수분해/ }).click()
  await expect(page).toHaveURL(/\/quiz/)
  await expect(page.getByText('0개 답변')).toBeVisible()
})

test('⑥ 그래프 탐색 — 검색·시트 체인·스코프 전환·진단 루프 CTA', async ({ page }) => {
  await page.goto('/graph')
  // 기억값 없음 — 대표 단원 스코프 (중3·현재 학기 추정)
  await expect(page.getByRole('button', { name: /▾/ })).toBeVisible()
  await expect(page.locator('canvas').first()).toBeVisible() // Cytoscape 캔버스

  // 검색 → 결과 탭 → 스코프 자동 전환 + 시트 오픈
  await page.getByPlaceholder('🔍 개념 검색 (예: 인수분해)').fill('인수분해')
  await page.getByRole('button', { name: /인수분해 · 인수분해/ }).click()
  await expect(page.getByText('예: x²−5x+6 → (x−2)(x−3)')).toBeVisible()
  // 3열 스택 체인 — 먼저/지금/다음
  await expect(page.getByText('먼저')).toBeVisible()
  await expect(page.getByText('다음')).toBeVisible()
  await expect(page.getByRole('button', { name: '다항식의 곱셈' })).toBeVisible()

  // 체인 pill 탭 = 그 노드로 선택 이동 + 시트 내용 교체
  await page.getByRole('button', { name: '다항식의 곱셈' }).click()
  await expect(page.getByText('예: (x+1)(x+2) = x²+3x+2')).toBeVisible()

  // 상시 CTA — 탐색→진단 루프
  await page.getByRole('button', { name: '내 약점 진단하기' }).click()
  await expect(page).toHaveURL(/\/entry/)
})

test('⑤-B 라우팅 404 — 풀스크린 에러 + 홈으로', async ({ page }) => {
  await page.goto('/no-such-page')
  await expect(page.getByText('여기로 가는 길이')).toBeVisible()
  await page.getByRole('button', { name: '홈으로' }).click()
  await expect(page.getByText('수학은 계단이다.')).toBeVisible()
})
