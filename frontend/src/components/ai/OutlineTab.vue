<template>
  <div class="assist-tab">
    <div class="label">文章标题</div>
    <el-input
      v-model="title"
      placeholder="输入标题生成大纲..."
      size="default"
      clearable
    />

    <el-button
      type="primary"
      round
      :disabled="!title || loading"
      :loading="loading"
      @click="handleGenerate"
      style="width: 100%; margin-top: 12px"
    >
      {{ loading ? '生成中...' : '生成大纲' }}
    </el-button>

    <div class="result-area" v-if="result || loading">
      <div class="label">大纲结果</div>
      <div class="result-scroll">
        <StreamText :text="result" :streaming="loading" />
      </div>
    </div>

    <el-button
      v-if="result && !loading"
      type="primary"
      round
      plain
      style="width: 100%; margin-top: 8px"
      @click="$emit('insert', result)"
    >
      插入到编辑器
    </el-button>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import StreamText from '@/components/common/StreamText.vue'
import { writingAssist } from '@/api/ai'

const props = defineProps({
  editorTitle: { type: String, default: '' },
  context: { type: String, default: '' },
})

defineEmits(['insert'])

const title = ref('')
const result = ref('')
const loading = ref(false)

watch(() => props.editorTitle, (val) => {
  if (!title.value && val) {
    title.value = val
  }
}, { immediate: true })

async function handleGenerate() {
  if (!title.value || loading.value) return
  result.value = ''
  loading.value = true

  try {
    const response = await writingAssist('outline', title.value, props.context)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    // 30秒超时保护
    const timeout = setTimeout(() => {
      reader.cancel()
      loading.value = false
    }, 30000)

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const parts = buffer.split('\n')
        buffer = parts.pop() || ''

        for (const part of parts) {
          if (!part.startsWith('data:')) continue
          const data = part.slice(5).trim()
          if (!data || data === '[DONE]') continue
          result.value += data
        }
      }
    } finally {
      clearTimeout(timeout)
    }
  } catch (e) {
    result.value = '生成失败，请稍后重试'
  }
  loading.value = false
}
</script>

<style lang="scss" scoped>
.assist-tab {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.label {
  font-size: 12px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 4px;
}

.result-area {
  margin-top: 8px;
}

.result-scroll {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
  color: $text-primary;
}
</style>