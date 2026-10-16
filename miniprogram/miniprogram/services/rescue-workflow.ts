import { readLocalAdopters, readLocalFollowUps, type LocalAdopterDraft, type LocalFollowUpDraft } from './adoption-drafts'
import { readLocalVaultDrafts, type LocalVaultDraft } from './vault-drafts'

export type RescueTaskKind = 'followup' | 'adoption' | 'treatment'

export interface RescueTask {
  id: string
  kind: RescueTaskKind
  title: string
  detail: string
  dueDate: string
  catId: string
  adopterId: string
  overdue: boolean
}

export interface RescueOverview {
  archiveCount: number
  adoptedCount: number
  treatingCount: number
  adopterCount: number
  followUpCount: number
  pendingFollowUpCount: number
  taskCount: number
  tasks: RescueTask[]
}

function formatDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function plusDays(timestamp: number, days: number): string {
  const date = new Date(timestamp)
  date.setDate(date.getDate() + days)
  return formatDate(date)
}

function latestFollowUpDates(followUps: LocalFollowUpDraft[]): Map<string, string> {
  const dates = new Map<string, string>()
  followUps.forEach((item) => {
    if (!dates.has(item.adopterId)) dates.set(item.adopterId, item.nextVisitDate)
  })
  return dates
}

function catName(drafts: Map<string, LocalVaultDraft>, catId: string): string {
  return drafts.get(catId)?.nickname || '未关联档案'
}

function buildAdoptionTask(adopter: LocalAdopterDraft, drafts: Map<string, LocalVaultDraft>, followUpDates: Map<string, string>, today: string): RescueTask {
  const dueDate = adopter.stage === 'adopted'
    ? followUpDates.get(adopter.id) || plusDays(adopter.updatedAt, 7)
    : plusDays(adopter.updatedAt, 3)
  const cat = catName(drafts, adopter.catId)
  const followUp = adopter.stage === 'adopted'

  return {
    id: `${followUp ? 'followup' : 'adoption'}:${adopter.id}`,
    kind: followUp ? 'followup' : 'adoption',
    title: followUp ? `回访 ${adopter.name}` : `跟进 ${adopter.name}`,
    detail: `${cat} · ${followUp ? '领养后回访' : '领养沟通'}`,
    dueDate,
    catId: adopter.catId,
    adopterId: adopter.id,
    overdue: dueDate <= today,
  }
}

export function buildRescueOverview(): RescueOverview {
  const drafts = readLocalVaultDrafts()
  const adopters = readLocalAdopters().filter((item) => item.catId)
  const followUps = readLocalFollowUps()
  const today = formatDate(new Date())
  const draftsById = new Map(drafts.map((draft) => [draft.id, draft]))
  const followUpDates = latestFollowUpDates(followUps)
  const adoptionTasks = adopters.map((adopter) => buildAdoptionTask(adopter, draftsById, followUpDates, today))
  const treatmentTasks = drafts
    .filter((draft) => draft.status === 'treating')
    .map((draft) => ({
      id: `treatment:${draft.id}`,
      kind: 'treatment' as const,
      title: `更新 ${draft.nickname} 的治疗进度`,
      detail: draft.region || '治疗中档案',
      dueDate: today,
      catId: draft.id,
      adopterId: '',
      overdue: false,
    }))
  const tasks = [...treatmentTasks, ...adoptionTasks].sort((left, right) => left.dueDate.localeCompare(right.dueDate) || left.title.localeCompare(right.title))

  return {
    archiveCount: drafts.length,
    adoptedCount: drafts.filter((draft) => draft.status === 'adopted').length,
    treatingCount: treatmentTasks.length,
    adopterCount: adopters.length,
    followUpCount: followUps.length,
    pendingFollowUpCount: adoptionTasks.filter((task) => task.kind === 'followup' && task.overdue).length,
    taskCount: tasks.filter((task) => task.overdue || task.kind === 'treatment').length,
    tasks,
  }
}
