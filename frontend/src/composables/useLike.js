/** 点赞切换：乐观更新 liked 状态和计数 */
import { ref } from 'vue'
import { likeArticle, unlikeArticle } from '@/api/interaction'

export function useLike(articleId, initialLiked = false, initialCount = 0) {
  const liked = ref(initialLiked)
  const count = ref(initialCount)
  const loading = ref(false)

  async function toggleLike() {
    if (loading.value) return
    loading.value = true
    try {
      if (liked.value) {
        const res = await unlikeArticle(articleId)
        liked.value = false
        count.value = res.data.count
      } else {
        const res = await likeArticle(articleId)
        liked.value = true
        count.value = res.data.count
      }
    } finally {
      loading.value = false
    }
  }

  return { liked, count, loading, toggleLike }
}
