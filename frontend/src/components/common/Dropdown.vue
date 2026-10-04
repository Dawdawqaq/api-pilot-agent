<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, useId, watch } from 'vue'
import { ChevronDown, Check } from 'lucide-vue-next'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  options: {
    type: Array, // Array of { label: string, value: string|number, description?: string, tag?: string }
    default: () => []
  },
  placeholder: {
    type: String,
    default: '请选择'
  },
  prefixIcon: {
    type: [Object, Function],
    default: null
  },
  emptyText: {
    type: String,
    default: '暂无可用选项'
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const isOpen = ref(false)
const dropdownRef = ref(null)
const focusedIndex = ref(-1)
const listId = useId()
watch(focusedIndex, async index => {
  await nextTick()
  if (index >= 0) document.getElementById(`${listId}-${index}`)?.scrollIntoView({block:'nearest'})
})

const selectedOption = computed(() => {
  return props.options.find(opt => String(opt.value) === String(props.modelValue))
})

const toggle = () => {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    const idx = props.options.findIndex(opt => String(opt.value) === String(props.modelValue))
    focusedIndex.value = idx >= 0 ? idx : 0
  }
}

const close = () => {
  isOpen.value = false
  focusedIndex.value = -1
}

const select = (option) => {
  emit('update:modelValue', option.value)
  emit('change', option)
  close()
}

// 键盘无障碍交互 (FB-001 要求支持键盘、焦点和选项状态)
const onKeydown = (e) => {
  if (!isOpen.value) {
    if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
      e.preventDefault()
      toggle()
    }
    return
  }

  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault()
      if (focusedIndex.value < props.options.length - 1) {
        focusedIndex.value++
      } else {
        focusedIndex.value = 0
      }
      break
    case 'ArrowUp':
      e.preventDefault()
      if (focusedIndex.value > 0) {
        focusedIndex.value--
      } else {
        focusedIndex.value = props.options.length - 1
      }
      break
    case 'Enter':
    case ' ':
      e.preventDefault()
      if (focusedIndex.value >= 0 && focusedIndex.value < props.options.length) {
        select(props.options[focusedIndex.value])
      }
      break
    case 'Escape':
    case 'Tab':
      close()
      break
  }
}

const handleClickOutside = (e) => {
  if (dropdownRef.value && !dropdownRef.value.contains(e.target)) {
    close()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div
    ref="dropdownRef"
    class="relative inline-block text-left text-sm"
    @keydown="onKeydown"
  >
    <!-- 统一选择器按钮 (FB-001) -->
    <button
      type="button"
      class="inline-flex items-center justify-between gap-1.5 sm:gap-2 px-2.5 sm:px-3 py-1.5 text-xs font-medium text-zinc-700 dark:text-zinc-200 bg-zinc-50 dark:bg-zinc-800/80 hover:bg-zinc-100 dark:hover:bg-zinc-750 border border-zinc-200 dark:border-zinc-700/80 rounded-lg shadow-sm transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:border-brand-500 min-w-[90px] sm:min-w-[130px] max-w-[120px] sm:max-w-[190px] active:scale-[0.98]"
      :aria-expanded="isOpen"
      aria-haspopup="listbox"
      role="combobox" :aria-controls="listId" :aria-activedescendant="isOpen && focusedIndex >= 0 ? `${listId}-${focusedIndex}` : undefined"
      :aria-label="selectedOption ? selectedOption.label : placeholder" :aria-description="placeholder"
      @click="toggle"
    >
      <div class="flex items-center gap-1.5 truncate">
        <component :is="prefixIcon" v-if="prefixIcon" class="w-3.5 h-3.5 text-zinc-500 dark:text-zinc-400 shrink-0" />
        <span class="truncate">
          {{ selectedOption ? (selectedOption.displayLabel || selectedOption.label) : placeholder }}
        </span>
      </div>
      <ChevronDown
        class="w-3.5 h-3.5 text-zinc-400 dark:text-zinc-500 shrink-0 transition-transform duration-150"
        :class="{ 'rotate-180': isOpen }"
      />
    </button>

    <!-- 统一圆角面板 (FB-001) -->
    <transition
      enter-active-class="transition duration-100 ease-out"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition duration-75 ease-in"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div
        v-if="isOpen"
        class="absolute right-0 z-50 mt-1 min-w-[200px] max-w-xs origin-top-right rounded-lg bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 shadow-xl py-1 text-xs focus:outline-none overflow-hidden"
        role="listbox"
        :id="listId" :aria-label="placeholder"
      >
        <div v-if="options.length === 0" class="px-3 py-2 text-zinc-400 text-center">
          {{ emptyText }}
        </div>
        <div v-else class="max-h-60 overflow-y-auto py-0.5">
          <div
            v-for="(option, index) in options"
            :key="option.value"
            role="option"
            :id="`${listId}-${index}`"
            :aria-selected="String(option.value) === String(modelValue)"
            class="flex items-center justify-between px-3 py-2 cursor-pointer transition-colors"
            :class="{
              'bg-brand-50/80 dark:bg-brand-950/40 text-brand-600 dark:text-brand-400 font-medium': String(option.value) === String(modelValue),
              'bg-zinc-100 dark:bg-zinc-700/60 text-zinc-900 dark:text-zinc-100': focusedIndex === index && String(option.value) !== String(modelValue),
              'text-zinc-700 dark:text-zinc-300 hover:bg-zinc-50 dark:hover:bg-zinc-700/40': focusedIndex !== index && String(option.value) !== String(modelValue)
            }"
            @click="select(option)"
            @mouseenter="focusedIndex = index"
          >
            <div class="flex flex-col truncate pr-2">
              <span class="truncate text-xs">{{ option.label }}</span>
              <span v-if="option.description" class="text-xs text-zinc-400 dark:text-zinc-500 font-normal truncate mt-0.5 font-mono">
                {{ option.description }}
              </span>
            </div>
            <div class="flex items-center gap-1.5 shrink-0">
              <span v-if="option.tag" class="px-1.5 py-0.5 text-xs font-mono rounded bg-zinc-100 dark:bg-zinc-700 text-zinc-500 dark:text-zinc-400">
                {{ option.tag }}
              </span>
              <Check
                v-if="String(option.value) === String(modelValue)"
                class="w-3.5 h-3.5 text-brand-600 dark:text-brand-400"
              />
            </div>
          </div>
        </div>

        <!-- 底部快捷新建入口 -->
        <div v-if="$slots.footer" class="border-t border-zinc-100 dark:border-zinc-700/60 pt-1 mt-1">
          <slot name="footer" :close="close" />
        </div>
      </div>
    </transition>
  </div>
</template>
