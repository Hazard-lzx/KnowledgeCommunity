<!-- AI 创作 Agent 页面：左侧对话区 + 右侧输入区 -->
<template>
  <div class="agent-view">
    <!-- 左侧对话区 -->
    <div class="agent-chat">
      <div class="chat-header">
        <el-icon :size="20" color="#6C63FF"><MagicStick /></el-icon>
        <span>AI 创作助手</span>
      </div>

      <div class="chat-messages" ref="messagesRef">
        <!-- 空状态 -->
        <div class="empty-state" v-if="steps.length === 0">
          <el-icon :size="48" color="#c0c4cc"><MagicStick /></el-icon>
          <p>输入创作目标，AI 将自动规划并完成文章创作</p>
        </div>

        <!-- 步骤列表 -->
        <div v-for="(step, idx) in steps" :key="idx" class="step-item" :class="step.type">
          <div class="step-icon">
            <span v-if="step.type === 'thinking'">💭</span>
            <span v-else-if="step.type === 'tool_start'">🔧</span>
            <span v-else-if="step.type === 'tool_result'">📄</span>
            <span v-else-if="step.type === 'final_chunk'">✅</span>
            <span v-else-if="step.type === 'error'">❌</span>
          </div>
          <div class="step-content">
            <div class="step-label">
              {{ stepLabel(step.type) }}
            </div>
            <div class="step-text" v-if="step.type === 'final_chunk'">
              <v-md-preview :text="step.data" />
            </div>
            <div class="step-text" v-else>{{ step.data }}</div>
          </div>
        </div>

        <!-- 加载动画 -->
        <div v-if="executing" class="step-item thinking">
          <div class="step-icon">💭</div>
          <div class="step-content">
            <div class="step-label">思考中</div>
            <div class="thinking-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 最终操作按钮 -->
      <div class="chat-actions" v-if="finalArticle && !executing">
        <el-button type="primary" round @click="publishArticle">
          发布到社区
        </el-button>
        <el-button round @click="editArticle">
          继续编辑
        </el-button>
      </div>
    </div>

    <!-- 右侧输入区 -->
    <div class="agent-input">
      <div class="input-header">
        <span>创作设置</span>
      </div>

      <div class="input-form">
        <div class="form-item">
          <label>创作目标</label>
          <el-input
            v-model="goal"
            type="textarea"
            :rows="5"
            placeholder="例如：写一篇介绍 Spring Boot 3 新特性的文章"
            :disabled="executing"
          />
        </div>

        <div class="form-item">
          <label>风格</label>
          <el-select v-model="style" placeholder="选择风格" style="width: 100%" :disabled="executing">
            <el-option label="轻松易读" value="轻松易读" />
            <el-option label="专业严谨" value="专业严谨" />
            <el-option label="简洁精炼" value="简洁精炼" />
            <el-option label="生动有趣" value="生动有趣" />
          </el-select>
        </div>

        <div class="form-item">
          <label>目标字数：{{ wordCount }}</label>
          <el-slider
            v-model="wordCount"
            :min="200"
            :max="5000"
            :step="100"
            :disabled="executing"
          />
        </div>

        <el-button
          v-if="!executing"
          type="primary"
          round
          style="width: 100%"
          :disabled="!goal.trim()"
          @click="startCreate"
        >
          开始创作
        </el-button>

        <el-button
          v-else
          type="danger"
          round
          style="width: 100%"
          @click="abortCreate"
        >
          中止创作
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const goal = ref('')
const style = ref('轻松易读')
const wordCount = ref(2000)
const executing = ref(false)
const steps = ref([])
const finalArticle = ref('')
const messagesRef = ref(null)

let abortController = null

function stepLabel(type) {
  const labels = {
    thinking: '思考中',
    tool_start: '调用工具',
    tool_result: '工具结果',
    final_chunk: '创作完成',
    error: '出错',
  }
  return labels[type] || type
}

