import { buildRescueOverview, type RescueTask } from '../../../services/rescue-workflow'
import { readLocalVaultDrafts, type LocalVaultDraft } from '../../../services/vault-drafts'

type ToolTab = 'stats' | 'poster' | 'settings'
type ThemeName = '奶油粉' | '白色' | '夜间'

interface PosterDraft {
  id: string
  nickname: string
  initial: string
  coverPath: string
  statusLabel: string
  region: string
  story: string
}

interface ToolTask extends RescueTask {
  kindLabel: string
  dueLabel: string
  tone: string
  icon: string
}

const posterStorageKey = 'catlink:poster-selection:v1'
const themeOptions = [
  { value: '奶油粉', className: 'tools-page--cream', previewClass: 'theme-setting__sample--cream', navigationColor: '#fffaf8', pageColor: '#faf7f5', frontColor: '#000000' },
  { value: '白色', className: 'tools-page--white', previewClass: 'theme-setting__sample--white', navigationColor: '#ffffff', pageColor: '#ffffff', frontColor: '#000000' },
  { value: '夜间', className: 'tools-page--night', previewClass: 'theme-setting__sample--night', navigationColor: '#2b2629', pageColor: '#2b2629', frontColor: '#ffffff' },
] as const
const statusLabels: Record<LocalVaultDraft['status'], string> = {
  rescue: '救助中',
  treating: '治疗中',
  adoptable: '待领养',
  adopted: '已领养',
  released: '已放归',
  archived: '已归档',
}

function createPoster(draft: LocalVaultDraft): PosterDraft {
  return {
    id: draft.id,
    nickname: draft.nickname,
    initial: Array.from(draft.nickname)[0] || '猫',
    coverPath: draft.coverPath,
    statusLabel: statusLabels[draft.status],
    region: draft.region,
    story: draft.story,
  }
}

function presentTask(task: RescueTask): ToolTask {
  const kindLabel = task.kind === 'followup' ? '回访' : task.kind === 'adoption' ? '领养' : '治疗'
  const icon = task.kind === 'followup'
    ? '/assets/icons/task-followup.svg'
    : task.kind === 'adoption'
      ? '/assets/icons/task-adoption.svg'
      : '/assets/icons/task-treatment.svg'
  return {
    ...task,
    kindLabel,
    dueLabel: task.overdue ? `待处理 · ${task.dueDate}` : `计划 ${task.dueDate}`,
    tone: task.kind === 'followup' ? 'mint' : task.kind === 'adoption' ? 'apricot' : 'rose',
    icon,
  }
}

function isThemeName(value: unknown): value is ThemeName {
  return value === '奶油粉' || value === '白色' || value === '夜间'
}

function getTheme(theme: ThemeName) {
  return themeOptions.find((item) => item.value === theme) || themeOptions[0]
}

