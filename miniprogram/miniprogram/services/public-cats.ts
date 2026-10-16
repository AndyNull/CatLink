import type { CursorPage, PublicCatSummary, PublicCatTimelineItem } from '../models/api'
import { request } from './request'

export interface PublicCatFilters {
  province?: string
  city?: string
  district?: string
  street?: string
  community?: string
  lat?: number
  lng?: number
  radiusKm?: number
  status?: string
  keyword?: string
  gender?: string
  age?: string
  sterilized?: boolean
  updatedDays?: number
  staleDays?: number
  before?: string
  limit?: number
}

export function listPublicCats(filters: PublicCatFilters = {}): Promise<CursorPage<PublicCatSummary>> {
  return request({ path: '/api/v1/public/cats', data: filters })
}

export function getRandomEncouragement(): Promise<{ quote: string }> {
  return request({ path: '/api/v1/encouragements/random' })
}

export function getPublicCat(id: string): Promise<PublicCatSummary> {
  return request({ path: `/api/v1/public/cats/${encodeURIComponent(id)}` })
}

export function getPublicCatTimeline(id: string): Promise<{ items: PublicCatTimelineItem[] }> {
  return request({ path: `/api/v1/public/cats/${encodeURIComponent(id)}/timeline` })
}

export function listFollowedPublicCats(
  token: string,
): Promise<{ cats: PublicCatSummary[] }> {
  return request({ path: '/api/v1/public/me/followed-cats', token })
}

export function followPublicCat(id: string, token: string): Promise<{ followed: boolean; followCount: number }> {
  return request({ path: `/api/v1/public/cats/${encodeURIComponent(id)}/follow`, method: 'POST', token })
}

export function unfollowPublicCat(id: string, token: string): Promise<{ followed: boolean; followCount: number }> {
  return request({ path: `/api/v1/public/cats/${encodeURIComponent(id)}/follow`, method: 'DELETE', token })
}
