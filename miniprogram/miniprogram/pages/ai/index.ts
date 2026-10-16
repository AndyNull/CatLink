import { askAi, assistCatProfile, type AiChatHistoryItem } from '../../services/ai'
import { refreshSessionWhenNeeded } from '../../services/auth'

interface SuggestedPrompt {
  title: string
  description: string
  query: string
}

interface ProfileEntry {
  key: string
  label: string
  value: string
}
type AiMode = 'profile' | 'ask'
interface ChatMessage extends AiChatHistoryItem { id: string }
interface ChatSession { id: string; title: string; messages: ChatMessage[]; updatedAt: number }

const chatStorageKey = 'catlink:ai-chat-sessions:v1'

function newChatSession(): ChatSession {
  return { id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, title: '新会话', messages: [], updatedAt: Date.now() }
}

function readChatSessions(): ChatSession[] {
  const value = wx.getStorageSync(chatStorageKey)
  if (!Array.isArray(value)) return []
  let hasEmpty = false
  return value.filter((item) => {
    if (!item || typeof item.id !== 'string' || !Array.isArray(item.messages)) return false
    if (item.messages.length) return true
    if (hasEmpty) return false
    hasEmpty = true
    return true
  }).slice(0, 12)
}

function summarizeQuestion(question: string): string {
  const compact = question.replace(/^(请问|我想问一下|想咨询一下)/, '').replace(/[？?。！!]/g, '').trim()
  const firstClause = compact.split(/[，,；;]/)[0]
  return Array.from(firstClause || compact).slice(0, 10).join('') || '新会话'
}

const profileLabels: Record<string, string> = {
  nickname: '建议昵称',
  alias: '别名',
  gender: '性别',
  age: '年龄',
  ageBand: '年龄',
  coat: '毛色',
  weight: '体重',
  personality: '性格',
  personalityTags: '性格标签',
  health: '健康状况',
  healthSummary: '健康状况',
  status: '当前状态',
  location: '发现地点',
  story: '救助记录',
  notes: '补充建议',
}

function profileEntries(profile: Record<string, string>): ProfileEntry[] {
  return Object.entries(profile)
    .filter(([, value]) => typeof value === 'string' && value.trim())
    .slice(0, 12)
    .map(([key, value]) => ({ key, label: profileLabels[key] || key, value: value.trim() }))
}

