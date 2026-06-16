<!-- 点赞按钮：带动画和计数格式化 -->
<template>
  <button class="like-btn" :class="{ liked }" @click.stop="toggleLike">
    <span class="like-icon">
      <el-icon :size="size">
        <svg v-if="liked" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M923 283.6c-13.4-31.1-32.6-58.9-56.9-82.8-24.3-23.8-52.5-42.4-84-55.2-32.5-13.2-67-20-102.3-20-35.3 0-69.7 6.8-102.2 20-31.4 12.8-59.7 31.3-84 55.2L512 262.8l-81.6-81.2c-24.3-23.8-52.5-42.4-84-55.2-32.5-13.2-67-20-102.2-20-35.3 0-69.7 6.8-102.3 20-31.4 12.8-59.7 31.3-84 55.2-24.3 23.8-43.5 51.6-56.9 82.8-13.9 32.3-21 66.6-21 101.9 0 35.3 7.1 69.6 21 101.9 13.4 31.1 32.6 58.9 56.9 82.8L512 904.4l429.6-436.1c24.3-23.8 43.5-51.6 56.9-82.8 13.9-32.3 21-66.6 21-101.9 0-35.3-7.1-69.6-21-101.9z" fill="currentColor"/></svg>
        <svg v-else viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M923 283.6c-13.4-31.1-32.6-58.9-56.9-82.8-24.3-23.8-52.5-42.4-84-55.2-32.5-13.2-67-20-102.3-20-35.3 0-69.7 6.8-102.2 20-31.4 12.8-59.7 31.3-84 55.2L512 262.8l-81.6-81.2c-24.3-23.8-52.5-42.4-84-55.2-32.5-13.2-67-20-102.2-20-35.3 0-69.7 6.8-102.3 20-31.4 12.8-59.7 31.3-84 55.2-24.3 23.8-43.5 51.6-56.9 82.8-13.9 32.3-21 66.6-21 101.9 0 35.3 7.1 69.6 21 101.9 13.4 31.1 32.6 58.9 56.9 82.8L512 904.4l429.6-436.1c24.3-23.8 43.5-51.6 56.9-82.8 13.9-32.3 21-66.6 21-101.9 0-35.3-7.1-69.6-21-101.9zM512 814.8L193.1 491.5c-40.3-40.7-62.5-95-62.5-152.6 0-57.5 22.2-111.8 62.5-152.5 40.2-40.7 93.6-63.2 150.4-63.2 56.8 0 110.2 22.5 150.5 63.2L512 306.9l18-18.5c40.3-40.7 93.7-63.2 150.5-63.2s110.2 22.5 150.5 63.2c40.2 40.7 62.5 95 62.5 152.5 0 57.6-22.2 111.9-62.5 152.6L512 814.8z" fill="currentColor"/></svg>
      </el-icon>
    </span>
    <span class="like-count" v-if="showCount">{{ displayCount }}</span>
  </button>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  liked: Boolean,
  count: { type: Number, default: 0 },
  size: { type: Number, default: 18 },
  showCount: { type: Boolean, default: true },
})

const emit = defineEmits(['toggle'])

const displayCount = computed(() => {
  if (props.count >= 10000) return (props.count / 10000).toFixed(1) + 'w'
  if (props.count >= 1000) return (props.count / 1000).toFixed(1) + 'k'
  return props.count
})

function toggleLike() {
  emit('toggle')
}
</script>

<style lang="scss" scoped>
.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  cursor: pointer;
  color: $text-muted;
  transition: $transition;
  padding: 4px;

  &:hover {
    color: $primary;
  }

  &.liked {
    color: #f56c6c;

    .like-icon {
      animation: like-pop 0.3s ease;
    }
  }
}

.like-count {
  font-size: 13px;
  font-weight: 500;
}

@keyframes like-pop {
  0% { transform: scale(1); }
  50% { transform: scale(1.3); }
  100% { transform: scale(1); }
}
</style>
