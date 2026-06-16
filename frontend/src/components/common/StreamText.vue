<template>
  <div class="stream-text">
    <span class="text-content">{{ displayedText }}</span>
    <span v-if="streaming" class="cursor-blink">|</span>
  </div>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'

const props = defineProps({
  text: { type: String, default: '' },
  streaming: { type: Boolean, default: false },
  speed: { type: Number, default: 30 },
})

const displayedText = ref('')
let timer = null

// 当文本变化时，流式追加新内容
watch(() => props.text, (newVal) => {
  if (!props.streaming) {
    displayedText.value = newVal
    return
  }
  const diff = newVal.slice(displayedText.value.length)
  if (!diff) return
  let i = 0
  clearInterval(timer)
  timer = setInterval(() => {
    if (i < diff.length) {
      displayedText.value += diff[i]
      i++
    } else {
      clearInterval(timer)
    }
  }, props.speed)
}, { immediate: true })

// 当 streaming 从 true 变为 false 时，直接显示完整文本
watch(() => props.streaming, (isStreaming) => {
  if (!isStreaming) {
    displayedText.value = props.text
    clearInterval(timer)
  }
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<style lang="scss" scoped>
.stream-text {
  line-height: 1.7;
  white-space: pre-wrap;
}

.cursor-blink {
  animation: blink 0.8s infinite;
  color: $primary;
  font-weight: 300;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>