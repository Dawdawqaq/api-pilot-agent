import test from 'node:test'
import assert from 'node:assert/strict'
import { api } from '../src/api/client.js'

test('SSE 重连从已收到的游标继续，切换任务后清理定时器和连接', t => {
  const saved={EventSource:globalThis.EventSource,setTimeout:globalThis.setTimeout,clearTimeout:globalThis.clearTimeout}
  t.after(()=>Object.assign(globalThis,saved))
  const sources=[],timers=[],cleared=[]
  globalThis.setTimeout=callback=>{timers.push(callback);return timers.length}
  globalThis.clearTimeout=id=>cleared.push(id)
  globalThis.EventSource=class {
    constructor(url) { this.url=url;this.closed=false;sources.push(this) }
    addEventListener(name,callback) { if(name==='agent-event')this.receive=callback }
    close() { this.closed=true }
  }
  const received=[],errors=[]
  const stop=api.subscribeTaskStream('9223372036854775801','2',0,event=>received.push(event),error=>errors.push(error))
  sources[0].receive({data:'{"sequenceNo":7,"state":"WAITING_CONFIRMATION"}',lastEventId:'7'})
  sources[0].receive({data:'{"sequenceNo":7,"state":"WAITING_CONFIRMATION"}',lastEventId:'7'})
  assert.equal(received.length,1)
  sources[0].onerror(new Error('模拟断网'))
  assert.equal(sources[0].closed,true)
  assert.equal(errors.length,1)
  timers[0]()
  sources[0].onerror(new Error('旧连接迟到的错误'))
  assert.equal(timers.length,1)
  assert.equal(sources[1].closed,false)
  assert.equal(sources[1].url,'/api/v1/projects/9223372036854775801/agent-tasks/2/stream?after=7')
  sources[1].receive({data:'{"sequenceNo":8,"state":"CANCELLED"}',lastEventId:'8'})
  assert.equal(received.length,2)
  sources[1].onerror(new Error('模拟再次断网'))
  stop()
  assert.equal(sources[1].closed,true)
  assert.ok(cleared.includes(2))
  timers[1]()
  assert.equal(sources.length,2)
  sources[1].receive({data:'{"sequenceNo":9}',lastEventId:'9'})
  assert.equal(received.length,2)
})
