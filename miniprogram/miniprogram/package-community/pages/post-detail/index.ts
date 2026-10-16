import { API_BASE_URL } from '../../../env'
import type { CommunityComment, CommunityPostSummary } from '../../../models/api'
import { refreshSessionWhenNeeded } from '../../../services/auth'
import {
  addCommunityComment,
  getCommunityComments,
  getCommunityPost,
  toggleCommunityPostLike,
} from '../../../services/community'

interface PostDetailView extends CommunityPostSummary {
  topicLabel: string
  locationLabel: string
  createdLabel: string
  authorInitial: string
  media: string[]
}

interface CommentView extends CommunityComment {
  authorInitial: string
  createdLabel: string
  replyLabel: string
}

const topicLabels: Record<string, string> = {
  rescue: '救助',
  seeking_adoption: '求领养',
  general: '交流',
}

function resolveMedia(value: string): string {
  if (!value || /^https?:\/\//.test(value) || value.startsWith('wxfile://')) return value
  return `${API_BASE_URL}${value}`
}

function mediaList(value: string): string[] {
  const normalized = value.trim()
  if (!normalized) return []
  if (normalized.startsWith('[')) {
    try {
      const parsed: unknown = JSON.parse(normalized)
      if (Array.isArray(parsed)) {
        return parsed
          .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
          .map((item) => resolveMedia(item.trim()))
      }
    } catch {}
  }
  return normalized.split(',').map((item) => resolveMedia(item.trim())).filter(Boolean)
}

function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  const hour = `${date.getHours()}`.padStart(2, '0')
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  return `${year}.${month}.${day} ${hour}:${minute}`
}

function toPost(post: CommunityPostSummary): PostDetailView {
  const locationLabel = [post.province, post.cityCode, post.districtCode, post.street, post.community]
    .filter(Boolean)
    .join(' · ')
  const authorName = post.authorName || '猫友'
  return {
    ...post,
    authorName,
    topicLabel: topicLabels[post.topic] || '交流',
    locationLabel: locationLabel || '未标记地区',
    createdLabel: formatTime(post.createdAt),
    authorInitial: Array.from(authorName)[0] || '猫',
    media: mediaList(post.mediaUrls),
  }
}

function toComment(comment: CommunityComment): CommentView {
  const authorName = comment.authorName || '猫友'
  return {
    ...comment,
    authorName,
    authorInitial: Array.from(authorName)[0] || '猫',
    createdLabel: formatTime(comment.createdAt),
    replyLabel: comment.replyToName ? `回复 ${comment.replyToName}` : '',
  }
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

async function currentToken(): Promise<string> {
  try {
    return (await refreshSessionWhenNeeded())?.accessToken || ''
  } catch {
    return ''
  }
}

Page({
  data: {
    postId: '',
    post: null as PostDetailView | null,
    comments: [] as CommentView[],
    commentDraft: '',
    replyToId: '',
    replyToName: '',
    commentFocus: false,
    loading: true,
    sending: false,
    liking: false,
    errorMessage: '',
  },

  onLoad(query: Record<string, string>) {
    const postId = (query.id || '').trim()
    if (!postId) {
      this.setData({ loading: false, errorMessage: '未找到社区动态' })
      return
    }
    this.setData({ postId })
    void this.loadDetail()
  },

  onPullDownRefresh() {
    void this.loadDetail().finally(() => wx.stopPullDownRefresh())
  },

  async loadDetail() {
    const postId = this.data.postId
    if (!postId) return
    this.setData({ loading: true, errorMessage: '' })
    const token = await currentToken()
    try {
      const [post, commentResult] = await Promise.all([
        getCommunityPost(postId, token || undefined),
        getCommunityComments(postId, token || undefined),
      ])
      this.setData({ post: toPost(post), comments: commentResult.comments.map(toComment) })
    } catch (error) {
      this.setData({ errorMessage: errorMessage(error, '动态加载失败，请稍后重试') })
    } finally {
      this.setData({ loading: false })
    }
  },

  goLogin() {
    const redirect = encodeURIComponent(`/package-community/pages/post-detail/index?id=${this.data.postId}`)
    wx.navigateTo({ url: `/package-account/pages/auth/index?redirect=${redirect}` })
  },

  async toggleLike() {
    const post = this.data.post
    if (!post || this.data.liking) return
    const token = await currentToken()
    if (!token) {
      this.goLogin()
      return
    }

    this.setData({ liking: true })
    try {
      const result = await toggleCommunityPostLike(post.id, token)
      this.setData({
        post: {
          ...post,
          liked: result.liked,
          likeCount: Math.max(0, post.likeCount + (result.liked ? 1 : -1)),
        },
      })
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '点赞失败'), icon: 'none' })
    } finally {
      this.setData({ liking: false })
    }
  },

  updateComment(event: WechatMiniprogram.Input) {
    this.setData({ commentDraft: event.detail.value.slice(0, 1000) })
  },

  replyToComment(event: WechatMiniprogram.TouchEvent) {
    const id = String(event.currentTarget.dataset.id || '')
    const name = String(event.currentTarget.dataset.name || '')
    if (!id) return
    this.setData({ replyToId: id, replyToName: name, commentFocus: true })
  },

  cancelReply() {
    this.setData({ replyToId: '', replyToName: '', commentFocus: false })
  },

  async submitComment() {
    const body = this.data.commentDraft.trim()
    if (!body) {
      wx.showToast({ title: '请先填写评论', icon: 'none' })
      return
    }
    if (this.data.sending) return

    const token = await currentToken()
    if (!token) {
      this.goLogin()
      return
    }

    this.setData({ sending: true })
    try {
      await addCommunityComment(this.data.postId, body, token, this.data.replyToId)
      const commentResult = await getCommunityComments(this.data.postId, token)
      const post = this.data.post
      this.setData({
        comments: commentResult.comments.map(toComment),
        commentDraft: '',
        replyToId: '',
        replyToName: '',
        commentFocus: false,
        post: post ? { ...post, commentCount: post.commentCount + 1 } : null,
      })
      wx.showToast({ title: '评论已发布', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '评论发布失败'), icon: 'none' })
    } finally {
      this.setData({ sending: false })
    }
  },
})