Page({
  data: {
    activeTab: 'stats' as ToolTab,
    stats: [
      { label: '全部档案', value: 0, tone: 'rose', icon: '/assets/icons/stat-rescued.svg' },
      { label: '待领养', value: 0, tone: 'apricot', icon: '/assets/icons/action-adoption.svg' },
      { label: '已领养', value: 0, tone: 'mint', icon: '/assets/icons/stat-adopted.svg' },
      { label: '待回访', value: 0, tone: 'neutral', icon: '/assets/icons/stat-followup.svg' },
    ],
    drafts: [] as LocalVaultDraft[],
    adopterCount: 0,
    followUpCount: 0,
    taskPreview: [] as ToolTask[],
    hasTasks: false,
    selectedTheme: '奶油粉' as ThemeName,
    themeClass: 'tools-page--cream',
    themePreviewClass: 'theme-setting__sample--cream',
    themeOptions,
    poster: null as PosterDraft | null,
  },

  onLoad(query: Record<string, string>) {
    if (query.tab === 'settings') this.setData({ activeTab: 'settings' })
  },

  onShow() {
    this.loadToolsData()
  },

  loadToolsData() {
    const drafts = readLocalVaultDrafts()
    const overview = buildRescueOverview()
    const storedTheme = wx.getStorageSync('catlink-theme')
    const selectedTheme = isThemeName(storedTheme) ? storedTheme : '奶油粉'
    const storedPosterId = wx.getStorageSync(posterStorageKey)
    const poster = typeof storedPosterId === 'string' ? drafts.find((item) => item.id === storedPosterId) : undefined
    const theme = getTheme(selectedTheme)
    this.setData({
      drafts,
      stats: [
        { label: '全部档案', value: overview.archiveCount, tone: 'rose', icon: '/assets/icons/stat-rescued.svg' },
        { label: '待领养', value: drafts.filter((draft) => draft.status === 'adoptable').length, tone: 'apricot', icon: '/assets/icons/action-adoption.svg' },
        { label: '已领养', value: overview.adoptedCount, tone: 'mint', icon: '/assets/icons/stat-adopted.svg' },
        { label: '待回访', value: overview.pendingFollowUpCount, tone: 'neutral', icon: '/assets/icons/stat-followup.svg' },
      ],
      adopterCount: overview.adopterCount,
      followUpCount: overview.followUpCount,
      taskPreview: overview.tasks.slice(0, 2).map(presentTask),
      hasTasks: overview.tasks.length > 0,
      selectedTheme,
      themeClass: theme.className,
      themePreviewClass: theme.previewClass,
      poster: poster ? createPoster(poster) : null,
    })
    this.applyTheme(selectedTheme)
  },

  selectTab(event: WechatMiniprogram.TouchEvent) {
    const activeTab = event.currentTarget.dataset.tab as ToolTab
    if (activeTab === 'stats' || activeTab === 'poster' || activeTab === 'settings') this.setData({ activeTab })
  },

  openTasks() {
    wx.navigateTo({ url: '/package-tools/pages/tasks/index' })
  },

  openTask(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id
    const task = this.data.taskPreview.find((item) => item.id === id)
    if (!task) return
    if (task.kind === 'treatment') {
      wx.navigateTo({ url: `/package-vault/pages/detail/index?id=${encodeURIComponent(task.catId)}` })
      return
    }
    const query = task.kind === 'followup' ? `?adopterId=${encodeURIComponent(task.adopterId)}` : ''
    wx.navigateTo({ url: `/package-adoption/pages/index/index${query}` })
  },

  choosePoster() {
    if (!this.data.drafts.length) {
      wx.showToast({ title: '请先建立档案', icon: 'none' })
      return
    }
    wx.showActionSheet({
      itemList: this.data.drafts.map((draft) => draft.nickname),
      success: ({ tapIndex }) => {
        const draft = this.data.drafts[tapIndex]
        if (!draft) return
        wx.setStorageSync(posterStorageKey, draft.id)
        this.setData({ poster: createPoster(draft) })
        wx.showToast({ title: '海报已更新', icon: 'success' })
      },
    })
  },

  selectTheme(event: WechatMiniprogram.TouchEvent) {
    const selectedTheme = event.currentTarget.dataset.theme
    if (!isThemeName(selectedTheme)) return
    const theme = getTheme(selectedTheme)
    wx.setStorageSync('catlink-theme', selectedTheme)
    this.setData({ selectedTheme, themeClass: theme.className, themePreviewClass: theme.previewClass })
    this.applyTheme(selectedTheme)
    wx.showToast({ title: `已应用${selectedTheme}主题`, icon: 'success' })
  },

  applyTheme(selectedTheme: ThemeName) {
    const theme = getTheme(selectedTheme)
    wx.setNavigationBarColor({ backgroundColor: theme.navigationColor, frontColor: theme.frontColor })
    wx.setBackgroundColor({ backgroundColorTop: theme.pageColor, backgroundColorBottom: theme.pageColor })
  },

  onShareAppMessage() {
    const poster = this.data.poster
    return {
      title: poster ? `${poster.nickname}的救助档案` : 'CatLink 救助档案',
      path: '/pages/home/index',
    }
  },

  onShareTimeline() {
    const poster = this.data.poster
    return { title: poster ? `${poster.nickname}的救助档案` : 'CatLink 救助档案' }
  },
})
