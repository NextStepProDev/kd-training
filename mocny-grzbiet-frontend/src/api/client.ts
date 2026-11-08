import i18n from '../i18n'
import type { User } from '../types'
import {
  getAccessToken,
  getRefreshToken,
  isAccessTokenExpired,
  saveTokens,
  clearTokens,
} from '../utils/tokenStorage'
import { refreshTokens } from './auth'
import { ApiError, parseRetryAfter } from '../utils/errors'

const API_BASE = '/api'

/**
 * Why a refresh failed, because the answer decides whether the user stays logged in.
 *
 * A bare `string | null` could not carry it: the caller saw "no token" and had to guess between
 * "the server refused us" and "I could not ask". `transient` keeps the tokens — the request fails,
 * the session does not.
 */
type RefreshOutcome =
  | { token: string; reason?: undefined; status?: undefined }
  | { token: null; reason: 'rejected' | 'transient'; status?: number }

let refreshPromise: Promise<RefreshOutcome> | null = null

async function ensureValidToken(): Promise<string | null> {
  const accessToken = getAccessToken()
  if (!accessToken) return null

  if (!isAccessTokenExpired()) return accessToken

  return (await refreshOnce()).token
}

/**
 * Refresh, joining an attempt already in flight instead of starting a second one.
 *
 * Used both when the local clock says the token expired and when the server answers 401 on a token
 * we still believed valid (clock skew, or a token rotated away by another tab). The 401 path used to
 * call `doRefresh()` directly and so skipped this dedupe: a page firing eight requests at once fired
 * eight refreshes, all into the `auth` bucket the backend rations at 15/min. Nobody was logged out by
 * it — the backend keeps a grace window after rotation — but it was the app manufacturing the 429
 * that then did log people out.
 */
async function refreshOnce(): Promise<RefreshOutcome> {
  if (refreshPromise) return refreshPromise

  refreshPromise = doRefresh()
  try {
    return await refreshPromise
  } finally {
    refreshPromise = null
  }
}

async function doRefresh(): Promise<RefreshOutcome> {
  const refresh = getRefreshToken()
  if (!refresh) {
    clearTokens()
    return { token: null, reason: 'rejected' }
  }

  try {
    const tokens = await refreshTokens(refresh)
    saveTokens(tokens)
    return { token: tokens.accessToken }
  } catch (error) {
    // Only a server that actively refused the refresh token means the session is over. A 429 from
    // the limiter, a 502 mid-deploy or a dead network are transient, and wiping the tokens for one
    // of those is exactly the reported bug: "too many requests, and then it logged me out" of an
    // account that was perfectly valid. A non-ApiError here is the network throw from authFetch —
    // also transient, also not proof of anything.
    if (!(error instanceof ApiError) || !error.isAuthRejection) {
      return {
        token: null,
        reason: 'transient',
        status: error instanceof ApiError ? error.status : undefined,
      }
    }
    clearTokens()
    window.dispatchEvent(new CustomEvent('auth:session-expired'))
    return { token: null, reason: 'rejected' }
  }
}

/**
 * `blob` is for responses that are not JSON — currently the training comment photos, which cannot
 * be an `<img src>` because that carries no Authorization header and these files are health data
 * behind an authenticated endpoint.
 */
type FetchOptions = RequestInit & { responseType?: 'json' | 'blob' }

