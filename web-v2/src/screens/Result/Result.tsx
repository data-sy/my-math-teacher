import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import {
  createQueue,
  fetchChapters,
  fetchFrontier,
  fetchMyQueue,
  fetchPreview,
  fetchQueueEdges,
  fetchSavedDiagnosis,
  saveDiagnosis,
  toggleQueueItemDone,
} from '../../api/endpoints'
import { ApiError, AuthRequiredError } from '../../api/client'
import type { ResultCard } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import BottomSheet from '../../components/BottomSheet'
import ConceptGraph from '../../components/ConceptGraph'
import { nextChapterUp } from '../../lib/curriculum'
import {
  completedSession,
  setPendingAttribution,
  startSession,
} from '../../session/diagSession'
import s from './Result.module.css'

export default function Result() {
  const [params] = useSearchParams()
  return params.get('view') === 'saved' ? <SavedResult /> : <FreeResult />
}

// ══════════ ④-A 무료 상태 (+ 약점 0) ══════════

function FreeResult() {
  const nav = useNavigate()
  const { isLoggedIn } = useAuth()
  const session = useMemo(completedSession, [])
  const [moreOpen, setMoreOpen] = useState(false)
  const [gateBusy, setGateBusy] = useState(false)
  const [gateErr, setGateErr] = useState(false)

  const preview = useQuery({
    queryKey: ['preview', session?.savedAt],
    queryFn: () => fetchPreview(session!.entry, session!.answered),
    enabled: !!session,
    retry: false, // 429 를 즉시 인라인으로 (카탈로그)
  })

  // 게이트 CTA — 비로그인 = 로그인 트리거 / 로그인 = 그 자리에서 귀속+큐 생성 → B 상태 전환 (04 ●6)
  async function onGateCta() {
    if (!session || gateBusy) return
    if (!isLoggedIn) {
      setPendingAttribution()
      nav('/login')
      return
    }
    setGateBusy(true)
    setGateErr(false)
    try {
      const saved = await saveDiagnosis(session.entry, session.answered)
      await createQueue(saved.userTestId)
      nav('/result?view=saved', { replace: true })
    } catch (e) {
      if (e instanceof AuthRequiredError) {
        // 토큰 만료 — client 가 토큰을 비웠으니 비로그인 게이트 경로로 수렴
        setPendingAttribution()
        nav('/login')
        return
      }
      setGateErr(true)
      setGateBusy(false)
    }
  }

  if (!session) {
    // 완주 세션 없음 — 결과를 만들 입력이 없다 (완주·미저장 이탈 = 소실 허용, 04 ●1)
    return (
      <div className="screen">
        <Link className="nav-back" to="/">‹ 홈</Link>
        <div className="err-card">
          보여줄 결과가 없어요 — 진단부터 시작해요.
          <br />
          <button className="retry" onClick={() => nav('/entry')}>진단 시작</button>
        </div>
      </div>
    )
  }

  if (preview.isError) {
    const e = preview.error
    const is429 = e instanceof ApiError && e.status === 429
    return (
      <div className="screen">
        <Link className="nav-back" to="/">‹ 홈</Link>
        {/* 429 = ④ 도착 자리 인라인, 답변 맵은 무사 (카탈로그) */}
        <div className="err-card">
          {is429 && e instanceof ApiError ? e.message : '연결이 잠깐 끊겼어요. 답변은 그대로 있어요.'}
          <br />
          <button className="retry" onClick={() => preview.refetch()}>다시 시도</button>
        </div>
      </div>
    )
  }

  if (!preview.data) {
    return (
      <div className="screen">
        <Link className="nav-back" to="/">‹ 홈</Link>
      </div>
    )
  }

  const { headline, cards, more, failSoft } = preview.data

  if (headline.weakCount === 0) {
    return <EmptyResult totalAsked={headline.totalAsked} />
  }

  return (
    <div className="screen">
      {/* ●1 ‹ 홈 */}
      <Link className="nav-back" to="/">‹ 홈</Link>

      {/* ●2 헤드라인 + 정직성 한 줄 (①에서 이동해온 확정 위치) */}
      <h1 className={s.headline}>
        확인한 {headline.totalAsked}개 중 {headline.weakCount}개 약점 —
        <br />
        가장 급한 건 '{headline.topConceptName}'
      </h1>
      <p className={s.honest}>자가응답 기반 AI 학습 가이드예요 — 시험 점수가 아니에요</p>

      {/* ●3 fail-soft 스트립 (조건부·인라인 — 지속 상태라 화면에 남음) */}
      {failSoft && (
        <div className={s.failsoft}>
          시급도 계산이 잠깐 안 돼요 — 약점 목록과 자료는 그대로예요
        </div>
      )}

      {/* ●4 개념 카드 top-N */}
      {cards.map((c) => (
        <CardView key={c.conceptId} card={c} onPathTap={onGateCta} />
      ))}

      {/* ●5 더 보기 토글 */}
      {more.length > 0 && (
        <>
          {moreOpen && more.map((c) => <CardView key={c.conceptId} card={c} onPathTap={onGateCta} />)}
          <button className={s.moreFold} onClick={() => setMoreOpen((v) => !v)}>
            {moreOpen ? '접기 ∧' : `더 보기 (중/하 ${more.length}개) ∨`}
          </button>
        </>
      )}

      {/* ●6 1차 CTA + 게이트 안내문 (무료 먼저) */}
      <div className={s.ctaWrap}>
        {gateErr && (
          <div className={s.qerr} role="alert">
            저장이 잘 안 됐어요 — 다시 시도해주세요. 진단 결과는 그대로 있어요.
          </div>
        )}
        <button className="btn-primary" onClick={onGateCta} disabled={gateBusy}>
          저장하고 학습 경로 시작하기
        </button>
        <p className={s.gateNote}>진단 결과와 자료는 무료예요 — 저장과 학습 경로는 로그인 후 제공</p>
      </div>
    </div>
  )
}

