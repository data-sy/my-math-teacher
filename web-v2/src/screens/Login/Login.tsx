import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { createQueue, saveDiagnosis } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { startOAuth, type OAuthProvider } from '../../auth/oauth'
import { completedSession, consumePendingAttribution, setPendingAttribution } from '../../session/diagSession'
import s from './Login.module.css'

const POST_LOGIN_KEY = 'mmt.postLogin'

// 진입 변형(05 ●3): gate = ④ 게이트 CTA 경유(기본) · home = 홈 상단 링크(귀속 없음) ·
// relogin = 401 재로그인(보던 화면 복귀)
type Variant = 'gate' | 'home' | 'relogin'

export default function Login() {
  const nav = useNavigate()
  const [params] = useSearchParams()
  const { loginWithToken } = useAuth()

  const token = params.get('token') // 콜백 = /login?token= (접점 시트 확정)
  const returnTo = params.get('return')
  const variant: Variant = params.get('from') === 'home' ? 'home' : returnTo ? 'relogin' : 'gate'

  const [error, setError] = useState<null | 'oauth' | 'attribution'>(
    params.get('error') ? 'oauth' : null,
  )
  const attempted = useRef(false)

  // ── 귀속 + 큐 생성 → ④-B 전환 (플로우 맵 ●7 — 유저 추가 조작 없음) ──
  async function attribute(): Promise<void> {
    const sess = completedSession()
    if (!sess) {
      nav('/', { replace: true })
      return
    }
    try {
      const saved = await saveDiagnosis(sess.entry, sess.answered)
      await createQueue(saved.userTestId)
      nav('/result?view=saved', { replace: true })
    } catch {
      setError('attribution')
    }
  }

  // ── OAuth 콜백 수신 ──
  useEffect(() => {
    if (!token || attempted.current) return
    attempted.current = true
    loginWithToken(token)
    const pending = consumePendingAttribution()
    if (pending && completedSession()) {
      attribute()
    } else {
      const dest = localStorage.getItem(POST_LOGIN_KEY) ?? '/'
      localStorage.removeItem(POST_LOGIN_KEY)
      nav(dest, { replace: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  function tapOAuth(provider: OAuthProvider) {
    // OAuth 왕복 후 목적지 — 콜백 URL 에는 token 만 오므로 여기서 기억해 둔다
    if (variant === 'home') localStorage.setItem(POST_LOGIN_KEY, '/')
    if (variant === 'relogin' && returnTo) localStorage.setItem(POST_LOGIN_KEY, returnTo)
    if (variant === 'gate') setPendingAttribution() // ④에서 이미 셋했어도 멱등
    startOAuth(provider)
  }

  if (token) {
    // 콜백 처리 중 — 귀속 실패 시에만 인라인 에러가 남는다
    return (
      <div className="screen">
        {error === 'attribution' ? (
          <>
            <h1 className={s.headline}>결과를 저장하고<br />학습 경로를 시작해요</h1>
            <div className={s.inlineErr}>
              저장이 잘 안 됐어요 — 다시 시도해주세요. 진단 결과는 그대로 있어요.
              <br />
              <button onClick={() => attribute()}>다시 시도</button>
            </div>
            <div className={s.later}>
              <Link className="txt-link" to="/result">나중에 할게요 — 무료 결과로 돌아가기</Link>
            </div>
          </>
        ) : (
          <p style={{ color: 'var(--sub)', fontSize: 13 }}>로그인 중…</p>
        )}
      </div>
    )
  }

  const backTo = variant === 'gate' ? '/result' : '/'
  const backLabel = variant === 'gate' ? '‹ 결과로' : '‹ 홈'

  return (
    <div className="screen">
      {/* ●1 상단 이탈 — ●4와 같은 목적지 */}
      <Link className="nav-back" to={backTo}>{backLabel}</Link>

      {/* ●2 헤드라인 + 안심 박스 — 게이트 보상의 연장 프레이밍 */}
      {variant === 'gate' && (
        <>
          <h1 className={s.headline}>결과를 저장하고<br />학습 경로를 시작해요</h1>
          <div className={s.gatebox}>
            방금 받은 진단 결과는 그대로 있어요.
            <br />
            로그인하면 계정에 저장되고, 그래프 학습 경로가 열려요.
          </div>
        </>
      )}
      {variant === 'home' && <h1 className={s.headline}>로그인</h1>}
      {variant === 'relogin' && (
        <>
          <h1 className={s.headline}>다시 로그인해주세요</h1>
          <div className={s.gatebox}>저장된 계단은 안전해요 — 로그인하면 보던 화면으로 돌아가요.</div>
        </>
      )}

      {/* ●3 OAuth 3사 — 동급(특정 사 우대 없음), 페이지 이동 */}
      <div className={s.oauth}>
        <button onClick={() => tapOAuth('google')}>구글로 계속하기</button>
        <button onClick={() => tapOAuth('naver')}>네이버로 계속하기</button>
        <button onClick={() => tapOAuth('kakao')}>카카오로 계속하기</button>
      </div>

      {/* 조건부 — OAuth 콜백 실패 복귀 (인라인, 재시도 컨텍스트 보존) */}
      {error === 'oauth' && (
        <div className={s.inlineErr}>로그인이 잘 안 됐어요 — 다시 시도해주세요. 진단 결과는 그대로 있어요.</div>
      )}

      {/* ●4 게이트를 강요하지 않는 명시적 출구 */}
      <div className={s.later}>
        {variant === 'gate' ? (
          <Link className="txt-link" to="/result">나중에 할게요 — 무료 결과로 돌아가기</Link>
        ) : (
          <Link className="txt-link" to="/">나중에 할게요 — 홈으로</Link>
        )}
      </div>
    </div>
  )
}
