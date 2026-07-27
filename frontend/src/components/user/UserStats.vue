<template>
  <div class="user-stats">
    <div class="stat-item" @click="$emit('showFollowing')">
      <span class="stat-value">{{ following }}</span>
      <span class="stat-label">关注</span>
    </div>
    <div class="stat-item" @click="$emit('showFollowers')">
      <span class="stat-value">{{ followers }}</span>
      <span class="stat-label">粉丝</span>
    </div>
    <div class="stat-item">
      <span class="stat-value">{{ articles }}</span>
      <span class="stat-label">发帖</span>
    </div>
    <div class="stat-item">
      <span class="stat-value">{{ likes }}</span>
      <span class="stat-label">获赞</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  following: { type: Number, default: 0 },
  followers: { type: Number, default: 0 },
  articles: { type: Number, default: 0 },
  likes: { type: Number, default: 0 },
})

defineEmits(['showFollowing', 'showFollowers'])
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
