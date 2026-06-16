/** 认证组合式函数：封装登录/登出逻辑 */
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

export function useAuth() {
  const authStore = useAuthStore()
  const router = useRouter()

  const isLoggedIn = computed(() => authStore.isLoggedIn)
  const user = computed(() => authStore.user)

  async function login(credentials) {
    await authStore.login(credentials)
    router.push('/')
  }

  async function logout() {
    await authStore.logout()
  }

  return { isLoggedIn, user, login, logout }
}
