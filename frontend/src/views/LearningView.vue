<!-- 学习中心：建设中占位页 -->
<template>
  <div class="learning-view">
      <div class="learning-header">
        <h1 class="learning-title gradient-text">学习中心</h1>
        <p class="learning-subtitle">持续学习，不断进步</p>
      </div>

      <div class="learning-placeholder">
        <el-empty description="学习中心正在建设中，敬请期待...">
          <template #image>
            <el-icon :size="80" color="#6C63FF"><Reading /></el-icon>
          </template>
        </el-empty>

        <div class="hot-qa-entry" @click="$router.push('/')">
          <div class="entry-card card">
            <el-icon :size="32" color="#6C63FF"><ChatDotRound /></el-icon>
            <h3>热门问答</h3>
            <p>探索社区中的 AI 问答</p>
          </div>
        </div>
      </div>
  </div>
</template>

<script setup>
</script>

<style lang="scss" scoped>
.learning-view {
  max-width: 800px;
  margin: 0 auto;
}

.learning-header {
  margin-bottom: 32px;
}

.learning-title {
  font-size: 28px;
  font-weight: 700;
}

.learning-subtitle {
  color: $text-secondary;
  font-size: 14px;
  margin-top: 4px;
}

.learning-placeholder {
  padding: 60px 0;
}

.hot-qa-entry {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

.entry-card {
  padding: 32px;
  text-align: center;
  cursor: pointer;
  width: 280px;

  h3 {
    margin-top: 16px;
    font-size: 18px;
    font-weight: 600;
  }

  p {
    color: $text-secondary;
    font-size: 14px;
    margin-top: 8px;
  }
}
</style>
