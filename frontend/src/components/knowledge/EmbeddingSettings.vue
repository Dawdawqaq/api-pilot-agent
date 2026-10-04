<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { ChevronDown } from 'lucide-vue-next'
import { api, formatApiError } from '../../api/client'
const props = defineProps({isMockMode:Boolean})
const config = ref(null), open = ref(false), loading = ref(false), busy = ref(false), error = ref(''), result = ref(null)
const form = ref({mode:'DEVELOPMENT',baseUrl:'',model:'',dimensions:null,apiKey:'',allowDocumentTransfer:false})
const selectApi = () => {
  if (form.value.mode === 'DEVELOPMENT') { form.value.dimensions = null; if (form.value.model === 'deterministic-local') form.value.model = '' }
  form.value.mode = 'API'
}
let disposed = false, revision = 0
onUnmounted(() => { disposed = true; form.value.apiKey = '' })
watch(form,() => { revision++; result.value = null },{deep:true,flush:'sync'})
async function load() {
  if (props.isMockMode || loading.value || busy.value) return
  loading.value = true; error.value = ''
  try {
    const response = await api.getEmbeddingConfig()
    if (disposed) return
    config.value = response
    form.value = {mode:response.mode,baseUrl:response.baseUrl || '',model:response.model || '',dimensions:response.requestedDimensions ?? null,apiKey:'',allowDocumentTransfer:false}
  } catch (failure) { if (!disposed) error.value = formatApiError(failure) }
  finally { if (!disposed) loading.value = false }
}
async function act(rebuild) {
  if (props.isMockMode || !config.value || busy.value) return
  const version = revision
  busy.value = true; error.value = ''; result.value = null
  const payload = {...form.value,dimensions:form.value.mode === 'DEVELOPMENT' ? null : form.value.dimensions || null}
  try {
    const response = rebuild ? await api.rebuildEmbedding(payload) : await api.testEmbedding(payload)
    if (disposed) return
    if (rebuild) {
      config.value = response; form.value.apiKey = ''; form.value.allowDocumentTransfer = false
      result.value = {success:true,message:'索引重建完成，新配置已经启用。'}
    } else if (version === revision) result.value = response
  } catch (failure) { if (!disposed) error.value = formatApiError(failure) }
  finally { if (!disposed) busy.value = false }
}
async function cleanUnused() {
  if (props.isMockMode || busy.value) return
  busy.value = true; error.value = ''
  try { const response = await api.cleanUnusedIndexes(); if (!disposed) config.value = response }
  catch (failure) { if (!disposed) error.value = formatApiError(failure) }
  finally { if (!disposed) busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="border-b border-zinc-200 dark:border-zinc-800 px-6 py-4 text-sm" aria-label="嵌入模型配置">
    <div class="flex items-center justify-between gap-4"><div class="min-w-0"><p class="font-medium">{{ config ? `嵌入模型 · ${config.model}` : '嵌入模型配置' }}</p><p class="mt-1 text-zinc-600 dark:text-zinc-400">{{ isMockMode ? '预览不读取真实嵌入配置' : config?.message || '正在读取当前生效配置…' }}</p></div><button class="btn shrink-0" :disabled="isMockMode || !config || busy" :aria-expanded="open" @click="open = !open"><span>配置嵌入</span><ChevronDown class="w-3.5 h-3.5" :class="{'-rotate-90':!open}" /></button></div>
    <p v-if="error" role="alert" class="mt-2 text-red-600 dark:text-red-400">{{ error }} <button class="underline" :disabled="busy || loading" @click="load">重新读取</button></p>
    <p v-if="config" class="meta mt-2">{{ config.dimensions || '自动识别' }} 维 · 全部项目 {{ config.indexedDocuments }} 份已索引文档 / {{ config.indexedChunks }} 个切片</p>
    <p v-if="config?.unusedCollections" class="mt-2 meta">有 {{ config.unusedCollections }} 个未使用的索引集合。<button class="underline" :disabled="busy || isMockMode" @click="cleanUnused">清理未使用索引</button></p>
    <form v-if="open" class="pt-5 space-y-4" @submit.prevent="act(true)">
      <fieldset :disabled="busy || config.rebuilding || !config.enabled" class="space-y-4">
        <div class="flex gap-5"><label class="flex items-center gap-2"><input v-model="form.mode" name="embedding-mode" type="radio" value="DEVELOPMENT" />开发向量</label><label class="flex items-center gap-2"><input :checked="form.mode === 'API'" name="embedding-mode" type="radio" @change="selectApi" />API 嵌入模型</label></div>
        <div v-if="form.mode === 'API'" class="grid grid-cols-2 gap-4">
          <label class="block">服务基础地址<input v-model="form.baseUrl" type="url" required maxlength="512" placeholder="https://供应商地址/v1" class="mt-1 w-full border border-zinc-200 dark:border-zinc-700 rounded-lg px-3 py-2 dark:bg-zinc-900 font-mono" /></label>
          <label class="block">嵌入模型名称<input v-model="form.model" required maxlength="200" placeholder="填写供应商支持的嵌入模型" class="mt-1 w-full border border-zinc-200 dark:border-zinc-700 rounded-lg px-3 py-2 dark:bg-zinc-900 font-mono" /></label>
          <label class="block">独立 API Key<input v-model="form.apiKey" type="password" autocomplete="new-password" maxlength="4096" :placeholder="config.apiKeyConfigured ? '留空沿用当前地址已保存的密钥' : '填写嵌入供应商密钥'" class="mt-1 w-full border border-zinc-200 dark:border-zinc-700 rounded-lg px-3 py-2 dark:bg-zinc-900" /></label>
          <label class="block">输出维度（可选）<input v-model.number="form.dimensions" type="number" min="1" max="8192" placeholder="留空由连接测试识别" class="mt-1 w-full border border-zinc-200 dark:border-zinc-700 rounded-lg px-3 py-2 dark:bg-zinc-900" /></label>
        </div>
        <p class="text-zinc-600 dark:text-zinc-400">对话模型与嵌入模型独立配置。重建包含所有项目已索引的文档（包括回收站项目中保留的文档），期间暂停知识库写入和检索；新索引全部完成后才启用，失败继续使用原索引。</p>
        <label v-if="form.mode === 'API'" class="flex items-start gap-2"><input v-model="form.allowDocumentTransfer" type="checkbox" class="mt-1" /><span>允许将上述 {{ config.indexedChunks }} 个业务切片发送给所选嵌入供应商，以重建索引。</span></label>
        <div class="flex gap-3"><button type="button" class="btn" @click="act(false)">测试连接</button><button class="btn" :disabled="form.mode === 'API' && (!form.allowDocumentTransfer || !config.persistentStorageReady)">重建索引并启用配置</button></div>
      </fieldset>
      <p v-if="busy || config.rebuilding" role="status" class="meta">正在执行，请稍候。重建耗时取决于切片数量与供应商速度。</p>
      <p v-if="result" :class="['status',result.success ? 'status-ok' : 'status-err']" role="status">{{ result.message }}</p>
      <p class="meta">测试连接只发送固定探测文本，可能产生少量费用。API Key 加密保存，不会写入浏览器存储。更换地址需要重新填写。</p>
    </form>
  </section>
</template>
