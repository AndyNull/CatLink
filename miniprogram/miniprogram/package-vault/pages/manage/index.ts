import {
  moveLocalVaultDraftToTrash,
  readLocalVaultDrafts,
  readLocalVaultTrash,
  removeLocalVaultDraftForever,
  restoreLocalVaultDraft,
  type LocalVaultDraft,
  type LocalVaultTrashItem,
} from '../../../services/vault-drafts'

type ManageTab = 'vault' | 'trash'

const statusLabels: Record<LocalVaultDraft['status'], string> = {
  rescue: '救助中',
  treating: '治疗中',
  adoptable: '待领养',
  adopted: '已领养',
  released: '已放归',
  archived: '已归档',
}

function formatDate(timestamp: number): string {
  const date = new Date(timestamp)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

interface ManagedTrashItem extends LocalVaultTrashItem {
  id: string
  deletedLabel: string
  statusLabel: string
}

interface ManagedDraft extends LocalVaultDraft {
  statusLabel: string
}

Page({
  data: {
    activeTab: 'vault' as ManageTab,
    drafts: [] as ManagedDraft[],
    trash: [] as ManagedTrashItem[],
  },

  onLoad(query: Record<string, string>) {
    if (query.tab === 'trash') this.setData({ activeTab: 'trash' })
  },

  onShow() {
    this.loadData()
  },

  loadData() {
    this.setData({
      drafts: readLocalVaultDrafts().map((draft) => ({ ...draft, statusLabel: statusLabels[draft.status] })),
      trash: readLocalVaultTrash().map((item) => ({
        ...item,
        id: item.draft.id,
        deletedLabel: formatDate(item.deletedAt),
        statusLabel: statusLabels[item.draft.status],
      })),
    })
  },

  selectTab(event: WechatMiniprogram.TouchEvent) {
    const activeTab = event.currentTarget.dataset.tab as ManageTab
    if (activeTab === 'vault' || activeTab === 'trash') this.setData({ activeTab })
  },

  goNew() {
    wx.navigateTo({ url: '/package-vault/pages/index/index' })
  },

  openDraft(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id
    if (typeof id === 'string') wx.navigateTo({ url: `/package-vault/pages/detail/index?id=${encodeURIComponent(id)}` })
  },

  restore(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id
    if (typeof id !== 'string') return
    restoreLocalVaultDraft(id)
    this.loadData()
    wx.showToast({ title: '已恢复档案', icon: 'success' })
  },

  moveToTrash(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id
    if (typeof id !== 'string') return

    wx.showModal({
      title: '移入回收站',
      content: '可在回收站内恢复这份档案。',
      confirmColor: '#a74a50',
      success: ({ confirm }) => {
        if (!confirm) return
        moveLocalVaultDraftToTrash(id)
        this.loadData()
        wx.showToast({ title: '已移入回收站', icon: 'success' })
      },
    })
  },

  removeForever(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id
    if (typeof id !== 'string') return

    wx.showModal({
      title: '彻底删除',
      content: '彻底删除后无法恢复。',
      confirmColor: '#a74a50',
      success: ({ confirm }) => {
        if (!confirm) return
        removeLocalVaultDraftForever(id)
        this.loadData()
        wx.showToast({ title: '已彻底删除', icon: 'success' })
      },
    })
  },
})