function CardView({ card, onPathTap }: { card: ResultCard; onPathTap?: () => void }) {
  const badge =
    card.urgency === null
      ? '시급도 —' // fail-soft 결측 (04 ●3)
      : card.urgency === 'HIGH'
        ? '시급도 상'
        : card.urgency === 'MID'
          ? '시급도 중'
          : '시급도 하'
  return (
    <div className={s.card}>
      <div className={s.crow}>
        <span className={s.cname}>{card.conceptName}</span>
        <span
          className={`${s.badge} ${card.urgency === 'HIGH' ? s.badgeHi : card.urgency === 'MID' ? s.badgeMid : ''}`}
        >
          {badge}
        </span>
      </div>
      {/* 근거 한 줄 — 0개면 숨긴다. 전체 개념의 35%(577/1,631)가 0개라 "급하다고 올려놓고
          근거는 아무것도 안 막음"이 라이브에서 실제로 나왔다 (2026-08-05 실측·사용자 결정 B).
          숫자 대신 후수 개념명을 지목하는 안은 백엔드 트랙([M8])으로 분리 */}
      {card.urgencyBasis.blockedDescendants > 0 && (
        <div className={s.basis}>
          이걸 모르면 위로 {card.urgencyBasis.blockedDescendants}개 개념이 막혀요
        </div>
      )}
      <div className={s.links}>
        {card.links.map((l) => (
          <a key={l.url} href={l.url} target="_blank" rel="noreferrer">
            {l.label} ↗
          </a>
        ))}
      </div>
      {/* "학습 경로 보기 ›" 2차 — ●6 과 동일 게이트로 수렴. ④-B 재열람에선 없음 */}
      {onPathTap && (
        <button className={s.pathLink} onClick={onPathTap}>
          학습 경로 보기 ›
        </button>
      )}
    </div>
  )
}

// ══════════ ④ 약점 0 상태 (B안 확정) ══════════

function EmptyResult({ totalAsked }: { totalAsked: number }) {
  const nav = useNavigate()
  const session = useMemo(completedSession, [])
  const { data: chapters } = useQuery({ queryKey: ['chapters'], queryFn: () => fetchChapters() })

  const upChapter =
    session?.entry.scope === 'chapter' && chapters
      ? nextChapterUp(chapters, session.entry.chapterId)
      : null

  // "한 계단 위 진단하기" — ② 계단순 데이터 재사용, 탭 → 그 단원으로 ③ 새 세션 (04 ●12)
  async function startUp() {
    if (!upChapter) return
    try {
      await fetchFrontier(upChapter.chapterId)
      startSession({ scope: 'chapter', chapterId: upChapter.chapterId }, upChapter.chapterId, upChapter.name)
      nav('/quiz')
    } catch {
      /* 실패 시 화면 유지 — 재탭 */
    }
  }

  const firstConceptId = session?.answered[0]?.conceptId

  return (
    <div className="screen">
      <Link className="nav-back" to="/">‹ 홈</Link>
      <h1 className={s.headline}>
        확인한 {totalAsked}개 개념,
        <br />
        약점이 안 보여요! 👍
      </h1>
      {/* 다음 단원(단원 순서상 다음)이 있을 때만 "올라가볼까요?" 질문 노출 —
          마지막 단원(고3 맨 끝 등 다음 없음)이면 질문을 숨긴다 (2026-07-26 사용자 결정) */}
      <p className={s.emptySub}>여기는 탄탄해요{upChapter ? ' — 다음 단원으로 가볼까요?' : ''}</p>

      <div className={s.ctaWrap}>
        {/* 게이트 CTA·저장 없음 — 빈 큐라 저장할 것이 없음 (04 ●12).
            다음 단원 있으면 그 단원으로, 없으면(마지막) 다른 단원 선택으로 */}
        {upChapter ? (
          <button className="btn-primary" onClick={startUp}>
            다음 단원 '{upChapter.name}' 진단하기
          </button>
        ) : (
          <button className="btn-primary" onClick={() => nav('/entry')}>
            다른 단원 진단하기
          </button>
        )}
        <Link
          className={`txt-link ${s.graphLink}`}
          to={firstConceptId ? `/graph?conceptId=${firstConceptId}` : '/graph'}
        >
          진단한 개념, 그래프에서 확인하기 ›
        </Link>
      </div>
    </div>
  )
}

