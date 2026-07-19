// ⑤-B 풀스크린 에러 — 화면 컨텍스트가 없는 실패만 (라우팅 404 · 403 소유권)
import { useNavigate } from 'react-router-dom'
import s from './NotFound.module.css'

export default function NotFound() {
  const nav = useNavigate()
  return (
    <div className="screen">
      <div className={s.center}>
        <div className={s.face}>😅</div>
        <div className={s.copy}>
          여기로 가는 길이
          <br />
          없나 봐요
        </div>
        <div className={s.sub}>
          주소가 바뀌었거나 잘못 눌렸어요.
          <br />
          홈에서 다시 시작하면 돼요.
        </div>
      </div>
      <button className="btn-primary" onClick={() => nav('/')}>
        홈으로
      </button>
    </div>
  )
}
