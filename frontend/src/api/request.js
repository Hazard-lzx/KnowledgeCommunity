/**
 * Axios 封装：统一请求/响应拦截
 * - 请求拦截：自动附加 Bearer Token
 * - 响应拦截：统一处理 code!=200 和 401 跳转登录
 */
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截
request.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.accessToken) {
    config.headers.Authorization = `Bearer ${authStore.accessToken}`
  }
  return config
})

// 响应拦截
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      router.push('/login')
    } else {
      ElMessage.error(error.response?.data?.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
