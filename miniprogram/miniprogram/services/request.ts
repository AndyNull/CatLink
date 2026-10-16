import { API_BASE_URL } from '../env'
import type { ApiEnvelope } from '../models/api'

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
type RequestData = string | WechatMiniprogram.IAnyObject | ArrayBuffer

interface RequestOptions<TBody extends RequestData> {
  path: string
  method?: HttpMethod
  data?: TBody
  token?: string
}

export function request<TResponse, TBody extends RequestData = WechatMiniprogram.IAnyObject>(
  options: RequestOptions<TBody>,
): Promise<TResponse> {
  if (!API_BASE_URL) return Promise.reject(new Error('API 域名未配置'))

  return new Promise((resolve, reject) => {
    wx.request<ApiEnvelope<TResponse>>({
      url: `${API_BASE_URL}${options.path}`,
      method: (options.method ?? 'GET') as WechatMiniprogram.RequestOption['method'],
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        'X-Client-Platform': 'wechat-miniprogram',
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      },
      timeout: 10000,
      success(response) {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error(response.data.msg || `请求失败（${response.statusCode}）`))
          return
        }
        if (response.data.code !== 0 && response.data.code !== 200) {
          reject(new Error(response.data.msg || '请求失败'))
          return
        }
        resolve(response.data.data)
      },
      fail: reject,
    })
  })
}
