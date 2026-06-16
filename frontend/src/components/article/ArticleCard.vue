<!-- 文章卡片：瀑布流子项，含封面/标题/摘要/标签/作者/统计 -->
<template>
  <div class="article-card card waterfall-item" @click="goDetail">
    <div class="card-cover" :style="coverStyle">
      <img v-if="article.coverUrl" :src="article.coverUrl" :alt="article.title" />
    </div>
    <div class="card-body">
      <h3 class="card-title">{{ article.title }}</h3>
      <p class="card-summary" v-if="article.summary">{{ article.summary }}</p>
      <div class="card-tags" v-if="article.tags?.length">
        <span class="tag" v-for="tag in article.tags.slice(0, 3)" :key="tag">{{ tag }}</span>
      </div>
      <div class="card-footer">
        <div class="author" @click.stop="goProfile">
          <UserAvatar :avatar-url="article.avatarUrl" :username="article.username" :size="24" />
          <span class="author-name">{{ article.username }}</span>
        </div>
        <div class="card-stats">
          <span class="stat-item" :class="{ 'is-liked': article.liked }">
            <el-icon :size="14">
              <svg v-if="article.liked" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 896c-8.2 0-16.4-2.8-23-8.4C461.6 865.2 64 534.4 64 328 64 209.6 160.6 112 280 112c78.4 0 152.4 38.6 232 118.2C591.6 150.6 665.6 112 744 112c119.4 0 216 97.6 216 216 0 206.4-397.6 537.2-425 559.6-6.6 5.6-14.8 8.4-23 8.4z" fill="currentColor"/></svg>
              <svg v-else viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 896c-8 0-16-3-22.4-8.6C461.6 865 64 534 64 328 64 209.6 160.6 112 280 112c78.4 0 152.4 38.6 232 118.2C591.6 150.6 665.6 112 744 112c119.4 0 216 97.6 216 216 0 206-397.6 537-425 559.4-6.4 5.6-14.4 8.6-22.4 8.6zM280 160c-92.8 0-168 75.2-168 168 0 148.2 289.8 405.4 400 497.2 110.2-91.8 400-349 400-497.2 0-92.8-75.2-168-168-168-66.4 0-132.8 34.4-208 112l-24 25.2-24-25.2C412.8 194.4 346.4 160 280 160z" fill="currentColor"/></svg>
            </el-icon>
            {{ formatCount(article.likeCount) }}
          </span>
          <span class="stat-item" :class="{ 'is-collected': article.collected }">
            <el-icon :size="14"><StarFilled v-if="article.collected" /><Star v-else /></el-icon>
            {{ formatCount(article.collectCount) }}
          </span>
          <span class="stat-item">
            <el-icon :size="14"><View /></el-icon>
            {{ formatCount(article.viewCount) }}
          </span>
        </div>
        <span class="card-time">{{ relativeTime(article.createTime) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import relativeTimePlugin from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import UserAvatar from '@/components/common/UserAvatar.vue'

dayjs.extend(relativeTimePlugin)
dayjs.locale('zh-cn')

const props = defineProps({
  article: { type: Object, required: true },
})

const router = useRouter()

const gradients = [
  'linear-gradient(135deg, #667eea, #764ba2)',
  'linear-gradient(135deg, #f093fb, #f5576c)',
  'linear-gradient(135deg, #4facfe, #00f2fe)',
  'linear-gradient(135deg, #43e97b, #38f9d7)',
  'linear-gradient(135deg, #fa709a, #fee140)',
  'linear-gradient(135deg, #a18cd1, #fbc2eb)',
]

const coverStyle = computed(() => {
  if (props.article.coverUrl) return {}
  const idx = (props.article.id || 0) % gradients.length
  return { background: gradients[idx], minHeight: '120px' }
})

function relativeTime(time) {
  return dayjs(time).fromNow()
}

function formatCount(count) {
  if (!count) return 0
  if (count >= 10000) return (count / 10000).toFixed(1) + 'w'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k'
  return count
}

function goDetail() {
  router.push(`/article/${props.article.id}`)
}

function goProfile() {
  router.push(`/profile/${props.article.userId}`)
}
</script>

<style lang="scss" scoped>
.article-card {
  cursor: pointer;
}

.card-cover {
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 100%;
    display: block;
    object-fit: cover;
  }
}

.card-body {
  padding: 14px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 8px;
}

.card-summary {
  font-size: 13px;
  color: $text-secondary;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 10px;
}

.card-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 12px;

  .tag {
    font-size: 11px;
    padding: 2px 8px;
    border-radius: 20px;
    background: rgba(108, 99, 255, 0.08);
    color: $primary;
  }
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: $text-muted;

  .author {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    min-width: 0;
  }

  .author-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 12px;
    color: $text-secondary;
  }

  .card-stats {
    display: flex;
    align-items: center;
    gap: 8px;

    .stat-item {
      display: inline-flex;
      align-items: center;
      gap: 2px;

      &.is-liked {
        color: #f56c6c;
      }

      &.is-collected {
        color: #e6a23c;
      }
    }
  }

  .card-time {
    font-size: 11px;
    white-space: nowrap;
  }
}
</style>
