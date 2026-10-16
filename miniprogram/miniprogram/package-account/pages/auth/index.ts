import {
  getAuthPolicy,
  loginWithEmail,
  loginWithWechat,
  registerWithEmail,
  requestCaptcha,
  resendVerification,
  verifyEmail,
} from '../../../services/auth'

type AuthMode = 'login' | 'register' | 'verify'

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未完成，请稍后重试'
}

function isEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

function isStrongPassword(value: string): boolean {
  const kinds = [/[a-z]/.test(value), /[A-Z]/.test(value), /\d/.test(value), /[^A-Za-z\d]/.test(value)]
  return value.length >= 8 && value.length <= 128 && kinds.filter(Boolean).length >= 3
}

function captchaImagePath(value: string, captchaId: string): string {
  const base64 = value.replace(/^data:image\/[A-Za-z0-9.+-]+;base64,/, '')
  const path = `${wx.env.USER_DATA_PATH}/catlink-captcha-${captchaId}.png`
  wx.getFileSystemManager().writeFileSync(path, base64, 'base64')
  return path
}

function decodeRedirect(value: string | undefined): string {
  if (!value) return ''
  try {
    const redirect = decodeURIComponent(value)
    return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : ''
  } catch {
    return ''
  }
}

Page({
  data: {
    mode: 'login' as AuthMode,
    email: '',
    password: '',
    captcha: '',
    captchaId: '',
    captchaImage: '',
    captchaRequired: true,
    captchaLength: 6,
    emailReady: true,
    loading: false,
    refreshingCaptcha: false,
    redirect: '',
  },

  onLoad(query: Record<string, string>) {
    this.setData({ redirect: decodeRedirect(query.redirect) })
    this.loadPolicy()
  },

  async loadPolicy() {
    this.setData({ loading: true })
    try {
      const policy = await getAuthPolicy()
      this.setData({
        captchaRequired: policy.captchaRequired,
        captchaLength: policy.captchaLength,
        emailReady: policy.emailReady,
      })
      if (policy.captchaRequired) await this.refreshCaptcha()
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async refreshCaptcha() {
    this.setData({ refreshingCaptcha: true, captcha: '' })
    try {
      const challenge = await requestCaptcha(this.data.captchaId)
      this.setData({
        captchaId: challenge.captchaId,
        captchaImage: captchaImagePath(challenge.imageBase64, challenge.captchaId),
        captchaLength: challenge.length,
      })
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
    } finally {
      this.setData({ refreshingCaptcha: false })
    }
  },

  selectMode(event: WechatMiniprogram.TouchEvent) {
    const mode = event.currentTarget.dataset.mode as AuthMode
    if (mode !== 'login' && mode !== 'register' && mode !== 'verify') return
    this.setData({ mode })
  },

  updateField(event: WechatMiniprogram.Input) {
    const field = event.currentTarget.dataset.field
    const value = event.detail.value
    if (field === 'email') this.setData({ email: value })
    if (field === 'password') this.setData({ password: value })
    if (field === 'captcha') this.setData({ captcha: value })
  },

  async submit() {
    const email = this.data.email.trim().toLowerCase()
    if (!isEmail(email)) {
      wx.showToast({ title: '请输入正确的邮箱地址', icon: 'none' })
      return
    }

    if (this.data.mode === 'verify') {
      await this.submitVerification(email)
      return
    }

    if (this.data.mode === 'register') {
      await this.submitRegistration(email)
      return
    }

    await this.submitLogin(email)
  },

  async submitWechatLogin() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      await loginWithWechat()
      wx.showToast({ title: '登录成功', icon: 'success' })
      this.finishAuthentication()
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async submitLogin(email: string) {
    if (!this.data.password) {
      wx.showToast({ title: '请输入密码', icon: 'none' })
      return
    }
    if (this.data.captchaRequired && (!this.data.captchaId || !this.data.captcha.trim())) {
      wx.showToast({ title: '请输入图形验证码', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    try {
      await loginWithEmail({
        email,
        password: this.data.password,
        captchaId: this.data.captchaId,
        captcha: this.data.captcha.trim(),
      })
      wx.showToast({ title: '登录成功', icon: 'success' })
      this.finishAuthentication()
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
      if (this.data.captchaRequired) await this.refreshCaptcha()
    } finally {
      this.setData({ loading: false })
    }
  },

  async submitRegistration(email: string) {
    if (!this.data.emailReady) {
      wx.showToast({ title: '邮箱验证暂不可用', icon: 'none' })
      return
    }
    if (!isStrongPassword(this.data.password)) {
      wx.showToast({ title: '密码需为8-128位且包含三类字符', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    try {
      await registerWithEmail(email, this.data.password)
      this.setData({ mode: 'verify', password: '', captcha: '' })
      wx.showToast({ title: '验证码已发送', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async submitVerification(email: string) {
    const code = this.data.captcha.trim()
    if (!code) {
      wx.showToast({ title: '请输入邮箱验证码', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    try {
      await verifyEmail(email, code)
      wx.showToast({ title: '邮箱已验证', icon: 'success' })
      this.finishAuthentication()
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async resendCode() {
    const email = this.data.email.trim().toLowerCase()
    if (!isEmail(email)) {
      wx.showToast({ title: '请输入正确的邮箱地址', icon: 'none' })
      return
    }
    try {
      await resendVerification(email)
      wx.showToast({ title: '验证码已发送', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: 'none' })
    }
  },

  finishAuthentication() {
    const redirect = this.data.redirect
    if (redirect) {
      const pagePath = redirect.split('?')[0]
      if (['/pages/home/index', '/pages/archive/index', '/pages/community/index', '/pages/ai/index', '/pages/profile/index'].includes(pagePath)) {
        wx.switchTab({ url: pagePath })
        return
      }
      wx.redirectTo({ url: redirect })
      return
    }
    if (getCurrentPages().length > 1) {
      wx.navigateBack()
      return
    }
    wx.redirectTo({ url: '/package-account/pages/index/index' })
  },
})
