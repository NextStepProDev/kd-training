import { describe, it, expect, beforeEach } from 'vitest'
import { saveTokens, getAccessToken, getRefreshToken, isAccessTokenExpired, clearTokens, hasTokens } from './tokenStorage'

describe('tokenStorage', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('should save and retrieve tokens', () => {
    saveTokens({ accessToken: 'access-123', refreshToken: 'refresh-456', expiresIn: 900 })

    expect(getAccessToken()).toBe('access-123')
    expect(getRefreshToken()).toBe('refresh-456')
  })

  it('should return null when no tokens saved', () => {
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('should detect non-expired token', () => {
    saveTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 })

    expect(isAccessTokenExpired()).toBe(false)
  })

  it('should detect expired token', () => {
    saveTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 0 })

    expect(isAccessTokenExpired()).toBe(true)
  })

  it('should return expired when no expiry saved', () => {
    expect(isAccessTokenExpired()).toBe(true)
  })

  it('should clear all tokens', () => {
    saveTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 })
    clearTokens()

    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
    expect(isAccessTokenExpired()).toBe(true)
  })

  it('should detect hasTokens correctly', () => {
    expect(hasTokens()).toBe(false)

    saveTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 })
    expect(hasTokens()).toBe(true)
  })
})
