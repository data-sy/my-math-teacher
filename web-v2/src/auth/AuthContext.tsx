import { createContext, useContext, useSyncExternalStore, type ReactNode } from 'react'
import { logout as apiLogout } from '../api/endpoints'
import { clearAccessToken, getAccessToken, setAccessToken, subscribeToken } from './tokenStore'

interface AuthValue {
  isLoggedIn: boolean
  loginWithToken: (token: string) => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const token = useSyncExternalStore(subscribeToken, getAccessToken)

  const value: AuthValue = {
    isLoggedIn: token !== null,
    loginWithToken: setAccessToken,
    logout: async () => {
      try {
        await apiLogout() // DELETE /api/v1/auth/authentication (접점 시트)
      } finally {
        clearAccessToken()
      }
    },
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const v = useContext(AuthContext)
  if (!v) throw new Error('useAuth outside AuthProvider')
  return v
}
