import { getNotifications, markNotificationsRead, type AppNotification } from '../../../services/account'
import { refreshSessionWhenNeeded } from '../../../services/auth'

interface NotificationView extends AppNotification {
  timeLabel: string
  kindLabel: string
  read: boolean
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '通知加载失败，请稍后重试'
}

function notificationKind(kind: string): string {
  const labels: Record<string, string> = {
    announcement: '公告',
    comment: '评论',
    reply: '回复',
    like: '点赞',
    comment_like: '点赞',
    follow: '关注',
    vault: '档案',
  }
  return labels[kind] || '提醒'
}

function notificationTime(value: number): string {
  const date = new Date(value)
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  const hour = `${date.getHours()}`.padStart(2, '0')
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

function notificationView(item: AppNotification): NotificationView {
  return {
    ...item,
    read: item.read || item.isRead,
    timeLabel: notificationTime(item.createdAt),
    kindLabel: notificationKind(item.kind),
  }
}

Page({
  data: {
    loading: false,
    needsLogin: false,
    errorText: '',
    items: [] as NotificationView[],
    hasUnread: false,
  },

  onShow() {
    this.loadNotifications()
  },

  async loadNotifications() {
    this.setData({ loading: true, errorText: '', needsLogin: false })
    try {
      const session = await refreshSessionWhenNeeded()
      if (!session) {
        this.setData({ needsLogin: true, items: [], hasUnread: false })
        return
      }
      const result = await getNotifications(session.accessToken)
      const items = result.items.map(notificationView)
      const hasUnread = items.some((item) => !item.read)
      wx.setStorageSync('catlink-notification-unread', hasUnread)
      this.setData({ needsLogin: false, items, hasUnread })
    } catch (error) {
      this.setData({ errorText: errorMessage(error) })
    } finally {
      this.setData({ loading: false })
    }
  },

  openLogin() {
    wx.navigateTo({ url: '/package-account/pages/auth/index?redirect=/package-account/pages/notifications/index' })
  },

  async markAllRead() {
    if (!this.data.hasUnread) return
    try {
      const session = await refreshSessionWhenNeeded()
      if (!session) return
      await markNotificationsRead(session.accessToken)
      const items = this.data.items.map((item) => ({ ...item, read: true, isRead: true }))
      wx.setStorageSync('catlink-notification-unread', false)
      this.setData({ items, hasUnread: false })
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
    }
  },

  async openNotification(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id as string
    const item = this.data.items.find((entry) => entry.id === id)
    if (!item || item.read) return

    try {
      const session = await refreshSessionWhenNeeded()
      if (!session) return
      await markNotificationsRead(session.accessToken, id)
      const items = this.data.items.map((entry) => entry.id === id ? { ...entry, read: true, isRead: true } : entry)
      const hasUnread = items.some((entry) => !entry.read)
      wx.setStorageSync('catlink-notification-unread', hasUnread)
      this.setData({ items, hasUnread })
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
    }
  },
})
