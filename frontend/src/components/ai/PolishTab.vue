<template>
  <div class="assist-tab">
    <div class="selected-text" v-if="selectedText">
      <div class="label">选中文本</div>
      <div class="text-block">{{ selectedText }}</div>
    </div>
    <div class="no-selection" v-else>
      <el-icon :size="24" color="#c0c4cc"><Edit /></el-icon>
      <span>请在编辑器中选中一段文字</span>
    </div>

    <el-button
      type="primary"
      round
      :disabled="!selectedText || loading"
      :loading="loading"
      @click="handleGenerate"
      style="width: 100%; margin-top: 12px"
    >
      {{ loading ? '润色中...' : '开始润色' }}
    </el-button>

    <div class="result-area" v-if="result || loading">
      <div class="label">润色结果</div>
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
      @click="$emit('replace', result)"
    >
      替换选中文本
    </el-button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Edit } from '@element-plus/icons-vue'
import StreamText from '@/components/common/StreamText.vue'
import { writingAssist } from '@/api/ai'

const props = defineProps({
  selectedText: { type: String, default: '' },
  context: { type: String, default: '' },
})

defineEmits(['replace'])

const result = ref('')
const loading = ref(false)

async function handleGenerate() {
  if (!props.selectedText || loading.value) return
  result.value = ''
  loading.value = true

  try {
    const response = await writingAssist('polish', props.selectedText, props.context)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

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
  } catch (e) {
    result.value = '润色失败，请稍后重试'
  } finally {
    loading.value = false
  }
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

.selected-text .text-block {
  background: rgba(108, 99, 255, 0.06);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.6;
  color: $text-primary;
  max-height: 120px;
  overflow-y: auto;
}

.no-selection {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 0;
  color: $text-muted;
  font-size: 13px;
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