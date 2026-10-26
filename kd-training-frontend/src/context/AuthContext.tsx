import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api/client'
import { loginUser as apiLogin } from '../api/auth'
import { saveTokens, clearTokens, hasTokens, type AuthTokens } from '../utils/tokenStorage'
import { ApiError } from '../utils/errors'
import { useToast } from './ToastContext'
import type { User } from '../types'

/**
 * A failed `/user/me` is not proof that the login is over.
 *
 * This used to clear the tokens on ANY error, so one 429 from the rate limiter — or a momentary
 * network drop — threw the user out of a session that was never in question and made them log in
 * again. Only a server that actively refused the credentials ends a session.
 *
 * The default is to KEEP the session, and it needs positive proof to do anything: a network failure
 * arrives here as a plain Error, so "not an ApiError" has to mean "I don't know", not "log them out".
 * Keeping a dead token costs nothing — the next request gets a 401, the refresh runs, and THAT path
 * ends the session properly if the server really refuses it. The mistake does not reverse: cleared
 * tokens are gone, and the user is on the login screen with a valid account.
 */
function endSessionOnlyIfRejected(error: unknown, forget: () => void) {
  if (!(error instanceof ApiError) || !error.isAuthRejection) return
  clearTokens()
  forget()
}

interface AuthContextType {
  user: User | null
  isLoading: boolean
  isAuthenticated: boolean
  isAdmin: boolean
  isSuperAdmin: boolean
  login: (email: string, password: string) => Promise<User>
  loginWithTokens: (tokens: AuthTokens) => Promise<User>
  logout: () => void
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const { i18n } = useTranslation()
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(() => hasTokens())

  const i18nRef = useRef(i18n)
  useEffect(() => { i18nRef.current = i18n })

  const syncLanguage = useCallback((preferredLanguage: string) => {
    if (preferredLanguage && preferredLanguage !== i18nRef.current.language) {
      i18nRef.current.changeLanguage(preferredLanguage)
    }
  }, [])

  /** Reload on demand (exposed as refreshUser) — shows the spinner, unlike the mount load below. */
  const fetchUser = useCallback(async () => {
    if (!hasTokens()) {
      setUser(null)
      setIsLoading(false)
      return
    }
    try {
      setIsLoading(true)
      const currentUser = await authApi.getCurrentUser()
      setUser(currentUser)
      syncLanguage(currentUser.preferredLanguage)
    } catch (error) {
      endSessionOnlyIfRejected(error, () => setUser(null))
    } finally {
      setIsLoading(false)
    }
  }, [syncLanguage])

  /**
   * The initial load. It repeats the body of fetchUser above rather than calling it, and that is not
   * an oversight: `react-hooks/set-state-in-effect` follows calls out of an effect, so any shared
   * helper that eventually sets state trips it — awaiting first does not help, because the rule is
   * static. Written as a promise chain, the state changes sit in callbacks where they belong and the
   * cascading render the rule warns about does not happen. If you unify these two, run `npm run
   * lint` before believing it worked.
   */
  useEffect(() => {
    if (!hasTokens()) return
    authApi.getCurrentUser()
      .then(currentUser => {
        setUser(currentUser)
        syncLanguage(currentUser.preferredLanguage)
      })
      .catch(error => {
        endSessionOnlyIfRejected(error, () => setUser(null))
      })
      .finally(() => setIsLoading(false))
  }, [syncLanguage])

  useEffect(() => {
    const handler = () => {
      clearTokens()
      setUser(null)
      queryClient.clear()
      showToast(i18nRef.current.t('sessionExpired', { ns: 'errors' }), 'error')
    }
    window.addEventListener('auth:session-expired', handler)
    return () => window.removeEventListener('auth:session-expired', handler)
  }, [queryClient, showToast])

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await apiLogin({ email, password })
    saveTokens(tokens)
    const currentUser = await authApi.getCurrentUser()
    setUser(currentUser)
    syncLanguage(currentUser.preferredLanguage)
    showToast(i18nRef.current.t('loggedIn', { ns: 'auth' }))
    return currentUser
  }, [syncLanguage, showToast])

  const loginWithTokens = useCallback(async (tokens: AuthTokens) => {
    saveTokens(tokens)
    const currentUser = await authApi.getCurrentUser()
    setUser(currentUser)
    syncLanguage(currentUser.preferredLanguage)
    showToast(i18nRef.current.t('loggedIn', { ns: 'auth' }))
    return currentUser
  }, [syncLanguage, showToast])

  const logout = useCallback(() => {
    authApi.logout()
    setUser(null)
    queryClient.clear()
    showToast(i18nRef.current.t('loggedOut', { ns: 'auth' }))
  }, [queryClient, showToast])

  const value = useMemo<AuthContextType>(() => ({
    user,
    isLoading,
    isAuthenticated: !!user,
    isAdmin: user?.isAdmin ?? false,
    isSuperAdmin: user?.superAdmin ?? false,
    login,
    loginWithTokens,
    logout,
    refreshUser: fetchUser,
  }), [user, isLoading, login, loginWithTokens, logout, fetchUser])

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
