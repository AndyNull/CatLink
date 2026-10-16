/// <reference types="miniprogram-api-typings" />

interface IAppOption {
  globalData: {
    apiBaseUrl: string
    noticePosition?: { noticeLeft: number; noticeTop: number }
  }
}
