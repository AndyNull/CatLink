import {
  getLocalVaultDraft,
  saveLocalVaultDraft,
  updateLocalVaultDraft,
  type LocalVaultDraftGender,
  type LocalVaultDraftStatus,
  type LocalVaultDraftSterilization,
} from '../../../services/vault-drafts'

const today = formatDate(new Date())
const maxAlbumCount = 9
const aiProfilePendingStorageKey = 'catlink:ai-profile-pending:v1'

const statusOptions = [
  { value: 'rescue', label: '救助中' },
  { value: 'treating', label: '治疗中' },
  { value: 'adoptable', label: '待领养' },
  { value: 'adopted', label: '已领养' },
  { value: 'released', label: '已放归' },
  { value: 'archived', label: '已归档' },
] as const

const genderOptions = [
  { value: 'male', label: '弟弟' },
  { value: 'female', label: '妹妹' },
  { value: 'unknown', label: '未知' },
] as const

const sterilizationOptions = [
  { value: 'unknown', label: '未知' },
  { value: 'sterilized', label: '已绝育' },
  { value: 'not-sterilized', label: '未绝育' },
] as const

function formatDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function saveMediaFile(tempFilePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().saveFile({
      tempFilePath,
      success: (result) => resolve(result.savedFilePath),
      fail: reject,
    })
  })
}

function chooseImages(count: number): Promise<string[]> {
  return new Promise((resolve, reject) => {
    wx.chooseMedia({
      count,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (result) => {
        Promise.all(result.tempFiles.map((file) => saveMediaFile(file.tempFilePath))).then(resolve, reject)
      },
      fail: reject,
    })
  })
}

function showMediaError(error: unknown): void {
  const message = typeof error === 'object' && error !== null && 'errMsg' in error
    ? String(error.errMsg)
    : String(error)
  if (message.toLowerCase().includes('cancel')) return

  wx.showToast({ title: '图片保存失败，请重试', icon: 'none' })
}

function toTags(value: string): string[] {
  return Array.from(new Set(value.split(/[，,、\s]+/).map((item) => item.trim()).filter(Boolean))).slice(0, 12)
}

function isStatus(value: unknown): value is LocalVaultDraftStatus {
  return statusOptions.some((option) => option.value === value)
}

function isGender(value: unknown): value is LocalVaultDraftGender {
  return genderOptions.some((option) => option.value === value)
}

