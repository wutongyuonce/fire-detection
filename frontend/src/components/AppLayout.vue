<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const menus = [
  { path: '/dashboard', label: '系统概览' },
  { path: '/upload', label: '视频上传' },
  { path: '/tasks', label: '任务列表' },
  { path: '/events', label: '火情事件' },
  { path: '/images', label: '火情图片' },
]

const activeMenu = computed(() => route.path)

function handleSelect(path) {
  router.push(path)
}
</script>

<template>
  <div class="layout-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-title">施工场地火焰监测系统</div>
        <div class="brand-subtitle">Vue + Spring Boot + YOLO</div>
      </div>

      <el-menu :default-active="activeMenu" class="side-menu" @select="handleSelect">
        <el-menu-item v-for="menu in menus" :key="menu.path" :index="menu.path">
          {{ menu.label }}
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="page-content">
      <div class="page-header">
        <h1>{{ route.meta.title || '页面' }}</h1>
        <span>火焰识别与预警管理</span>
      </div>

      <div class="page-body">
        <slot />
      </div>
    </main>
  </div>
</template>
