import { API_BASE_URL } from '../../env'
import { getAccount } from '../../services/account'
import { refreshSessionWhenNeeded } from '../../services/auth'
import { readSession, updateSessionAccount, type AccountIdentity } from '../../services/session'
import { readLocalVaultDrafts } from '../../services/vault-drafts'

function accountView(account: AccountIdentity) {
  const avatarUrl = account.avatarUrl.startsWith('/') ? `${API_BASE_URL}${account.avatarUrl}` : account.avatarUrl
  return {
    signedIn: true,
    displayName: account.nickname || account.displayName || 'CatLink 用户',
    profileMeta: account.profileTitle || account.email,
    avatarUrl,
    profileAction: '编辑 ›',
  }
}

Page({
  data: {
    signedIn: false,
    displayName: '登录 CatLink',
    profileMeta: '同步个人资料、互动和消息。',
    avatarUrl: '',
    profileAction: '登录 ›',
    metrics: [
      { value: 0, label: '私密档案' },
      { value: 0, label: '救助进行中' },
      { value: 0, label: '已领养' },
    ],
  },

  onShow() {
    this.getTabBar()?.setData({ selected: 4, ...getApp<IAppOption>().globalData.noticePosition })
    this.loadProfile()
  },

  async loadProfile() {
    const drafts = readLocalVaultDrafts()
    this.setData({
      metrics: [
        { value: drafts.length, label: '私密档案' },
        { value: drafts.filter((draft) => draft.status === 'rescue' || draft.status === 'treating').length, label: '救助进行中' },
        { value: drafts.filter((draft) => draft.status === 'adopted').length, label: '已领养' },
      ],
    })

    const cached = readSession()
    if (!cached) {
      this.setData({
        signedIn: false,
        displayName: '登录 CatLink',
        profileMeta: '同步个人资料、互动和消息。',
        avatarUrl: '',
        profileAction: '登录 ›',
      })
      return
    }

    this.setData(accountView(cached.account))
    try {
      const session = await refreshSessionWhenNeeded()
      if (!session) return
      const account = await getAccount(session.accessToken)
      updateSessionAccount(account)
      this.setData(accountView(account))
    } catch {
      // 保留最近一次已验证资料，避免短暂网络波动遮蔽账号入口。
    }
  },

  goAccount() {
    const url = readSession()
      ? '/package-account/pages/index/index'
      : '/package-account/pages/auth/index'
    wx.navigateTo({ url })
  },
})
