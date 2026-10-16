import { buildRescueOverview, type RescueTask } from '../../services/rescue-workflow'
import { getRandomEncouragement, listPublicCats } from '../../services/public-cats'

interface HomeTask extends RescueTask {
  kindLabel: string
  dueLabel: string
  tone: string
  icon: string
}

interface HomePublicCat {
  id: string
  name: string
  status: string
  cover: string
}

const statusLabels: Record<string, string> = {
  rescue: '救助中',
  adoptable: '待领养',
  treating: '治疗中',
  adopted: '已领养',
  released: '已放归',
  archived: '已归档',
}

function presentTask(task: RescueTask): HomeTask {
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

Page({
  data: {
    stats: [
      { value: 0, label: '全部档案', icon: '/assets/icons/stat-rescued.svg', tone: 'rose' },
      { value: 0, label: '已领养', icon: '/assets/icons/stat-adopted.svg', tone: 'mint' },
      { value: 0, label: '待回访', icon: '/assets/icons/stat-followup.svg', tone: 'apricot' },
      { value: 0, label: '治疗中', icon: '/assets/icons/stat-treatment.svg', tone: 'neutral' },
    ],
    tasks: [] as HomeTask[],
    hasTasks: false,
    quote: '把每一次遇见，整理成可以继续照护的档案。',
    publicCats: [] as HomePublicCat[],
    hasPublicCats: false,
    publicLoadedAt: 0,
  },

  onShow() {
    this.getTabBar()?.setData({ selected: 0, ...getApp<IAppOption>().globalData.noticePosition })
    this.loadOverview()
    if (Date.now() - this.data.publicLoadedAt > 30000) void this.loadPublicContent()
  },

  loadOverview() {
    const overview = buildRescueOverview()
    this.setData({
      stats: [
        { value: overview.archiveCount, label: '全部档案', icon: '/assets/icons/stat-rescued.svg', tone: 'rose' },
        { value: overview.adoptedCount, label: '已领养', icon: '/assets/icons/stat-adopted.svg', tone: 'mint' },
        { value: overview.pendingFollowUpCount, label: '待回访', icon: '/assets/icons/stat-followup.svg', tone: 'apricot' },
        { value: overview.treatingCount, label: '治疗中', icon: '/assets/icons/stat-treatment.svg', tone: 'neutral' },
      ],
      tasks: overview.tasks.slice(0, 3).map(presentTask),
      hasTasks: overview.tasks.length > 0,
    })
  },

  async loadPublicContent() {
    const [encouragementResult, catsResult] = await Promise.allSettled([
      getRandomEncouragement(),
      listPublicCats({ limit: 2 }),
    ])
    const update: Partial<typeof this.data> = {}
    if (encouragementResult.status === 'fulfilled') {
      update.quote = encouragementResult.value.quote
    }
    if (catsResult.status === 'fulfilled') {
      const cats = catsResult.value
      const publicCats = cats.items.map((cat) => ({
        id: cat.id,
        name: cat.nickname || cat.alias || '未命名猫咪',
        status: cat.catStatus || statusLabels[cat.statusCode || ''] || '公开档案',
        cover: cat.coverUrl || '',
      }))
      update.publicCats = publicCats
      update.hasPublicCats = publicCats.length > 0
    }
    update.publicLoadedAt = Date.now()
    this.setData(update)
  },

  openTask(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id
    const task = this.data.tasks.find((item) => item.id === id)
    if (!task) return
    if (task.kind === 'treatment') {
      wx.navigateTo({ url: `/package-vault/pages/detail/index?id=${encodeURIComponent(task.catId)}` })
      return
    }
    const query = task.kind === 'followup' ? `?adopterId=${encodeURIComponent(task.adopterId)}` : ''
    wx.navigateTo({ url: `/package-adoption/pages/index/index${query}` })
  },
})
