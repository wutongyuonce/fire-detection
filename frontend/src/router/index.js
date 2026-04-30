import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import UploadView from '../views/UploadView.vue'
import TasksView from '../views/TasksView.vue'
import EventsView from '../views/EventsView.vue'
import ImagesView from '../views/ImagesView.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: DashboardView, meta: { title: '系统概览' } },
  { path: '/upload', component: UploadView, meta: { title: '视频上传' } },
  { path: '/tasks', component: TasksView, meta: { title: '任务列表' } },
  { path: '/events', component: EventsView, meta: { title: '火情事件' } },
  { path: '/images', component: ImagesView, meta: { title: '火情图片' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '火焰监测系统'} - 施工场地火焰监测系统`
})

export default router
