<!-- 应用布局：左侧导航 + 右侧内容区 -->
<template>
  <div class="app-layout">
    <NavSidebar />
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <keep-alive include="SearchView">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import NavSidebar from './NavSidebar.vue'
</script>

<style lang="scss" scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
}

.main-content {
  flex: 1;
  margin-left: $sidebar-width;
  padding: 24px 32px;
  overflow-y: auto;

  @media (max-width: $mobile) {
    margin-left: 0;
    padding: 16px;
  }
}
</style>
