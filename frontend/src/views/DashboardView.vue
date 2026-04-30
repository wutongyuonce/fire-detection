<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import { getOverview } from '../api'

const loading = ref(false)
const overview = ref({
  todayFireCount: 0,
  latestEvent: null,
})

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

onMounted(fetchOverview)
</script>

<template>
  <AppLayout>
    <div class="dashboard-content">
      <div v-loading="loading" class="card-grid">
        <el-card class="status-card warning">
          <div class="metric-title">今日火情数（摄像头）</div>
          <div class="metric-value">{{ overview.todayFireCount }}</div>
        </el-card>
      </div>

      <el-card class="section-card">
        <template #header>最近一次火情（摄像头）</template>
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
        <el-empty v-else description="暂无摄像头火情事件" />
      </el-card>
    </div>
  </AppLayout>
</template>

<style scoped>
.dashboard-content {
  display: flex;
  flex-direction: column;
  gap: 28px;
}
</style>
