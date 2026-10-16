import { API_BASE_URL } from '../../../env'
import type { PublicCatSummary, PublicCatTimelineItem } from '../../../models/api'
import { refreshSessionWhenNeeded } from '../../../services/auth'
import {
  followPublicCat,
  getPublicCat,
  getPublicCatTimeline,
  listFollowedPublicCats,
  unfollowPublicCat,
} from '../../../services/public-cats'

interface DetailRow {
  label: string
  value: string
}

interface TimelineView extends PublicCatTimelineItem {
  kindLabel: string
  dateLabel: string
}

interface CatDetailView extends PublicCatSummary {
  displayName: string
  statusLabel: string
  cover: string
  photos: string[]
  locationLabel: string
  profileRows: DetailRow[]
  storyText: string
  updatedLabel: string
}

const statusLabels: Record<string, string> = {
  rescue: '救助中',
  adoptable: '待领养',
  treating: '治疗中',
  adopted: '已领养',
  released: '已放归',
  archived: '已归档',
  observation: '观察中',
  foster: '寄养中',
  missing: '走失中',
}

const timelineLabels: Record<string, string> = {
  seen: '看见记录',
  fed: '投喂记录',
  normal: '近况更新',
  attention: '需要关注',
}

function resolveMedia(value?: string): string {
  if (!value || /^https?:\/\//.test(value) || value.startsWith('wxfile://')) return value || ''
  return `${API_BASE_URL}${value}`
}

function dateLabel(timestamp: number): string {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}.${month}.${day}`
}

function epochDayLabel(value?: number | null): string {
  if (typeof value !== 'number') return ''
  return dateLabel(value * 86_400_000)
}

function nonEmptyRows(rows: DetailRow[]): DetailRow[] {
  return rows.filter((row) => row.value.trim().length > 0)
}

function toDetail(cat: PublicCatSummary): CatDetailView {
  const locationLabel = cat.locationName || [cat.province, cat.cityCode, cat.districtCode, cat.street, cat.community]
    .filter(Boolean)
    .join(' · ')
  const photos = (cat.photoUrls || []).map(resolveMedia).filter(Boolean)
  const cover = resolveMedia(cat.coverUrl) || photos[0] || ''
  const profileRows = nonEmptyRows([
    { label: '性别', value: cat.gender || '' },
    { label: '年龄', value: cat.ageBand || '' },
    { label: '花色', value: cat.coat || '' },
    { label: '体重', value: cat.weight || '' },
    { label: '健康', value: cat.healthSummary || '' },
    { label: '获救日期', value: epochDayLabel(cat.rescuedDateEpochDay) },
  ])

  return {
    ...cat,
    displayName: cat.nickname || cat.alias || '未命名猫咪',
    statusLabel: cat.catStatus || statusLabels[cat.statusCode || ''] || '公开档案',
    cover,
    photos: photos.filter((photo) => photo !== cover),
    locationLabel: locationLabel || '未标记地区',
    profileRows,
    storyText: cat.story || cat.statusReason || '这份公开档案还没有补充更多说明。',
    updatedLabel: dateLabel(cat.updatedAt),
  }
}

function toTimeline(item: PublicCatTimelineItem): TimelineView {
  return {
    ...item,
    kindLabel: timelineLabels[item.kind] || '近况更新',
    dateLabel: dateLabel(item.createdAt),
  }
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

Page({
  data: {
    catId: '',
    cat: null as CatDetailView | null,
    timeline: [] as TimelineView[],
    isFollowing: false,
    loading: true,
    followPending: false,
    errorMessage: '',
  },

  onLoad(query: Record<string, string>) {
    const catId = (query.id || '').trim()
    if (!catId) {
      this.setData({ loading: false, errorMessage: '未找到公开猫咪档案' })
      return
    }
    this.setData({ catId })
    void this.loadDetail()
  },

  onPullDownRefresh() {
    void this.loadDetail().finally(() => wx.stopPullDownRefresh())
  },

  async loadDetail() {
    const catId = this.data.catId
    if (!catId) return
    this.setData({ loading: true, errorMessage: '' })

    let token = ''
    try {
      token = (await refreshSessionWhenNeeded())?.accessToken || ''
    } catch {
      token = ''
    }

    try {
      const [cat, timeline, followed] = await Promise.all([
        getPublicCat(catId),
        getPublicCatTimeline(catId),
        token ? listFollowedPublicCats(token).catch(() => null) : Promise.resolve(null),
      ])
      this.setData({
        cat: toDetail(cat),
        timeline: timeline.items.map(toTimeline),
        isFollowing: Boolean(followed?.cats.some((item) => item.id === catId)),
      })
    } catch (error) {
      this.setData({ errorMessage: errorMessage(error, '档案加载失败，请稍后重试') })
    } finally {
      this.setData({ loading: false })
    }
  },

  goLogin() {
    const redirect = encodeURIComponent(`/package-community/pages/cat-detail/index?id=${this.data.catId}`)
    wx.navigateTo({ url: `/package-account/pages/auth/index?redirect=${redirect}` })
  },

  async toggleFollow() {
    if (this.data.followPending || !this.data.cat) return

    let token = ''
    try {
      token = (await refreshSessionWhenNeeded())?.accessToken || ''
    } catch {
      token = ''
    }
    if (!token) {
      this.goLogin()
      return
    }

    this.setData({ followPending: true })
    try {
      const result = this.data.isFollowing
        ? await unfollowPublicCat(this.data.catId, token)
        : await followPublicCat(this.data.catId, token)
      this.setData({
        isFollowing: result.followed,
        cat: { ...this.data.cat, followCount: result.followCount },
      })
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '关注操作失败'), icon: 'none' })
    } finally {
      this.setData({ followPending: false })
    }
  },
})
