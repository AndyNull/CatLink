import { request } from './request'
import type { AccountIdentity } from './session'

export type AppAccount = AccountIdentity

export interface AccountUpdate {
  nickname?: string
  username?: string
  profileTitle?: string
  profileBackground?: string
  avatarFrame?: string
}

export interface AppNotification {
  id: string
  kind: string
  title: string
  body: string
  refType: string
  refId: string
  read: boolean
  isRead: boolean
  createdAt: number
}

export interface NotificationList {
  items: AppNotification[]
  cursor: string
}

export function getAccount(token: string): Promise<AppAccount> {
  return request({ path: '/api/v1/account', token })
}

export function updateAccount(token: string, data: AccountUpdate): Promise<AppAccount> {
  return request({ path: '/api/v1/account', method: 'PATCH', data, token })
}

export function getNotifications(token: string): Promise<NotificationList> {
  return request({ path: '/api/v1/account/notifications', token })
}

export function markNotificationsRead(token: string, id = ''): Promise<{ ok: boolean }> {
  return request({ path: '/api/v1/account/notifications/read', method: 'POST', data: { id }, token })
}
