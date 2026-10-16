import {
  readLocalAdopters,
  readLocalFollowUps,
  saveLocalAdopter,
  saveLocalFollowUp,
  updateLocalAdopterCat,
  updateLocalAdopterStage,
  type LocalAdopterDraft,
  type LocalAdopterStage,
  type LocalFollowUpDraft,
  type LocalFollowUpStatus,
} from '../../../services/adoption-drafts'
import { readLocalVaultDrafts, updateLocalVaultDraftStatus, type LocalVaultDraft } from '../../../services/vault-drafts'

type AdoptionTab = 'adopters' | 'followups'

interface VaultChoice {
  id: string
  nickname: string
  statusLabel: string
}

interface AdopterItem extends LocalAdopterDraft {
  initial: string
  stageLabel: string
  catName: string
}

interface FollowUpItem extends LocalFollowUpDraft {
  adopterName: string
  catName: string
  statusLabel: string
}

const today = formatDate(new Date())
const adopterStageOptions = [
  { value: 'candidate', label: '沟通中' },
  { value: 'adopted', label: '已领养' },
] as const
const followUpStatusOptions = [
  { value: 'well', label: '状态良好' },
  { value: 'attention', label: '需关注' },
] as const
const statusLabels: Record<LocalVaultDraft['status'], string> = {
  rescue: '救助中',
  treating: '治疗中',
  adoptable: '待领养',
  adopted: '已领养',
  released: '已放归',
  archived: '已归档',
}

function formatDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function plusDays(value: string, days: number): string {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + days)
  return formatDate(date)
}

function isAdopterStage(value: unknown): value is LocalAdopterStage {
  return value === 'candidate' || value === 'adopted'
}

function isFollowUpStatus(value: unknown): value is LocalFollowUpStatus {
  return value === 'well' || value === 'attention'
}

function buildVaultChoices(drafts: LocalVaultDraft[]): VaultChoice[] {
  return drafts.map((draft) => ({ id: draft.id, nickname: draft.nickname, statusLabel: statusLabels[draft.status] }))
}

function buildAdopterItems(adopters: LocalAdopterDraft[], vaultDrafts: VaultChoice[]): AdopterItem[] {
  const catNames = new Map(vaultDrafts.map((item) => [item.id, item.nickname]))
  return adopters.map((item) => ({
    ...item,
    initial: Array.from(item.name)[0] || '领',
    stageLabel: item.stage === 'adopted' ? '已领养' : '沟通中',
    catName: catNames.get(item.catId) || '档案待关联',
  }))
}

function buildFollowUpItems(followUps: LocalFollowUpDraft[], adopters: AdopterItem[], vaultDrafts: VaultChoice[]): FollowUpItem[] {
  const adopterNames = new Map(adopters.map((item) => [item.id, item.name]))
  const catNames = new Map(vaultDrafts.map((item) => [item.id, item.nickname]))
  return followUps.map((item) => ({
    ...item,
    adopterName: adopterNames.get(item.adopterId) || '已移除领养人',
    catName: catNames.get(item.catId) || '档案待关联',
    statusLabel: item.status === 'attention' ? '需关注' : '状态良好',
  }))
}

