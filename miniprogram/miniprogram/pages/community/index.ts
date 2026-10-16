import { API_BASE_URL } from '../../env'
import type { CommunityPostSummary, PublicCatSummary } from '../../models/api'
import {
  listCommunityFeed,
  listFollowingFeed,
  toggleCommunityPostLike,
  type CommunityTopic,
} from '../../services/community'
import { listPublicCats } from '../../services/public-cats'
import { refreshSessionWhenNeeded } from '../../services/auth'

type FeedKind = '公开' | '关注' | '最新' | '附近'
type PublicCategory = 'all' | 'rescue' | 'adoptable' | 'treating' | 'adopted' | 'released' | 'archived'
type PostCategory = 'all' | 'rescue' | 'seeking_adoption'
type Category = PublicCategory | PostCategory

interface CategoryOption {
  value: Category
  label: string
}

interface PublicCatCard extends PublicCatSummary {
  displayName: string
  statusLabel: string
  locationLabel: string
  metaLabel: string
  updatedLabel: string
  cover: string
}

interface CommunityPostCard extends CommunityPostSummary {
  topicLabel: string
  locationLabel: string
  bodyPreview: string
  createdLabel: string
  cover: string
  authorInitial: string
}

const FEEDS: FeedKind[] = ['公开', '关注', '最新', '附近']
const PUBLIC_CATEGORIES: CategoryOption[] = [
  { value: 'all', label: '全部' },
  { value: 'rescue', label: '救助中' },
  { value: 'adoptable', label: '待领养' },
  { value: 'treating', label: '治疗中' },
  { value: 'adopted', label: '已领养' },
  { value: 'released', label: '已放归' },
  { value: 'archived', label: '已归档' },
]
const POST_CATEGORIES: CategoryOption[] = [
  { value: 'all', label: '全部' },
  { value: 'rescue', label: '救助' },
  { value: 'seeking_adoption', label: '求领养' },
]
const topicLabels: Record<string, string> = {
  rescue: '救助',
  seeking_adoption: '求领养',
  general: '交流',
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

function formatPublishedAt(timestamp: number): string {
  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000))
  if (minutes < 1) return '刚刚更新'
  if (minutes < 60) return `${minutes} 分钟前`
  if (minutes < 1440) return `${Math.floor(minutes / 60)} 小时前`

  const date = new Date(timestamp)
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日`
}

function previewBody(body: string): string {
  const normalized = body.replace(/\s+/g, ' ').trim()
  return normalized.length > 120 ? `${normalized.slice(0, 120)}...` : normalized
}

function resolveMedia(value?: string): string {
  if (!value) return ''
  if (/^https?:\/\//.test(value)) return value
  return `${API_BASE_URL}${value}`
}

function categoryOptions(feed: FeedKind): CategoryOption[] {
  return feed === '公开' ? PUBLIC_CATEGORIES : POST_CATEGORIES
}

function categoryLabel(feed: FeedKind, category: Category): string {
  return categoryOptions(feed).find((option) => option.value === category)?.label || '全部'
}

function postTopic(category: Category): CommunityTopic {
  if (category === 'rescue' || category === 'seeking_adoption') return category
  return 'all'
}

function toPublicCatCard(cat: PublicCatSummary): PublicCatCard {
  const details = [cat.gender, cat.ageBand, cat.coat].filter(Boolean).join(' · ')
  const location = cat.locationName || [cat.province, cat.districtCode || cat.cityCode].filter(Boolean).join(' · ')
  return {
    ...cat,
    displayName: cat.nickname || cat.alias || '未命名猫咪',
    statusLabel: cat.catStatus || statusLabels[cat.statusCode || ''] || '公开档案',
    locationLabel: location || '未标记地区',
    metaLabel: details || cat.healthSummary || '等待更多记录',
    updatedLabel: formatPublishedAt(cat.updatedAt),
    cover: resolveMedia(cat.coverUrl),
  }
}

function toCommunityPostCard(post: CommunityPostSummary): CommunityPostCard {
  const location = [post.province, post.districtCode || post.cityCode].filter(Boolean).join(' · ')
  return {
    ...post,
    topicLabel: topicLabels[post.topic] || '交流',
    locationLabel: location || '未标记地区',
    bodyPreview: previewBody(post.body),
    createdLabel: formatPublishedAt(post.createdAt),
    cover: resolveMedia(post.mediaUrls.split(',')[0]?.trim()),
    authorInitial: post.authorName.slice(0, 1),
  }
}

function getEmptyState(feed: FeedKind, query: string): { emptyTitle: string; emptyBody: string } {
  if (query.trim()) return { emptyTitle: '没有匹配内容', emptyBody: '换个关键词或分类继续查找。' }
  if (feed === '公开') return { emptyTitle: '暂无公开猫咪', emptyBody: '新的公开档案会显示在这里。' }
  if (feed === '关注') return { emptyTitle: '关注的人还没有新动态', emptyBody: '关注救助人后，更新会显示在这里。' }
  if (feed === '附近') return { emptyTitle: '附近还没有动态', emptyBody: '可以稍后刷新，或扩大查看范围。' }
  return { emptyTitle: '暂无最新动态', emptyBody: '新的救助记录会显示在这里。' }
}

function getCurrentLocation(): Promise<{ latitude: number; longitude: number }> {
  return new Promise((resolve, reject) => {
    wx.getLocation({
      type: 'gcj02',
      success: ({ latitude, longitude }) => resolve({ latitude, longitude }),
      fail: reject,
    })
  })
}

async function optionalToken(): Promise<string> {
  try {
    return (await refreshSessionWhenNeeded())?.accessToken || ''
  } catch {
    return ''
  }
}

Page({
  data: {
    feeds: FEEDS,
    activeFeed: '公开' as FeedKind,
    activeCategory: 'all' as Category,
    activeCategoryLabel: '全部',
    categoryOpen: false,
    categoryOptions: PUBLIC_CATEGORIES,
    query: '',
    publicCats: [] as PublicCatCard[],
    posts: [] as CommunityPostCard[],
    hasResults: false,
    isPublicFeed: true,
    isLoading: true,
    isLoadingMore: false,
    hasMore: false,
    errorMessage: '',
    emptyTitle: '正在加载公开猫咪',
    emptyBody: '请稍候。',
    needsLogin: false,
    latitude: 0,
    longitude: 0,
    nextCursor: '',
    loadedAt: 0,
  },

  onShow() {
    this.getTabBar()?.setData({ selected: 2, ...getApp<IAppOption>().globalData.noticePosition })
    if (Date.now() - this.data.loadedAt > 30000) void this.loadContent()
  },

  selectFeed(event: WechatMiniprogram.TouchEvent) {
    const activeFeed = event.currentTarget.dataset.feed as FeedKind
    if (!FEEDS.includes(activeFeed)) return
    if (activeFeed === this.data.activeFeed) {
      this.setData({ categoryOpen: false })
      return
    }

    const activeCategory: Category = 'all'
    this.setData({
      activeFeed,
      activeCategory,
      activeCategoryLabel: categoryLabel(activeFeed, activeCategory),
      categoryOpen: false,
      categoryOptions: categoryOptions(activeFeed),
      errorMessage: '',
      needsLogin: false,
    })
    if (activeFeed === '附近') {
      void this.loadNearby()
      return
    }
    void this.loadContent()
  },

  openCategorySheet() {
    this.setData({ categoryOpen: !this.data.categoryOpen })
  },

  selectCategory(event: WechatMiniprogram.TouchEvent) {
    const value = event.currentTarget.dataset.category as Category
    const option = categoryOptions(this.data.activeFeed).find((item) => item.value === value)
    if (!option) return
    if (option.value === this.data.activeCategory) {
      this.setData({ categoryOpen: false })
      return
    }
    this.setData({ activeCategory: option.value, activeCategoryLabel: option.label, categoryOpen: false })
    void this.loadContent()
  },

  updateQuery(event: WechatMiniprogram.Input) {
    this.setData({ query: event.detail.value })
  },

  search() {
    void this.loadContent()
  },

  clearQuery() {
    if (!this.data.query) return
    this.setData({ query: '' })
    void this.loadContent()
  },

  async loadNearby() {
    this.setData({ isLoading: true, errorMessage: '', needsLogin: false })
    try {
      const { latitude, longitude } = await getCurrentLocation()
      this.setData({ latitude, longitude })
      await this.loadContent()
    } catch {
      this.setData({
        isLoading: false,
        hasResults: false,
        errorMessage: '未获取位置，无法加载附近动态',
        ...getEmptyState('附近', this.data.query),
      })
    }
  },

  async loadContent(loadMore = false) {
    if (this.data.isLoadingMore || (this.data.isLoading && loadMore)) return
    if (loadMore && !this.data.hasMore) return

    const activeFeed = this.data.activeFeed
    const query = this.data.query.trim()
    const before = loadMore ? this.data.nextCursor : ''
    this.setData({
      isLoading: !loadMore,
      isLoadingMore: loadMore,
      errorMessage: '',
      needsLogin: false,
      ...(loadMore ? {} : { publicCats: [], posts: [], hasResults: false, hasMore: false }),
    })

    try {
      if (activeFeed === '公开') {
        const page = await listPublicCats({
          ...(this.data.activeCategory !== 'all' ? { status: this.data.activeCategory as PublicCategory } : {}),
          keyword: query,
          before,
          limit: 12,
        })
        const publicCats = page.items.map(toPublicCatCard)
        const merged = loadMore ? [...this.data.publicCats, ...publicCats] : publicCats
        const deduplicated = merged.filter((item, index, items) => items.findIndex((candidate) => candidate.id === item.id) === index)
        this.setData({
          publicCats: deduplicated,
          posts: [],
          hasResults: deduplicated.length > 0,
          isPublicFeed: true,
          hasMore: Boolean(page.nextCursor),
          nextCursor: page.nextCursor || '',
          loadedAt: Date.now(),
          ...getEmptyState(activeFeed, query),
        })
        return
      }

      const token = await optionalToken()

      if (activeFeed === '关注' && !token) {
        this.setData({
          isPublicFeed: false,
          hasResults: false,
          needsLogin: true,
          loadedAt: Date.now(),
          ...getEmptyState(activeFeed, query),
        })
        return
      }

      const filters = {
        topic: postTopic(this.data.activeCategory),
        keyword: query,
        before,
        limit: 12,
        ...(activeFeed === '附近' ? { lat: this.data.latitude, lng: this.data.longitude, radiusKm: 5 } : {}),
      }
      const page = activeFeed === '关注'
        ? await listFollowingFeed(token as string, filters)
        : await listCommunityFeed(filters, token)
      const posts = page.items.map(toCommunityPostCard)
      const merged = loadMore ? [...this.data.posts, ...posts] : posts
      const deduplicated = merged.filter((item, index, items) => items.findIndex((candidate) => candidate.id === item.id) === index)
      this.setData({
        publicCats: [],
        posts: deduplicated,
        hasResults: deduplicated.length > 0,
        isPublicFeed: false,
        hasMore: Boolean(page.nextCursor),
        nextCursor: page.nextCursor || '',
        loadedAt: Date.now(),
        ...getEmptyState(activeFeed, query),
      })
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '内容加载失败，请稍后重试'
      this.setData({
        hasResults: loadMore ? this.data.hasResults : false,
        isPublicFeed: activeFeed === '公开',
        errorMessage,
        ...getEmptyState(activeFeed, query),
      })
    } finally {
      this.setData({ isLoading: false, isLoadingMore: false })
    }
  },

  loadMore() {
    void this.loadContent(true)
  },

  goPublish() {
    void optionalToken().then((token) => {
      if (!token) {
        wx.navigateTo({ url: '/package-account/pages/auth/index?redirect=/package-community/pages/index/index' })
        return
      }
      wx.navigateTo({ url: '/package-community/pages/index/index' })
    })
  },

  goLogin() {
    wx.navigateTo({ url: '/package-account/pages/auth/index' })
  },

  goCatDetail(event: WechatMiniprogram.TouchEvent) {
    const id = String(event.currentTarget.dataset.id || '')
    if (id) wx.navigateTo({ url: `/package-community/pages/cat-detail/index?id=${encodeURIComponent(id)}` })
  },

  goPostDetail(event: WechatMiniprogram.TouchEvent) {
    const id = String(event.currentTarget.dataset.id || '')
    if (id) wx.navigateTo({ url: `/package-community/pages/post-detail/index?id=${encodeURIComponent(id)}` })
  },

  async likePost(event: WechatMiniprogram.TouchEvent) {
    const id = String(event.currentTarget.dataset.id || '')
    if (!id) return
    const token = await optionalToken()
    if (!token) {
      this.goLogin()
      return
    }
    try {
      const result = await toggleCommunityPostLike(id, token)
      this.setData({
        posts: this.data.posts.map((post) => post.id === id
          ? { ...post, liked: result.liked, likeCount: Math.max(0, post.likeCount + (result.liked ? 1 : -1)) }
          : post),
      })
    } catch (error) {
      wx.showToast({ title: error instanceof Error ? error.message : '点赞失败', icon: 'none' })
    }
  },
})