// ══════════ ④-B 게이트 상태 (재열람·전환 도착) ══════════

/** ●8 그래프 높이 — 라벨 상시 노출로 바뀌며 220 → 280 (계단 3~4층이 한 화면에 들어오는 최소치) */
const GRAPH_H = 280

function SavedResult() {
  const nav = useNavigate()
  const qc = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [moreOpen, setMoreOpen] = useState(false)
  const [queueOpen, setQueueOpen] = useState(false)

  const queueQ = useQuery({ queryKey: ['queue', 'me'], queryFn: fetchMyQueue, retry: false })
  const queue = queueQ.data ?? null

  const diagQ = useQuery({
    queryKey: ['diagnosis', queue?.userTestId],
    queryFn: () => fetchSavedDiagnosis(queue!.userTestId),
    enabled: !!queue,
    retry: false,
  })

  // ●8 그래프 = **학습 큐 그 자체의 DAG** — 노드는 저장된 계단 항목만, 간선은 그들 사이 선수관계
  // (2026-08-05 결정 D). 구현은 중심 개념 선수 폐쇄를 그리던 것이라 큐에 없는 개념이 대부분이고
  // 큐 항목 상당수는 아예 빠져 있어, "학습 경로 보기"가 약속한 것과 화면이 어긋나 있었다.
  const currentItem = queue?.items.find((i) => i.current) ?? null
  const cardIds = (diagQ.data?.cards ?? []).map((c) => c.conceptId)
  const queueIds = queue?.items.map((i) => i.conceptId) ?? []
  // 노드 집합은 done 토글로 바뀌지 않는다 — 시그니처를 키로 써서 체크할 때마다 재레이아웃되는 걸 막음
  const nodeSig = queue?.items.map((i) => `${i.conceptId}:${i.conceptName}`).join('|') ?? ''
  // deps 가 nodeSig 뿐인 건 의도 — queue 객체는 토글마다 새로 오지만 노드 집합은 그대로다
  const graphNodes = useMemo(
    () => queue?.items.map((i) => ({ conceptId: i.conceptId, conceptName: i.conceptName })) ?? [],
    [nodeSig], // eslint-disable-line react-hooks/exhaustive-deps
  )
  const doneIds = useMemo(
    () => queue?.items.filter((i) => i.done).map((i) => i.conceptId) ?? [],
    [queue],
  )
  const edgesQ = useQuery({
    queryKey: ['queue-graph', nodeSig, cardIds.join(',')],
    queryFn: () => fetchQueueEdges(cardIds, queueIds),
    enabled: cardIds.length > 0 && queueIds.length > 0,
  })

  const toggle = useMutation({
    mutationFn: ({ queueItemId }: { queueItemId: string }) =>
      toggleQueueItemDone(queue!.queueId, queueItemId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['queue', 'me'] }),
    // 실패 시에도 큐 재조회 — 스테일 queueId/itemId 로 인한 반복 실패 자가치유
    onError: () => qc.invalidateQueries({ queryKey: ['queue', 'me'] }),
  })

  // 401 — 인라인 안내 → 재로그인 → 보던 화면 복귀 (05 카탈로그 확정)
  if (queueQ.error instanceof AuthRequiredError || diagQ.error instanceof AuthRequiredError) {
    return (
      <div className="screen screen--gated">
        <Link className="nav-back" to="/">‹ 홈</Link>
        <div className="err-card">
          다시 로그인해주세요 — 저장된 계단은 안전해요.
          <br />
          <button
            className="retry"
            onClick={() => nav(`/login?return=${encodeURIComponent('/result?view=saved')}`)}
          >
            다시 로그인
          </button>
        </div>
      </div>
    )
  }

  if (queueQ.isSuccess && !queue) {
    // 저장 큐 없음 — 재열람할 것이 없다
    return (
      <div className="screen screen--gated">
        <Link className="nav-back" to="/">‹ 홈</Link>
        <div className="err-card">
          저장된 학습 계단이 없어요 — 진단부터 시작해요.
          <br />
          <button className="retry" onClick={() => nav('/entry')}>진단 시작</button>
        </div>
      </div>
    )
  }

  const selectedItem = queue?.items.find((i) => i.conceptId === selectedId) ?? null
  const cards = diagQ.data?.cards ?? []
  const more = diagQ.data?.more ?? []

  return (
    <div className="screen screen--gated">
      {/* ●7 ‹ 홈 — 저장 상태라 언제든 복귀 가능 */}
      <Link className="nav-back" to="/">‹ 홈</Link>

      {/* ●8 그래프 경로 (순서 1 — 게이트 독점 자산) */}
      <div className={s.secTag}>나의 학습 경로 (저장됨)</div>
      <div className={s.graphBox}>
        {graphNodes.length > 0 && edgesQ.data ? (
          <ConceptGraph
            concepts={graphNodes}
            edges={edgesQ.data}
            selectedId={selectedId}
            onSelect={setSelectedId}
            doneIds={doneIds}
            currentId={currentItem?.conceptId ?? null}
            compact
            height={GRAPH_H}
          />
        ) : (
          <div style={{ height: GRAPH_H }} />
        )}
      </div>
      <div className={s.nodeInfo}>
        {selectedItem
          ? `${selectedItem.position}. ${selectedItem.conceptName} — ${
              selectedItem.done ? '완료함' : selectedItem.current ? '여기부터' : '아직'
            }`
          : '노드를 탭하면 몇 번째 계단인지 보여줘요'}
      </div>

      {/* ●9 진단 카드 재열람 (순서 2 — 2차 버튼 없음, 외부링크는 그대로) */}
      <div className={s.secTag}>진단 카드 (재열람)</div>
      {cards.map((c) => (
        <CardView key={c.conceptId} card={c} />
      ))}
      {more.length > 0 && (
        <>
          {moreOpen && more.map((c) => <CardView key={c.conceptId} card={c} />)}
          <button className={s.moreFold} onClick={() => setMoreOpen((v) => !v)}>
            {moreOpen ? '접기 ∧' : `더 보기 (중/하 ${more.length}개) ∨`}
          </button>
        </>
      )}

      {/* ●11 다시 진단하기 (보조) — 탭 시점엔 아무것도 폐기 안 됨. 하단 여백 = 플로팅 버튼 자리 */}
      <div className={s.ctaWrap} style={{ paddingBottom: 64 }}>
        <button className="btn-secondary" onClick={() => nav('/entry')}>
          다시 진단하기
        </button>
      </div>

      {/* ●10 큐 체크리스트 (순서 3) — 화면 위에 떠 있는 상주 pill → 탭하면 시트
          (2026-07-18 실기기 라운드: 페이지 하단 인라인 → 플로팅 진입으로 재배치) */}
      {queue && (
        <button className={s.queueFab} onClick={() => setQueueOpen(true)}>
          학습 계단 {queue.items.filter((i) => i.done).length}/{queue.items.length}
        </button>
      )}
      <BottomSheet open={queueOpen} onClose={() => setQueueOpen(false)}>
        <div className={s.secTag}>나의 학습 계단 — 체크리스트</div>
        <div className={s.qsub}>선수지식 먼저(위상순). 공부하고 오면 탭해서 완료 표시</div>
        {toggle.isError && (
          <div className={s.qerr} role="alert">
            체크가 저장되지 않았어요 — 다시 탭해주세요.
            {/* 원인 코드 — 실기기 재보고 시 원인 특정용 (2026-07-18 미재현 버그) */}
            <small>
              ({toggle.error instanceof ApiError ? `오류 ${toggle.error.status}` : toggle.error.name})
            </small>
            {toggle.error instanceof AuthRequiredError && (
              <button
                className="retry"
                onClick={() => nav(`/login?return=${encodeURIComponent('/result?view=saved')}`)}
              >
                다시 로그인
              </button>
            )}
          </div>
        )}
        {queue?.items.length === 0 && (
          <div className={s.qsub}>
            표시할 계단이 없어요 — 이미 아는 것으로 확인된 개념은 계단에 넣지 않아요.
          </div>
        )}
        {queue?.items
          .slice()
          .sort((a, b) => a.position - b.position)
          .map((item) => (
            <button
              key={item.queueItemId}
              className={`${s.qitem} ${item.done ? s.done : ''}`}
              onClick={() => toggle.mutate({ queueItemId: item.queueItemId })}
              disabled={toggle.isPending}
            >
              <span className={s.chk}>{item.done ? '✓' : ''}</span>
              {item.position}. {item.conceptName}
              {item.current && <span className={s.here}>여기부터</span>}
            </button>
          ))}
      </BottomSheet>
    </div>
  )
}
