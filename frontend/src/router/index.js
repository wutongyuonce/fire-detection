import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import VideoEventsView from '../views/VideoEventsView.vue'
import CameraEventsView from '../views/CameraEventsView.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: DashboardView, meta: { title: '系统概览' } },
  { path: '/video-events', component: VideoEventsView, meta: { title: '视频分析' } },
  { path: '/camera-events', component: CameraEventsView, meta: { title: '实时监控' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const title = to.meta.title ? `${to.meta.title} - 施工场地火焰监测系统` : '施工场地火焰监测系统'
  document.title = title
  next()
})

export default router
