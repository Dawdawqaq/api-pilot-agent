import test from 'node:test'
import assert from 'node:assert/strict'
import { presentSteps } from '../src/domain/task-state.js'

const request = {name:'查询',method:'GET',path:'/records',queryParams:{limit:'10'}}
const task = () => ({status:'EXECUTING',currentStep:20,plan:[{index:0,request}],toolCalls:[]})

test('工具游标或任务成功不等于步骤已有成功证据', () => {
  assert.equal(presentSteps({...task(),status:'SUCCEEDED'})[0].displayStatus,'unknown')
})
test('按请求签名匹配调用链结果而非工具编号', () => {
  const input = task()
  input.toolCalls = [{toolName:'executeHttpRequest',stepIndex:7,request:{steps:[request]},response:{steps:[{stepIndex:0,success:false}]}}]
  assert.equal(presentSteps(input)[0].displayStatus,'failed')
})
test('重规划变更请求后不沿用旧成功结果', () => {
  const input = task()
  input.toolCalls = [{toolName:'executeHttpRequest',request:{steps:[{...request,path:'/old-records'}]},response:{steps:[{stepIndex:0,success:true}]}}]
  assert.equal(presentSteps(input)[0].displayStatus,'unknown')
})
test('确认对应 confirmation 的步骤索引并使用展示序号', () => {
  const step = presentSteps({...task(),status:'WAITING_CONFIRMATION',confirmation:{stepIndex:0}})[0]
  assert.equal(step.displayStatus,'confirmation')
  assert.equal(step.displayIndex,1)
})
