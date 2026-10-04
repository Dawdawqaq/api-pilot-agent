import test from 'node:test'
import assert from 'node:assert/strict'
import { api, ApiError, formatApiError } from '../src/api/client.js'

const originalFetch = globalThis.fetch
const mockFetch = (t, handler) => {
  globalThis.fetch = handler
  t.after(() => { globalThis.fetch = originalFetch })
}

test('历史查询正确编码搜索词，游标与项目 ID 保持字符串', async t => {
  const urls=[]
  mockFetch(t, async url => { urls.push(url); return new Response(JSON.stringify({code:'SUCCESS',data:{items:[],total:0,hasMore:false,nextCursor:null}})) })
  for(const load of [api.getTaskHistory,api.getReportHistory]) await load('9223372036854775801',{query:'中文 & 100%',beforeId:'9223372036854775800'})
  for(const url of urls) {
    const request=new URL(url,'http://localhost')
    assert.match(request.pathname,/9223372036854775801/)
    assert.equal(request.searchParams.get('query'),'中文 & 100%')
    assert.equal(request.searchParams.get('beforeId'),'9223372036854775800')
  }
})

test('XML 导出声明接受 XML，保留原始文本及字符串 ID', async t => {
  const id = '9223372036854775801', xml = '<testsuite tests="1" failures="0"/>'
  mockFetch(t, async (url, options) => {
    assert.equal(url, `/api/v1/projects/${id}/reports/${id}/junit.xml`)
    assert.match(options.headers.Accept, /application\/xml/)
    return new Response(xml, {headers:{'Content-Type':'application/xml;charset=UTF-8'}})
  })
  assert.equal(await api.exportJUnitXml(id,id),xml)
})

test('XML 接口的 JSON 业务失败仍保留错误码和追踪标识', async t => {
  mockFetch(t, async () => new Response(JSON.stringify({code:'REPORT_NOT_FOUND',message:'报告不存在',requestId:'xml-request'}), {status:404,headers:{'Content-Type':'application/json'}}))
  await assert.rejects(api.exportJUnitXml('1','2'), error => error instanceof ApiError && error.code==='REPORT_NOT_FOUND' && formatApiError(error).includes('xml-request'))
})

test('业务 code 失败不能因 HTTP 200 显示成功', async t => {
  mockFetch(t, async () => new Response(JSON.stringify({code:'POLICY_DENIED',message:'策略阻止',requestId:'policy-request'}), {headers:{'Content-Type':'application/json'}}))
  await assert.rejects(api.getModelPolicy('1'), {code:'POLICY_DENIED',requestId:'policy-request'})
})

test('上传让浏览器生成 multipart boundary，不强制 JSON 请求头', async t => {
  mockFetch(t, async (_, options) => {
    assert.ok(options.body instanceof FormData)
    assert.equal(options.headers['Content-Type'],undefined)
    assert.ok(options.body.get('file'))
    return new Response(JSON.stringify({code:'SUCCESS',data:{id:'9223372036854775801'}}), {headers:{'Content-Type':'application/json'}})
  })
  const result=await api.uploadOpenApi('1',new Blob(['{}'],{type:'application/json'}))
  assert.equal(result.id,'9223372036854775801')
})

test('误配代理返回 HTML 时明确显示响应错误', async t => {
  mockFetch(t, async () => new Response('<html>not the backend</html>', {headers:{'Content-Type':'text/html'}}))
  await assert.rejects(api.listProjects(),{code:'INVALID_RESPONSE'})
})
