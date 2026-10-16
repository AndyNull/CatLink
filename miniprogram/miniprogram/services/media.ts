import { API_BASE_URL } from '../env'
import type { ApiEnvelope } from '../models/api'

export interface UploadedMedia {
  id: string
  url: string
}

function parseUploadResult(value: string): UploadedMedia {
  const response = JSON.parse(value) as ApiEnvelope<UploadedMedia>
  if ((response.code !== 0 && response.code !== 200) || !response.data?.id) {
    throw new Error(response.msg || '图片上传失败')
  }
  return response.data
}

export function uploadPostImage(filePath: string, token: string): Promise<UploadedMedia> {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${API_BASE_URL}/api/v1/media/upload`,
      filePath,
      name: 'file',
      formData: { category: 'post', temporary: 'true' },
      header: {
        Authorization: `Bearer ${token}`,
        'X-Client-Platform': 'wechat-miniprogram',
      },
      success: (response) => {
        try {
          if (response.statusCode < 200 || response.statusCode >= 300) throw new Error(`图片上传失败（${response.statusCode}）`)
          resolve(parseUploadResult(response.data))
        } catch (error) {
          reject(error)
        }
      },
      fail: reject,
    })
  })
}
