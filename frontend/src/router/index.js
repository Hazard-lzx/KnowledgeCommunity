/**
 * 路由配置
 * - meta.requiresAuth: 需要登录
 * - meta.guest: 仅游客可访问（已登录跳转首页）
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guest: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guest: true },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    children: [
      {
        path: '',
        name: 'Feed',
        component: () => import('@/views/FeedView.vue'),
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/SearchView.vue'),
      },
      {
        path: 'publish',
        name: 'Publish',
        component: () => import('@/views/PublishView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'publish/:id',
        name: 'EditArticle',
        component: () => import('@/views/PublishView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'article/:id',
        name: 'ArticleDetail',
        component: () => import('@/views/ArticleDetail.vue'),
      },
      {
        path: 'qa/:articleId',
        name: 'QA',
        component: () => import('@/views/QaView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'profile/:id',
        name: 'Profile',
        component: () => import('@/views/ProfileView.vue'),
      },
      {
        path: 'agent',
        name: 'Agent',
        component: () => import('@/views/AgentView.vue'),
        meta: { requiresAuth: true },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next('/login')
  } else if (to.meta.guest && authStore.isLoggedIn) {
    next('/')
  } else {
    next()
  }
})

export default router
