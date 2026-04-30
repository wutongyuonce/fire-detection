<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import { deleteImage, getImageDetail, getImages } from '../api'

const loading = ref(false)
const images = ref([])
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewImage = ref(null)
let timerId = null

async function fetchImages() {
  loading.value = true
  try {
    const response = await getImages()
    images.value = response.data.records || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function openPreview(id) {
  previewVisible.value = true
  previewLoading.value = true
  try {
    const response = await getImageDetail(id)
    previewImage.value = response.data
  } catch (error) {
    ElMessage.error(error.message)
    previewVisible.value = false
  } finally {
    previewLoading.value = false
  }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确认删除该火情图片吗？', '提示', { type: 'warning' })
    await deleteImage(id)
    ElMessage.success('删除成功')
    fetchImages()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

onMounted(() => {
  fetchImages()
  timerId = window.setInterval(fetchImages, 10000)
})

onBeforeUnmount(() => {
  if (timerId) {
    window.clearInterval(timerId)
  }
})
</script>

<template>
  <AppLayout>
    <div class="panel-toolbar">
      <div class="toolbar-left">
        <span>图片列表每 10 秒自动刷新</span>
      </div>
      <div class="toolbar-right">
        <el-button @click="fetchImages">刷新列表</el-button>
      </div>
    </div>

    <el-card class="section-card">
      <template #header>火情图片列表</template>
      <el-table v-loading="loading" :data="images" border>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="eventId" label="事件ID" width="100" />
        <el-table-column prop="fileName" label="文件名" min-width="180" />
        <el-table-column prop="captureTime" label="截图时间" min-width="180" />
        <el-table-column prop="fileUrl" label="访问地址" min-width="260" />
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button type="primary" link @click="openPreview(scope.row.id)">预览</el-button>
            <el-button type="danger" link @click="handleDelete(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="previewVisible" title="图片预览" width="800px">
      <div v-loading="previewLoading">
        <el-descriptions v-if="previewImage" :column="2" border>
          <el-descriptions-item label="文件名">{{ previewImage.fileName }}</el-descriptions-item>
          <el-descriptions-item label="事件ID">{{ previewImage.eventId }}</el-descriptions-item>
          <el-descriptions-item label="来源类型">{{ previewImage.sourceType }}</el-descriptions-item>
          <el-descriptions-item label="截图时间">{{ previewImage.captureTime }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ previewImage.fileSize ? `${(previewImage.fileSize / 1024).toFixed(1)} KB` : '未知' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ previewImage.createdAt }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="previewImage?.fileUrl" class="dialog-preview">
          <img :src="`http://localhost:8080${previewImage.fileUrl}`" alt="fire image" class="preview-image" />
        </div>
      </div>
    </el-dialog>
  </AppLayout>
</template>
