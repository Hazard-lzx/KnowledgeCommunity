<!-- 登录页 -->
<template>
  <div class="login-page">
    <div class="login-card card">
      <h2 class="login-title gradient-text">欢迎回来</h2>
      <p class="login-subtitle">登录知识社区，发现更多精彩</p>

      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" size="large" round class="login-btn" :loading="loading" native-type="submit">
          登录
        </el-button>
      </el-form>

      <div class="login-footer">
        还没有账号？<router-link to="/register" class="link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useAuth } from '@/composables/useAuth'

const { login } = useAuth()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await login(form)
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $gradient;
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 40px 36px;
  border-radius: 20px;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}

.login-subtitle {
  color: $text-secondary;
  font-size: 14px;
  margin-bottom: 32px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  margin-top: 8px;
}

.login-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: $text-secondary;

  .link {
    color: $primary;
    font-weight: 500;
  }
}
</style>
