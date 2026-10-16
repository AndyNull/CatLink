import { request } from './request'
import {
  clearSession,
  getDeviceIdentity,
  readSession,
  saveSession,
  sessionNeedsRefresh,
  type AccountIdentity,
  type AppSession,
} from './session'

export interface AuthPolicy {
  captchaRequired: boolean
  captchaLength: number
  captchaWidth: number
  captchaHeight: number
  emailReady: boolean
}

export interface CaptchaChallenge {
  captchaId: string
  imageBase64: string
  length: number
  expiresInSeconds: number
}

export interface AuthTokens extends AppSession {}

export interface RegisterResult {
  account: AccountIdentity
  verificationSent: boolean
}

export function getAuthPolicy(): Promise<AuthPolicy> {
  return request({ path: '/api/v1/auth/policy' })
}

export function requestCaptcha(captchaId = ''): Promise<CaptchaChallenge> {
  return request({ path: '/api/v1/auth/captcha', method: 'POST', data: { captchaId } })
}

export async function loginWithEmail(input: {
  email: string
  password: string
  captchaId: string
  captcha: string
}): Promise<AppSession> {
  const device = getDeviceIdentity()
  const session = await request<AuthTokens>({
    path: '/api/v1/auth/login',
    method: 'POST',
    data: { ...input, ...device },
  })
  saveSession(session)
  return session
}

export async function loginWithWechat(): Promise<AppSession> {
  const code = await new Promise<string>((resolve, reject) => {
    wx.login({
      success(result) {
        if (result.code) resolve(result.code)
        else reject(new Error('微信登录凭证获取失败'))
      },
      fail: reject,
    })
  })
  const session = await request<AuthTokens>({
    path: '/api/v1/auth/wechat/login',
    method: 'POST',
    data: { code, ...getDeviceIdentity() },
  })
  saveSession(session)
  return session
}

export function registerWithEmail(email: string, password: string): Promise<RegisterResult> {
  return request({ path: '/api/v1/auth/register', method: 'POST', data: { email, password } })
}

export async function verifyEmail(email: string, code: string): Promise<AppSession> {
  const device = getDeviceIdentity()
  const session = await request<AuthTokens>({
    path: '/api/v1/auth/email/verify',
    method: 'POST',
    data: { email, code, ...device },
  })
  saveSession(session)
  return session
}

export function resendVerification(email: string): Promise<{ accepted: boolean }> {
  return request({ path: '/api/v1/auth/email/resend', method: 'POST', data: { email } })
}

export async function refreshSession(): Promise<AppSession | null> {
  const current = readSession()
  if (!current) return null

  const session = await request<AuthTokens>({
    path: '/api/v1/auth/refresh',
    method: 'POST',
    data: { refreshToken: current.refreshToken },
  })
  saveSession(session)
  return session
}

export async function refreshSessionWhenNeeded(): Promise<AppSession | null> {
  const session = readSession()
  if (!session || !sessionNeedsRefresh(session)) return session
  return refreshSession()
}

export async function logoutCurrentSession(): Promise<void> {
  const session = readSession()
  if (!session) return
  try {
    await request({ path: '/api/v1/auth/logout', method: 'POST', token: session.accessToken })
  } finally {
    clearSession()
  }
}
