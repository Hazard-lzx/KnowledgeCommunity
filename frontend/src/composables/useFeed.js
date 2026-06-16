/** Feed 流组合式函数：封装分页加载逻辑 */
import { useFeedStore } from '@/stores/feed'

export function useFeed() {
  const feedStore = useFeedStore()

  async function loadMore() {
    await feedStore.fetchNext()
  }

  function reset() {
    feedStore.reset()
  }

  return {
    items: feedStore.items,
    cursor: feedStore.cursor,
    hasMore: feedStore.hasMore,
    loading: feedStore.loading,
    loadMore,
    reset,
  }
}