Page({
  data: {
    today,
    activeTab: 'adopters' as AdoptionTab,
    adopterStageOptions,
    followUpStatusOptions,
    vaultDrafts: [] as VaultChoice[],
    name: '',
    contact: '',
    selectedCatId: '',
    selectedCatName: '',
    adopterStage: 'candidate' as LocalAdopterStage,
    adopters: [] as AdopterItem[],
    followUps: [] as FollowUpItem[],
    selectedAdopterId: '',
    selectedAdopterName: '',
    followUpDate: today,
    nextFollowUpDate: plusDays(today, 30),
    followUpStatus: 'well' as LocalFollowUpStatus,
    followUpNote: '',
  },

  onLoad(query: Record<string, string>) {
    const data: Partial<{ activeTab: AdoptionTab; selectedCatId: string; selectedAdopterId: string }> = {}
    if (query.catId) data.selectedCatId = query.catId
    if (query.adopterId) {
      data.activeTab = 'followups'
      data.selectedAdopterId = query.adopterId
    }
    this.setData(data)
  },

  onShow() {
    this.loadRecords()
  },

  loadRecords() {
    const vaultDrafts = buildVaultChoices(readLocalVaultDrafts())
    const adopters = buildAdopterItems(readLocalAdopters(), vaultDrafts)
    const followUps = buildFollowUpItems(readLocalFollowUps(), adopters, vaultDrafts)
    const selectedCat = vaultDrafts.find((item) => item.id === this.data.selectedCatId)
    const selectedAdopter = adopters.find((item) => item.id === this.data.selectedAdopterId && item.stage === 'adopted')
    this.setData({
      vaultDrafts,
      adopters,
      followUps,
      selectedCatId: selectedCat?.id || '',
      selectedCatName: selectedCat?.nickname || '',
      selectedAdopterId: selectedAdopter?.id || '',
      selectedAdopterName: selectedAdopter?.name || '',
    })
  },

  selectTab(event: WechatMiniprogram.TouchEvent) {
    const activeTab = event.currentTarget.dataset.tab as AdoptionTab
    if (activeTab === 'adopters' || activeTab === 'followups') this.setData({ activeTab })
  },

  chooseAdopterCat() {
    if (!this.data.vaultDrafts.length) {
      wx.showToast({ title: '请先建立一份档案', icon: 'none' })
      return
    }
    wx.showActionSheet({
      itemList: this.data.vaultDrafts.map((item) => `${item.nickname} · ${item.statusLabel}`),
      success: ({ tapIndex }) => {
        const draft = this.data.vaultDrafts[tapIndex]
        if (draft) this.setData({ selectedCatId: draft.id, selectedCatName: draft.nickname })
      },
    })
  },

  updateAdopterField(event: WechatMiniprogram.Input) {
    const field = event.currentTarget.dataset.field
    const value = event.detail.value.slice(0, field === 'name' ? 30 : 60)
    if (field === 'name') this.setData({ name: value })
    if (field === 'contact') this.setData({ contact: value })
  },

  selectAdopterStage(event: WechatMiniprogram.TouchEvent) {
    const adopterStage = event.currentTarget.dataset.stage
    if (isAdopterStage(adopterStage)) this.setData({ adopterStage })
  },

  addAdopter() {
    const name = this.data.name.trim()
    if (!this.data.selectedCatId) {
      wx.showToast({ title: '请选择关联档案', icon: 'none' })
      return
    }
    if (!name) {
      wx.showToast({ title: '请填写领养人称呼', icon: 'none' })
      return
    }
    saveLocalAdopter({
      catId: this.data.selectedCatId,
      name,
      contact: this.data.contact.trim().slice(0, 60),
      stage: this.data.adopterStage,
    })
    if (this.data.adopterStage === 'adopted') updateLocalVaultDraftStatus(this.data.selectedCatId, 'adopted')
    this.setData({ name: '', contact: '', adopterStage: 'candidate' })
    this.loadRecords()
    wx.showToast({ title: '已添加领养人', icon: 'success' })
  },

  linkAdopterCat(adopter: AdopterItem) {
    if (!this.data.vaultDrafts.length) {
      wx.showToast({ title: '请先建立一份档案', icon: 'none' })
      return
    }
    wx.showActionSheet({
      itemList: this.data.vaultDrafts.map((item) => `${item.nickname} · ${item.statusLabel}`),
      success: ({ tapIndex }) => {
        const draft = this.data.vaultDrafts[tapIndex]
        if (!draft) return
        updateLocalAdopterCat(adopter.id, draft.id)
        this.loadRecords()
        wx.showToast({ title: '已关联档案', icon: 'success' })
      },
    })
  },

  updateAdopterStage(adopter: AdopterItem, stage: LocalAdopterStage) {
    if (stage === 'adopted' && !adopter.catId) {
      wx.showToast({ title: '请先关联档案', icon: 'none' })
      return
    }
    updateLocalAdopterStage(adopter.id, stage)
    if (stage === 'adopted') updateLocalVaultDraftStatus(adopter.catId, 'adopted')
    this.loadRecords()
    wx.showToast({ title: stage === 'adopted' ? '已标记为领养完成' : '已恢复为沟通中', icon: 'success' })
  },

  manageAdopter(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id
    if (typeof id !== 'string') return
    const adopter = this.data.adopters.find((item) => item.id === id)
    if (!adopter) return
    const actions: { value: 'link' | 'followup' | 'candidate' | 'adopted'; label: string }[] = []
    if (!adopter.catId) actions.push({ value: 'link', label: '关联档案' })
    if (adopter.stage === 'adopted') {
      actions.push({ value: 'followup', label: '登记回访' }, { value: 'candidate', label: '标记为沟通中' })
    } else {
      actions.push({ value: 'adopted', label: '标记为已领养' })
    }
    wx.showActionSheet({
      itemList: actions.map((item) => item.label),
      success: ({ tapIndex }) => {
        const action = actions[tapIndex]?.value
        if (action === 'link') this.linkAdopterCat(adopter)
        if (action === 'followup') this.setData({ activeTab: 'followups', selectedAdopterId: adopter.id, selectedAdopterName: adopter.name })
        if (action === 'candidate' || action === 'adopted') this.updateAdopterStage(adopter, action)
      },
    })
  },

  chooseFollowUpAdopter() {
    const adopted = this.data.adopters.filter((item) => item.stage === 'adopted' && item.catId)
    if (!adopted.length) {
      wx.showToast({ title: '请先完成一位领养人的关联', icon: 'none' })
      return
    }
    wx.showActionSheet({
      itemList: adopted.map((item) => `${item.name} · ${item.catName}`),
      success: ({ tapIndex }) => {
        const adopter = adopted[tapIndex]
        if (adopter) this.setData({ selectedAdopterId: adopter.id, selectedAdopterName: adopter.name })
      },
    })
  },

  updateFollowUpDate(event: WechatMiniprogram.PickerChange) {
    if (typeof event.detail.value === 'string') this.setData({ followUpDate: event.detail.value, nextFollowUpDate: plusDays(event.detail.value, 30) })
  },

  updateNextFollowUpDate(event: WechatMiniprogram.PickerChange) {
    if (typeof event.detail.value === 'string') this.setData({ nextFollowUpDate: event.detail.value })
  },

  selectFollowUpStatus(event: WechatMiniprogram.TouchEvent) {
    const followUpStatus = event.currentTarget.dataset.status
    if (isFollowUpStatus(followUpStatus)) this.setData({ followUpStatus })
  },

  updateFollowUpNote(event: WechatMiniprogram.Input) {
    this.setData({ followUpNote: event.detail.value.slice(0, 500) })
  },

  addFollowUp() {
    const adopter = this.data.adopters.find((item) => item.id === this.data.selectedAdopterId && item.stage === 'adopted' && item.catId)
    const note = this.data.followUpNote.trim()
    if (!adopter) {
      wx.showToast({ title: '请选择已领养的领养人', icon: 'none' })
      return
    }
    if (this.data.nextFollowUpDate < this.data.followUpDate) {
      wx.showToast({ title: '下次回访不能早于本次回访', icon: 'none' })
      return
    }
    if (!note) {
      wx.showToast({ title: '请填写回访记录', icon: 'none' })
      return
    }
    saveLocalFollowUp({
      adopterId: adopter.id,
      catId: adopter.catId,
      visitDate: this.data.followUpDate,
      nextVisitDate: this.data.nextFollowUpDate,
      status: this.data.followUpStatus,
      note,
    })
    this.setData({ followUpNote: '', followUpStatus: 'well' })
    this.loadRecords()
    wx.showToast({ title: '回访已记录', icon: 'success' })
  },
})
