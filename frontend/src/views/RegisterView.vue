<!-- 注册页 -->
<template>
  <div class="register-page">
    <div class="register-card card">
      <h2 class="register-title gradient-text">加入社区</h2>
      <p class="register-subtitle">创建账号，开始你的知识之旅</p>

      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="handleRegister">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="邮箱（选填）" size="large" prefix-icon="Message" />
        </el-form-item>
        <el-button type="primary" size="large" round class="register-btn" :loading="loading" native-type="submit">
          注册
        </el-button>
      </el-form>

      <div class="register-footer">
        已有账号？<router-link to="/login" class="link">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  email: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度3-50', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度6-50', trigger: 'blur' },
  ],
}

async function handleRegister() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await register(form)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $gradient;
  padding: 20px;
}

.register-card {
  width: 100%;
  max-width: 420px;
  padding: 40px 36px;
  border-radius: 20px;
}

.register-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}

.register-subtitle {
  color: $text-secondary;
  font-size: 14px;
  margin-bottom: 32px;
}

.register-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  margin-top: 8px;
}

.register-footer {
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
