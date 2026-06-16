<!-- AI 问答面板：基于文章的 RAG 问答，SSE 流式响应，嵌入文章详情右侧 -->
<template>
  <div class="qa-panel">
    <div class="qa-header">
      <div class="header-left">
        <div class="header-ai-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a4 4 0 0 1 4 4c0 2-1.5 3.5-2 4l-1 3h-2l-1-3c-.5-.5-2-2-2-4a4 4 0 0 1 4-4z"/><path d="M12 10v8"/><path d="M10 18h4"/><path d="M17 13.5A6 6 0 0 1 12 22a6 6 0 0 1-5-2.5"/></svg>
        </div>
        <div class="header-text">
          <span class="qa-title">AI 问答</span>
          <span class="qa-subtitle">基于本文内容</span>
        </div>
      </div>
      <div class="header-actions">
        <button class="header-btn" @click="clearChat" title="清空对话">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        </button>
        <button class="header-btn close-btn" @click="$emit('close')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    </div>

    <div class="chat-messages" ref="messagesRef">
      <!-- 欢迎 -->
      <div class="welcome-msg" v-if="!messages.length">
        <div class="welcome-icon">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a4 4 0 0 1 4 4c0 2-1.5 3.5-2 4l-1 3h-2l-1-3c-.5-.5-2-2-2-4a4 4 0 0 1 4-4z"/><path d="M12 10v8"/><path d="M10 18h4"/><path d="M17 13.5A6 6 0 0 1 12 22a6 6 0 0 1-5-2.5"/></svg>
        </div>
        <p class="welcome-title">有什么想问的吗？</p>
        <p class="welcome-desc">我会基于这篇文章的内容帮你解答</p>
        <div class="welcome-suggestions">
          <button
            v-for="(s, i) in suggestions"
            :key="i"
            class="suggestion-btn"
            @click="question = s; sendQuestion()"
          >
            {{ s }}
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="chat-message"
        :class="msg.role"
      >
        <div class="message-avatar">
          <div v-if="msg.role === 'user'" class="user-avatar">{{ msg.userInitial || '我' }}</div>
          <div v-else class="ai-avatar">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a4 4 0 0 1 4 4c0 2-1.5 3.5-2 4l-1 3h-2l-1-3c-.5-.5-2-2-2-4a4 4 0 0 1 4-4z"/><path d="M12 10v8"/><path d="M10 18h4"/><path d="M17 13.5A6 6 0 0 1 12 22a6 6 0 0 1-5-2.5"/></svg>
          </div>
        </div>
        <div class="message-body">
          <div class="message-label">{{ msg.role === 'user' ? '你' : 'AI' }}</div>
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

      <!-- 加载 -->
      <div v-if="sending && messages.length && messages[messages.length-1].content === ''" class="thinking-indicator">
        <div class="thinking-dots"><span></span><span></span><span></span></div>
      </div>
    </div>

    <div class="chat-input">
      <div class="input-wrap">
        <input
          v-model="question"
          placeholder="输入你的问题..."
          @keydown.enter.prevent="sendQuestion"
          :disabled="sending"
          class="msg-input"
        />
        <button
          class="send-btn"
          :class="{ active: question.trim() }"
          :disabled="!question.trim() || sending"
          @click="sendQuestion"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import StreamText from '@/components/common/StreamText.vue'
import { askQuestion } from '@/api/ai'

const props = defineProps({
  articleId: { type: [String, Number], required: true },
})

defineEmits(['close'])

const question = ref('')
const sending = ref(false)
const messages = ref([])
const messagesRef = ref(null)

const suggestions = [
  '这篇文章主要讲了什么？',
  '帮我总结一下核心观点',
  '这篇文章对谁有帮助？',
]

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function clearChat() {
  messages.value = []
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
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 120000)
  try {
    const response = await askQuestion(props.articleId, q, controller.signal)
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const readTimeout = setTimeout(() => controller.abort(), 15000)
      let result
      try {
        result = await reader.read()
      } catch (e) {
        break
      }
      clearTimeout(readTimeout)

      const { done, value } = result
      if (done) break

      const text = decoder.decode(value, { stream: true })
      const lines = text.split('\n')
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (!data) continue
          if (data === '[DONE]') {
            reader.cancel()
            aiMsg.streaming = false
            sending.value = false
            clearTimeout(timeoutId)
            return
          }
          if (data.startsWith('[ERROR]')) {
            aiMsg.content += data.replace('[ERROR] ', '')
            break
          }
          aiMsg.content += data
          scrollToBottom()
        }
      }
    }

    reader.cancel()
    aiMsg.streaming = false
  } catch (e) {
    if (e.name === 'AbortError') {
      if (!aiMsg.content) {
        aiMsg.content = '回答超时，请稍后重试。'
      }
    } else {
      aiMsg.content = '抱歉，AI 回答出错，请稍后重试。'
    }
    aiMsg.streaming = false
  } finally {
    sending.value = false
    clearTimeout(timeoutId)
  }
}
</script>

