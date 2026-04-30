<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import { uploadVideo, getEvents, getEventDetail, deleteEvent, clearUploadedVideos } from '../api'

const file = ref(null)
const uploading = ref(false)
const result = ref(null)

const loading = ref(false)
const events = ref([])
const detailLoading = ref(false)
const detailVisible = ref(false)
const currentEvent = ref(null)
const clearing = ref(false)

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
    ElMessage.success('视频分析完成')
    fetchEvents()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    uploading.value = false
  }
}

async function fetchEvents() {
  loading.value = true
  try {
    const response = await getEvents({ sourceType: 'UPLOAD_VIDEO' })
    events.value = response.data.records || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function showEventDetail(id) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await getEventDetail(id)
    currentEvent.value = response.data
  } catch (error) {
    ElMessage.error(error.message)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确认删除该火情事件吗？', '提示', { type: 'warning' })
    await deleteEvent(id)
    ElMessage.success('删除成功')
    fetchEvents()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

async function handleClearVideos() {
  try {
    await ElMessageBox.confirm('确认清空所有已上传的视频文件吗？', '提示', { type: 'warning' })
    clearing.value = true
    const response = await clearUploadedVideos()
    ElMessage.success(`已清空 ${response.data.deletedCount} 个视频文件`)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '清空失败')
    }
  } finally {
    clearing.value = false
  }
}

onMounted(fetchEvents)
</script>

<template>
  <AppLayout>
    <div class="video-events-content">
      <el-card class="section-card">
        <template #header>
          <div class="card-header-row">
            <span>上传视频并分析</span>
          </div>
        </template>

      <div class="upload-wrapper">
        <div class="upload-controls">
          <el-upload :auto-upload="false" :limit="1" :show-file-list="false" :on-change="handleFileChange" accept="video/*">
            <el-button type="primary" size="large">选择视频文件</el-button>
          </el-upload>
          <el-button type="danger" size="large" :loading="uploading" :disabled="!file" @click="submitUpload">
            开始分析
          </el-button>
          <el-button size="large" :loading="clearing" @click="handleClearVideos">
            清空视频文件
          </el-button>
        </div>
        <div v-if="file" class="selected-file">已选择：{{ file.name }}</div>
      </div>

      <div v-if="result" class="result-area">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="任务编号">{{ result.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="来源名称">{{ result.sourceName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="result.status === 'FINISHED' ? 'success' : result.status === 'FAILED' ? 'danger' : 'warning'">
              {{ result.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="分析帧数">{{ result.frameCount }}</el-descriptions-item>
          <el-descriptions-item label="火情次数">{{ result.fireCount }}</el-descriptions-item>
          <el-descriptions-item label="结果摘要">
            <el-tag :type="result.fireCount > 0 ? 'danger' : 'success'" size="large">
              {{ result.resultSummary }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <el-card class="section-card">
      <template #header>火情事件列表（基于视频）</template>
      <el-table v-loading="loading" :data="events" border>
        <el-table-column prop="eventNo" label="事件编号" min-width="220" />
        <el-table-column prop="sourceName" label="来源视频" min-width="160" />
        <el-table-column prop="videoTimecode" label="视频时间" width="120" />
        <el-table-column prop="confidence" label="置信度" width="120" />
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button type="primary" link @click="showEventDetail(scope.row.id)">详情</el-button>
            <el-button type="danger" link @click="handleDelete(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </el-card>
    </div>

    <el-dialog v-model="detailVisible" title="火情事件详情" width="760px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="currentEvent" :column="2" border>
          <el-descriptions-item label="事件编号">{{ currentEvent.eventNo }}</el-descriptions-item>
          <el-descriptions-item label="来源名称">{{ currentEvent.sourceName }}</el-descriptions-item>
          <el-descriptions-item label="视频时间码">{{ currentEvent.videoTimecode || '无' }}</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ currentEvent.confidence }}</el-descriptions-item>
          <el-descriptions-item label="持续时长">{{ currentEvent.durationSeconds || '无' }} 秒</el-descriptions-item>
          <el-descriptions-item label="帧号">{{ currentEvent.taskFrameNo || '无' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ currentEvent.remark || '无' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="currentEvent?.snapshotUrl" class="dialog-preview">
          <img :src="`http://localhost:8080${currentEvent.snapshotUrl}`" alt="event snapshot" class="preview-image" />
        </div>
      </div>
    </el-dialog>
  </AppLayout>
</template>

<style scoped>
.video-events-content {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.upload-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.upload-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}

.selected-file {
  font-size: 13px;
  color: #6b7280;
}

.result-area {
  margin-top: 16px;
}
</style>
