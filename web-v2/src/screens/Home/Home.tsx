import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { fetchMyQueue } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import s from './Home.module.css'

export default function Home() {
  const nav = useNavigate()
  const { isLoggedIn } = useAuth()
  const [params] = useSearchParams()

  // OAuth 실패 콜백은 백엔드가 `/?error=` 로 보낸다 (2026-07-18 실코드 확인 — A-15 정정).
  // 인라인 실패 안내는 ⑤-A 소관(05 카탈로그) — 그대로 넘긴다.
  const oauthError = params.get('error')
  useEffect(() => {
    if (oauthError) nav(`/login?error=${encodeURIComponent(oauthError)}`, { replace: true })
  }, [oauthError, nav])

  // 배너 데이터 — 로그인 상태에서만 1회 호출 (01 ●1: 비로그인 시 호출 없음)
  const { data: queue } = useQuery({
    queryKey: ['queue', 'me'],
    queryFn: fetchMyQueue,
    enabled: isLoggedIn,
  })

  const nextItem = queue?.items.find((i) => i.current) // 서버 파생 "여기부터" (전부 완료면 없음)
  const allDone = !!queue && queue.items.length > 0 && !nextItem

  return (
    <div className="screen">
      {/* ●7 조용한 로그인 링크 — 비로그인 시에만 (●1 배너와 상호 배타) */}
      {!isLoggedIn && (
        <div className={s.loginRow}>
          <Link className="txt-link" to="/login?from=home">
            로그인
          </Link>
        </div>
      )}

      {/* ●1 재진입 배너 — 로그인 + 활성 큐 보유 시에만. 완료 시 문구만 대체(A안) */}
      {isLoggedIn && queue && queue.items.length > 0 && (
        <button className={s.resumeBanner} onClick={() => nav('/result?view=saved')}>
          <b>{allDone ? '학습 계단 전부 완료 🎉 ›' : `이어서: ${nextItem!.conceptName} ›`}</b>
          <span className="sub">저장된 학습 계단으로 돌아가기</span>
        </button>
      )}

      {/* ●2 가치제안 헤드라인 — 정직성 한 줄은 ④에서만 (B안 확정) */}
      <h1 className={s.headline}>
        수학은 계단이다.
        <br />
        어디서 막혔는지,
        <br />
        3분 자가진단으로 AI가 짚어준다.
      </h1>

      {/* ●3 히어로 비주얼 = 지식그래프 미끼 — 탭해도 이동하지 않음.
          클러스터는 고정 좌표라 래퍼로 박스 중앙 정렬 (넓은 웹·120px 축소에서 쏠림·바닥 잘림 방지).
          배열 = 왼쪽 아래→오른쪽 위 오름 계단 — 헤드라인 "수학은 계단이다"와 시각 일치
          (2026-07-18 실기기 라운드: 지그재그는 계단으로 안 읽힘 → 재배치) */}
      <div className={s.heroGraph} aria-hidden="true">
        <div className={s.gcluster}>
          {/* 계단 프로파일 = 디딤판(가로) + 챌판(세로) — 빈 계단(?) 디딤판만 빨간 점선 */}
          <span className={s.gstep} style={{ top: 100, left: 0, width: 62 }} />
          <span className={s.griser} style={{ top: 84, left: 62, height: 16 }} />
          <span className={`${s.gstep} ${s.gstepGap}`} style={{ top: 84, left: 62, width: 62 }} />
          <span className={s.griser} style={{ top: 68, left: 124, height: 16 }} />
          <span className={s.gstep} style={{ top: 68, left: 124, width: 62 }} />
          <span className={s.griser} style={{ top: 52, left: 186, height: 16 }} />
          <span className={s.gstep} style={{ top: 52, left: 186, width: 62 }} />
          <span className={s.griser} style={{ top: 36, left: 248, height: 16 }} />
          <span className={s.gstep} style={{ top: 36, left: 248, width: 62 }} />
          <span className={s.gnode} style={{ top: 64, left: 13 }}>개념</span>
          <span className={`${s.gnode} ${s.gnodeGap}`} style={{ top: 48, left: 75 }}>?</span>
          <span className={s.gnode} style={{ top: 32, left: 137, animationDelay: '0.6s' }}>개념</span>
          <span className={s.gnode} style={{ top: 16, left: 199, animationDelay: '1.2s' }}>개념</span>
          <span className={s.gnode} style={{ top: 0, left: 261, animationDelay: '1.8s' }}>개념</span>
        </div>
      </div>

      {/* ●4 작동방식 스텝 */}
      <ul className={s.steps}>
        <li>알아요 / 몰라요만 답하면</li>
        <li>그래프가 무너진 토대를 추적하고</li>
        <li>AI가 급한 순서로 짚어줘요</li>
      </ul>

      {/* ●5 1차 CTA — 화면 유일 버튼, 하단 고정 */}
      <div className={s.cta}>
        <button className="btn-primary" onClick={() => nav('/entry')}>
          무료 진단 시작
        </button>
      </div>

      {/* ●6 조용한 텍스트 링크 */}
      <Link className={`txt-link ${s.browseLink}`} to="/graph">
        개념 그래프 둘러보기 ›
      </Link>
    </div>
  )
}
