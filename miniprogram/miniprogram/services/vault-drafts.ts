export const LOCAL_VAULT_DRAFTS_STORAGE_KEY = 'catlink:local-vault-drafts:v1'
export const LOCAL_VAULT_TRASH_STORAGE_KEY = 'catlink:local-vault-trash:v1'

export type LocalVaultDraftStatus = 'rescue' | 'treating' | 'adoptable' | 'adopted' | 'released' | 'archived'
export type LocalVaultDraftGender = 'male' | 'female' | 'unknown'
export type LocalVaultDraftSterilization = 'unknown' | 'sterilized' | 'not-sterilized'

export interface LocalVaultDraft {
  id: string
  createdAt: number
  updatedAt: number
  syncState: 'local-only'
  coverPath: string
  albumPaths: string[]
  nickname: string
  discoveredDate: string
  status: LocalVaultDraftStatus
  gender: LocalVaultDraftGender
  sterilization: LocalVaultDraftSterilization
  age: string
  coat: string
  weight: string
  healthSummary?: string
  personality: string
  tags: string[]
  region: string
  story: string
  isPublic: boolean
}

export interface LocalVaultTrashItem {
  deletedAt: number
  draft: LocalVaultDraft
}

export type LocalVaultDraftInput = Omit<LocalVaultDraft, 'id' | 'createdAt' | 'updatedAt' | 'syncState'>

const statuses: LocalVaultDraftStatus[] = ['rescue', 'treating', 'adoptable', 'adopted', 'released', 'archived']
const genders: LocalVaultDraftGender[] = ['male', 'female', 'unknown']
const sterilizations: LocalVaultDraftSterilization[] = ['unknown', 'sterilized', 'not-sterilized']

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string')
}

function isLocalVaultDraft(value: unknown): value is LocalVaultDraft {
  if (!isRecord(value)) return false

  return typeof value.id === 'string'
    && typeof value.createdAt === 'number'
    && typeof value.updatedAt === 'number'
    && value.syncState === 'local-only'
    && typeof value.coverPath === 'string'
    && isStringArray(value.albumPaths)
    && typeof value.nickname === 'string'
    && typeof value.discoveredDate === 'string'
    && statuses.includes(value.status as LocalVaultDraftStatus)
    && genders.includes(value.gender as LocalVaultDraftGender)
    && sterilizations.includes(value.sterilization as LocalVaultDraftSterilization)
    && typeof value.age === 'string'
    && typeof value.coat === 'string'
    && typeof value.weight === 'string'
    && (value.healthSummary === undefined || typeof value.healthSummary === 'string')
    && typeof value.personality === 'string'
    && isStringArray(value.tags)
    && typeof value.region === 'string'
    && typeof value.story === 'string'
    && typeof value.isPublic === 'boolean'
}

/** Reads drafts that are deliberately kept on this device until Vault sync exists. */
export function readLocalVaultDrafts(): LocalVaultDraft[] {
  const stored = wx.getStorageSync(LOCAL_VAULT_DRAFTS_STORAGE_KEY)
  if (!Array.isArray(stored)) return []

  return stored
    .filter(isLocalVaultDraft)
    .sort((left, right) => right.updatedAt - left.updatedAt)
}

/** Persists a new local-only draft. It intentionally does not enqueue Vault sync. */
export function saveLocalVaultDraft(input: LocalVaultDraftInput): LocalVaultDraft {
  const now = Date.now()
  const draft: LocalVaultDraft = {
    ...input,
    id: `local-${now.toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
    createdAt: now,
    updatedAt: now,
    syncState: 'local-only',
  }

  wx.setStorageSync(LOCAL_VAULT_DRAFTS_STORAGE_KEY, [draft, ...readLocalVaultDrafts()])
  return draft
}

export function getLocalVaultDraft(id: string): LocalVaultDraft | null {
  return readLocalVaultDrafts().find((draft) => draft.id === id) || null
}

export function updateLocalVaultDraft(id: string, input: LocalVaultDraftInput): LocalVaultDraft | null {
  const drafts = readLocalVaultDrafts()
  const current = drafts.find((draft) => draft.id === id)
  if (!current) return null

  const draft: LocalVaultDraft = {
    ...current,
    ...input,
    id: current.id,
    createdAt: current.createdAt,
    syncState: 'local-only',
    updatedAt: Date.now(),
  }
  wx.setStorageSync(LOCAL_VAULT_DRAFTS_STORAGE_KEY, [draft, ...drafts.filter((item) => item.id !== id)])
  return draft
}

export function updateLocalVaultDraftStatus(id: string, status: LocalVaultDraftStatus): void {
  const drafts = readLocalVaultDrafts()
  const current = drafts.find((draft) => draft.id === id)
  if (!current || current.status === status) return

  wx.setStorageSync(LOCAL_VAULT_DRAFTS_STORAGE_KEY, [{ ...current, status, updatedAt: Date.now() }, ...drafts.filter((item) => item.id !== id)])
}

export function readLocalVaultTrash(): LocalVaultTrashItem[] {
  const stored = wx.getStorageSync(LOCAL_VAULT_TRASH_STORAGE_KEY)
  if (!Array.isArray(stored)) return []

  return stored
    .filter((item): item is LocalVaultTrashItem => isRecord(item) && typeof item.deletedAt === 'number' && isLocalVaultDraft(item.draft))
    .sort((left, right) => right.deletedAt - left.deletedAt)
}

export function moveLocalVaultDraftToTrash(id: string): void {
  const drafts = readLocalVaultDrafts()
  const draft = drafts.find((item) => item.id === id)
  if (!draft) return

  wx.setStorageSync(LOCAL_VAULT_DRAFTS_STORAGE_KEY, drafts.filter((item) => item.id !== id))
  wx.setStorageSync(LOCAL_VAULT_TRASH_STORAGE_KEY, [{ draft, deletedAt: Date.now() }, ...readLocalVaultTrash().filter((item) => item.draft.id !== id)])
}

export function restoreLocalVaultDraft(id: string): void {
  const trash = readLocalVaultTrash()
  const item = trash.find((entry) => entry.draft.id === id)
  if (!item) return

  wx.setStorageSync(LOCAL_VAULT_TRASH_STORAGE_KEY, trash.filter((entry) => entry.draft.id !== id))
  wx.setStorageSync(LOCAL_VAULT_DRAFTS_STORAGE_KEY, [{ ...item.draft, updatedAt: Date.now() }, ...readLocalVaultDrafts().filter((entry) => entry.id !== id)])
}

export function removeLocalVaultDraftForever(id: string): void {
  wx.setStorageSync(LOCAL_VAULT_TRASH_STORAGE_KEY, readLocalVaultTrash().filter((entry) => entry.draft.id !== id))
}