export async function fetchApi<T>(
  endpoint: string,
  options?: FetchOptions
): Promise<T> {
  const token = await ensureValidToken()

  const headers: Record<string, string> = {
    'Accept-Language': i18n.language,
    ...(options?.headers as Record<string, string>),
  }

  if (!(options?.body instanceof FormData)) {
    headers['Content-Type'] = headers['Content-Type'] ?? 'application/json'
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  // Only reads may be replayed. A write that timed out or came back 502 may well have been
  // applied — the request reached the server and the answer was lost on the way back — so sending
  // it again creates a second training, a second comment, a second copy. The endpoints that can
  // detect a repeat (an enrolment, a duplicate e-mail) already answer 409; the 1-on-1 calendar
  // deliberately cannot, because two identical sessions on one day is a legitimate plan.
  const method = (options?.method ?? 'GET').toUpperCase()
  const replayable = method === 'GET' || method === 'HEAD'

  let response: Response

  const doFetch = async (): Promise<Response> => {
    const ctrl = new AbortController()
    const tid = setTimeout(() => ctrl.abort(), 30000)
    try {
      const res = await fetch(`${API_BASE}${endpoint}`, {
        ...options,
        headers,
        signal: ctrl.signal,
      })
      clearTimeout(tid)
      return res
    } catch (err) {
      clearTimeout(tid)
      throw err
    }
  }

  try {
    response = await doFetch()
  } catch {
    // A dropped connection says nothing about whether the server acted on the request, so a write
    // stops here and lets the caller decide — every form in the app surfaces the error next to its
    // save button rather than retrying behind the user's back.
    if (!replayable) {
      throw new Error(i18n.t('network', { ns: 'errors' }))
    }
    console.warn(`[API] ${method} ${endpoint} — network error, retrying in 1.5s…`)
    await new Promise(r => setTimeout(r, 1500))
    try {
      response = await doFetch()
    } catch {
      throw new Error(i18n.t('network', { ns: 'errors' }))
    }
  }

  if (response.status === 401 && token) {
    const outcome = await refreshOnce()
    if (outcome.token) {
      headers['Authorization'] = `Bearer ${outcome.token}`
      try {
        response = await doFetch()
      } catch {
        throw new Error(i18n.t('network', { ns: 'errors' }))
      }
    } else if (outcome.reason === 'transient') {
      // The session is intact — say so, instead of sending someone to the login screen over a rate
      // limit or a restarting backend. The status carried here is the one that actually failed (429,
      // 502, or 503 when the network never answered) and MUST NOT be the 401 of the original
      // request: this very object reaches the catch in AuthContext, where a 401 counts as proof that
      // the server refused us and ends the session.
      throw new ApiError(
        i18n.t('refreshUnavailable', { ns: 'errors' }),
        outcome.status ?? 503,
        'REFRESH_UNAVAILABLE',
      )
    } else {
      throw new ApiError(i18n.t('sessionExpired', { ns: 'errors' }), 401, null)
    }
  }

  // Gateway errors (502/503/504) almost always mean the backend is restarting — most
  // often a deploy, during which it can be unreachable for ~2 min while the JVM boots.
  // Retry a few times with backoff so a redeploy degrades to a brief "updating" blip
  // (combined with React Query's own retries) instead of a hard error screen. A genuine
  // 500 (app error) gets a single quick retry — no point waiting on a real bug.
  // Reads only, for the reason given above the first fetch: a 502 from a backend that is going
  // down can follow a request it already committed.
  if (replayable && response.status >= 500 && response.status < 600) {
    const isGateway = response.status === 502 || response.status === 503 || response.status === 504
    const backoffs = isGateway ? [1500, 3000, 5000] : [1000]
    for (const delay of backoffs) {
      console.warn(`[API] ${method} ${endpoint} → ${response.status}, retrying in ${delay}ms…`)
      await new Promise(r => setTimeout(r, delay))
      const retryToken = await ensureValidToken()
      if (retryToken) headers['Authorization'] = `Bearer ${retryToken}`
      try {
        response = await doFetch()
      } catch {
        continue // network error mid-retry — keep trying remaining backoffs
      }
      if (!(response.status >= 500 && response.status < 600)) break
    }
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    // ApiError for every failure, not just the ones the backend described: the two places that
    // decide whether a session is over need the status, and a bare Error gave them nothing to tell
    // "this token was refused" from "I was rate limited" or "the network blinked".
    const fail = (message: string) => new ApiError(
      message,
      response.status,
      body?.code ?? null,
      parseRetryAfter(response.headers.get('Retry-After')),
    )
    const serverMessage = body?.message
    if (serverMessage) {
      throw fail(serverMessage)
    }
    if (response.status === 429) {
      throw fail(i18n.t('rateLimited', { ns: 'errors' }))
    }
    if (response.status === 500) {
      throw fail(i18n.t('server', { ns: 'errors' }))
    }
    // 502/503/504 survived the retry loop above -> backend still down (likely a deploy).
    if (response.status === 502 || response.status === 503 || response.status === 504) {
      throw fail(i18n.t('serviceUpdating', { ns: 'errors' }))
    }
    if (response.status === 404) {
      throw fail(i18n.t('notFound', { ns: 'errors' }))
    }
    if (response.status === 403) {
      throw fail(i18n.t('forbidden', { ns: 'errors' }))
    }
    throw fail(i18n.t('generic', { status: response.status, ns: 'errors' }))
  }

  if (options?.responseType === 'blob') {
    return (await response.blob()) as T
  }

  // Some endpoints ack with an empty body (204, or a 201/200 "created/updated" with no payload).
  // Read as text first and only parse when there's something — calling .json() on an empty body
  // throws "Unexpected end of JSON input".
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/**
 * Fetches an authenticated binary resource by the absolute API path the server handed back.
 *
 * Both roles use this unchanged: the server already returns the URL that belongs to the caller, so
 * there is nothing here to branch on. The leading `/api` is stripped because `fetchApi` adds it.
 */
export const fetchApiBlob = (absolutePath: string) =>
  fetchApi<Blob>(absolutePath.replace(/^\/api/, ''), { responseType: 'blob' })

export const authApi = {
  getCurrentUser: () => fetchApi<User>('/user/me'),
  logout: () => {
    const refreshToken = getRefreshToken()
    clearTokens()
    if (refreshToken) {
      fetch(`${API_BASE}/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      }).catch(() => {})
    }
  },
  changePassword: (currentPassword: string, newPassword: string) =>
    fetchApi<void>('/user/me/password', {
      method: 'PUT',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
  deleteAccount: (password: string | null) =>
    fetchApi<void>('/user/me', {
      method: 'DELETE',
      body: JSON.stringify({ password }),
    }),
  updateMarketing: (enabled: boolean) =>
    fetchApi<User>('/user/me/marketing', {
      method: 'PUT',
      body: JSON.stringify({ enabled }),
    }),
  submitConsents: (acceptedPrivacy: boolean, acceptedTerms: boolean, acceptedMarketing: boolean) =>
    fetchApi<User>('/user/me/consents', {
      method: 'POST',
      body: JSON.stringify({ acceptedPrivacy, acceptedTerms, acceptedMarketing }),
    }),
  /** One-time explicit consent to training-data processing; /my-training 409s without it. */
  grantTrainingConsent: () =>
    fetchApi<User>('/user/me/training-consent', { method: 'POST' }),
  updateLanguage: (language: string) =>
    fetchApi<void>('/user/me/language', {
      method: 'PUT',
      body: JSON.stringify({ language }),
    }),
  updateProfile: (firstName: string, lastName: string, phone: string) =>
    fetchApi<User>('/user/me', {
      method: 'PUT',
      body: JSON.stringify({ firstName, lastName, phone }),
    }),
  uploadAvatar: (file: Blob) => {
    const formData = new FormData()
    formData.append('file', file, 'avatar.jpg')
    return fetchApi<User>('/user/me/avatar', { method: 'POST', body: formData })
  },
  deleteAvatar: () => fetchApi<User>('/user/me/avatar', { method: 'DELETE' }),
  logoutAllDevices: () =>
    fetchApi<void>('/user/me/logout-all', { method: 'POST' }),
}

export const userApi = authApi
