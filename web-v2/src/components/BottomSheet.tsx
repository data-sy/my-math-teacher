// 모바일 바텀시트 — 시트 밖 탭 = 닫기 (02 ●6 · 06 ●5 공용 규약)
import type { ReactNode } from 'react'
import s from './BottomSheet.module.css'

interface Props {
  open: boolean
  onClose: () => void
  children: ReactNode
  /** ⑥ 노드 시트처럼 캔버스 위에 겹치되 배경을 어둡게 하지 않는 변형 */
  transparentBackdrop?: boolean
}

export default function BottomSheet({ open, onClose, children, transparentBackdrop }: Props) {
  if (!open) return null
  return (
    <div
      className={`${s.backdrop} ${transparentBackdrop ? s.transparent : ''}`}
      onClick={onClose}
      role="presentation"
    >
      <div className={s.sheet} onClick={(e) => e.stopPropagation()}>
        <div className={s.grab} />
        {children}
      </div>
    </div>
  )
}