Page({
  data: {
    mode: 'profile' as AiMode,
    description: '',
    descriptionCount: 0,
    submitting: false,
    errorMessage: '',
    profile: {} as Record<string, string>,
    entries: [] as ProfileEntry[],
    hasProfile: false,
    question: '',
    answering: false,
    chatSessions: [] as ChatSession[],
    activeSessionId: '',
    activeSessionTitle: '新会话',
    messages: [] as ChatMessage[],
    sessionMenuOpen: false,
    scrollIntoView: '',
    prompts: [
      { title: '现场发现', description: '整理发现与处置情况', query: '在小区花坛发现一只橘白幼猫，约三个月大，右前腿轻微擦伤，精神尚可，已经安置到安全处。' },
      { title: '检查记录', description: '归纳健康与照护重点', query: '猫咪已完成基础检查，体重 2.6kg，食欲正常，怕生但不攻击，医生建议一周后复查。' },
      { title: '领养准备', description: '梳理公开信息与性格', query: '这只母猫亲人，喜欢逗猫棒，已绝育和驱虫，适合有耐心的家庭领养。' },
    ] as SuggestedPrompt[],
  },

  onShow() {
    this.getTabBar()?.setData({ selected: 3, ...getApp<IAppOption>().globalData.noticePosition })
  },

  onLoad() {
    const sessions = readChatSessions()
    const active = sessions[0] || newChatSession()
    const chatSessions = sessions.length ? sessions : [active]
    this.setData({ chatSessions, activeSessionId: active.id, activeSessionTitle: active.title, messages: active.messages })
    wx.setStorageSync(chatStorageKey, chatSessions)
  },

  selectMode(event: WechatMiniprogram.TouchEvent) {
    const mode = event.currentTarget.dataset.mode as AiMode
    if (mode === 'profile' || mode === 'ask') this.setData({ mode, errorMessage: '' })
  },

  updateDescription(event: WechatMiniprogram.Input) {
    const description = event.detail.value
    this.setData({ description, descriptionCount: description.length })
  },

  selectPrompt(event: WechatMiniprogram.TouchEvent) {
    const description = String(event.currentTarget.dataset.prompt || '')
    if (!description) return
    this.setData({ description, descriptionCount: description.length, errorMessage: '' })
  },

  updateQuestion(event: WechatMiniprogram.Input) {
    this.setData({ question: event.detail.value, errorMessage: '' })
  },

  toggleSessionMenu() {
    this.setData({ sessionMenuOpen: !this.data.sessionMenuOpen })
  },

  createChatSession() {
    if (!this.data.messages.length) return
    const active = newChatSession()
    const chatSessions = [active, ...this.data.chatSessions].slice(0, 12)
    wx.setStorageSync(chatStorageKey, chatSessions)
    this.setData({ chatSessions, activeSessionId: active.id, activeSessionTitle: active.title, messages: [], question: '', sessionMenuOpen: false, scrollIntoView: '' })
  },

  selectChatSession(event: WechatMiniprogram.TouchEvent) {
    const id = String(event.currentTarget.dataset.id || '')
    const active = this.data.chatSessions.find((item) => item.id === id)
    if (!active) return
    const last = active.messages[active.messages.length - 1]
    this.setData({ activeSessionId: active.id, activeSessionTitle: active.title, messages: active.messages, sessionMenuOpen: false, scrollIntoView: last?.id || '' })
  },

  deleteChatSession(event: WechatMiniprogram.TouchEvent) {
    const id = String(event.currentTarget.dataset.id || '')
    if (!id) return
    wx.showModal({
      title: '删除会话',
      content: '删除后无法恢复。',
      confirmColor: '#8b4a5d',
      success: ({ confirm }) => {
        if (!confirm) return
        let chatSessions = this.data.chatSessions.filter((item) => item.id !== id)
        if (!chatSessions.length) chatSessions = [newChatSession()]
        wx.setStorageSync(chatStorageKey, chatSessions)
        if (id !== this.data.activeSessionId) return void this.setData({ chatSessions })
        const active = chatSessions[0]
        const last = active.messages[active.messages.length - 1]
        this.setData({ chatSessions, activeSessionId: active.id, activeSessionTitle: active.title, messages: active.messages, sessionMenuOpen: false, scrollIntoView: last?.id || '' })
      },
    })
  },

  saveChat(messages: ChatMessage[], title: string) {
    const activeSessionId = this.data.activeSessionId
    const current = this.data.chatSessions.find((item) => item.id === activeSessionId) || newChatSession()
    const updated = { ...current, id: activeSessionId || current.id, title, messages, updatedAt: Date.now() }
    const chatSessions = [updated, ...this.data.chatSessions.filter((item) => item.id !== activeSessionId)].slice(0, 12)
    wx.setStorageSync(chatStorageKey, chatSessions)
    this.setData({ chatSessions, activeSessionId: updated.id, activeSessionTitle: updated.title, messages, scrollIntoView: messages[messages.length - 1]?.id || '' })
  },

  async askQuestion() {
    const question = this.data.question.trim()
    if (!question || this.data.answering) return
    const session = await refreshSessionWhenNeeded()
    if (!session?.accessToken) {
      wx.navigateTo({ url: '/package-account/pages/auth/index?redirect=/pages/ai/index' })
      return
    }
    const history = this.data.messages.slice(-12)
    const userMessage: ChatMessage = { id: `user-${Date.now()}`, role: 'user', content: question }
    const pendingMessages = [...this.data.messages, userMessage]
    const title = this.data.activeSessionTitle === '新会话' ? summarizeQuestion(question) : this.data.activeSessionTitle
    this.setData({ answering: true, errorMessage: '', question: '' })
    this.saveChat(pendingMessages, title)
    try {
      const result = await askAi(question, history, session.accessToken)
      const assistantMessage: ChatMessage = { id: `assistant-${Date.now()}`, role: 'assistant', content: result.answer }
      this.saveChat([...pendingMessages, assistantMessage], title)
    } catch (error) {
      this.setData({ errorMessage: error instanceof Error ? error.message : 'AI 暂时无法回答，请稍后重试' })
    } finally {
      this.setData({ answering: false })
    }
  },

  async generateProfile() {
    const description = this.data.description.trim()
    if (!description) {
      wx.showToast({ title: '请先描述猫咪的情况', icon: 'none' })
      return
    }
    if (this.data.submitting) return

    const session = await refreshSessionWhenNeeded()
    if (!session?.accessToken) {
      wx.navigateTo({ url: '/package-account/pages/auth/index?redirect=/pages/ai/index' })
      return
    }

    this.setData({ submitting: true, errorMessage: '' })
    try {
      const result = await assistCatProfile(description, session.accessToken)
      const entries = profileEntries(result.profile)
      this.setData({ profile: result.profile, entries, hasProfile: entries.length > 0 })
      if (!entries.length) this.setData({ errorMessage: '暂未提取到可用字段，请补充更具体的信息。' })
    } catch (error) {
      this.setData({ errorMessage: error instanceof Error ? error.message : '生成建议失败，请稍后重试' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  clearProfile() {
    this.setData({ profile: {}, entries: [], hasProfile: false, errorMessage: '' })
  },

  applyProfile() {
    if (!this.data.hasProfile) return
    wx.setStorageSync('catlink:ai-profile-pending:v1', this.data.profile)
    wx.navigateTo({ url: '/package-vault/pages/index/index' })
  },
})
