export type CommunityDraftTopic = 'rescue' | 'seeking_adoption' | 'general'

export interface CommunityDraft {
  id: string
  topic: CommunityDraftTopic
  title: string
  body: string
  district: string
  createdAt: number
}

const storageKey = 'catlink:community-drafts:v1'

function isCommunityDraft(value: unknown): value is CommunityDraft {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Record<string, unknown>
  return typeof item.id === 'string'
    && (item.topic === 'rescue' || item.topic === 'seeking_adoption' || item.topic === 'general')
    && typeof item.title === 'string'
    && typeof item.body === 'string'
    && typeof item.district === 'string'
    && typeof item.createdAt === 'number'
}

export function readCommunityDrafts(): CommunityDraft[] {
  const stored = wx.getStorageSync(storageKey)
  if (!Array.isArray(stored)) return []
  return stored.filter(isCommunityDraft).sort((left, right) => right.createdAt - left.createdAt)
}

export function saveCommunityDraft(input: Omit<CommunityDraft, 'id' | 'createdAt'>): CommunityDraft {
  const draft: CommunityDraft = { ...input, id: `post-${Date.now().toString(36)}`, createdAt: Date.now() }
  wx.setStorageSync(storageKey, [draft, ...readCommunityDrafts()])
  return draft
}
