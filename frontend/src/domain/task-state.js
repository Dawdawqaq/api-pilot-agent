export const isTerminalTask = task => ['SUCCEEDED', 'FAILED', 'NEEDS_REVIEW', 'CANCELLED'].includes(task?.status)

const canonical = value => {
  if (Array.isArray(value)) return value.map(canonical)
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]))
  return value
}

// 比较完整请求，计划变化后不得继承先前的执行结果。
const signature = request => JSON.stringify(canonical(request || {}))
export function presentSteps(task) {
  const results = new Map()
  const plan = task?.plan || []
  for (const call of [...(task?.toolCalls || [])].sort((a, b) => String(a.createdAt || '').localeCompare(String(b.createdAt || '')))) {
    if (call.toolName !== 'executeHttpRequest' || !Array.isArray(call.response?.steps)) continue
    for (const result of call.response.steps) {
      const offset = Number(result.stepIndex)
      const step = plan[offset]
      const executed = call.request?.steps?.[offset]
      if (!step || !executed || signature(executed) !== signature(step.request)) continue
      results.set(offset, result)
    }
  }
  return plan.map((step, offset) => {
    const result = results.get(offset)
    const confirming = task.status === 'WAITING_CONFIRMATION' && Number(task.confirmation?.stepIndex) === Number(step.index)
    return { ...step, displayIndex: offset + 1, result,
      displayStatus: result ? (result.success ? 'success' : 'failed') : confirming ? 'confirmation' : 'unknown' }
  })
}
