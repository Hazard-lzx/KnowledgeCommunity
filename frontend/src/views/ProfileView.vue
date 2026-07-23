<!-- 个人主页：头像/签名/统计 + 文章列表 + 编辑资料 -->
<template>
  <div class="profile-view" v-if="profile">
      <!-- 头部：头像 + 姓名 + 签名 + 统计 -->
      <div class="profile-header card">
        <div class="profile-top">
          <UserAvatar :avatar-url="profile.avatarUrl" :username="profile.username" :size="72" />
          <div class="profile-meta">
            <h2 class="profile-name">{{ profile.username }}</h2>
            <p class="profile-signature">{{ profile.signature || '这个人很懒，什么都没写~' }}</p>
          </div>
          <el-button v-if="isMe" size="small" round @click="showEditDialog = true">编辑资料</el-button>
        </div>
        <UserStats
          :following="profile.followingCount || 0"
          :followers="profile.followerCount || 0"
          :articles="profile.articleCount || 0"
          :likes="profile.likeCount || 0"
        />
      </div>

      <!-- 文章列表 -->
      <div class="profile-articles">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="已发布" name="published">
            <div class="waterfall waterfall-5" v-if="publishedArticles.length">
              <div class="card-wrapper" v-for="article in publishedArticles" :key="article.id">
                <ArticleCard :article="article" />
                <el-icon class="delete-icon" :size="18" @click.stop="handleDelete(article.id)"><Delete /></el-icon>
              </div>
            </div>
            <div v-else class="empty-tip">暂无已发布文章</div>
          </el-tab-pane>
          <el-tab-pane label="草稿" name="draft" v-if="isMe">
            <div class="draft-list" v-if="draftArticles.length">
              <div v-for="article in draftArticles" :key="article.id" class="draft-item card">
                <div class="draft-info">
                  <h4 class="draft-title">{{ article.title }}</h4>
                  <span class="draft-time">{{ formatTime(article.createTime) }}</span>
                </div>
                <div class="draft-actions">
                  <el-button size="small" @click="$router.push(`/publish/${article.id}`)">编辑</el-button>
                  <el-button size="small" type="danger" @click="handleDelete(article.id)">删除</el-button>
                </div>
              </div>
            </div>
            <div v-else class="empty-tip">暂无草稿</div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <el-dialog v-model="showEditDialog" title="编辑资料" width="480px">
        <el-form :model="editForm" label-width="80px">
          <el-form-item label="头像">
            <el-upload :show-file-list="false" :before-upload="handleAvatarUpload" accept="image/*">
              <UserAvatar :avatar-url="editForm.avatarUrl" :username="editForm.username" :size="60" />
            </el-upload>
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="editForm.username" />
          </el-form-item>
          <el-form-item label="签名">
            <el-input v-model="editForm.signature" type="textarea" :rows="3" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showEditDialog = false">取消</el-button>
          <el-button type="primary" @click="saveProfile">保存</el-button>
        </template>
      </el-dialog>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import UserAvatar from '@/components/common/UserAvatar.vue'
import UserStats from '@/components/user/UserStats.vue'
import ArticleCard from '@/components/article/ArticleCard.vue'
import { useAuthStore } from '@/stores/auth'
import { getUserProfile, updateProfile } from '@/api/user'
import { uploadFile } from '@/api/ai'
import { deleteArticle, getUserArticles } from '@/api/article'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const profile = ref(null)
const activeTab = ref('published')
const publishedArticles = ref([])
const draftArticles = ref([])
const showEditDialog = ref(false)
const editForm = reactive({ username: '', signature: '', avatarUrl: '' })

const isMe = computed(() => route.params.id === 'me')

