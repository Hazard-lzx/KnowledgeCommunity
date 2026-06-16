<!-- 左侧导航栏：Logo + 菜单 + 用户信息 -->
<template>
  <aside class="nav-sidebar glass">
    <div class="nav-logo" @click="$router.push('/')">
      <span class="logo-icon">K</span>
      <span class="logo-text">知识社区</span>
    </div>

    <nav class="nav-menu">
      <router-link
        v-for="item in navItems"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        :class="{ active: isActive(item.path) }"
      >
        <el-icon :size="20"><component :is="item.icon" /></el-icon>
        <span class="nav-label">{{ item.label }}</span>
      </router-link>
    </nav>

    <div class="nav-footer">
      <el-popover
        v-if="authStore.isLoggedIn"
        placement="top-start"
        :width="140"
        trigger="click"
        :offset="8"
      >
        <template #reference>
          <div class="user-info">
            <el-avatar :size="36" :src="authStore.user?.avatarUrl">
              {{ authStore.user?.username?.charAt(0) }}
            </el-avatar>
            <span class="username">{{ authStore.user?.username }}</span>
          </div>
        </template>
        <div class="user-popover">
          <div class="popover-item" @click="$router.push('/profile/me'); closePopover()">
            <el-icon><UserFilled /></el-icon>
            <span>我的</span>
          </div>
          <div class="popover-item logout" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            <span>退出登录</span>
          </div>
        </div>
      </el-popover>
      <el-button v-else type="primary" round @click="$router.push('/login')">登录</el-button>
    </div>
  </aside>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { UserFilled, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const navItems = [
  { path: '/', label: '首页', icon: 'HomeFilled' },
  { path: '/search', label: '搜索', icon: 'Search' },
  { path: '/publish', label: '创作', icon: 'EditPen' },
  { path: '/agent', label: 'AI 创作', icon: 'MagicStick' },
  { path: '/profile/me', label: '我的', icon: 'UserFilled' },
]

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

function closePopover() {
  // el-popover click 会自动关闭
}

async function handleLogout() {
  await authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.nav-sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: $sidebar-width;
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(0, 0, 0, 0.06);
  z-index: 100;

  @media (max-width: $mobile) {
    transform: translateX(-100%);
  }
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 24px 20px;
  cursor: pointer;

  .logo-icon {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: $gradient;
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 18px;
  }

  .logo-text {
    font-size: 18px;
    font-weight: 700;
    background: $gradient;
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

.nav-menu {
  flex: 1;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 10px;
  color: $text-secondary;
  transition: $transition;
  font-size: 15px;

  &:hover {
    background: rgba(108, 99, 255, 0.08);
    color: $primary;
  }

  &.active {
    background: rgba(108, 99, 255, 0.12);
    color: $primary;
    font-weight: 600;
  }
}

.nav-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);

  .user-info {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;
    padding: 8px;
    border-radius: 10px;
    transition: $transition;

    &:hover {
      background: rgba(108, 99, 255, 0.08);
    }
  }

  .username {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }
}

.user-popover {
  display: flex;
  flex-direction: column;
  gap: 4px;

  .popover-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 8px;
    cursor: pointer;
    font-size: 14px;
    color: $text-primary;
    transition: $transition;

    &:hover {
      background: rgba(108, 99, 255, 0.08);
      color: $primary;
    }

    &.logout {
      color: #f56c6c;
      &:hover {
        background: rgba(245, 108, 108, 0.08);
      }
    }
  }
}
</style>
