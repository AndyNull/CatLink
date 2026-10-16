const { refreshSessionWhenNeeded } = require('../services/auth')
const { getNotifications } = require('../services/account')
const noticePositionKey = 'catlink:notice-position:v1'

Component({
  data: {
    selected: 0,
    hasUnread: false,
    noticeLeft: 0,
    noticeTop: 0,
    noticeDragging: false,
    noticeDragReady: false,
    tabs: [
      { pagePath: '/pages/home/index', text: '首页', icon: '/assets/icons/home.svg', activeIcon: '/assets/icons/home-active.svg' },
      { pagePath: '/pages/archive/index', text: '档案', icon: '/assets/icons/archive.svg', activeIcon: '/assets/icons/archive-active.svg' },
      { pagePath: '/pages/community/index', text: '社区', icon: '/assets/icons/community.svg', activeIcon: '/assets/icons/community-active.svg' },
      { pagePath: '/pages/ai/index', text: 'AI', icon: '/assets/icons/ai.svg', activeIcon: '/assets/icons/ai-active.svg' },
      { pagePath: '/pages/profile/index', text: '我的', icon: '/assets/icons/profile.svg', activeIcon: '/assets/icons/profile-active.svg' },
    ],
  },
  methods: {
    syncNoticePosition() {
      const globalPosition = getApp().globalData.noticePosition
      if (globalPosition) {
        this.setData(globalPosition)
        return
      }
      const stored = wx.getStorageSync(noticePositionKey)
      if (typeof stored?.left === 'number' && typeof stored?.top === 'number') {
        const position = { noticeLeft: stored.left, noticeTop: stored.top }
        getApp().globalData.noticePosition = position
        this.setData(position)
      }
    },
    initNoticePosition() {
      const info = wx.getSystemInfoSync()
      const scale = info.windowWidth / 750
      const stored = wx.getStorageSync(noticePositionKey)
      const left = typeof stored?.left === 'number' ? stored.left : 8
      const top = typeof stored?.top === 'number' ? stored.top : Math.max(120, info.windowHeight - 300 * scale)
      const position = { noticeLeft: Math.max(8, Math.min(info.windowWidth - 86 * scale, left)), noticeTop: Math.max(20, Math.min(info.windowHeight - 84 * scale, top)) }
      getApp().globalData.noticePosition = position
      this.setData(position)
    },
    noticeTouchStart(event) {
      const touch = event.touches[0]
      this.noticeStart = { x: touch.clientX, y: touch.clientY, left: this.data.noticeLeft, top: this.data.noticeTop }
      this.noticeLongPressTimer = setTimeout(() => this.setData({ noticeDragReady: true }), 360)
      this.setData({ noticeDragging: false, noticeDragReady: false })
    },
    noticeTouchMove(event) {
      const start = this.noticeStart
      if (!start || !this.data.noticeDragReady) return
      const touch = event.touches[0]
      const info = wx.getSystemInfoSync()
      const scale = info.windowWidth / 750
      const left = Math.max(8, Math.min(info.windowWidth - 84 * scale, start.left + touch.clientX - start.x))
      const top = Math.max(20, Math.min(info.windowHeight - 84 * scale, start.top + touch.clientY - start.y))
      if (Math.abs(touch.clientX - start.x) > 8 || Math.abs(touch.clientY - start.y) > 8) this.setData({ noticeDragging: true })
      this.setData({ noticeLeft: left, noticeTop: top })
      getApp().globalData.noticePosition = { noticeLeft: left, noticeTop: top }
      wx.setStorageSync(noticePositionKey, { left, top })
    },
    noticeTouchEnd() {
      if (this.noticeLongPressTimer) clearTimeout(this.noticeLongPressTimer)
      if (this.data.noticeDragReady) {
        const info = wx.getSystemInfoSync()
        const scale = info.windowWidth / 750
        const edge = this.data.noticeLeft + 42 * scale < info.windowWidth / 2 ? 10 : info.windowWidth - 86 * scale
        this.setData({ noticeLeft: edge })
        getApp().globalData.noticePosition = { noticeLeft: edge, noticeTop: this.data.noticeTop }
        wx.setStorageSync(noticePositionKey, { left: edge, top: this.data.noticeTop })
      }
      this.noticeStart = null
      this.setData({ noticeDragReady: false })
    },
    async refreshUnread() {
      const checkedAt = Number(wx.getStorageSync('catlink-notification-checked-at') || 0)
      this.setData({ hasUnread: wx.getStorageSync('catlink-notification-unread') === true })
      if (Date.now() - checkedAt < 30000) return
      try {
        const session = await refreshSessionWhenNeeded()
        if (!session) {
          this.setData({ hasUnread: false })
          return
        }
        const result = await getNotifications(session.accessToken)
        const hasUnread = result.items.some((item) => !(item.read || item.isRead))
        wx.setStorageSync('catlink-notification-unread', hasUnread)
        wx.setStorageSync('catlink-notification-checked-at', Date.now())
        this.setData({ hasUnread })
      } catch {
        this.setData({ hasUnread: wx.getStorageSync('catlink-notification-unread') === true })
      }
    },
    openNotifications() {
      if (this.data.noticeDragging) return
      wx.navigateTo({ url: '/package-account/pages/notifications/index' })
    },
    switchTab(event) {
      const index = Number(event.currentTarget.dataset.index)
      const tab = this.data.tabs[index]
      if (!tab || index === this.data.selected) return
      wx.switchTab({ url: tab.pagePath })
      this.setData({ selected: index })
    },
  },
  lifetimes: {
    ready() {
      this.initNoticePosition()
    },
  },
  pageLifetimes: {
    show() {
      this.syncNoticePosition()
      this.refreshUnread()
    },
  },
})
