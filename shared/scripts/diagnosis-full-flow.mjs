// 실서버 핵심 플로우 완주 스크립트: frontier→next 루프→preview→귀속→큐→done 토글
const BASE = 'http://localhost:8080'
const TOK = process.argv[2]
const chapterId = Number(process.argv[3] ?? 517)
const H = { 'Content-Type': 'application/json' }
const HA = { ...H, Authorization: `Bearer ${TOK}` }

const j = async (r) => {
  const text = await r.text()
  try { return { status: r.status, body: JSON.parse(text) } } catch { return { status: r.status, body: text } }
}

const entry = { scope: 'chapter', chapterId }
const answered = []
// ③ 루프 — 전부 몰라요 (drill-down 최대) 단 30문 안전 상한
for (let i = 0; i < 30; i++) {
  const r = await j(await fetch(`${BASE}/api/v1/diagnosis/next`, { method: 'POST', headers: H, body: JSON.stringify({ entry, answered }) }))
  if (r.status !== 200) { console.log('NEXT-FAIL', r.status, JSON.stringify(r.body).slice(0, 200)); process.exit(1) }
  if (r.body.done) break
  answered.push({ conceptId: r.body.next.conceptId, known: false })
}
console.log('answered:', answered.length)

const pv = await j(await fetch(`${BASE}/api/v1/diagnosis/preview`, { method: 'POST', headers: H, body: JSON.stringify({ entry, answered }) }))
console.log('preview:', pv.status, 'weak =', pv.body.headline?.weakCount, 'cards =', pv.body.cards?.length, 'more =', pv.body.more?.length)

const at = await j(await fetch(`${BASE}/api/v1/diagnosis`, { method: 'POST', headers: HA, body: JSON.stringify({ entry, answered }) }))
console.log('귀속:', at.status, 'userTestId =', at.body.userTestId, 'top-level keys =', Object.keys(at.body).join(','))
// 결정론: preview == 귀속 result
console.log('결정론 headline 동치:', JSON.stringify(pv.body.headline) === JSON.stringify(at.body.result?.headline))

const q = await j(await fetch(`${BASE}/api/v1/learning-queues`, { method: 'POST', headers: HA, body: JSON.stringify({ userTestId: at.body.userTestId }) }))
console.log('큐:', q.status, 'items =', q.body.items?.length, 'first =', JSON.stringify(q.body.items?.[0]))

if (q.body.items?.length) {
  const it = q.body.items[0]
  const d = await j(await fetch(`${BASE}/api/v1/learning-queues/${q.body.queueId}/items/${it.itemId ?? it.queueItemId ?? it.id}/done`, { method: 'PATCH', headers: HA }))
  console.log('done 토글:', d.status, JSON.stringify(d.body).slice(0, 150))
}
