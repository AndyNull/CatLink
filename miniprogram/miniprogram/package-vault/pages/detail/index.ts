import {
  getLocalVaultDraft,
  moveLocalVaultDraftToTrash,
  type LocalVaultDraft,
} from '../../../services/vault-drafts'

const statusLabels: Record<LocalVaultDraft['status'], string> = {
  rescue: '救助中',
  treating: '治疗中',
  adoptable: '待领养',
  adopted: '已领养',
  released: '已放归',
  archived: '已归档',
}

interface DetailDraft extends LocalVaultDraft {
  statusLabel: string
  genderLabel: string
  sterilizationLabel: string
  facts: { label: string; value: string }[]
  canManageAdoption: boolean
}

function presentDraft(draft: LocalVaultDraft): DetailDraft {
  const genderLabel = draft.gender === 'male' ? '弟弟' : draft.gender === 'female' ? '妹妹' : '未知'
  const sterilizationLabel = draft.sterilization === 'sterilized' ? '已绝育' : draft.sterilization === 'not-sterilized' ? '未绝育' : '未知'
  return {
    ...draft,
    statusLabel: statusLabels[draft.status],
    genderLabel,
    sterilizationLabel,
    canManageAdoption: draft.status === 'adoptable' || draft.status === 'adopted',
    facts: [
      { label: '发现日期', value: draft.discoveredDate },
      { label: '性别', value: genderLabel },
      { label: '绝育', value: sterilizationLabel },
      { label: '年龄', value: draft.age || '待补充' },
      { label: '体重', value: draft.weight || '待补充' },
      { label: '毛色', value: draft.coat || '待补充' },
    ],
  }
}

Page({
  data: {
    draft: null as DetailDraft | null,
  },

  onLoad(query: Record<string, string>) {
    if (query.id) this.loadDraft(query.id)
  },

  onShow() {
    if (this.data.draft) this.loadDraft(this.data.draft.id)
  },

  loadDraft(id: string) {
    const draft = getLocalVaultDraft(id)
    this.setData({ draft: draft ? presentDraft(draft) : null })
  },

  previewImage(event: WechatMiniprogram.TouchEvent) {
    const current = event.currentTarget.dataset.src
    const draft = this.data.draft
    if (typeof current !== 'string' || !draft) return

    wx.previewImage({ current, urls: [draft.coverPath, ...draft.albumPaths].filter(Boolean) })
  },

  editDraft() {
    const draft = this.data.draft
    if (draft) wx.navigateTo({ url: `/package-vault/pages/index/index?id=${encodeURIComponent(draft.id)}` })
  },

  openAdoption() {
    const draft = this.data.draft
    if (draft) wx.navigateTo({ url: `/package-adoption/pages/index/index?catId=${encodeURIComponent(draft.id)}` })
  },

  moveToTrash() {
    const draft = this.data.draft
    if (!draft) return

    wx.showModal({
      title: '移入回收站',
      content: '档案会保留在回收站，可随时恢复。',
      confirmColor: '#8b4a5d',
      success: ({ confirm }) => {
        if (!confirm) return
        moveLocalVaultDraftToTrash(draft.id)
        wx.redirectTo({ url: '/package-vault/pages/manage/index?tab=trash' })
      },
    })
  },
})
