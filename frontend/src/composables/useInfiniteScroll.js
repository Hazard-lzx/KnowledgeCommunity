/** 无限滚动：IntersectionObserver 监听哨兵元素，触底加载更多 */
import { ref, onMounted, onUnmounted, isRef } from 'vue'

export function useInfiniteScroll(loadMore, hasMore) {
  const sentinel = ref(null)
  let observer = null

  /** 兼容 ref 和 getter 函数两种写法 */
  function getHasMore() {
    if (isRef(hasMore)) return hasMore.value
    if (typeof hasMore === 'function') return hasMore()
    return hasMore
  }

  onMounted(() => {
    observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && getHasMore()) {
          loadMore()
        }
      },
      { rootMargin: '200px' }
    )
    if (sentinel.value) {
      observer.observe(sentinel.value)
    }
  })

  onUnmounted(() => {
    if (observer) {
      observer.disconnect()
    }
  })

  return { sentinel }
}
