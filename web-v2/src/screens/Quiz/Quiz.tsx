import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { fetchNext } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { NextQuestion } from '../../api/types'
import {
  clearSession,
  loadSession,
  saveSession,
  type DiagSession,
} from '../../session/diagSession'
import s from './Quiz.module.css'

type Phase =
  | { kind: 'loading' }
  | { kind: 'question'; q: NextQuestion; asked: number; remaining: number }
  | { kind: 'error'; message: string; stale: boolean } // stale = 400 (새로 시작 유도)

export default function Quiz() {
  const nav = useNavigate()
  const [session, setSession] = useState<DiagSession | null>(loadSession)
  const [phase, setPhase] = useState<Phase>({ kind: 'loading' })
  const [toastOn, setToastOn] = useState(false)
  // 바 후퇴 금지 — 지금까지의 max 유지, undo 만 예외 (03 ●2 확정)
  const maxRatioRef = useRef(0)
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const askNext = useCallback(
    async (cur: DiagSession, opts?: { undoReset?: boolean }) => {
      setPhase({ kind: 'loading' })
      try {
        const res = await fetchNext(cur.entry, cur.answered)
        if ('next' in res && res.next) {
          const { asked, estimatedRemaining } = res.progress
          const ratio = asked + estimatedRemaining > 0 ? asked / (asked + estimatedRemaining) : 0
          if (opts?.undoReset) {
            maxRatioRef.current = ratio // undo — 명시적 되돌리기: 바도 같이 후퇴
          } else {
            maxRatioRef.current = Math.max(maxRatioRef.current, ratio)
          }
          setPhase({ kind: 'question', q: res.next, asked, remaining: estimatedRemaining })
        } else {
          // done:true — 즉시 ④ 자동 전환 (03 ●4)
          const finished = { ...cur, done: true, savedAt: Date.now() }
          saveSession(finished)
          nav('/result', { replace: true })
        }
      } catch (e) {
        if (e instanceof ApiError && e.status === 400) {
          setPhase({ kind: 'error', message: e.message, stale: true })
        } else {
          setPhase({ kind: 'error', message: '연결이 잠깐 끊겼어요. 답변은 그대로 있어요.', stale: false })
        }
      }
    },
    [nav],
  )

  useEffect(() => {
    const cur = loadSession()
    if (!cur || cur.done) {
      nav('/entry', { replace: true }) // 세션 없이 직접 방문 — ②로
      return
    }
    askNext(cur)
    return () => {
      if (toastTimer.current) clearTimeout(toastTimer.current)
    }
  }, [askNext, nav])

  if (!session) return null

  function answer(know: boolean) {
    if (phase.kind !== 'question' || !session) return
    const updated: DiagSession = {
      ...session,
      answered: [...session.answered, { conceptId: phase.q.conceptId, known: know }],
      savedAt: Date.now(),
    }
    saveSession(updated) // 매 답변 직후 localStorage (F-2)
    setSession(updated)
    if (know) {
      // 토스트 = "알아요"에만 1.5초 (03 ●5 확정)
      setToastOn(true)
      if (toastTimer.current) clearTimeout(toastTimer.current)
      toastTimer.current = setTimeout(() => setToastOn(false), 1500)
    }
    askNext(updated)
  }

  // ‹ 이전 답 수정 — 직전 답 1개만 undo (인과 불변식, 03 ●6)
  function undo() {
    if (!session || session.answered.length === 0) return
    const updated: DiagSession = {
      ...session,
      answered: session.answered.slice(0, -1),
      savedAt: Date.now(),
    }
    saveSession(updated)
    setSession(updated)
    askNext(updated, { undoReset: true })
  }

  function restartFromEntry() {
    clearSession() // 400 스테일 맵 — 맵 폐기 후 ② (카탈로그)
    nav('/entry', { replace: true })
  }

  const barWidth = `${Math.round(maxRatioRef.current * 100)}%`

  return (
    <div className="screen">
      {/* ●1 ‹ 진입 — 경고 없음 (진행분은 localStorage 에) */}
      <Link className="nav-back" to="/entry">
        ‹ 진입
      </Link>

      {/* ●2 진척 — 숫자는 솔직하게, 바는 후퇴 금지 */}
      <div className={s.progress}>
        <span>{session.answered.length}개 답변</span>
        {phase.kind === 'question' && <span>남은 예상 ~{phase.remaining}</span>}
      </div>
      <div className={s.pbar}>
        <i style={{ width: barWidth }} />
      </div>

      {/* ●3 질문 카드 (또는 ●7 인라인 에러로 카드 자리 대체) */}
      {phase.kind === 'question' && (
        <div className={s.qcard}>
          <div className={s.qname}>{phase.q.conceptName}</div>
          <div className={s.qdesc}>예: {phase.q.description.replace(/^예:\s*/, '')}</div>
        </div>
      )}
      {phase.kind === 'loading' && <div className={s.loading}>…</div>}
      {phase.kind === 'error' && (
        <div className="err-card">
          {phase.stale ? (
            <>
              {phase.message} — 처음부터 다시 하는 게 좋겠어요.
              <br />
              <button className="retry" onClick={restartFromEntry}>
                새로 시작
              </button>
            </>
          ) : (
            <>
              {phase.message}
              <br />
              <button className="retry" onClick={() => session && askNext(session)}>
                다시 시도
              </button>
            </>
          )}
        </div>
      )}

      {/* ●5 토스트 — 알아요에만 일시 표시 */}
      <div className={s.toastWrap}>
        {toastOn && <div className={s.toast}>알겠어요 — 관련 기초는 건너뛸게요</div>}
      </div>

      {/* ●4 2택 — 하단 고정, 좌우 불변. 에러·로딩 중 비활성 */}
      <div className={s.qbtns}>
        <button className={s.dont} disabled={phase.kind !== 'question'} onClick={() => answer(false)}>
          몰라요
        </button>
        <button className={s.know} disabled={phase.kind !== 'question'} onClick={() => answer(true)}>
          알아요
        </button>
      </div>

      {/* ●6 undo — 답변 0개면 숨김 */}
      <div className={s.undoRow}>
        {session.answered.length > 0 && <button onClick={undo}>‹ 이전 답 수정</button>}
      </div>
    </div>
  )
}
