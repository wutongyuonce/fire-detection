<script setup>
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const menus = [
  { path: '/dashboard', label: '系统概览' },
  { path: '/video-events', label: '视频分析' },
  { path: '/camera-events', label: '实时监控' },
]

function isActive(path) {
  return route.path === path
}
</script>

<template>
  <div class="app-layout">
    <aside class="app-aside">
      <div class="app-aside-inner">
        <div class="app-brand">
          <div class="app-brand-icon">🔥</div>
          <div class="app-brand-text">施工场地火焰监测</div>
        </div>
        <nav class="app-menu">
          <div
            v-for="menu in menus"
            :key="menu.path"
            :class="['app-menu-item', { active: isActive(menu.path) }]"
            @click="router.push(menu.path)"
          >
            {{ menu.label }}
          </div>
        </nav>
      </div>
    </aside>
    <main class="app-main">
      <slot />
    </main>
  </div>
</template>
