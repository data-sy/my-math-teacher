// fetch 래퍼 — Bearer 부착 · 401 시 reissue 1회 재시도 (접점 시트 인증 역학)
import type { ApiErrorBody, ReissueResponse } from './types'
import { getAccessToken, setAccessToken, clearAccessToken } from '../auth/tokenStore'

export const API_BASE: string = mockEnabled()
  ? '' // mock 모드 = 상대 경로 (MSW 가 same-origin 요청을 가로챔)
  : (import.meta.env.VITE_API_BASE ?? '')

export function mockEnabled(): boolean {
  // 기본값: dev = mock on (백엔드 미기동 환경) · prod 빌드 = off.
  // 명시 플래그가 항상 우선 — 실서버 연동 dev 는 VITE_ENABLE_MOCK=false,
  // mock 으로 prod 빌드 검증(Playwright)은 VITE_ENABLE_MOCK=true 로 빌드.
  const flag = import.meta.env.VITE_ENABLE_MOCK
  if (flag === 'true') return true
  if (flag === 'false') return false
  return import.meta.env.DEV
}

export class ApiError extends Error {
  status: number
  body: ApiErrorBody
  constructor(status: number, body: ApiErrorBody) {
    super(body.message ?? `요청이 실패했어요 (${status})`)
    this.status = status
    this.body = body
  }
}

/** 401 이 reissue 로도 안 풀릴 때 던짐 — 화면은 재로그인 유도로 처리 (05 카탈로그 401 행) */
export class AuthRequiredError extends Error {
  constructor() {
    super('다시 로그인해주세요')
  }
}

interface RequestOptions {
  method?: string
  body?: unknown
  auth?: boolean
}

async function rawRequest(path: string, opts: RequestOptions, token: string | null): Promise<Response> {
  const headers: Record<string, string> = {}
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  if (opts.auth && token) headers['Authorization'] = `Bearer ${token}`
  return fetch(`${API_BASE}${path}`, {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  })
}

async function tryReissue(expiredToken: string): Promise<string | null> {
  try {
    const res = await fetch(`${API_BASE}/api/v1/auth/reissue`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      // 쿠키의 refreshToken 필요 — withCredentials 필수 (접점 시트)
      credentials: 'include',
      body: JSON.stringify({ grantType: 'Bearer', accessToken: expiredToken }),
    })
    if (!res.ok) return null
    const data = (await res.json()) as ReissueResponse
    return data.accessToken ?? null
  } catch {
    return null
  }
}

export async function apiFetch<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  let token = getAccessToken()
  let res = await rawRequest(path, opts, token)

  if (res.status === 401 && opts.auth && token) {
    const fresh = await tryReissue(token)
    if (fresh) {
      setAccessToken(fresh)
      token = fresh
      res = await rawRequest(path, opts, token)
    }
  }

  if (res.status === 401 && opts.auth) {
    clearAccessToken()
    throw new AuthRequiredError()
  }

  if (!res.ok) {
    // 실서버 에러 바디 = plain text (2026-07-18 실측 — {message} JSON 가정 뒤집힘, 401 은 빈 바디)
    let body: ApiErrorBody = {}
    const text = await res.text().catch(() => '')
    if (text) {
      try {
        const parsed: unknown = JSON.parse(text)
        body = typeof parsed === 'object' && parsed !== null ? (parsed as ApiErrorBody) : { message: String(parsed) }
      } catch {
        body = { message: text }
      }
    }
    throw new ApiError(res.status, body)
  }

  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
