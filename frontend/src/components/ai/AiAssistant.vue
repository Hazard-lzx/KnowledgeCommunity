<!-- AI 写作助手面板：续写 / 润色 / 大纲三个 Tab -->
<template>
  <div class="ai-assistant">
    <div class="assistant-header">
      <el-icon :size="18" color="#6C63FF"><MagicStick /></el-icon>
      <span class="header-title">AI 写作助手</span>
    </div>

    <el-tabs v-model="activeTab" class="assistant-tabs">
      <el-tab-pane label="续写" name="continue">
        <ContinueTab
          :selected-text="selectedText"
          :context="context"
          @insert="(text) => $emit('insert', text)"
        />
      </el-tab-pane>
      <el-tab-pane label="润色" name="polish">
        <PolishTab
          :selected-text="selectedText"
          :context="context"
          @replace="(text) => $emit('replace', text)"
        />
      </el-tab-pane>
      <el-tab-pane label="大纲" name="outline">
        <OutlineTab
          :editor-title="editorTitle"
          :context="context"
          @insert="(text) => $emit('insert', text)"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import ContinueTab from './ContinueTab.vue'
import PolishTab from './PolishTab.vue'
import OutlineTab from './OutlineTab.vue'

defineProps({
  selectedText: { type: String, default: '' },
  editorTitle: { type: String, default: '' },
  context: { type: String, default: '' },
})

defineEmits(['insert', 'replace'])

const activeTab = ref('continue')
</script>

<style lang="scss" scoped>
.ai-assistant {
  background: rgba(219, 234, 254, 0.3);
  border: 1px solid #94a3b8;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 600px;
}

.assistant-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;

  .header-title {
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
  }
}

.assistant-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__content) {
    flex: 1;
    overflow-y: auto;
  }

  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }

  :deep(.el-tabs__item) {
    font-size: 13px;
    padding: 0 12px;
  }
}
</style>