onMounted(async () => {
  try {
    const userId = isMe.value ? authStore.user?.userId : route.params.id
    if (userId) {
      const res = await getUserProfile(userId)
      profile.value = res.data
      editForm.username = profile.value.username
      editForm.signature = profile.value.signature || ''
      editForm.avatarUrl = profile.value.avatarUrl || ''

      // 加载已发布文章
      const pubRes = await getUserArticles(userId, 1)
      publishedArticles.value = pubRes.data || []

      // 加载草稿文章
      const draftRes = await getUserArticles(userId, 0)
      draftArticles.value = draftRes.data || []
    }
  } catch {
    profile.value = {
      username: isMe.value ? authStore.user?.username || '我' : '用户',
      avatarUrl: authStore.user?.avatarUrl,
      signature: authStore.user?.signature || '',
      followingCount: 0,
      followerCount: 0,
      articleCount: 0,
      likeCount: 0,
    }
    editForm.username = profile.value.username
    editForm.signature = profile.value.signature
    editForm.avatarUrl = profile.value.avatarUrl
  }
})

function formatTime(time) {
  return dayjs(time).format('YYYY-MM-DD')
}

async function handleAvatarUpload(file) {
  const res = await uploadFile(file)
  editForm.avatarUrl = res.data.objectUrl
  return false
}

async function saveProfile() {
  try {
    await updateProfile(editForm)
    ElMessage.success('资料已更新')
    showEditDialog.value = false
    // 刷新页面数据
    const userId = isMe.value ? authStore.user?.userId : route.params.id
    if (userId) {
      const res = await getUserProfile(userId)
      profile.value = res.data
    }
  } catch {
    ElMessage.error('更新失败')
  }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除这篇文章吗？', '提示', { type: 'warning' })
    await deleteArticle(id)
    ElMessage.success('文章已删除')
    publishedArticles.value = publishedArticles.value.filter(a => a.id !== id)
  } catch {
    // 用户取消
  }
}
</script>

<style lang="scss" scoped>
.profile-view {
  max-width: 1200px;
  margin: 0 auto;
}

.profile-header {
  padding: 24px;
  margin-bottom: 20px;

  :deep(.user-stats) {
    justify-content: flex-start;
  }
}

.profile-top {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.profile-meta {
  flex: 1;
  min-width: 0;
}

.profile-name {
  font-size: 22px;
  font-weight: 700;
}

.profile-signature {
  color: $text-secondary;
  font-size: 14px;
  margin-top: 4px;
}

.profile-articles {
  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }
}

/* 五列瀑布流 */
.waterfall-5 {
  column-count: 5;
  column-gap: 14px;

  :deep(.article-card) {
    break-inside: avoid;
    margin-bottom: 14px;

    .card-body {
      padding: 10px;
    }

    .card-title {
      font-size: 13px;
      margin-bottom: 6px;
    }

    .card-summary {
      font-size: 12px;
      margin-bottom: 8px;
      -webkit-line-clamp: 2;
    }

    .card-tags .tag {
      font-size: 10px;
      padding: 1px 6px;
    }

    .card-footer {
      font-size: 11px;
      gap: 6px;
    }
  }
}

.card-wrapper {
  position: relative;
  break-inside: avoid;
  margin-bottom: 14px;

  .delete-icon {
    position: absolute;
    top: 8px;
    right: 8px;
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    background: rgba(245, 108, 108, 0.85);
    color: #fff;
    cursor: pointer;
    opacity: 0;
    transition: opacity 0.2s;
    z-index: 5;

    &:hover {
      background: #f56c6c;
    }
  }

  &:hover .delete-icon {
    opacity: 1;
  }
}

.empty-tip {
  text-align: center;
  color: $text-muted;
  padding: 60px 0;
  font-size: 14px;
}

/* 草稿列表 */
.draft-item {
  display: flex;
  align-items: center;
  padding: 14px 18px;
  margin-bottom: 10px;
}

.draft-info {
  flex: 1;
  min-width: 0;
}

.draft-title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-time {
  font-size: 12px;
  color: $text-muted;
}

.draft-actions {
  display: flex;
  gap: 8px;
  margin-left: 16px;
}

@media (max-width: 1100px) {
  .waterfall-5 { column-count: 4; }
}
@media (max-width: 900px) {
  .waterfall-5 { column-count: 3; }
}
@media (max-width: 600px) {
  .waterfall-5 { column-count: 2; }
}
</style>
