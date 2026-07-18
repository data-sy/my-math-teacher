import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { fetchChapters, fetchFrontier } from '../../api/endpoints'
import type { Chapter } from '../../api/types'
import BottomSheet from '../../components/BottomSheet'
import {
  currentSemester,
  ladderLabel,
  ladderWindow,
  pickDefaultChapter,
} from '../../lib/curriculum'
import {
  clearSession,
  loadGradeSemester,
  resumableSession,
  saveGradeSemester,
  startSession,
  type GradeSemester,
} from '../../session/diagSession'
import s from './Entry.module.css'

const GRADES = ['중1', '중2', '중3', '고1', '고2', '고3']

export default function Entry() {
  const nav = useNavigate()
  const [gs, setGs] = useState<GradeSemester | null>(loadGradeSemester)
  const [resume, setResume] = useState(resumableSession)
  const [gradeSheetOpen, setGradeSheetOpen] = useState(false)
  const [chapterSheetOpen, setChapterSheetOpen] = useState(false)
  const [starting, setStarting] = useState(false)

  const { data: chapters, isError, refetch } = useQuery({
    queryKey: ['chapters'],
    queryFn: () => fetchChapters(gs?.grade, gs?.semester),
  })

  const month = new Date().getMonth() + 1
  const defaultChapter =
    chapters && gs ? pickDefaultChapter(chapters, gs.grade, gs.semester, month) : null
  const candidates = chapters && defaultChapter ? ladderWindow(chapters, defaultChapter.chapterId) : []

  function chooseGradeSemester(g: GradeSemester) {
    saveGradeSemester(g)
    setGs(g)
    setGradeSheetOpen(false)
  }

  // pick-list 원탭 = 즉시 ③ (별도 확인 없음). 다른 단원 탭 = 기존 세션 확인 없이 자동 폐기 (02 ●4 확정)
  async function startChapter(c: Chapter) {
    if (starting) return
    setStarting(true)
    try {
      await fetchFrontier(c.chapterId) // ②→③ 계약 호출 (단원 검증)
      startSession({ scope: 'chapter', chapterId: c.chapterId }, c.chapterId, c.name)
      nav('/quiz')
    } catch {
      setStarting(false) // 실패 시 화면 유지 — 재탭으로 재시도
    }
  }

  // escape (b) — 전체 훑기 (scope=full)
  function startFullScan() {
    startSession({ scope: 'full', schoolLevel: '중등' }, null, '전체 훑기')
    nav('/quiz')
  }

  return (
    <div className="screen">
      {/* ●1 ‹ 홈 — 경고 없이 이동 (맵은 localStorage 에 무사) */}
      <Link className="nav-back" to="/">
        ‹ 홈
      </Link>

      {/* ●2 헤드라인 */}
      <h1 className={s.headline}>지금 어디쯤이야?</h1>
      <p className={s.subcopy}>진단하고 싶은 것을 고르세요.</p>

      {/* ●3 학년·학기 칩 — 설정된 경우에만. 미설정이면 아래 gradeFirst 가 먼저 */}
      {gs && (
        <div className={s.gradeRow}>
          <button className={s.gradeChip} onClick={() => setGradeSheetOpen(true)}>
            {gs.grade} · {gs.semester}학기 ▾
          </button>
          <span className={s.gradeHint}>바꾸면 아래 후보가 갱신돼요</span>
        </div>
      )}

      {/* ●4 진행 중 세션 노트 (조건부 — 미완주·미저장 세션) */}
      {resume && (
        <div className={s.resumeNote}>
          진행 중인 진단이 있어요 — '{resume.chapterName}' {resume.answered.length}개 답변까지
          <div className={s.resumeBtns}>
            <button onClick={() => nav('/quiz')}>이어서 진행</button>
            <button
              onClick={() => {
                clearSession() // 확인탭 없이 즉시 폐기 (02 ●4 확정 — 확인 모달은 백로그)
                setResume(null)
              }}
            >
              새로 시작
            </button>
          </div>
        </div>
      )}

      {/* 최초 방문 — 학년 미설정: pick-list 자리에 학년 선택 먼저 (02 ●3 확정) */}
      {!gs && (
        <div className={s.gradeFirst}>
          <p>학년을 알려주면 지금 시기 단원을 추천해요</p>
          <GradePicker onPick={chooseGradeSemester} />
        </div>
      )}

      {/* ●5 pick-list — 계단순 ±후보, default 강조 + 배지, 원탭 즉시 시작 */}
      {gs && candidates.length > 0 && defaultChapter && (
        <div>
          <div className={s.ladderTag}>계단순 (아래 단계 → 위 단계)</div>
          {candidates.map((c) => (
            <button
              key={c.chapterId}
              className={`${s.pickItem} ${c.chapterId === defaultChapter.chapterId ? s.default : ''}`}
              onClick={() => startChapter(c)}
              disabled={starting}
            >
              <span>
                {c.name} <small>{ladderLabel(c, defaultChapter.order)}</small>
              </span>
              {c.chapterId === defaultChapter.chapterId && <span className={s.reco}>지금 시기 추천</span>}
            </button>
          ))}
        </div>
      )}

      {isError && (
        <div className="err-card">
          연결이 잠깐 끊겼어요.
          <br />
          <button className="retry" onClick={() => refetch()}>
            다시 시도
          </button>
        </div>
      )}

      {/* ●6·●7 escape 두 갈래 — 보조 버튼 위계 */}
      <div className={s.escapeRow}>
        <button className="btn-secondary" onClick={() => setChapterSheetOpen(true)}>
          여기 없음? — 다른 단원 직접 선택
        </button>
        <button className="btn-secondary" onClick={startFullScan}>
          모르겠어 → 전체 훑기
        </button>
      </div>

      {/* 학년·학기 선택 시트 */}
      <BottomSheet open={gradeSheetOpen} onClose={() => setGradeSheetOpen(false)}>
        <div className={s.sheetTitle}>학년 · 학기</div>
        <GradePicker current={gs} onPick={chooseGradeSemester} />
      </BottomSheet>

      {/* escape (a) — 전체 단원 목록 바텀시트 (학년별 그룹) */}
      <BottomSheet open={chapterSheetOpen} onClose={() => setChapterSheetOpen(false)}>
        <div className={s.sheetTitle}>다른 단원 직접 선택</div>
        {GRADES.filter((g) => chapters?.some((c) => c.grade === g)).map((g) => (
          <div key={g}>
            <div className={s.sheetGroup}>{g}</div>
            {chapters
              ?.filter((c) => c.grade === g)
              .map((c) => (
                <button key={c.chapterId} className={s.sheetItem} onClick={() => startChapter(c)} disabled={starting}>
                  {c.name} <small style={{ color: 'var(--sub)' }}>· {c.semester}학기</small>
                </button>
              ))}
          </div>
        ))}
      </BottomSheet>
    </div>
  )
}

function GradePicker({
  current,
  onPick,
}: {
  current?: GradeSemester | null
  onPick: (g: GradeSemester) => void
}) {
  const [grade, setGrade] = useState<string | null>(current?.grade ?? null)
  const month = new Date().getMonth() + 1
  return (
    <div>
      <div className={s.gradeGrid}>
        {GRADES.map((g) => (
          <button key={g} className={grade === g ? s.on : ''} onClick={() => setGrade(g)}>
            {g}
          </button>
        ))}
      </div>
      {grade && (
        <div className={s.semRow}>
          {([1, 2] as const).map((sem) => (
            <button
              key={sem}
              className={current?.grade === grade && current?.semester === sem ? s.on : ''}
              onClick={() => onPick({ grade, semester: sem })}
            >
              {sem}학기{currentSemester(month) === sem ? ' (지금)' : ''}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