async function startCreate() {
  if (!goal.value.trim() || executing.value) return

  executing.value = true
  steps.value = []
  finalArticle.value = ''
  abortController = new AbortController()

  try {
    const token = authStore.accessToken
    const response = await fetch('/api/ai/agent/create', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify({
        goal: goal.value,
        style: style.value,
        wordCount: wordCount.value,
      }),
      signal: abortController.signal,
    })

    if (!response.ok) {
      const errText = await response.text()
      throw new Error(`HTTP ${response.status}: ${errText}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        // 流正常结束，确保停止加载状态
        executing.value = false
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      // 保留最后一行（可能不完整）
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const raw = line.slice(5).trim()
          if (!raw) continue

          try {
            const event = JSON.parse(raw)
            if (event.type === 'done') {
              executing.value = false
              return
            }

            steps.value.push({
              type: event.type,
              data: event.data,
            })

            // 捕获最终文章
            if (event.type === 'final_chunk') {
              finalArticle.value = event.data
            }

            // 自动滚动到底部
            await nextTick()
            if (messagesRef.value) {
              messagesRef.value.scrollTop = messagesRef.value.scrollHeight
            }
          } catch (e) {
            // 非 JSON 数据忽略
          }
        }
      }
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      steps.value.push({ type: 'error', data: '请求失败：' + e.message })
    }
  } finally {
    executing.value = false
    abortController = null
  }
}

function abortCreate() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  executing.value = false
  ElMessage.info('已中止创作')
}

function publishArticle() {
  const content = encodeURIComponent(finalArticle.value)
  router.push(`/publish?agent=1&content=${content}`)
}

function editArticle() {
  const content = encodeURIComponent(finalArticle.value)
  router.push(`/publish?agent=1&content=${content}`)
}
</script>

<style lang="scss" scoped>
.agent-view {
  display: flex;
  gap: 20px;
  max-width: 1200px;
  margin: 0 auto;
  height: calc(100vh - 48px);
  padding: 0 0 0 0;
}

.agent-chat {
  flex: 7;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: $card-radius;
  box-shadow: $card-shadow;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 16px;
  font-weight: 600;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: $text-muted;
  gap: 16px;

  p {
    font-size: 14px;
  }
}

.step-item {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  animation: fadeIn 0.3s ease;

  &.thinking .step-content {
    background: rgba(108, 99, 255, 0.06);
  }

  &.tool_start .step-content {
    background: rgba(64, 158, 255, 0.06);
  }

  &.tool_result .step-content {
    background: rgba(103, 194, 58, 0.06);
  }

  &.final_chunk .step-content {
    background: rgba(108, 99, 255, 0.08);
  }

  &.error .step-content {
    background: rgba(245, 108, 108, 0.08);
  }
}

.step-icon {
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 4px;
}

.step-content {
  flex: 1;
  border-radius: 10px;
  padding: 12px 16px;
  min-width: 0;
}

.step-label {
  font-size: 12px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 4px;
}

.step-text {
  font-size: 14px;
  line-height: 1.6;
  color: $text-primary;
  word-break: break-word;

  :deep(.github-markdown-body) {
    padding: 0;
    font-size: 14px;
  }
}

.thinking-dots {
  display: flex;
  gap: 4px;

  span {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: $primary;
    animation: bounce 1.4s infinite ease-in-out both;

    &:nth-child(1) { animation-delay: -0.32s; }
    &:nth-child(2) { animation-delay: -0.16s; }
  }
}

.chat-actions {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid #f0f0f0;
}

.agent-input {
  flex: 3;
  background: white;
  border-radius: $card-radius;
  box-shadow: $card-shadow;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.input-header {
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 16px;
  font-weight: 600;
}

.input-form {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;

  label {
    font-size: 13px;
    font-weight: 600;
    color: $text-secondary;
  }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

@media (max-width: $tablet) {
  .agent-view {
    flex-direction: column;
    height: auto;
  }

  .agent-chat {
    min-height: 60vh;
  }

  .agent-input {
    min-height: auto;
  }
}
</style>
