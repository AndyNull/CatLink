import {
  createCommunityPost,
  type CommunityTopic,
} from '../../../services/community'
import { refreshSessionWhenNeeded } from '../../../services/auth'
import { uploadPostImage } from '../../../services/media'

const maxImageCount = 9
const topicOptions = [
  { value: 'rescue', label: '救助' },
  { value: 'seeking_adoption', label: '求领养' },
  { value: 'general', label: '交流' },
] as const

function isTopic(value: unknown): value is CommunityTopic {
  return topicOptions.some((option) => option.value === value)
}

function createPostId(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (flag) => {
    const random = Math.floor(Math.random() * 16)
    return (flag === 'x' ? random : (random & 0x3) | 0x8).toString(16)
  })
}

function choosePostImages(count: number): Promise<string[]> {
  return new Promise((resolve, reject) => {
    wx.chooseMedia({
      count,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: ({ tempFiles }) => resolve(tempFiles.map((file) => file.tempFilePath)),
      fail: reject,
    })
  })
}

Page({
  data: {
    topicOptions,
    topic: 'rescue' as CommunityTopic,
    title: '',
    body: '',
    district: '',
    imagePaths: [] as string[],
    bodyCount: 0,
    publishing: false,
    maxImageCount,
  },

  selectTopic(event: WechatMiniprogram.TouchEvent) {
    const topic = event.currentTarget.dataset.topic
    if (isTopic(topic)) this.setData({ topic })
  },

  updateField(event: WechatMiniprogram.Input) {
    const value = event.detail.value
    const field = event.currentTarget.dataset.field
    if (field === 'title') this.setData({ title: value })
    if (field === 'body') this.setData({ body: value, bodyCount: value.length })
    if (field === 'district') this.setData({ district: value })
  },

  chooseImages() {
    const remaining = maxImageCount - this.data.imagePaths.length
    if (remaining <= 0) {
      wx.showToast({ title: `最多添加 ${maxImageCount} 张图片`, icon: 'none' })
      return
    }
    choosePostImages(remaining)
      .then((paths) => this.setData({ imagePaths: [...this.data.imagePaths, ...paths].slice(0, maxImageCount) }))
      .catch(() => undefined)
  },

  removeImage(event: WechatMiniprogram.TouchEvent) {
    const index = Number(event.currentTarget.dataset.index)
    if (!Number.isInteger(index)) return
    this.setData({ imagePaths: this.data.imagePaths.filter((_, current) => current !== index) })
  },

  async publish() {
    const title = this.data.title.trim()
    const body = this.data.body.trim()
    if (!title || !body) {
      wx.showToast({ title: '请填写标题和内容', icon: 'none' })
      return
    }
    if (this.data.publishing) return

    const session = await refreshSessionWhenNeeded()
    if (!session?.accessToken) {
      wx.redirectTo({ url: '/package-account/pages/auth/index?redirect=/package-community/pages/index/index' })
      return
    }

    this.setData({ publishing: true })
    try {
      const uploads = await Promise.all(this.data.imagePaths.map((path) => uploadPostImage(path, session.accessToken)))
      await createCommunityPost({
        id: createPostId(),
        topic: this.data.topic,
        title,
        body,
        districtCode: this.data.district.trim(),
        mediaIds: uploads.map((upload) => upload.id),
      }, session.accessToken)
      wx.showToast({ title: '动态已发布', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 450)
    } catch (error) {
      wx.showToast({ title: error instanceof Error ? error.message : '发布失败，请稍后重试', icon: 'none' })
      this.setData({ publishing: false })
    }
  },
})
