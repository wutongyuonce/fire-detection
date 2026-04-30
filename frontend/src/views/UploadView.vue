<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import { uploadVideo } from '../api'

const file = ref(null)
const uploading = ref(false)
const result = ref(null)

function handleFileChange(uploadFile) {
  file.value = uploadFile.raw || null
}

async function submitUpload() {
  if (!file.value) {
    ElMessage.warning('请先选择一个视频文件')
    return
  }

  const formData = new FormData()
  formData.append('file', file.value)
  formData.append('sourceName', file.value.name)

  uploading.value = true
  try {
    const response = await uploadVideo(formData)
    result.value = response.data
    ElMessage.success('视频上传并分析完成')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <AppLayout>
    <el-card class="section-card">
      <template #header>上传视频并分析</template>

      <div class="upload-panel">
        <el-upload :auto-upload="false" :limit="1" :show-file-list="true" :on-change="handleFileChange">
          <el-button type="primary">选择视频</el-button>
        </el-upload>

        <el-button type="danger" :loading="uploading" @click="submitUpload">
          开始分析
        </el-button>
      </div>
    </el-card>

    <el-card v-if="result" class="section-card">
      <template #header>分析结果</template>
      <div class="detail-grid">
        <div>任务编号：{{ result.taskNo }}</div>
        <div>任务状态：{{ result.status }}</div>
        <div>视频路径：{{ result.videoPath }}</div>
        <div>处理帧数：{{ result.frameCount }}</div>
        <div>火情次数：{{ result.fireCount }}</div>
        <div>结果摘要：{{ result.resultSummary }}</div>
      </div>
    </el-card>
  </AppLayout>
</template>
