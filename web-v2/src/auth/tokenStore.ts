// accessToken 보관 = localStorage (가정 A-3 — 어댑터 한 곳으로 격리)
const KEY = 'mmt.accessToken'

type Listener = () => void
const listeners = new Set<Listener>()

export function getAccessToken(): string | null {
  return localStorage.getItem(KEY)
}

export function setAccessToken(token: string): void {
  localStorage.setItem(KEY, token)
  listeners.forEach((l) => l())
}

export function clearAccessToken(): void {
  localStorage.removeItem(KEY)
  listeners.forEach((l) => l())
}

export function subscribeToken(l: Listener): () => void {
  listeners.add(l)
  return () => listeners.delete(l)
}
