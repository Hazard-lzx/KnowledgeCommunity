/** Feed 流状态管理：游标分页加载 */
import { defineStore } from 'pinia'
import { getFeed } from '@/api/feed'

export const useFeedStore = defineStore('feed', {
  state: () => ({
    items: [],
    cursor: null,
    hasMore: true,
    loading: false,
  }),

  actions: {
    async fetchNext() {
      if (this.loading || !this.hasMore) return
      this.loading = true
      try {
        const res = await getFeed({ cursor: this.cursor, size: 10 })
        this.items.push(...res.data.items)
        this.cursor = res.data.nextCursor
        this.hasMore = res.data.hasMore
      } finally {
        this.loading = false
      }
    },

    reset() {
      this.items = []
      this.cursor = null
      this.hasMore = true
    },
  },
})
