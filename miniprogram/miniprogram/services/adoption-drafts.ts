export type LocalAdopterStage = 'candidate' | 'adopted'
export type LocalFollowUpStatus = 'well' | 'attention'

export interface LocalAdopterDraft {
  id: string
  catId: string
  name: string
  contact: string
  stage: LocalAdopterStage
  createdAt: number
  updatedAt: number
}

export interface LocalFollowUpDraft {
  id: string
  adopterId: string
  catId: string
  visitDate: string
  nextVisitDate: string
  status: LocalFollowUpStatus
  note: string
  createdAt: number
}

const adopterStorageKey = 'catlink:adopter-drafts:v1'
const followUpStorageKey = 'catlink:follow-up-drafts:v1'

function formatDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function plusDays(value: string, days: number): string {
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value
  date.setDate(date.getDate() + days)
  return formatDate(date)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function readAdopter(value: unknown): LocalAdopterDraft | null {
  if (!isRecord(value) || typeof value.id !== 'string' || typeof value.name !== 'string' || typeof value.contact !== 'string' || typeof value.createdAt !== 'number') return null

  return {
    id: value.id,
    catId: typeof value.catId === 'string' ? value.catId : '',
    name: value.name,
    contact: value.contact,
    stage: value.stage === 'adopted' ? 'adopted' : 'candidate',
    createdAt: value.createdAt,
    updatedAt: typeof value.updatedAt === 'number' ? value.updatedAt : value.createdAt,
  }
}

function readFollowUp(value: unknown): LocalFollowUpDraft | null {
  if (!isRecord(value)
    || typeof value.id !== 'string'
    || typeof value.adopterId !== 'string'
    || typeof value.visitDate !== 'string'
    || (value.status !== 'well' && value.status !== 'attention')
    || typeof value.note !== 'string'
    || typeof value.createdAt !== 'number') return null

  return {
    id: value.id,
    adopterId: value.adopterId,
    catId: typeof value.catId === 'string' ? value.catId : '',
    visitDate: value.visitDate,
    nextVisitDate: typeof value.nextVisitDate === 'string' ? value.nextVisitDate : plusDays(value.visitDate, 30),
    status: value.status,
    note: value.note,
    createdAt: value.createdAt,
  }
}

export function readLocalAdopters(): LocalAdopterDraft[] {
  const stored = wx.getStorageSync(adopterStorageKey)
  if (!Array.isArray(stored)) return []

  return stored
    .map(readAdopter)
    .filter((item): item is LocalAdopterDraft => item !== null)
    .sort((left, right) => right.updatedAt - left.updatedAt)
}

export function saveLocalAdopter(input: Pick<LocalAdopterDraft, 'catId' | 'name' | 'contact' | 'stage'>): LocalAdopterDraft {
  const now = Date.now()
  const adopter: LocalAdopterDraft = {
    ...input,
    id: `adopter-${now.toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
    createdAt: now,
    updatedAt: now,
  }

  wx.setStorageSync(adopterStorageKey, [adopter, ...readLocalAdopters()])
  return adopter
}

export function updateLocalAdopterStage(id: string, stage: LocalAdopterStage): void {
  const adopters = readLocalAdopters()
  wx.setStorageSync(adopterStorageKey, adopters.map((item) => item.id === id ? { ...item, stage, updatedAt: Date.now() } : item))
}

export function updateLocalAdopterCat(id: string, catId: string): void {
  const adopters = readLocalAdopters()
  wx.setStorageSync(adopterStorageKey, adopters.map((item) => item.id === id ? { ...item, catId, updatedAt: Date.now() } : item))
}

export function readLocalFollowUps(): LocalFollowUpDraft[] {
  const stored = wx.getStorageSync(followUpStorageKey)
  if (!Array.isArray(stored)) return []

  const catIdsByAdopter = new Map(readLocalAdopters().map((adopter) => [adopter.id, adopter.catId]))
  return stored
    .map(readFollowUp)
    .filter((item): item is LocalFollowUpDraft => item !== null)
    .map((item) => item.catId ? item : { ...item, catId: catIdsByAdopter.get(item.adopterId) || '' })
    .sort((left, right) => right.visitDate.localeCompare(left.visitDate) || right.createdAt - left.createdAt)
}

export function saveLocalFollowUp(input: Omit<LocalFollowUpDraft, 'id' | 'createdAt'>): LocalFollowUpDraft {
  const now = Date.now()
  const followUp: LocalFollowUpDraft = {
    ...input,
    id: `follow-up-${now.toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
    createdAt: now,
  }

  wx.setStorageSync(followUpStorageKey, [followUp, ...readLocalFollowUps()])
  return followUp
}
