import i18n from '../i18n'
import { ApiError, parseRetryAfter } from '../utils/errors'

const API_BASE = '/api/auth'

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  phone: string
  preferredLanguage?: string
  acceptedPrivacy: boolean
  acceptedMarketing: boolean
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthTokensResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface MessageResponse {
  message: string
}

async function authFetch<T>(endpoint: string, body: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${endpoint}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept-Language': i18n.language,
      },
      body: JSON.stringify(body),
    })
  } catch {
    // Deliberately a bare Error: "I never reached the server" is the one case with no status to
    // carry, and doRefresh classifies exactly that absence as transient.
    throw new Error(i18n.t('network', { ns: 'errors' }))
  }

  if (!response.ok) {
    const error = await response.json().catch(() => null)
    // The status has to leave this function, because /refresh runs through it and doRefresh may only
    // end the session when the server actually refused the token — not when it was too busy to answer.
    const fail = (message: string) => new ApiError(
      message,
      response.status,
      error?.code ?? null,
      parseRetryAfter(response.headers.get('Retry-After')),
    )
    const serverMessage = error?.message
    if (serverMessage) {
      throw fail(serverMessage)
    }
    if (response.status === 429) {
      throw fail(i18n.t('rateLimited', { ns: 'errors' }))
    }
    if (response.status >= 500) {
      throw fail(i18n.t('authServer', { ns: 'errors' }))
    }
    throw fail(i18n.t('authGeneric', { status: response.status, ns: 'errors' }))
  }

  return response.json()
}

export function registerUser(data: RegisterRequest): Promise<MessageResponse> {
  return authFetch('/register', data)
}

export function loginUser(data: LoginRequest): Promise<AuthTokensResponse> {
  return authFetch('/login', data)
}

export function verifyEmail(token: string): Promise<MessageResponse> {
  return authFetch(`/verify-email?token=${encodeURIComponent(token)}`, {})
}

export function resendVerification(email: string): Promise<MessageResponse> {
  return authFetch('/resend-verification', { email })
}

export function forgotPassword(email: string): Promise<MessageResponse> {
  return authFetch('/forgot-password', { email })
}

export function resetPassword(token: string, newPassword: string): Promise<MessageResponse> {
  return authFetch('/reset-password', { token, newPassword })
}

export function refreshTokens(refreshToken: string): Promise<AuthTokensResponse> {
  return authFetch('/refresh', { refreshToken })
}
