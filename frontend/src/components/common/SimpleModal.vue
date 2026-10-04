<script setup>
import { X } from 'lucide-vue-next'
import { ref, useId } from 'vue'
import { useDialog } from '../../composables/useDialog'

const props = defineProps({
  isOpen: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['close'])
const dialogRef = ref(null)
const titleId = useId()
useDialog(() => props.isOpen, dialogRef, () => emit('close'))
</script>

<template>
  <teleport to="body">
    <transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="isOpen"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-zinc-950/40 backdrop-blur-xs select-none"
      >
        <div
          ref="dialogRef" role="dialog" aria-modal="true" :aria-labelledby="titleId" tabindex="-1"
          class="w-full max-w-md rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-md overflow-hidden"
          @click.stop
        >
          <!-- 头部 -->
          <div class="h-12 px-4 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between">
            <h3 :id="titleId" class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
              {{ title }}
            </h3>
            <button
              type="button"
              aria-label="关闭对话框"
              class="p-1.5 rounded-lg text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
              @click="emit('close')"
            >
              <X class="w-4 h-4" />
            </button>
          </div>

          <!-- 内容主体 -->
          <div class="p-5 text-xs">
            <slot />
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>
