<!-- 骨架屏卡片：加载占位 -->
<template>
  <div class="skeleton-card card">
    <div class="skeleton-cover" :style="{ height: coverHeight + 'px' }"></div>
    <div class="skeleton-body">
      <div class="skeleton-line title"></div>
      <div class="skeleton-line title short"></div>
      <div class="skeleton-line text"></div>
      <div class="skeleton-line text short"></div>
    </div>
  </div>
</template>

<script setup>
const coverHeight = 100 + Math.floor(Math.random() * 100)
</script>

<style lang="scss" scoped>
.skeleton-card {
  overflow: hidden;
}

.skeleton-cover {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-body {
  padding: 14px;
}

.skeleton-line {
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  margin-bottom: 10px;

  &.title {
    height: 16px;
    width: 80%;

    &.short {
      width: 50%;
    }
  }

  &.text {
    height: 12px;
    width: 100%;

    &.short {
      width: 70%;
      margin-bottom: 0;
    }
  }
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
