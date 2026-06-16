<!-- AI 问答页：基于文章的 RAG 问答，SSE 流式响应 -->
<template>
  <div class="qa-view">
      <div class="qa-header">
        <h2 class="qa-title gradient-text">AI 问答助手</h2>
        <p class="qa-subtitle">基于文章内容的智能问答</p>
      </div>

      <div class="chat-container">
        <div class="chat-messages" ref="messagesRef">
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="chat-message"
            :class="msg.role"
          >
            <div class="message-avatar">
              <UserAvatar v-if="msg.role === 'user'" :size="32" username="我" />
              <div v-else class="ai-avatar">AI</div>
            </div>
            <div class="message-content">
              <StreamText
                v-if="msg.streaming"
                :text="msg.content"
                :streaming="msg.streaming"
              />
              <v-md-preview v-else :text="msg.content" />
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="question"
            placeholder="输入你的问题..."
            size="large"
            @keyup.enter="sendQuestion"
            :disabled="sending"
          >
            <template #append>
              <el-button type="primary" @click="sendQuestion" :loading="sending">
                发送
              </el-button>
            </template>
          </el-input>
        </div>
      </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import UserAvatar from '@/components/common/UserAvatar.vue'
import StreamText from '@/components/common/StreamText.vue'
import { askQuestion } from '@/api/ai'

const route = useRoute()
const question = ref('')
const sending = ref(false)
const messages = ref([])
const messagesRef = ref(null)

const articleId = route.params.articleId

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function sendQuestion() {
  if (!question.value.trim() || sending.value) return

  const q = question.value.trim()
  question.value = ''

  messages.value.push({ role: 'user', content: q })
  messages.value.push({ role: 'assistant', content: '', streaming: true })
  scrollToBottom()

  sending.value = true
  const aiMsg = messages.value[messages.value.length - 1]

  try {
    const response = await askQuestion(articleId, q)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const text = decoder.decode(value, { stream: true })
      const lines = text.split('\n')
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data === '[DONE]') break
          aiMsg.content += data
          scrollToBottom()
        }
      }
    }

    aiMsg.streaming = false
  } catch (e) {
    aiMsg.content = '抱歉，AI 回答出错，请稍后重试。'
    aiMsg.streaming = false
  } finally {
    sending.value = false
  }
}
</script>

<style lang="scss" scoped>
.qa-view {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 48px);
}

.qa-header {
  margin-bottom: 20px;
}

.qa-title {
  font-size: 24px;
  font-weight: 700;
}

.qa-subtitle {
  color: $text-secondary;
  font-size: 14px;
  margin-top: 4px;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: $card-radius;
  box-shadow: $card-shadow;
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chat-message {
  display: flex;
  gap: 12px;

  &.user {
    flex-direction: row-reverse;

    .message-content {
      background: rgba(108, 99, 255, 0.1);
      border-radius: 16px 4px 16px 16px;
    }
  }

  &.assistant {
    .message-content {
      background: #f8f9fa;
      border-radius: 4px 16px 16px 16px;
    }
  }
}

.message-avatar {
  flex-shrink: 0;
}

.ai-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: $gradient;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;

  :deep(.github-markdown-body) {
    padding: 0;
    font-size: 14px;
  }
}

.chat-input {
  padding: 16px 20px;
  border-top: 1px solid #f0f0f0;
}
</style>
