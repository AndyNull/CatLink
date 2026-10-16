import { buildRescueOverview, type RescueTask } from '../../../services/rescue-workflow'

type TaskFilter = 'all' | 'due' | 'upcoming'

interface TaskItem extends RescueTask {
  kindLabel: string
  dueLabel: string
  tone: string
  icon: string
}

const filters: { value: TaskFilter; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'due', label: '待处理' },
  { value: 'upcoming', label: '计划中' },
]

function presentTask(task: RescueTask): TaskItem {
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

function filterTasks(tasks: TaskItem[], filter: TaskFilter): TaskItem[] {
  if (filter === 'due') return tasks.filter((task) => task.overdue || task.kind === 'treatment')
  if (filter === 'upcoming') return tasks.filter((task) => !task.overdue && task.kind !== 'treatment')
  return tasks
}

Page({
  data: {
    filters,
    activeFilter: 'all' as TaskFilter,
    tasks: [] as TaskItem[],
    visibleTasks: [] as TaskItem[],
  },

  onShow() {
    this.loadTasks()
  },

  loadTasks() {
    const tasks = buildRescueOverview().tasks.map(presentTask)
    this.setData({ tasks, visibleTasks: filterTasks(tasks, this.data.activeFilter) })
  },

  selectFilter(event: WechatMiniprogram.TouchEvent) {
    const activeFilter = event.currentTarget.dataset.filter as TaskFilter
    if (activeFilter !== 'all' && activeFilter !== 'due' && activeFilter !== 'upcoming') return
    this.setData({ activeFilter, visibleTasks: filterTasks(this.data.tasks, activeFilter) })
  },

  openTask(event: WechatMiniprogram.TouchEvent) {
    const id = event.currentTarget.dataset.id
    const task = this.data.visibleTasks.find((item) => item.id === id)
    if (!task) return
    if (task.kind === 'treatment') {
      wx.navigateTo({ url: `/package-vault/pages/detail/index?id=${encodeURIComponent(task.catId)}` })
      return
    }
    const query = task.kind === 'followup' ? `?adopterId=${encodeURIComponent(task.adopterId)}` : ''
    wx.navigateTo({ url: `/package-adoption/pages/index/index${query}` })
  },
})
