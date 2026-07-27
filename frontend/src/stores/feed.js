/** Feed 流状态管理：游标分页加载 */
import { defineStore } from 'pinia'
import { getFeed, getFollowingFeed } from '@/api/feed'

export const useFeedStore = defineStore('feed', {
  state: () => ({
    items: [],
    cursor: null,
    hasMore: true,
    loading: false,
    mode: 'all', // 'all' | 'following'
  }),

  actions: {
    async fetchNext() {
      if (this.loading || !this.hasMore) return
      this.loading = true
      try {
        const api = this.mode === 'following' ? getFollowingFeed : getFeed
        const res = await api({ cursor: this.cursor, size: 10 })
        this.items.push(...res.data.items)
        this.cursor = res.data.nextCursor
        this.hasMore = res.data.hasMore
      } finally {
        this.loading = false
      }
    },

    reset(mode = 'all') {
      this.items = []
      this.cursor = null
      this.hasMore = true
      this.mode = mode
    },
  },
})
