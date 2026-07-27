<!-- 首页/关注 Feed 流：瀑布流 + 无限滚动 -->
<template>
  <div class="feed-view">
    <div class="feed-header">
      <h1 class="feed-title">{{ isFollowing ? '你的关注' : 'It\'s never too old to learn.' }}</h1>
      <p class="feed-subtitle" v-if="isFollowing && feedStore.items.length === 0 && !feedStore.loading">
        关注一些创作者，这里将展示他们的最新文章
      </p>
    </div>

    <!-- 骨架屏 -->
    <div class="waterfall" v-if="feedStore.items.length === 0 && feedStore.loading">
      <SkeletonCard v-for="i in 8" :key="i" />
    </div>

    <!-- 瀑布流 -->
    <div class="waterfall" v-else>
      <ArticleCard
        v-for="article in feedStore.items"
        :key="article.id"
        :article="article"
      />
    </div>

    <!-- 无限滚动哨兵 -->
    <div ref="sentinel" class="scroll-sentinel">
      <el-icon v-if="feedStore.loading" class="is-loading" :size="24"><Loading /></el-icon>
      <span v-else-if="!feedStore.hasMore" class="no-more">没有更多了</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import ArticleCard from '@/components/article/ArticleCard.vue'
import SkeletonCard from '@/components/common/SkeletonCard.vue'
import { useFeedStore } from '@/stores/feed'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'

const route = useRoute()
const feedStore = useFeedStore()

const isFollowing = computed(() => route.meta.mode === 'following')

const { sentinel } = useInfiniteScroll(
  () => feedStore.fetchNext(),
  () => feedStore.hasMore
)

onMounted(() => {
  const mode = route.meta.mode || 'all'
  feedStore.reset(mode)
  feedStore.fetchNext()
})

// 路由切换时重新加载
watch(
  () => route.meta.mode,
  (newMode) => {
    feedStore.reset(newMode || 'all')
    feedStore.fetchNext()
  }
)
</script>

<style lang="scss" scoped>
.feed-view {
  max-width: 1600px;
  margin: 0 auto;
}

.feed-header {
  margin-bottom: 28px;
  background: linear-gradient(135deg, #8B83FF, #60A5FA);
  padding: 20px 24px;
  border-radius: 12px;
}

.feed-title {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
}

.feed-subtitle {
  color: $text-secondary;
  font-size: 14px;
  margin-top: 4px;
}

.scroll-sentinel {
  text-align: center;
  padding: 32px 0;
  color: $text-muted;
}

.no-more {
  font-size: 13px;
}
</style>
