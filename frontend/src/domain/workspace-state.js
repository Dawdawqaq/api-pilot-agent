const PREFIX = 'dochelper_goal_draft:'
const CONTEXT_KEY = 'dochelper_workspace_context'

// 目标文本可跨刷新恢复；所有初始变量只保存在本次页面内存，避免持久化凭据。
export function createWorkspaceState(storage) {
  const drafts = new Map()
  let persistenceAvailable = Boolean(storage)
  const read = key => { try { return JSON.parse(storage?.getItem(key) || 'null') } catch { return null } }
  const write = (key, value) => { try { storage?.setItem(key, JSON.stringify(value)) } catch { persistenceAvailable = false } }
  return {
    get persistenceAvailable() { return persistenceAvailable },
    getDraft(projectId) {
      if (!drafts.has(projectId)) {
        const saved = read(PREFIX + projectId)
        drafts.set(projectId, {goal: typeof saved?.goal === 'string' ? saved.goal.slice(0,2000) : '', variables:'{}'})
      }
      return {...drafts.get(projectId)}
    },
    saveDraft(projectId, draft) {
      if (!projectId) return
      drafts.set(projectId, {...draft})
      write(PREFIX + projectId, {goal: draft.goal})
    },
    getContext() {
      const saved = read(CONTEXT_KEY)
      return {projectId: typeof saved?.projectId === 'string' ? saved.projectId : '', environments: saved?.environments && typeof saved.environments === 'object' && !Array.isArray(saved.environments) ? saved.environments : {}}
    },
    saveContext(projectId, environmentId) {
      if (!projectId) return
      const context = this.getContext()
      context.projectId = String(projectId)
      if (environmentId) context.environments[projectId] = String(environmentId)
      write(CONTEXT_KEY, context)
    }
  }
}

let storage
try { storage = typeof window !== 'undefined' ? window.localStorage : null } catch { storage = null }
export const workspaceState = createWorkspaceState(storage)
