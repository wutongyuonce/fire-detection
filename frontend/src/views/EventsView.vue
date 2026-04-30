<script setup>
import { onBeforeUnmount, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import { deleteEvent, getEventDetail, getEvents } from '../api'

const loading = ref(false)
const events = ref([])
const detailLoading = ref(false)
const detailVisible = ref(false)
const currentEvent = ref(null)
let timerId = null

async function fetchEvents() {
  loading.value = true
  try {
    const response = await getEvents()
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
  timerId = window.setInterval(fetchEvents, 10000)
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
        <span>事件列表每 10 秒自动刷新</span>
      </div>
      <div class="toolbar-right">
        <el-button @click="fetchEvents">刷新列表</el-button>
      </div>
    </div>

    <el-card class="section-card">
      <template #header>火情事件列表</template>
      <el-table v-loading="loading" :data="events" border>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="eventNo" label="事件编号" min-width="220" />
        <el-table-column prop="sourceName" label="来源名称" min-width="160" />
        <el-table-column prop="eventTime" label="发生时间" min-width="180" />
        <el-table-column prop="confidence" label="置信度" width="120" />
        <el-table-column prop="snapshotUrl" label="截图地址" min-width="260" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button type="primary" link @click="showEventDetail(scope.row.id)">详情</el-button>
            <el-button type="danger" link @click="handleDelete(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="火情事件详情" width="760px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="currentEvent" :column="2" border>
          <el-descriptions-item label="事件编号">{{ currentEvent.eventNo }}</el-descriptions-item>
          <el-descriptions-item label="任务ID">{{ currentEvent.taskId }}</el-descriptions-item>
          <el-descriptions-item label="来源类型">{{ currentEvent.sourceType }}</el-descriptions-item>
          <el-descriptions-item label="来源名称">{{ currentEvent.sourceName }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ currentEvent.eventTime }}</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ currentEvent.confidence }}</el-descriptions-item>
          <el-descriptions-item label="持续时长">{{ currentEvent.durationSeconds || '无' }}</el-descriptions-item>
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
