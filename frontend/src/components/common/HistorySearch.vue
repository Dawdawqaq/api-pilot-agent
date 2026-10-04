<script setup>
import { ref, watch } from 'vue'
const props = defineProps({label:String,query:{type:String,default:''},busy:Boolean,total:{type:Number,default:0},loaded:{type:Number,default:0}})
const emit = defineEmits(['search'])
const text = ref(props.query)
watch(() => props.query, query => { text.value = query })
</script>
<template>
  <div class="p-2 space-y-2 border-b border-zinc-200/60 dark:border-zinc-800">
    <form class="flex gap-1.5" @submit.prevent="emit('search',text.trim())">
      <input v-model="text" type="search" :aria-label="label" :placeholder="label" maxlength="200" :disabled="busy" class="w-full min-w-0 px-2 py-1.5 text-sm rounded-md border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 focus-ring" />
      <button class="btn shrink-0" :disabled="busy">搜索</button>
    </form>
    <p class="meta">{{ query ? '匹配' : '共' }} {{ total }} 条 · 已显示 {{ loaded }} 条</p>
  </div>
</template>
