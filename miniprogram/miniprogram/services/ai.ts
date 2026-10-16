import { request } from './request'

export interface AiChatHistoryItem {
  role: 'user' | 'assistant'
  content: string
}

export function assistCatProfile(description: string, token: string): Promise<{ profile: Record<string, string> }> {
  return request({ path: '/api/v1/ai/cat-profile', method: 'POST', data: { description }, token })
}

export function askAi(question: string, history: AiChatHistoryItem[], token: string): Promise<{ answer: string }> {
  return request({ path: '/api/v1/ai/chat', method: 'POST', data: { question, history }, token })
}
