<script setup>
import { ref } from 'vue'
import { api, formatApiError } from '../../api/client'
import SimpleModal from '../common/SimpleModal.vue'
import ProjectDataView from './ProjectDataView.vue'

const props = defineProps({projects:Array,projectId:String,environments:Array,isMockMode:Boolean})
const emit = defineEmits(['updated'])
const editing = ref(null), deleting = ref(null), busy = ref(false), error = ref('')
const form = ref({})
const edit = (kind, item) => { error.value = ''; editing.value = {kind,id:String(item.id)}; form.value = {...item} }
const failure = formatApiError
const save = async () => {
  if (busy.value || props.isMockMode) return
  busy.value = true; error.value = ''
  try {
    if (editing.value.kind === 'project') {
      await api.updateProject(editing.value.id, {name:form.value.name,description:form.value.description,status:form.value.status})
    } else {
      const {name,baseUrl,allowedMethods,allowPrivateNetwork,defaultEnvironment} = form.value
      await api.updateEnvironment(props.projectId,editing.value.id,{name,baseUrl,allowedMethods,allowPrivateNetwork,defaultEnvironment})
    }
    editing.value = null
    emit('updated',props.projectId)
  } catch (value) { error.value = failure(value) }
  finally { busy.value = false }
}
const remove = async () => {
  if (busy.value || props.isMockMode) return
  busy.value = true; error.value = ''
  try {
    if (deleting.value.kind === 'project') await api.deleteProject(deleting.value.id)
    else await api.deleteEnvironment(props.projectId,deleting.value.id)
    deleting.value = null
    emit('updated',props.projectId)
  } catch (value) { error.value = failure(value) }
  finally { busy.value = false }
}
</script>

<template>
  <main class="max-w-5xl w-full mx-auto px-6 py-8 space-y-8 text-sm">
    <header><h1 class="text-lg font-semibold">项目与环境</h1><p class="mt-2 text-zinc-600 dark:text-zinc-400">在顶栏新建项目或环境，在此编辑、归档和管理留存数据。</p></header>
    <p v-if="isMockMode" role="status" class="text-amber-700 dark:text-amber-300">演示数据仅供查看，编辑与删除不会调用真实后端。</p>
    <section aria-labelledby="projects-heading">
      <h2 id="projects-heading" class="font-semibold mb-3">项目</h2>
      <p v-if="!projects?.length" class="text-zinc-500">暂无项目，请使用顶栏项目选择器创建。</p>
      <table v-else class="w-full text-left border-collapse"><thead><tr class="border-b border-zinc-200 dark:border-zinc-700"><th class="py-3">名称 / 编码</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="project in projects" :key="project.id" class="border-b border-zinc-100 dark:border-zinc-800"><td class="py-3">{{ project.name }} <span class="text-zinc-500 font-mono ml-2">{{ project.code }}</span></td><td>{{ project.status === 'ARCHIVED' ? '已归档' : '启用' }}</td><td class="space-x-4"><button class="text-brand-600 underline" :disabled="isMockMode || busy" @click="edit('project',project)">编辑项目</button><button class="text-red-600 underline" :disabled="isMockMode || busy" @click="deleting = {kind:'project',id:project.id,name:project.name}; error = ''">移入回收站</button></td></tr></tbody>
      </table>
    </section>
    <section aria-labelledby="environments-heading">
      <h2 id="environments-heading" class="font-semibold mb-3">当前项目的环境</h2>
      <p v-if="!environments?.length" class="text-zinc-500">当前项目没有环境，请使用顶栏环境选择器创建。</p>
      <table v-else class="w-full text-left"><thead><tr class="border-b border-zinc-200 dark:border-zinc-700"><th class="py-3">名称</th><th>Base URL</th><th>操作</th></tr></thead><tbody><tr v-for="environment in environments" :key="environment.id" class="border-b border-zinc-100 dark:border-zinc-800"><td class="py-3">{{ environment.name }}<span v-if="environment.defaultEnvironment" class="ml-2 text-zinc-500">默认</span></td><td class="font-mono">{{ environment.baseUrl }}</td><td class="space-x-4"><button class="text-brand-600 underline" :disabled="isMockMode || busy" @click="edit('environment',environment)">编辑环境</button><button class="text-red-600 underline" :disabled="isMockMode || busy" @click="deleting = {kind:'environment',id:environment.id,name:environment.name}; error = ''">删除环境</button></td></tr></tbody></table>
    </section>
    <SimpleModal :is-open="!!editing" :title="editing?.kind === 'project' ? '编辑项目' : '编辑环境'" @close="!busy && (editing = null)">
      <form class="space-y-4" @submit.prevent="save">
        <label class="block">名称<input v-model="form.name" required :maxlength="editing?.kind === 'project' ? 128 : 64" class="mt-1 w-full p-2 rounded-lg border dark:bg-zinc-800" /></label>
        <template v-if="editing?.kind === 'project'"><label class="block">说明<textarea v-model="form.description" maxlength="500" class="mt-1 w-full p-2 rounded-lg border dark:bg-zinc-800" /></label><label class="block">状态<select v-model="form.status" class="ml-3 p-2 rounded-lg border dark:bg-zinc-800"><option value="ACTIVE">启用</option><option value="ARCHIVED">归档</option></select></label></template>
        <template v-else><label class="block">Base URL<input v-model="form.baseUrl" required maxlength="512" type="url" class="mt-1 w-full p-2 rounded-lg border dark:bg-zinc-800" /></label><label class="block">允许方法<input v-model="form.allowedMethods" maxlength="128" class="mt-1 w-full p-2 rounded-lg border dark:bg-zinc-800" /></label><label class="flex gap-2"><input v-model="form.allowPrivateNetwork" type="checkbox" />允许私网与环回地址</label><label class="flex gap-2"><input v-model="form.defaultEnvironment" type="checkbox" />设为默认环境</label></template>
        <p v-if="error" role="alert" class="text-red-600">{{ error }}</p>
        <button class="px-4 py-2 rounded-lg bg-brand-600 text-white disabled:opacity-50" :disabled="busy || isMockMode">{{ busy ? '保存中…' : '保存修改' }}</button>
      </form>
    </SimpleModal>
    <ProjectDataView :project-id="projectId" :project-code="projects?.find(item => String(item.id) === projectId)?.code || ''" :is-mock-mode="isMockMode" @updated="id => emit('updated',id)" />
    <SimpleModal :is-open="!!deleting" :title="deleting?.kind === 'project' ? '移入项目回收站' : '确认删除环境'" @close="!busy && (deleting = null)">
      <p class="mb-4">{{ deleting?.kind === 'project' ? `将“${deleting?.name}”移入回收站？关联数据、文档与向量仍保留，可在回收站恢复。` : `确认删除环境“${deleting?.name}”？历史测试证据仍保留。` }}</p><p v-if="error" role="alert" class="text-red-600 mb-3">{{ error }}</p><button class="px-4 py-2 rounded-lg bg-red-600 text-white disabled:opacity-50" :disabled="busy || isMockMode" @click="remove">{{ busy ? '处理中…' : deleting?.kind === 'project' ? '确认移入回收站' : '确认删除环境' }}</button>
    </SimpleModal>
  </main>
</template>
