const sessionStorageKey = 'catlink:session:v1'
const deviceStorageKey = 'catlink:device:v1'

export interface AccountIdentity {
  id: string
  email: string
  emailVerified: boolean
  displayName: string
  nickname: string
  avatarUrl: string
  username: string
  usernameEditable: boolean
  usernameTemporary: boolean
  profileBackground: string
  profileTitle: string
  avatarFrame: string
}

export interface AppSession {
  accessToken: string
  refreshToken: string
  accessExpiresAt: number
  deviceId: string
  account: AccountIdentity
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isAccountIdentity(value: unknown): value is AccountIdentity {
  if (!isRecord(value)) return false
  return typeof value.id === 'string'
    && typeof value.email === 'string'
    && typeof value.displayName === 'string'
    && typeof value.nickname === 'string'
    && typeof value.avatarUrl === 'string'
    && typeof value.username === 'string'
    && typeof value.emailVerified === 'boolean'
    && typeof value.usernameEditable === 'boolean'
    && typeof value.usernameTemporary === 'boolean'
    && typeof value.profileBackground === 'string'
    && typeof value.profileTitle === 'string'
    && typeof value.avatarFrame === 'string'
}

function isSession(value: unknown): value is AppSession {
  if (!isRecord(value)) return false
  return typeof value.accessToken === 'string'
    && typeof value.refreshToken === 'string'
    && typeof value.accessExpiresAt === 'number'
    && typeof value.deviceId === 'string'
    && isAccountIdentity(value.account)
}

export function readSession(): AppSession | null {
  const value: unknown = wx.getStorageSync(sessionStorageKey)
  return isSession(value) ? value : null
}

export function saveSession(session: AppSession): void {
  wx.setStorageSync(sessionStorageKey, session)
}

export function clearSession(): void {
  wx.removeStorageSync(sessionStorageKey)
}

export function updateSessionAccount(account: AccountIdentity): void {
  const session = readSession()
  if (session) saveSession({ ...session, account })
}

export function hasUsableSession(): boolean {
  const session = readSession()
  return session !== null && session.accessToken.length > 0 && session.refreshToken.length > 0
}

export function sessionNeedsRefresh(session: AppSession): boolean {
  return session.accessExpiresAt <= Date.now() + 60_000
}

export function getDeviceIdentity(): { deviceName: string; deviceId: string } {
  const stored: unknown = wx.getStorageSync(deviceStorageKey)
  const deviceId = typeof stored === 'string' && stored.length > 0
    ? stored
    : `wx-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`

  if (stored !== deviceId) wx.setStorageSync(deviceStorageKey, deviceId)
  const system = wx.getSystemInfoSync()
  return { deviceName: system.model || '微信小程序', deviceId }
}
