<!-- 文章详情：Markdown 渲染 + 点赞/收藏/关注 + AI 问答面板 -->
<template>
  <div class="article-detail-page" v-if="article">
    <div class="back-nav" @click="$router.back()">
      <el-icon :size="20"><ArrowLeft /></el-icon>
      <span>返回</span>
    </div>

    <div class="detail-cover" v-if="article.coverUrl" @click="previewCover = true">
      <img :src="article.coverUrl" :alt="article.title" />
      <div class="cover-zoom-hint">
        <el-icon :size="14"><ZoomIn /></el-icon>
        <span>点击查看原图</span>
      </div>
    </div>

    <!-- 图片预览遮罩 -->
    <div class="cover-preview-overlay" v-if="previewCover" @click="previewCover = false">
      <img :src="article.coverUrl" :alt="article.title" class="cover-preview-img" />
    </div>

    <div class="article-detail-layout">
      <!-- 文章主体 -->
      <div class="article-detail" :class="{ 'with-qa': showQa }">
        <article class="detail-main">
          <h1 class="detail-title">{{ article.title }}</h1>

          <div class="detail-meta">
            <div class="author-info" @click="$router.push(`/profile/${article.userId}`)">
              <UserAvatar :avatar-url="article.avatarUrl" :username="article.username" :size="40" />
              <div class="author-text">
                <span class="author-name">{{ article.username }}</span>
                <span class="publish-time">{{ formatTime(article.createTime) }}</span>
              </div>
            </div>
            <el-button
              v-if="!isOwner"
              :type="isFollowed ? 'default' : 'primary'"
              round
              size="small"
              @click="toggleFollow"
            >
              {{ isFollowed ? '已关注' : '关注' }}
            </el-button>
            <div v-if="isOwner" class="owner-actions">
              <el-button type="primary" round size="small" @click="$router.push(`/publish/${article.id}`)">
                编辑
              </el-button>
              <el-button type="danger" round size="small" @click="handleDelete">
                删除
              </el-button>
            </div>
          </div>

          <div class="detail-tags" v-if="article.tags?.length">
            <el-tag v-for="tag in article.tags" :key="tag" size="small" round>{{ tag }}</el-tag>
          </div>

          <v-md-preview :text="article.content" class="markdown-body" />

          <div class="detail-actions">
            <el-button
              :type="article.liked ? 'danger' : 'default'"
              round
              @click="handleLike"
            >
              <el-icon>
                <svg v-if="article.liked" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 896c-8.2 0-16.4-2.8-23-8.4C461.6 865.2 64 534.4 64 328 64 209.6 160.6 112 280 112c78.4 0 152.4 38.6 232 118.2C591.6 150.6 665.6 112 744 112c119.4 0 216 97.6 216 216 0 206.4-397.6 537.2-425 559.6-6.6 5.6-14.8 8.4-23 8.4z" fill="currentColor"/></svg>
                <svg v-else viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 896c-8 0-16-3-22.4-8.6C461.6 865 64 534 64 328 64 209.6 160.6 112 280 112c78.4 0 152.4 38.6 232 118.2C591.6 150.6 665.6 112 744 112c119.4 0 216 97.6 216 216 0 206-397.6 537-425 559.4-6.4 5.6-14.4 8.6-22.4 8.6zM280 160c-92.8 0-168 75.2-168 168 0 148.2 289.8 405.4 400 497.2 110.2-91.8 400-349 400-497.2 0-92.8-75.2-168-168-168-66.4 0-132.8 34.4-208 112l-24 25.2-24-25.2C412.8 194.4 346.4 160 280 160z" fill="currentColor"/></svg>
              </el-icon>
              {{ article.liked ? '已点赞' : '点赞' }} {{ article.likeCount }}
            </el-button>
            <el-button
              :type="article.collected ? 'warning' : 'default'"
              round
              @click="handleCollect"
            >
              <el-icon><StarFilled v-if="article.collected" /><Star v-else /></el-icon>
              {{ article.collected ? '已收藏' : '收藏' }} {{ article.collectCount }}
            </el-button>
          </div>
        </article>
      </div>

      <!-- 右侧 AI 问答面板 -->
      <transition name="slide">
        <div class="qa-sidebar" v-if="showQa">
          <ArticleQaPanel :article-id="article.id" @close="showQa = false" />
        </div>
      </transition>
    </div>

    <!-- 悬浮 AI 入口 -->
    <div class="ai-fab" @click="showQa = true" v-if="!showQa">
      <el-icon :size="24"><ChatDotRound /></el-icon>
      <span>问 AI</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { getArticle } from '@/api/article'
