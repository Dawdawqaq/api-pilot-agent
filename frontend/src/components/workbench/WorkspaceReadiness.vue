<script setup>
import { ref, watch, onUnmounted, computed } from 'vue'
import { ChevronDown } from 'lucide-vue-next'
import { api, formatApiError } from '../../api/client'
const props = defineProps({projectId:String,environmentId:String,isMockMode:Boolean,revision:Number})
const emit = defineEmits(['navigate'])
const data = ref(null), busy = ref(false), error = ref(''), expanded = ref(false)
let version = 0, disposed = false
const blocked = computed(() => data.value?.checks.filter(item => item.status === 'BLOCKED') || [])
async function refresh() {
  const current = ++version
  data.value = null; error.value = ''; busy.value = false
  if (props.isMockMode || !props.projectId) return
  busy.value = true
  try {
    const result = await api.getReadiness(props.projectId,props.environmentId)
    if (current === version && !disposed) { data.value = result; expanded.value = !result.ready }
  } catch (failure) { if (current === version && !disposed) error.value = formatApiError(failure) }
  finally { if (current === version && !disposed) busy.value = false }
}
watch(() => [props.projectId,props.environmentId,props.isMockMode,props.revision],refresh,{immediate:true})
onUnmounted(() => { disposed = true; version++ })
</script>
<template>
  <section v-if="!isMockMode" class="border-b border-zinc-200 dark:border-zinc-800 px-6 py-3 text-sm" aria-label="开始前检查">
    <div class="flex items-center justify-between gap-4">
      <button class="flex items-center gap-2 focus-ring rounded" :disabled="!data" :aria-expanded="expanded" @click="expanded = !expanded">
        <ChevronDown class="w-4 h-4" :class="{'-rotate-90':!expanded}" />
        <span v-if="!projectId">先创建一个测试项目</span>
        <span v-else-if="busy">正在检查使用条件…</span>
        <span v-else-if="data" :class="['status',data.ready ? 'status-ok' : 'status-warn']">{{ data.ready ? '配置已就绪' : `还有 ${blocked.length} 项需要配置` }}</span>
        <span v-else>就绪状态尚未确认</span>
      </button>
      <button v-if="projectId" class="btn" :disabled="busy" @click="refresh">重新检查</button>
      <button v-else class="btn" @click="emit('navigate','create-project')">创建项目</button>
    </div>
    <p v-if="error" role="alert" class="mt-2 text-red-600 dark:text-red-400">{{ error }}</p>
    <div v-if="expanded && data" class="mt-3 divide-y divide-zinc-100 dark:divide-zinc-800">
      <div v-for="item in data.checks" :key="item.key" class="grid grid-cols-[7rem_1fr_auto] items-start gap-3 py-2">
        <span :class="['status',item.status === 'BLOCKED' ? 'status-warn' : item.status === 'READY' ? 'status-ok' : '']">{{ item.label }}</span>
        <span class="text-zinc-600 dark:text-zinc-400">{{ item.message }}</span>
        <button class="underline underline-offset-4 focus-ring" @click="emit('navigate',item.action)">查看配置</button>
      </div>
      <p class="pt-2 meta">这里只检查配置与访问策略。模型连接测试在设置里执行；目标服务是否响应，以实际测试结果为准。</p>
    </div>
  </section>
</template>
