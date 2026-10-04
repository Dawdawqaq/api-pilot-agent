import test from 'node:test'
import assert from 'node:assert/strict'
import { taskRecovery, remainingConfirmation } from '../src/domain/task-recovery.js'

test('写入结果不确定和恢复上下文丢失时禁止提供复用入口', () => {
  for(const task of [{status:'NEEDS_REVIEW'},{status:'FAILED',errorCode:'EXECUTOR_409_003'},{status:'FAILED',errorCode:'AGENT_409_005'}]) {
    assert.equal(taskRecovery(task).reuse,false)
    assert.match(taskRecovery(task).message,/核验/)
  }
})
test('已执行过写步骤的失败不能描述为无副作用的重试', () => {
  const task={status:'FAILED',toolCalls:[{toolName:'executeHttpRequest',request:{steps:[{method:'POST'}]}}]}
  assert.match(taskRecovery(task).message,/已有写请求可能已生效/)
  assert.equal(taskRecovery({status:'SUCCEEDED'}),null)
})
test('规划错误引导配置，目标连接错误引导环境', () => {
  assert.equal(taskRecovery({status:'FAILED',errorCode:'AGENT_422_001'}).settings,true)
  assert.equal(taskRecovery({status:'FAILED',errorCode:'EXECUTOR_502_001'}).projects,true)
})
test('确认期限临界点即过期，无效时间不生成假的倒计时', () => {
  const deadline=Date.parse('2026-10-04T03:00:00Z')
  assert.equal(remainingConfirmation('2026-10-04T03:00:00Z',deadline-61000).label,'剩余 1 分 1 秒')
  assert.equal(remainingConfirmation('2026-10-04T03:00:00Z',deadline).expired,true)
  assert.equal(remainingConfirmation('bad-time'),null)
})