function isSterilization(value: unknown): value is LocalVaultDraftSterilization {
  return sterilizationOptions.some((option) => option.value === value)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function profileText(profile: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = profile[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

function profileTags(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === 'string').map((item) => item.trim()).filter(Boolean)
  return typeof value === 'string' ? toTags(value) : []
}

function profileGender(value: unknown): LocalVaultDraftGender | null {
  if (typeof value !== 'string') return null
  const normalized = value.trim().toLowerCase()
  if (normalized === 'male' || normalized === '公' || normalized === '男' || normalized === '弟弟' || normalized === '雄') return 'male'
  if (normalized === 'female' || normalized === '母' || normalized === '女' || normalized === '妹妹' || normalized === '雌') return 'female'
  if (normalized === 'unknown' || normalized === '未知') return 'unknown'
  return null
}

function profileStatus(value: unknown): LocalVaultDraftStatus | null {
  if (isStatus(value)) return value
  if (value === '救助中') return 'rescue'
  if (value === '治疗中') return 'treating'
  if (value === '待领养') return 'adoptable'
  if (value === '已领养') return 'adopted'
  if (value === '已放归') return 'released'
  if (value === '已归档') return 'archived'
  return null
}

Page({
  data: {
    today,
    maxAlbumCount,
    statusOptions,
    genderOptions,
    sterilizationOptions,
    coverPath: '',
    albumPaths: [] as string[],
    nickname: '',
    discoveredDate: today,
    status: 'rescue' as LocalVaultDraftStatus,
    gender: 'unknown' as LocalVaultDraftGender,
    sterilization: 'unknown' as LocalVaultDraftSterilization,
    age: '',
    coat: '',
    weight: '',
    healthSummary: '',
    personality: '',
    tags: '',
    region: '',
    story: '',
    editId: '',
    saving: false,
  },

  onLoad(query: Record<string, string>) {
    if (!query.id) return

    const draft = getLocalVaultDraft(query.id)
    if (!draft) {
      wx.showToast({ title: '未找到这份档案', icon: 'none' })
      return
    }

    wx.setNavigationBarTitle({ title: '编辑档案' })
    this.setData({
      editId: draft.id,
      coverPath: draft.coverPath,
      albumPaths: draft.albumPaths,
      nickname: draft.nickname,
      discoveredDate: draft.discoveredDate,
      status: draft.status,
      gender: draft.gender,
      sterilization: draft.sterilization,
      age: draft.age,
      coat: draft.coat,
      weight: draft.weight,
      healthSummary: draft.healthSummary || '',
      personality: draft.personality,
      tags: draft.tags.join('、'),
      region: draft.region,
      story: draft.story,
    })
  },

  onShow() {
    if (!this.data.editId) this.applyAiProfile()
  },

  applyAiProfile() {
    const stored: unknown = wx.getStorageSync(aiProfilePendingStorageKey)
    if (!isRecord(stored)) return

    wx.removeStorageSync(aiProfilePendingStorageKey)
    const tags = profileTags(stored.personalityTags)
    const suggestedGender = profileGender(stored.gender)
    const suggestedStatus = profileStatus(stored.status || stored.statusCode || stored.catStatus)
    const nextTags = Array.from(new Set([...toTags(this.data.tags), ...tags])).join('、')
    this.setData({
      nickname: profileText(stored, 'nickname') || this.data.nickname,
      age: profileText(stored, 'age', 'ageBand') || this.data.age,
      coat: profileText(stored, 'coat') || this.data.coat,
      weight: profileText(stored, 'weight') || this.data.weight,
      healthSummary: profileText(stored, 'healthSummary') || this.data.healthSummary,
      personality: profileText(stored, 'personality') || tags.join('、') || this.data.personality,
      tags: nextTags,
      story: profileText(stored, 'story', 'rescueStory') || this.data.story,
      gender: suggestedGender || this.data.gender,
      status: suggestedStatus || this.data.status,
    })
    wx.showToast({ title: '已填入 AI 建议', icon: 'success' })
  },

  chooseCover() {
    chooseImages(1)
      .then((paths) => {
        if (paths[0]) this.setData({ coverPath: paths[0] })
      })
      .catch(showMediaError)
  },

  chooseAlbum() {
    const remaining = maxAlbumCount - this.data.albumPaths.length
    if (remaining <= 0) {
      wx.showToast({ title: `相册最多 ${maxAlbumCount} 张`, icon: 'none' })
      return
    }

    chooseImages(remaining)
      .then((paths) => {
        const albumPaths = Array.from(new Set([...this.data.albumPaths, ...paths])).slice(0, maxAlbumCount)
        this.setData({
          albumPaths,
          coverPath: this.data.coverPath || paths[0] || '',
        })
      })
      .catch(showMediaError)
  },

  removeCover() {
    this.setData({ coverPath: '' })
  },

  removeAlbumImage(event: WechatMiniprogram.TouchEvent) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index) || index < 0 || index >= this.data.albumPaths.length) return

    const removedPath = this.data.albumPaths[index]
    const albumPaths = this.data.albumPaths.filter((_, itemIndex) => itemIndex !== index)
    this.setData({
      albumPaths,
      coverPath: this.data.coverPath === removedPath ? albumPaths[0] || '' : this.data.coverPath,
    })
  },

  useAlbumAsCover(event: WechatMiniprogram.TouchEvent) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index) || index < 0 || index >= this.data.albumPaths.length) return

    this.setData({ coverPath: this.data.albumPaths[index] })
  },

  updateTextField(event: WechatMiniprogram.Input) {
    const value = event.detail.value
    switch (event.currentTarget.dataset.field) {
      case 'nickname': this.setData({ nickname: value }); break
      case 'age': this.setData({ age: value }); break
      case 'coat': this.setData({ coat: value }); break
      case 'weight': this.setData({ weight: value }); break
      case 'healthSummary': this.setData({ healthSummary: value }); break
      case 'personality': this.setData({ personality: value }); break
      case 'tags': this.setData({ tags: value }); break
      case 'region': this.setData({ region: value }); break
      case 'story': this.setData({ story: value }); break
    }
  },

  updateDiscoveredDate(event: WechatMiniprogram.PickerChange) {
    if (typeof event.detail.value === 'string') this.setData({ discoveredDate: event.detail.value })
  },

  selectStatus(event: WechatMiniprogram.TouchEvent) {
    const status = event.currentTarget.dataset.status
    if (isStatus(status)) this.setData({ status })
  },

  selectGender(event: WechatMiniprogram.TouchEvent) {
    const gender = event.currentTarget.dataset.gender
    if (isGender(gender)) this.setData({ gender })
  },

  selectSterilization(event: WechatMiniprogram.TouchEvent) {
    const sterilization = event.currentTarget.dataset.sterilization
    if (isSterilization(sterilization)) this.setData({ sterilization })
  },

  saveDraft() {
    const nickname = this.data.nickname.trim()
    if (!this.data.coverPath) {
      wx.showToast({ title: '请先添加封面照片', icon: 'none' })
      return
    }
    if (!nickname) {
      wx.showToast({ title: '请填写猫咪昵称', icon: 'none' })
      return
    }
    if (this.data.saving) return

    this.setData({ saving: true })
    try {
      const input = {
        coverPath: this.data.coverPath,
        albumPaths: this.data.albumPaths,
        nickname,
        discoveredDate: this.data.discoveredDate,
        status: this.data.status,
        gender: this.data.gender,
        sterilization: this.data.sterilization,
        age: this.data.age.trim(),
        coat: this.data.coat.trim(),
        weight: this.data.weight.trim(),
        healthSummary: this.data.healthSummary.trim(),
        personality: this.data.personality.trim(),
        tags: toTags(this.data.tags),
        region: this.data.region.trim(),
        story: this.data.story.trim(),
        isPublic: false,
      }
      const draft = this.data.editId
        ? updateLocalVaultDraft(this.data.editId, input)
        : saveLocalVaultDraft(input)
      if (!draft) {
        this.setData({ saving: false })
        wx.showToast({ title: '未找到这份档案', icon: 'none' })
        return
      }
      wx.showToast({ title: '档案已保存', icon: 'success' })
      if (this.data.editId) {
        wx.redirectTo({ url: `/package-vault/pages/detail/index?id=${encodeURIComponent(draft.id)}` })
        return
      }
      wx.switchTab({ url: '/pages/archive/index' })
    } catch {
      this.setData({ saving: false })
      wx.showToast({ title: '档案保存失败，请重试', icon: 'none' })
    }
  },
})
