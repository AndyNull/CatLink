export interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

export interface CursorPage<T> {
  items: T[]
  nextCursor?: string | null
}

export interface PublicCatSummary {
  id: string
  ownerAccountId?: string
  nickname?: string
  alias?: string
  story?: string
  gender?: string
  ageBand?: string
  coat?: string
  personalityTags?: string
  healthSummary?: string
  weight?: string
  dietRestrictions?: string
  contactWithHumans?: string
  contactWithCats?: string
  statusCode?: string
  catStatus?: string
  statusReason?: string
  archiveReason?: string
  coverUrl?: string
  photoUrls?: string[]
  province?: string
  cityCode?: string
  districtCode?: string
  street?: string
  community?: string
  locationName?: string
  rescuedDateEpochDay?: number
  statusDateEpochDay?: number | null
  followCount: number
  updatedAt: number
  distanceKm?: number
  distanceApproximate?: boolean
  ownerName?: string
  ownerUsername?: string
  ownerAvatarUrl?: string
  latestTimeline?: PublicCatTimelineItem
}

export interface PublicCatTimelineItem {
  id: string
  kind: string
  body: string
  createdAt: number
}

export interface CommunityPostSummary {
  id: string
  topic: string
  authorName: string
  authorAccountId: string
  title: string
  body: string
  mediaUrls: string
  catRef?: string | null
  cityCode?: string
  districtCode?: string
  province?: string
  street?: string
  community?: string
  liked: boolean
  likeCount: number
  commentCount: number
  createdAt: number
}

export interface CommunityComment {
  id: string
  postId: string
  authorAccountId: string
  authorName: string
  authorAvatar?: string
  body: string
  replyToId?: string | null
  replyToName?: string
  liked: boolean
  likeCount: number
  replyCount: number
  createdAt: number
}
