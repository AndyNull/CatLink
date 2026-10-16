import type { CommunityComment, CommunityPostSummary, CursorPage } from '../models/api'
import { request } from './request'

export interface CommunityFeedFilters {
  topic?: string
  city?: string
  district?: string
  keyword?: string
  updatedDays?: number
  before?: string
  limit?: number
  lat?: number
  lng?: number
  radiusKm?: number
}

export type CommunityTopic = 'all' | 'rescue' | 'seeking_adoption' | 'general'

export interface CreateCommunityPostInput {
  id: string
  topic: CommunityTopic
  title: string
  body: string
  cityCode?: string
  districtCode?: string
  province?: string
  street?: string
  community?: string
  latitude?: number
  longitude?: number
  mediaUrls?: string
  mediaIds?: string[]
  catRef?: string
}

export function listCommunityFeed(
  filters: CommunityFeedFilters = {},
  token?: string,
): Promise<CursorPage<CommunityPostSummary>> {
  return request({ path: '/api/v1/community/feed', data: filters, token })
}

export function listFollowingFeed(
  token: string,
  filters: Pick<CommunityFeedFilters, 'topic' | 'before' | 'limit'> = {},
): Promise<CursorPage<CommunityPostSummary>> {
  return request({ path: '/api/v1/community/feed/following', data: filters, token })
}

export function getCommunityPost(id: string, token?: string): Promise<CommunityPostSummary> {
  return request({ path: `/api/v1/community/posts/${encodeURIComponent(id)}`, token })
}

export function getCommunityComments(id: string, token?: string): Promise<{ comments: CommunityComment[] }> {
  return request({ path: `/api/v1/community/posts/${encodeURIComponent(id)}/comments`, token })
}

export function createCommunityPost(input: CreateCommunityPostInput, token: string): Promise<{ id: string }> {
  return request({ path: '/api/v1/community/posts', method: 'POST', data: input, token })
}

export function toggleCommunityPostLike(id: string, token: string): Promise<{ liked: boolean }> {
  return request({ path: `/api/v1/community/posts/${encodeURIComponent(id)}/like`, method: 'POST', token })
}

export function addCommunityComment(id: string, body: string, token: string, replyToId = ''): Promise<{ id: string }> {
  return request({
    path: `/api/v1/community/posts/${encodeURIComponent(id)}/comments`,
    method: 'POST',
    data: { body, replyToId },
    token,
  })
}
