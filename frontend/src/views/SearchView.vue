<!-- 搜索页：关键词搜索 + 标签过滤 + 联想建议 + 无限滚动 -->
<template>
  <div class="search-view">
      <div class="search-header">
        <el-input
          v-model="keyword"
          placeholder="搜索文章..."
          size="large"
          class="search-input"
          @keyup.enter="doSearch"
          clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>


      </div>

      <!-- 联想下拉 -->
      <div class="suggest-list" v-if="suggestions.length && showSuggest">
        <div
          v-for="s in suggestions"
          :key="s"
          class="suggest-item"
          @click="keyword = s; showSuggest = false; doSearch()"
        >
          {{ s }}
        </div>
      </div>

      <!-- 搜索结果：仅在搜索后才展示 -->
      <div class="search-results" v-if="searched && results.length">
        <ArticleCard
          v-for="item in results"
          :key="item.id"
          :article="item"
        />
      </div>

      <div class="empty-state" v-if="searched && results.length === 0">
        <el-empty description="没有找到相关内容" />
      </div>

      <!-- 未搜索时的提示 -->
      <div class="empty-state" v-if="!searched">
        <el-empty description="输入关键词开始搜索" />
      </div>

      <div ref="sentinel" class="scroll-sentinel" v-if="searched">
        <el-icon v-if="loading" class="is-loading" :size="24"><Loading /></el-icon>
      </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'SearchView' })
import { ref, watch } from 'vue'
import ArticleCard from '@/components/article/ArticleCard.vue'
import { search, suggest } from '@/api/search'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'

const keyword = ref('')
const selectedTag = ref('')
const results = ref([])
const searchAfter = ref('')
const hasMore = ref(true)
const loading = ref(false)
const searched = ref(false)
const suggestions = ref([])
const showSuggest = ref(false)
const tagSuggestions = ref(['前端', '后端', 'AI', '算法', '架构'])

let suggestTimer = null

watch(keyword, (val) => {
  clearTimeout(suggestTimer)
  if (!val.trim()) {
    suggestions.value = []
    showSuggest.value = false
    return
  }
  suggestTimer = setTimeout(async () => {
    const res = await suggest(val)
    suggestions.value = res.data
    showSuggest.value = res.data.length > 0
  }, 300)
})

async function doSearch() {
  showSuggest.value = false
  results.value = []
  searchAfter.value = ''
  hasMore.value = true
  searched.value = true
  await loadMore()
}

function toggleTag(tag) {
  selectedTag.value = selectedTag.value === tag ? '' : tag
  doSearch()
}

async function loadMore() {
  if (!searched.value || loading.value || !hasMore.value) return
  loading.value = true
  try {
    const res = await search({
      keyword: keyword.value,
      tag: selectedTag.value,
      size: 10,
      searchAfter: searchAfter.value || undefined,
    })
    results.value.push(...res.data.hits)
    searchAfter.value = res.data.searchAfter
    hasMore.value = res.data.hasMore
  } finally {
    loading.value = false
  }
}

const { sentinel } = useInfiniteScroll(loadMore, hasMore)
</script>

<style lang="scss" scoped>
.search-view {
  max-width: 1000px;
  margin: 0 auto;
}

.search-header {
  margin-bottom: 24px;
}

.search-input {
  :deep(.el-input__wrapper) {
    border-radius: 12px;
    padding: 4px 16px;
  }
}

.tag-filter {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  flex-wrap: wrap;

  .filter-tag {
    cursor: pointer;
    border-radius: 20px;
  }
}

.suggest-list {
  background: white;
  border-radius: 12px;
  box-shadow: $card-shadow-hover;
  margin-bottom: 16px;
  overflow: hidden;
  position: relative;
  z-index: 10;
}

.suggest-item {
  padding: 12px 20px;
  cursor: pointer;
  transition: $transition;

  &:hover {
    background: rgba(108, 99, 255, 0.06);
  }
}

.search-results {
  column-count: 3;
  column-gap: 20px;

  @media (max-width: $tablet) {
    column-count: 2;
  }
  @media (max-width: $mobile) {
    column-count: 1;
  }
}

.empty-state {
  padding: 60px 0;
}

.scroll-sentinel {
  text-align: center;
  padding: 32px 0;
  color: $text-muted;
}
</style>
