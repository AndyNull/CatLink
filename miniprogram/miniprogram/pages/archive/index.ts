import {
  moveLocalVaultDraftToTrash,
  readLocalVaultDrafts,
  type LocalVaultDraft,
  type LocalVaultDraftStatus,
} from '../../services/vault-drafts'

type ArchiveFilter = 'all' | LocalVaultDraftStatus

interface ArchiveDraft extends LocalVaultDraft {
  genderLabel: string
  statusLabel: string
}

const primaryFilters: { label: string; value: ArchiveFilter }[] = [
  { label: '全部', value: 'all' },
  { label: '救助中', value: 'rescue' },
  { label: '治疗中', value: 'treating' },
  { label: '待领养', value: 'adoptable' },
  { label: '已领养', value: 'adopted' },
]

const extraFilters: { label: string; value: ArchiveFilter }[] = [
  { label: '已放归', value: 'released' },
  { label: '已归档', value: 'archived' },
]

const statusLabels: Record<LocalVaultDraftStatus, string> = {
  rescue: '救助中',
  treating: '治疗中',
  adoptable: '待领养',
  adopted: '已领养',
  released: '已放归',
  archived: '已归档',
}

function matchesQuery(draft: LocalVaultDraft, query: string): boolean {
  if (!query) return true

  return [draft.nickname, draft.coat, draft.gender, draft.personality, draft.region, ...draft.tags]
    .join(' ')
    .toLowerCase()
    .includes(query.toLowerCase())
}

function presentDraft(draft: LocalVaultDraft): ArchiveDraft {
  return {
    ...draft,
    genderLabel: draft.gender === 'male' ? '弟弟' : draft.gender === 'female' ? '妹妹' : '性别未知',
    statusLabel: statusLabels[draft.status],
  }
}

function filterDrafts(drafts: LocalVaultDraft[], activeFilter: ArchiveFilter, query: string): ArchiveDraft[] {
  return drafts
    .filter((draft) => (activeFilter === 'all' || draft.status === activeFilter) && matchesQuery(draft, query.trim()))
    .map(presentDraft)
}

Page({
  data: {
    primaryFilters,
    extraFilters,
    activeFilter: 'all' as ArchiveFilter,
    query: '',
    drafts: [] as LocalVaultDraft[],
    visibleDrafts: [] as ArchiveDraft[],
    hasDrafts: false,
  },

  onShow() {
    this.getTabBar()?.setData({ selected: 1, ...getApp<IAppOption>().globalData.noticePosition })
    this.loadDrafts()
  },

  loadDrafts() {
    const drafts = readLocalVaultDrafts()
    this.setData({
      drafts,
      hasDrafts: drafts.length > 0,
      visibleDrafts: filterDrafts(drafts, this.data.activeFilter, this.data.query),
    })
  },

  selectFilter(event: WechatMiniprogram.TouchEvent) {
    const activeFilter = event.currentTarget.dataset.filter as ArchiveFilter
    if (!['all', 'rescue', 'treating', 'adoptable', 'adopted', 'released', 'archived'].includes(activeFilter)) return

    this.setData({
      activeFilter,
      visibleDrafts: filterDrafts(this.data.drafts, activeFilter, this.data.query),
    })
  },

  openMoreFilters() {
    wx.showActionSheet({
      itemList: extraFilters.map((item) => item.label),
      success: ({ tapIndex }) => {
        const activeFilter = extraFilters[tapIndex]?.value
        if (!activeFilter) return

        this.setData({
          activeFilter,
          visibleDrafts: filterDrafts(this.data.drafts, activeFilter, this.data.query),
        })
      },
    })
  },

  updateQuery(event: WechatMiniprogram.Input) {
    const query = event.detail.value
    this.setData({
      query,
      visibleDrafts: filterDrafts(this.data.drafts, this.data.activeFilter, query),
    })
  },

  openDraft(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id
    if (typeof id === 'string') wx.navigateTo({ url: `/package-vault/pages/detail/index?id=${encodeURIComponent(id)}` })
  },

  archiveDraft(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id
    if (typeof id !== 'string') return

    wx.showModal({
      title: '移入回收站',
      content: '档案会保留在回收站，可随时恢复。',
      confirmColor: '#8b4a5d',
      success: ({ confirm }) => {
        if (!confirm) return
        moveLocalVaultDraftToTrash(id)
        this.loadDrafts()
      },
    })
  },
})
