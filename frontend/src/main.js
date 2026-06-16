import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import VMdEditor from '@kangc/v-md-editor'
import '@kangc/v-md-editor/lib/style/base-editor.css'
import '@kangc/v-md-editor/lib/theme/style/vuepress.css'
import vuepressTheme from '@kangc/v-md-editor/lib/theme/vuepress.js'
import VMdPreview from '@kangc/v-md-editor/lib/preview'
import '@kangc/v-md-editor/lib/style/preview.css'
import 'animate.css'
import './assets/styles/global.scss'
import App from './App.vue'
import router from './router'

VMdEditor.use(vuepressTheme)
VMdPreview.use(vuepressTheme)

const app = createApp(App)

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())

// 初始化认证状态（从 localStorage 恢复用户信息）
import { useAuthStore } from '@/stores/auth'
const authStore = useAuthStore()
authStore.init()

app.use(router)
app.use(ElementPlus)
app.use(VMdEditor)
app.use(VMdPreview)

app.mount('#app')
