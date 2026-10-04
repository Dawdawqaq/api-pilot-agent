import test from 'node:test'
import assert from 'node:assert/strict'
import { api } from '../src/api/client.js'

test('网络恢复立即重新订阅，退出后移除 online 监听', t => {
  const saved={EventSource:globalThis.EventSource,window:globalThis.window}
  t.after(()=>{globalThis.EventSource=saved.EventSource;if(saved.window===undefined)delete globalThis.window;else globalThis.window=saved.window})
  const sources=[],listeners=new Map()
  globalThis.window={addEventListener:(name,callback)=>listeners.set(name,callback),removeEventListener:(name,callback)=>{assert.equal(listeners.get(name),callback);listeners.delete(name)}}
  globalThis.EventSource=class {
    constructor(url){this.url=url;this.closed=false;sources.push(this)}
    addEventListener(name,callback){this.receive=callback}
    close(){this.closed=true}
  }
  const stop=api.subscribeTaskStream('1','2',0,()=>{},()=>{})
  sources[0].receive({data:'{"sequenceNo":9}',lastEventId:'9'})
  listeners.get('online')()
  assert.equal(sources[0].closed,true)
  assert.equal(sources[1].url,'/api/v1/projects/1/agent-tasks/2/stream?after=9')
  stop()
  assert.equal(sources[1].closed,true)
  assert.equal(listeners.size,0)
})
