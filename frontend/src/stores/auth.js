/** 认证状态管理：登录/登出、Token 持久化 */
import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi } from '@/api/auth'
import { getUserProfile } from '@/api/user'
import router from '@/router'

/** 从 JWT payload 解析用户信息 */
function parseToken(token) {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(jsonPayload)
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    accessToken: localStorage.getItem('accessToken') || '',
    isLoggedIn: !!localStorage.getItem('accessToken'),
  }),

  actions: {
    async login(credentials) {
      const res = await loginApi(credentials)
      this.accessToken = res.data.accessToken
      this.isLoggedIn = true
      localStorage.setItem('accessToken', res.data.accessToken)
      // 从 JWT 解析用户信息
      const payload = parseToken(res.data.accessToken)
      if (payload) {
        this.user = {
          userId: Number(payload.sub),
          username: payload.username,
        }
        // 获取完整用户信息（含头像）
        try {
          const profile = await getUserProfile(this.user.userId)
          if (profile.data) {
            this.user.avatarUrl = profile.data.avatarUrl
          }
        } catch (e) {
          // 忽略获取头像失败
        }
      }
    },

    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 忽略登出请求错误
      }
      this.accessToken = ''
      this.isLoggedIn = false
      this.user = null
      localStorage.removeItem('accessToken')
      router.push('/login')
    },

    /** 初始化：从 localStorage 恢复登录状态 */
    async init() {
      if (this.accessToken) {
        const payload = parseToken(this.accessToken)
        if (payload) {
          this.user = {
            userId: Number(payload.sub),
            username: payload.username,
          }
          // 获取完整用户信息（含头像）
          try {
            const profile = await getUserProfile(this.user.userId)
            if (profile.data) {
              this.user.avatarUrl = profile.data.avatarUrl
            }
          } catch (e) {
            // 忽略获取头像失败
          }
        }
      }
    },
  },
})
