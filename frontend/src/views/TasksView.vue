<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import { getTaskDetail, getTasks } from '../api'

const loading = ref(false)
const tasks = ref([])
const detailLoading = ref(false)
const detailVisible = ref(false)
const currentTask = ref(null)
let timerId = null

async function fetchTasks() {
  loading.value = true
  try {
    const response = await getTasks()
    tasks.value = response.data.records || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function showTaskDetail(taskId) {
  detailLoading.value = true
  detailVisible.value = true
  try {
    const response = await getTaskDetail(taskId)
    currentTask.value = response.data
  } catch (error) {
    ElMessage.error(error.message)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  fetchTasks()
  timerId = window.setInterval(fetchTasks, 8000)
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
        <span>任务列表每 8 秒自动刷新</span>
      </div>
      <div class="toolbar-right">
        <el-button @click="fetchTasks">刷新列表</el-button>
      </div>
    </div>

    <el-card class="section-card">
      <template #header>检测任务列表</template>
      <el-table v-loading="loading" :data="tasks" border>
        <el-table-column prop="taskId" label="ID" width="90" />
        <el-table-column prop="taskNo" label="任务编号" min-width="220" />
        <el-table-column prop="taskType" label="任务类型" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="sourceType" label="来源类型" width="140" />
        <el-table-column prop="sourceName" label="来源名称" min-width="180" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button type="primary" link @click="showTaskDetail(scope.row.taskId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="任务详情" width="720px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="currentTask" :column="2" border>
          <el-descriptions-item label="任务编号">{{ currentTask.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="任务状态">{{ currentTask.status }}</el-descriptions-item>
          <el-descriptions-item label="任务类型">{{ currentTask.taskType }}</el-descriptions-item>
          <el-descriptions-item label="来源类型">{{ currentTask.sourceType }}</el-descriptions-item>
          <el-descriptions-item label="来源名称">{{ currentTask.sourceName }}</el-descriptions-item>
          <el-descriptions-item label="视频路径">{{ currentTask.videoPath || '无' }}</el-descriptions-item>
          <el-descriptions-item label="处理帧数">{{ currentTask.frameCount }}</el-descriptions-item>
          <el-descriptions-item label="火情次数">{{ currentTask.fireCount }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ currentTask.startTime || '无' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ currentTask.endTime || '无' }}</el-descriptions-item>
          <el-descriptions-item label="结果摘要" :span="2">{{ currentTask.resultSummary || '无' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">{{ currentTask.errorMessage || '无' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </AppLayout>
</template>
