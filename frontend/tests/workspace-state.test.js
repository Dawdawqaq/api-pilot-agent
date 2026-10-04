import test from 'node:test'
import assert from 'node:assert/strict'
import { createWorkspaceState } from '../src/domain/workspace-state.js'

const storage = () => {
  const values = new Map()
  return {values,getItem:key => values.get(key),setItem:(key,value) => values.set(key,value)}
}
test('项目草稿隔离，换页可恢复变量，刷新只恢复目标且不持久化凭据', () => {
  const disk=storage(), state=createWorkspaceState(disk)
  state.saveDraft('9223372036854775801',{goal:'项目甲目标',variables:'{"token":"private-value"}'})
  state.saveDraft('2',{goal:'项目乙目标',variables:'{"id":"123"}'})
  assert.equal(state.getDraft('9223372036854775801').variables,'{"token":"private-value"}')
  assert.equal(state.getDraft('2').goal,'项目乙目标')
  assert.equal([...disk.values.values()].join('').includes('private-value'),false)
  const reopened=createWorkspaceState(disk)
  assert.deepEqual(reopened.getDraft('9223372036854775801'),{goal:'项目甲目标',variables:'{}'})
})
test('环境按项目记忆，项目及环境 ID 不转为数字', () => {
  const state=createWorkspaceState(storage())
  state.saveContext('9223372036854775801','9223372036854775802')
  state.saveContext('2','3')
  assert.equal(state.getContext().environments['9223372036854775801'],'9223372036854775802')
  assert.equal(state.getContext().projectId,'2')
})
test('存储禁用或内容损坏时仍可使用内存草稿', () => {
  const state=createWorkspaceState({getItem:()=>'{bad',setItem:()=>{throw new Error('存储已禁用')}})
  assert.equal(state.getDraft('1').goal,'')
  state.saveDraft('1',{goal:'保留草稿',variables:'{}'})
  assert.equal(state.getDraft('1').goal,'保留草稿')
  assert.equal(state.persistenceAvailable,false)
})
