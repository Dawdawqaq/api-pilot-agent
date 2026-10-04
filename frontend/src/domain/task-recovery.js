// 只提供下一步操作指引，不把复用目标当作重放已执行请求。
export function taskRecovery(task) {
  const code = task.errorCode || ''
  if (task.status === 'NEEDS_REVIEW' || code === 'EXECUTOR_409_003' || code === 'AGENT_409_005') {
    return {message:'请先到被测系统核验已发出的写请求是否生效，确认记录与业务状态后再决定后续测试。此处不提供重复执行入口。',reuse:false}
  }
  if (!['FAILED','CANCELLED'].includes(task.status)) return null
  const writes = (task.toolCalls || []).filter(call => call.toolName === 'executeHttpRequest').some(call =>
    (call.request?.steps || []).some(step => !['GET','HEAD','OPTIONS'].includes(String(step.method || '').toUpperCase())))
  const suffix = writes ? '已有写请求可能已生效，请核验后再提交新目标。' : '复用目标只回填草稿，不会自动执行；请检查环境和变量后提交。'
  if (code === 'AGENT_408_001' || code === 'AGENT_409_002') return {message:'本次任务或人工确认已过期，请调整目标或及时确认新计划。'+suffix,reuse:true}
  if (code === 'AGENT_422_001') return {message:'请检查 LLM 配置与连接、项目资料规则及导入的接口；可以缩小目标后重新规划。'+suffix,reuse:true,settings:true}
  if (code.startsWith('EXECUTOR_403_')) return {message:'请核对环境地址、允许的方法、私网访问设置及当前 OpenAPI 版本。'+suffix,reuse:true,projects:true}
  if (code.startsWith('EXECUTOR_422_')) return {message:'请检查初始变量名称、JSONPath 和响应结构。'+suffix,reuse:true}
  if (code === 'EXECUTOR_502_001' || code === 'EXECUTOR_504_001') return {message:'请确认被测服务正在运行且环境地址可访问。'+suffix,reuse:true,projects:true}
  return {message:'查看下方执行证据，修正错误原因后可复用目标新建任务。'+suffix,reuse:true}
}

export function remainingConfirmation(expiresAt, now = Date.now()) {
  const deadline = new Date(expiresAt).getTime()
  if (!expiresAt || !Number.isFinite(deadline)) return null
  const seconds = Math.max(0,Math.ceil((deadline - now) / 1000))
  return {expired:seconds === 0,label:seconds === 0 ? '确认已过期，请重新准备测试目标' : '剩余 '+Math.floor(seconds / 60)+' 分 '+seconds % 60+' 秒'}
}