import { deleteArticle } from '@/api/article'
import { likeArticle, unlikeArticle, collectArticle, uncollectArticle } from '@/api/interaction'
import { followUser, unfollowUser } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import UserAvatar from '@/components/common/UserAvatar.vue'
import ArticleQaPanel from '@/components/ai/ArticleQaPanel.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const article = ref(null)
const isFollowed = ref(false)
const showQa = ref(false)
const previewCover = ref(false)

const isOwner = computed(() => article.value?.userId === authStore.user?.userId)

onMounted(async () => {
  const res = await getArticle(route.params.id)
  article.value = res.data
  isFollowed.value = res.data.followed ?? false
})

function formatTime(time) {
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

async function handleLike() {
  if (!authStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  if (article.value.liked) {
    const res = await unlikeArticle(article.value.id)
    article.value.liked = false
    article.value.likeCount = res.data.count
  } else {
    const res = await likeArticle(article.value.id)
    article.value.liked = true
    article.value.likeCount = res.data.count
  }
}

async function handleCollect() {
  if (!authStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  if (article.value.collected) {
    const res = await uncollectArticle(article.value.id)
    article.value.collected = false
    article.value.collectCount = res.data.count
  } else {
    const res = await collectArticle(article.value.id)
    article.value.collected = true
    article.value.collectCount = res.data.count
  }
}

async function toggleFollow() {
  if (!authStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  if (isFollowed.value) {
    await unfollowUser(article.value.userId)
  } else {
    await followUser(article.value.userId)
  }
  isFollowed.value = !isFollowed.value
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定要删除这篇文章吗？删除后不可恢复。', '删除文章', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteArticle(article.value.id)
    ElMessage.success('文章已删除')
    router.push('/')
  } catch {
    // 错误已在拦截器中处理
  }
}
</script>

<style lang="scss" scoped>
.article-detail-page {
  max-width: 1200px;
  margin: 0 auto;
  position: relative;
}

.article-detail-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.article-detail {
  flex: 1;
  min-width: 0;
  transition: all 0.3s ease;

  &.with-qa {
    max-width: 800px;
  }
}

.qa-sidebar {
  width: 380px;
  flex-shrink: 0;
  position: sticky;
  top: 68px;

  @media (max-width: 900px) {
    position: fixed;
    right: 0;
    top: 48px;
    bottom: 0;
    width: 360px;
    z-index: 200;
  }
}

/* 滑入动画 */
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.back-nav {
  position: fixed;
  left: calc(50% - 440px);
  top: 20px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border-radius: 20px;
  background: white;
  box-shadow: $card-shadow;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
  transition: $transition;
  z-index: 10;

  &:hover {
    background: #f5f5f5;
    transform: translateX(-2px);
  }

  @media (max-width: 960px) {
    left: 16px;
  }
}

.detail-cover {
  border-radius: $card-radius;
  overflow: hidden;
  margin-bottom: 24px;
  position: relative;
  cursor: pointer;

  img {
    width: 100%;
    display: block;
    object-fit: cover;
    max-height: 400px;
  }

  .cover-zoom-hint {
    position: absolute;
    bottom: 12px;
    right: 12px;
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 6px 12px;
    background: rgba(0, 0, 0, 0.5);
    color: white;
    border-radius: 16px;
    font-size: 12px;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover .cover-zoom-hint {
    opacity: 1;
  }
}

.cover-preview-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  cursor: zoom-out;

  .cover-preview-img {
    max-width: 90vw;
    max-height: 90vh;
    object-fit: contain;
    border-radius: 8px;
  }
}

.detail-main {
  background: white;
  border-radius: $card-radius;
  padding: 32px;
  box-shadow: $card-shadow;
}

.detail-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.4;
  margin-bottom: 20px;
}

.detail-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.author-text {
  display: flex;
  flex-direction: column;
}

.author-name {
  font-weight: 600;
  font-size: 15px;
}

.publish-time {
  font-size: 12px;
  color: $text-muted;
}

.owner-actions {
  display: flex;
  gap: 8px;
}

.detail-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
  margin-top: 24px;
}

.ai-fab {
  position: fixed;
  bottom: 40px;
  right: 40px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 20px;
  background: $gradient;
  color: white;
  border-radius: 28px;
  box-shadow: 0 4px 20px rgba(108, 99, 255, 0.4);
  cursor: pointer;
  transition: $transition;
  font-size: 14px;
  font-weight: 500;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 24px rgba(108, 99, 255, 0.5);
  }
}
</style>
