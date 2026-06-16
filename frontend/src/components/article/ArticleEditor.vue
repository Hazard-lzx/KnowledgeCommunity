<!-- 文章编辑器：Markdown 编辑 + 封面上传 + 标签 + 发布/草稿 + AI助手 -->
<template>
  <div class="article-editor-layout">
    <!-- 左侧编辑区 -->
    <div class="editor-main">
      <div class="editor-header">
        <el-input
          v-model="title"
          placeholder="请输入文章标题..."
          size="large"
          class="title-input"
        />
      </div>

      <el-upload
        class="cover-upload"
        :show-file-list="false"
        :before-upload="handleCoverUpload"
        accept=".jpg,.jpeg,.png"
        drag
      >
        <div v-if="coverUrl" class="cover-preview">
          <img :src="coverUrl" alt="封面预览" />
          <div class="cover-overlay">点击更换封面</div>
        </div>
        <div v-else class="cover-placeholder">
          <el-icon :size="32"><Picture /></el-icon>
          <span class="cover-text">上传封面</span>
          <span class="cover-hint">支持 JPG、PNG 格式</span>
        </div>
      </el-upload>

      <v-md-editor
        ref="editorRef"
        v-model="content"
        height="500px"
        placeholder="开始写作..."
        @selectionchange="onSelectionChange"
      />

      <div class="editor-footer">
        <el-input
          v-model="tags"
          placeholder="标签，逗号分隔"
          class="tags-input"
        />
        <div class="editor-actions">
          <el-button round @click="saveDraft">存草稿</el-button>
          <el-button type="primary" round @click="publish" :loading="publishing">
            {{ isEdit ? '更新' : '发布' }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 右侧 AI 助手面板（桌面端） -->
    <div class="editor-assistant" v-if="showAssistant && isDesktop">
      <AiAssistant
        :selected-text="selectedText"
        :editor-title="title"
        :context="content"
        @insert="handleInsert"
        @replace="handleReplace"
      />
    </div>

    <!-- 平板/移动端折叠按钮 -->
    <div class="ai-fab" @click="showAssistant = !showAssistant" v-if="!isDesktop">
      <el-icon :size="20"><MagicStick /></el-icon>
    </div>

    <!-- 移动端 AI 面板抽屉 -->
    <el-drawer
      v-model="showAssistant"
      title="AI 写作助手"
      direction="rtl"
      size="360px"
      :append-to-body="true"
      class="ai-drawer"
      v-if="!isDesktop"
    >
      <AiAssistant
        :selected-text="selectedText"
        :editor-title="title"
        :context="content"
        @insert="handleInsert"
        @replace="handleReplace"
      />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, MagicStick } from '@element-plus/icons-vue'
import { createArticle, getArticle, updateArticle } from '@/api/article'
import { uploadFile } from '@/api/ai'
import AiAssistant from '@/components/ai/AiAssistant.vue'

const route = useRoute()
const router = useRouter()

const title = ref('')
const content = ref('')
const tags = ref('')
const coverUrl = ref('')
const publishing = ref(false)
const isEdit = ref(false)
const showAssistant = ref(false)
const selectedText = ref('')
const editorRef = ref(null)
const isDesktop = ref(window.innerWidth > 900)
let articleId = null

function handleResize() {
  isDesktop.value = window.innerWidth > 900
  if (isDesktop.value) {
    showAssistant.value = true
  }
}

