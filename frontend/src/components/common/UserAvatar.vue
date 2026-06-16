<template>
  <el-avatar :size="size" :src="avatarUrl" class="user-avatar">
    {{ username?.charAt(0) }}
  </el-avatar>
</template>

<script setup>
defineProps({
  avatarUrl: String,
  username: String,
  size: { type: Number, default: 36 },
})
</script>

<style lang="scss" scoped>
.user-avatar {
  background: $gradient;
  color: white;
  font-weight: 600;
}
</style>
