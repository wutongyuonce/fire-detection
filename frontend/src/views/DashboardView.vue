<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import { getOverview } from '../api'

const loading = ref(false)
const overview = ref({
  runningTaskCount: 0,
  todayFireCount: 0,
  systemStatus: 'IDLE',
  latestEvent: null,
})
let timerId = null

async function fetchOverview() {
  loading.value = true
  try {
    const response = await getOverview()
    overview.value = response.data
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchOverview()
  timerId = window.setInterval(fetchOverview, 10000)
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
        <span>每 10 秒自动刷新一次概览数据</span>
      </div>
      <div class="toolbar-right">
        <el-button @click="fetchOverview">立即刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="card-grid">
      <el-card class="status-card">
        <div class="metric-title">运行中任务</div>
        <div class="metric-value">{{ overview.runningTaskCount }}</div>
      </el-card>

      <el-card class="status-card warning">
        <div class="metric-title">今日火情数</div>
        <div class="metric-value">{{ overview.todayFireCount }}</div>
      </el-card>

      <el-card class="status-card info">
        <div class="metric-title">系统状态</div>
        <div class="metric-value small">{{ overview.systemStatus }}</div>
      </el-card>
    </div>

    <el-card class="section-card">
      <template #header>最近一次火情</template>
      <div v-if="overview.latestEvent" class="detail-grid">
        <div>事件编号：{{ overview.latestEvent.eventNo }}</div>
        <div>发生时间：{{ overview.latestEvent.eventTime }}</div>
        <div>置信度：{{ overview.latestEvent.confidence }}</div>
        <div>
          截图地址：
          <el-link
            v-if="overview.latestEvent.snapshotUrl"
            :href="`http://localhost:8080${overview.latestEvent.snapshotUrl}`"
            target="_blank"
            type="primary"
          >
            查看截图
          </el-link>
          <span v-else>暂无</span>
        </div>
      </div>
      <el-empty v-else description="暂无火情事件" />
    </el-card>
  </AppLayout>
</template>
