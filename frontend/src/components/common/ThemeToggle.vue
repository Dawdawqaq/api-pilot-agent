<script setup>
import { ref, onMounted } from 'vue'
import { Sun, Moon } from 'lucide-vue-next'
import Tooltip from './Tooltip.vue'

const isDark = ref(false)

const updateTheme = (dark) => {
  isDark.value = dark
  if (dark) {
    document.documentElement.classList.add('dark')
    localStorage.setItem('dochelper_theme', 'dark')
  } else {
    document.documentElement.classList.remove('dark')
    localStorage.setItem('dochelper_theme', 'light')
  }
}

const toggleTheme = () => {
  updateTheme(!isDark.value)
}

onMounted(() => {
  const saved = localStorage.getItem('dochelper_theme')
  if (saved) {
    updateTheme(saved === 'dark')
  } else {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    updateTheme(prefersDark)
  }
})
</script>

<template>
  <!-- FB-002: 浅色下显示月亮，提示“切换到深色模式”；深色下显示太阳，提示“切换到浅色模式” -->
  <Tooltip :content="isDark ? '切换到浅色模式' : '切换到深色模式'" position="bottom">
    <button
      type="button"
      class="inline-flex items-center justify-center p-2 rounded-lg text-zinc-600 dark:text-zinc-300 hover:text-zinc-900 dark:hover:text-white hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
      :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
      @click="toggleTheme"
    >
      <Sun v-if="isDark" class="w-4 h-4 text-amber-400 transition-transform duration-200 rotate-0 hover:rotate-45" />
      <Moon v-else class="w-4 h-4 text-zinc-600 transition-transform duration-200 -rotate-12 hover:rotate-0" />
    </button>
  </Tooltip>
</template>