onMounted(() => {
  showAssistant.value = isDesktop.value
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

onMounted(async () => {
  if (route.params.id) {
    isEdit.value = true
    articleId = route.params.id
    const res = await getArticle(articleId)
    title.value = res.data.title
    content.value = res.data.content
    tags.value = res.data.tags?.join(',') || ''
    coverUrl.value = res.data.coverUrl || ''
  }

  // 从 AI Agent 跳转过来，携带文章内容
  if (route.query.agent && route.query.content) {
    const agentContent = decodeURIComponent(route.query.content)
    content.value = agentContent
    // 尝试从内容中提取标题（第一个 # 标题行）
    const titleMatch = agentContent.match(/^#\s+(.+)$/m)
    if (titleMatch) {
      title.value = titleMatch[1].trim()
    }
  }
})

/** 编辑器选中文本变化 */
function onSelectionChange() {
  if (editorRef.value) {
    selectedText.value = editorRef.value.getSelection() || ''
  }
}

/** 插入文本到编辑器光标位置 */
function handleInsert(text) {
    const ta = document.querySelector(".v-md-textarea-editor textarea")
    if (ta) {
      const start = ta.selectionStart
      const end = ta.selectionEnd
      const before = content.value.substring(0, start)
      const after = content.value.substring(end)
      content.value = before + text + after
      requestAnimationFrame(() => {
        const pos = start + text.length
        ta.setSelectionRange(pos, pos)
        ta.focus()
      })
    } else {
      content.value = (content.value || "") + "\n\n" + text
    }
  }


/** 替换编辑器中选中的文本 */
function handleReplace(text) {
  if (editorRef.value) {
    // 先获取当前选区，替换 content 中的选中文本
    const sel = selectedText.value
    if (sel) {
      content.value = content.value.replace(sel, text)
      selectedText.value = ''
    }
  }
}

async function handleCoverUpload(file) {
  const res = await uploadFile(file)
  coverUrl.value = res.data.objectUrl
  return false
}

async function saveDraft() {
  await doSave(0)
}

async function publish() {
  if (!title.value.trim()) return ElMessage.warning('请输入标题')
  if (!content.value.trim()) return ElMessage.warning('请输入内容')
  publishing.value = true
  try {
    await doSave(1)
    router.push('/')
  } finally {
    publishing.value = false
  }
}

async function doSave(status) {
  const data = {
    title: title.value,
    content: content.value,
    tags: tags.value,
    coverUrl: coverUrl.value,
    status,
  }
  if (isEdit.value) {
    await updateArticle(articleId, data)
    ElMessage.success('更新成功')
  } else {
    await createArticle(data)
    ElMessage.success(status === 1 ? '发布成功' : '已存为草稿')
  }
}
</script>

<style lang="scss" scoped>
.article-editor-layout {
  display: flex;
  gap: 20px;
  max-width: 1600px;
  margin: 0 auto;
}

.editor-main {
  flex: 7;
  min-width: 0;
}

.editor-assistant {
  flex: 3;
  min-width: 280px;
  max-width: 360px;

  @media (max-width: 900px) {
    display: none;
  }
}

/* 移动端浮动按钮 */
.ai-fab {
  position: fixed;
  bottom: 40px;
  right: 40px;
  display: none;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  background: $gradient;
  color: white;
  border-radius: 50%;
  box-shadow: 0 4px 20px rgba(108, 99, 255, 0.4);
  cursor: pointer;
  transition: $transition;
  z-index: 100;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 24px rgba(108, 99, 255, 0.5);
  }

  @media (max-width: 900px) {
    display: flex;
  }
}

.title-input {
  :deep(.el-input__wrapper) {
    box-shadow: none !important;
    border: 1px solid #94a3b8;
    border-radius: 12px;
    font-size: 24px;
    font-weight: 600;
    padding: 4px 16px;
    background: rgba(219, 234, 254, 0.5);

    &:focus-within {
      border-color: $primary;
    }
  }

  :deep(.el-input__inner) {
    height: 48px;
  }
}

.cover-upload {
  width: 100%;
  margin: 16px 0;

  :deep(.el-upload) {
    width: 100%;
  }

  :deep(.el-upload-dragger) {
    width: 100%;
    border-radius: 12px;
    border: 1px solid #94a3b8;
    padding: 0;
    background: rgba(219, 234, 254, 0.5);
    transition: $transition;

    &:hover {
      border-color: $primary;
    }
  }
}

.cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: $text-muted;

  .cover-text {
    font-size: 16px;
    font-weight: 500;
    color: $text-secondary;
  }

  .cover-hint {
    font-size: 12px;
    color: $text-muted;
  }
}

.cover-preview {
  position: relative;
  width: 100%;
  border-radius: 12px;
  overflow: hidden;

  img {
    width: 100%;
    max-height: 300px;
    object-fit: cover;
    display: block;
  }

  .cover-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.4);
    color: white;
    font-size: 14px;
    font-weight: 500;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover .cover-overlay {
    opacity: 1;
  }
}

.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0;
  gap: 16px;
}

:deep(.v-md-editor) {
  border-radius: 12px;
  border: 1px solid #94a3b8;

  .v-md-textarea-editor {
    background: rgba(219, 234, 254, 0.5);
  }

  .v-md-editor-preview {
    background: rgba(219, 234, 254, 0.3);
  }
}

.tags-input {
  width: 300px;

  :deep(.el-input__wrapper) {
    border-radius: 20px;
  }
}

.editor-actions {
  display: flex;
  gap: 12px;
}

/* 移动端抽屉样式 */
:deep(.ai-drawer) {
  .el-drawer__body {
    padding: 16px;
  }
}
</style>