<style lang="scss" scoped>
.qa-panel {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 48px);
  background: #f8f9fb;
  border-radius: $card-radius;
  box-shadow: $card-shadow;
  overflow: hidden;
}

/* 头部 */
.qa-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: white;
  border-bottom: 1px solid #eee;

  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .header-ai-icon {
    width: 32px;
    height: 32px;
    border-radius: 10px;
    background: linear-gradient(135deg, #6C63FF, #8B7FFF);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .header-text {
    display: flex;
    flex-direction: column;
  }

  .qa-title {
    font-size: 15px;
    font-weight: 600;
    color: #1a1a1a;
  }

  .qa-subtitle {
    font-size: 11px;
    color: #999;
  }

  .header-actions {
    display: flex;
    gap: 4px;
  }

  .header-btn {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    border: none;
    background: transparent;
    color: #999;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s;

    &:hover {
      background: #f0f0f0;
      color: #555;
    }
  }
}

/* 消息区 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: #ddd;
    border-radius: 2px;
  }
}

/* 欢迎 */
.welcome-msg {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 16px 24px;
  text-align: center;

  .welcome-icon {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    background: linear-gradient(135deg, #6C63FF, #8B7FFF);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 16px;
    box-shadow: 0 4px 16px rgba(108, 99, 255, 0.25);
  }

  .welcome-title {
    font-size: 16px;
    font-weight: 600;
    color: #1a1a1a;
    margin: 0 0 4px 0;
  }

  .welcome-desc {
    font-size: 13px;
    color: #999;
    margin: 0 0 20px 0;
  }

  .welcome-suggestions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
    max-width: 300px;
  }

  .suggestion-btn {
    padding: 10px 16px;
    border-radius: 10px;
    border: 1px solid #e8e8e8;
    background: white;
    font-size: 13px;
    color: #555;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      border-color: #6C63FF;
      color: #6C63FF;
      background: #f8f7ff;
    }
  }
}

/* 消息 */
.chat-message {
  display: flex;
  gap: 10px;

  &.user {
    flex-direction: row-reverse;

    .message-body {
      align-items: flex-end;
    }

    .message-content {
      background: linear-gradient(135deg, #6C63FF, #7B73FF);
      border: none;
      border-radius: 12px 4px 12px 12px;
      color: white;
    }
  }

  &.assistant {
    .message-body {
      align-items: flex-start;
    }

    .message-content {
      background: white;
      border: 1px solid #e8e8e8;
      border-radius: 4px 12px 12px 12px;
      color: #1a1a1a;
    }
  }
}

.message-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #ddd;
  color: #666;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.ai-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6C63FF, #8B7FFF);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  max-width: 80%;
}

.message-label {
  font-size: 11px;
  color: #aaa;
  padding: 0 4px;
}

.message-content {
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;

  :deep(.github-markdown-body) {
    padding: 0;
    font-size: 14px;
    line-height: 1.6;
  }

  :deep(.github-markdown-body p) {
    margin: 0 0 2px 0;
  }

  :deep(.github-markdown-body p:last-child) {
    margin-bottom: 0;
  }

  :deep(.github-markdown-body ul),
  :deep(.github-markdown-body ol) {
    margin: 0 0 2px 0;
    padding-left: 16px;
  }

  :deep(.github-markdown-body li) {
    margin-bottom: 0;
  }

  :deep(.github-markdown-body pre),
  :deep(.github-markdown-body blockquote) {
    margin: 4px 0;
  }
}

/* 加载动画 */
.thinking-indicator {
  display: flex;
  justify-content: flex-start;
  padding-left: 40px;

  .thinking-dots {
    display: flex;
    gap: 4px;

    span {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #6C63FF;
      animation: bounce 1.4s infinite ease-in-out both;

      &:nth-child(1) { animation-delay: -0.32s; }
      &:nth-child(2) { animation-delay: -0.16s; }
    }
  }
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

/* 输入区 */
.chat-input {
  padding: 12px 16px;
  background: white;
  border-top: 1px solid #eee;

  .input-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
    background: #f5f6f8;
    border-radius: 12px;
    padding: 4px 4px 4px 14px;
    transition: box-shadow 0.15s;

    &:focus-within {
      box-shadow: 0 0 0 2px rgba(108, 99, 255, 0.15);
    }
  }

  .msg-input {
    flex: 1;
    border: none;
    outline: none;
    background: transparent;
    font-size: 14px;
    color: #1a1a1a;
    padding: 8px 0;

    &::placeholder {
      color: #bbb;
    }
  }

  .send-btn {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    border: none;
    background: #ddd;
    color: white;
    cursor: not-allowed;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s;

    &.active {
      background: linear-gradient(135deg, #6C63FF, #7B73FF);
      cursor: pointer;

      &:hover {
        transform: scale(1.05);
        box-shadow: 0 2px 8px rgba(108, 99, 255, 0.3);
      }
    }
  }
}
</style>
