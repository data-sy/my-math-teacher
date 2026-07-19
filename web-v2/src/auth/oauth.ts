// OAuth 진입 = XHR 아닌 페이지 이동 (접점 시트).
// mock 모드 = 가짜 콜백으로 즉시 복귀 (가정 A-4 — 분기는 이 파일 한 곳).
import { mockEnabled } from '../api/client'

export type OAuthProvider = 'google' | 'naver' | 'kakao'

export function startOAuth(provider: OAuthProvider): void {
  if (mockEnabled()) {
    const url = new URL('/login', window.location.origin)
    url.searchParams.set('token', `mock-token-${provider}`)
    // 콜백 복귀를 흉내 — 실제 백엔드도 프론트 /login?token= 으로 리다이렉트한다
    window.location.assign(url.pathname + url.search)
    return
  }
  const base = import.meta.env.VITE_API_BASE ?? ''
  window.location.assign(`${base}/oauth2/authorization/${provider}`)
}
