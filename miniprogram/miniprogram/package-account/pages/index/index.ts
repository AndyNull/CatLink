import { getAccount, updateAccount, type AppAccount, type AccountUpdate } from '../../../services/account'
import { logoutCurrentSession, refreshSessionWhenNeeded } from '../../../services/auth'
import { readSession, updateSessionAccount } from '../../../services/session'

type AccountPanel = 'profile' | 'account'

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未完成，请稍后重试'
}

function isUsername(value: string): boolean {
  return /^[A-Za-z0-9]{6,18}$/.test(value)
}

function accountData(account: AppAccount) {
  return {
    email: account.email,
    emailVerified: account.emailVerified,
    nickname: account.nickname || account.displayName,
    username: account.username,
    usernameEditable: account.usernameEditable,
    profileTitle: account.profileTitle,
    original: account,
  }
}

Page({
  data: {
    activePanel: 'profile' as AccountPanel,
    loading: false,
    email: '',
    emailVerified: false,
    nickname: '',
    username: '',
    usernameEditable: false,
    profileTitle: '',
    original: null as AppAccount | null,
  },

  onLoad(query: Record<string, string>) {
    if (query.panel === 'account' || query.panel === 'privacy') this.setData({ activePanel: 'account' })
  },

  onShow() {
    this.loadAccount()
  },

  async loadAccount() {
    const cached = readSession()
    if (!cached) {
      wx.redirectTo({ url: '/package-account/pages/auth/index' })
      return
    }

    this.setData({ loading: true, ...accountData(cached.account) })
    try {
      const session = await refreshSessionWhenNeeded()
      if (!session) return
      const account = await getAccount(session.accessToken)
      updateSessionAccount(account)
      this.setData(accountData(account))
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  selectPanel(event: WechatMiniprogram.TouchEvent) {
    const activePanel = event.currentTarget.dataset.panel as AccountPanel
    if (activePanel === 'profile' || activePanel === 'account') this.setData({ activePanel })
  },

  updateTextField(event: WechatMiniprogram.Input) {
    const value = event.detail.value
    const field = event.currentTarget.dataset.field
    if (field === 'nickname') this.setData({ nickname: value })
    if (field === 'username') this.setData({ username: value })
    if (field === 'profileTitle') this.setData({ profileTitle: value })
  },

  async saveProfile() {
    const original = this.data.original
    if (!original || !readSession()) return

    const nickname = this.data.nickname.trim()
    const username = this.data.username.trim()
    const profileTitle = this.data.profileTitle.trim()
    if (!nickname) {
      wx.showToast({ title: '请填写昵称', icon: 'none' })
      return
    }
    if (this.data.usernameEditable && username && !isUsername(username)) {
      wx.showToast({ title: '用户名需为6-18位字母或数字', icon: 'none' })
      return
    }
    if (profileTitle.length > 80) {
      wx.showToast({ title: '用户称号不能超过80个字符', icon: 'none' })
      return
    }

    const update: AccountUpdate = {}
    if (nickname !== original.nickname) update.nickname = nickname
    if (this.data.usernameEditable && username !== original.username && username) update.username = username
    if (profileTitle !== original.profileTitle && profileTitle) update.profileTitle = profileTitle
    if (Object.keys(update).length === 0) {
      wx.showToast({ title: '没有需要保存的修改', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    try {
      const session = await refreshSessionWhenNeeded()
      if (!session) return
      const account = await updateAccount(session.accessToken, update)
      updateSessionAccount(account)
      this.setData(accountData(account))
      wx.showToast({ title: '资料已保存', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  logout() {
    wx.showModal({
      title: '退出当前账号',
      content: '退出后可随时重新登录。',
      success: async ({ confirm }) => {
        if (!confirm) return
        try {
          await logoutCurrentSession()
        } catch (error) {
          wx.showToast({ title: errorMessage(error), icon: 'none' })
        } finally {
          wx.switchTab({ url: '/pages/profile/index' })
        }
      },
    })
  },
})
