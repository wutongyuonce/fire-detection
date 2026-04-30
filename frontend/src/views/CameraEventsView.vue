<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoCamera } from '@element-plus/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import { useCameraMonitor } from '../composables/useCameraMonitor'
import { getEvents, getEventDetail, deleteEvent } from '../api'

const {
  monitoring, cameraActive, fireStatus, latestConfidence,
  lastBoxes, recordedUrl, startCamera: monitorStart, stopCamera: monitorStop,
  clearRecordedUrl, onFireDetected, offFireDetected,
} = useCameraMonitor()

const videoRef = ref(null)
const canvasRef = ref(null)
const loading = ref(false)
const events = ref([])
const detailLoading = ref(false)
const detailVisible = ref(false)
const currentEvent = ref(null)

async function attachStream() {
  await nextTick()
  if (!videoRef.value) return
  const stream = videoRef.value.srcObject
  if (stream) return
  const el = document.querySelector('video[data-hidden-camera]')
  if (el && el.srcObject) {
    videoRef.value.srcObject = el.srcObject
    videoRef.value.play().catch(() => {})
  }
}

async function handleStartCamera() {
  try {
    await monitorStart()
    await nextTick()
    attachToVisibleVideo()
  } catch (e) {
    ElMessage.error(e.message || '无法访问摄像头')
  }
}

function attachToVisibleVideo() {
  const hv = document.querySelector('video[data-hidden-camera]')
  if (hv && hv.srcObject && videoRef.value) {
    videoRef.value.srcObject = hv.srcObject
    videoRef.value.play().catch(() => {})
  }
}

function drawBoxes() {
  const canvas = canvasRef.value
  const video = videoRef.value
  if (!canvas || !video) return
  const ctx = canvas.getContext('2d')
  canvas.width = video.videoWidth || 640
  canvas.height = video.videoHeight || 480
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  if (!lastBoxes.value || lastBoxes.value.length === 0) return

  for (const box of lastBoxes.value) {
    const x1 = box.x1
    const y1 = box.y1
    const x2 = box.x2
    const y2 = box.y2
    const w = x2 - x1
    const h = y2 - y1

    ctx.strokeStyle = '#ff0000'
    ctx.lineWidth = 3
    ctx.strokeRect(x1, y1, w, h)

    ctx.fillStyle = 'rgba(255, 0, 0, 0.15)'
    ctx.fillRect(x1, y1, w, h)

    const label = `fire ${(box.confidence * 100).toFixed(1)}%`
    ctx.font = 'bold 16px Arial'
    const textWidth = ctx.measureText(label).width
    ctx.fillStyle = 'rgba(255, 0, 0, 0.8)'
    ctx.fillRect(x1, y1 - 24, textWidth + 12, 24)
    ctx.fillStyle = '#fff'
    ctx.fillText(label, x1 + 6, y1 - 6)
  }
}

watch(lastBoxes, drawBoxes, { deep: true })

function handleFireDetected() {
  fetchEvents()
}

async function fetchEvents() {
  loading.value = true
  try {
    const response = await getEvents({ sourceType: 'CAMERA' })
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

onMounted(() => {
  fetchEvents()
  onFireDetected(handleFireDetected)
  if (cameraActive.value) {
    nextTick(() => attachToVisibleVideo())
  }
})

onBeforeUnmount(() => {
  offFireDetected(handleFireDetected)
})
</script>

<template>
  <AppLayout>
    <div class="camera-content">
      <el-card class="section-card">
        <template #header>
          <div class="card-header-row">
            <span>摄像头实时监控</span>
            <div class="header-status">
              <span v-if="monitoring && fireStatus" class="fire-alert">
                <span class="alert-dot" />
                检测到火情！ 置信度 {{ (latestConfidence * 100).toFixed(1) }}%
              </span>
              <span v-else-if="monitoring" class="monitor-ok">监控中</span>
              <el-button v-if="!cameraActive" type="primary" @click="handleStartCamera">开启摄像头</el-button>
              <el-button v-else type="info" @click="monitorStop">关闭摄像头</el-button>
            </div>
          </div>
        </template>

        <div class="camera-area">
          <div class="video-container">
            <video
              ref="videoRef"
              class="camera-preview"
              autoplay
              muted
              playsinline
            />
            <canvas
              ref="canvasRef"
              class="detection-overlay"
            />
            <div v-if="!cameraActive" class="camera-placeholder">
              <el-icon :size="48"><VideoCamera /></el-icon>
              <span>点击上方"开启摄像头"启动实时监控</span>
            </div>
            <div v-if="monitoring" class="monitor-badge">
              <span class="badge-dot" />
              实时分析中
            </div>
          </div>

          <div v-if="recordedUrl" class="recorded-area">
            <div class="recorded-header">
              <span>录像回放</span>
              <el-button text @click="clearRecordedUrl">关闭</el-button>
            </div>
            <video :src="recordedUrl" controls class="recorded-preview" />
          </div>
        </div>
      </el-card>

      <el-card class="section-card">
        <template #header>
          <div class="card-header-row">
            <span>火情事件列表（基于摄像头）</span>
          </div>
        </template>
        <el-table v-loading="loading" :data="events" border>
          <el-table-column prop="eventNo" label="事件编号" min-width="220" />
          <el-table-column prop="sourceName" label="来源" min-width="160" />
          <el-table-column prop="eventTime" label="发生时间" min-width="180" />
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
          <el-descriptions-item label="发生时间">{{ currentEvent.eventTime }}</el-descriptions-item>
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
.camera-content {
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

.header-status {
  display: flex;
  align-items: center;
  gap: 16px;
}

.fire-alert {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #ef4444;
  font-weight: 700;
  font-size: 14px;
  animation: pulse-alert 1s infinite;
}

@keyframes pulse-alert {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.alert-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
  animation: blink 0.6s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.2; }
}

.monitor-ok {
  color: #22c55e;
  font-weight: 600;
  font-size: 14px;
}

.camera-area {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.video-container {
  position: relative;
  width: 100%;
  max-width: 800px;
  aspect-ratio: 16 / 9;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
}

.camera-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.detection-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.camera-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9ca3af;
  gap: 12px;
}

.monitor-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.badge-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  animation: blink 1s infinite;
}

.recorded-area {
  max-width: 800px;
}

.recorded-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 600;
}

.recorded-preview {
  width: 100%;
  border-radius: 12px;
  background: #000;
}

.dialog-preview {
  margin-top: 16px;
  text-align: center;
}

.preview-image {
  max-width: 100%;
  max-height: 500px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}
</style>
