<template>
  <div class="user-stats">
    <div class="stat-item" v-for="stat in stats" :key="stat.label">
      <span class="stat-value">{{ stat.value }}</span>
      <span class="stat-label">{{ stat.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  following: { type: Number, default: 0 },
  followers: { type: Number, default: 0 },
  articles: { type: Number, default: 0 },
  likes: { type: Number, default: 0 },
})

const stats = computed(() => [
  { label: '关注', value: props.following },
  { label: '粉丝', value: props.followers },
  { label: '发帖', value: props.articles },
  { label: '获赞', value: props.likes },
])
</script>

<style lang="scss" scoped>
.user-stats {
  display: flex;
  gap: 32px;
  justify-content: center;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;

  &:hover .stat-value {
    color: $primary;
  }
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: $text-primary;
  transition: $transition;
}

.stat-label {
  font-size: 13px;
  color: $text-secondary;
}
</style>
