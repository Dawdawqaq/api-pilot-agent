// 历史任务与工具调用共用状态文案，未知状态保持中性显示。
const statuses = {
  SUCCEEDED: { text: '成功', tone: 'status-ok' },
  FAILED: { text: '失败', tone: 'status-err' },
  REJECTED: { text: '已拒绝', tone: 'status-warn' },
  WAITING_CONFIRMATION: { text: '待确认', tone: 'status-warn' },
  NEEDS_REVIEW: { text: '需核验', tone: 'status-warn' },
  CANCELLED: { text: '已取消', tone: '' },
  REPORTING: { text: '报告中', tone: 'status-busy' },
  EXECUTING: { text: '执行中', tone: 'status-busy' },
  OBSERVING: { text: '执行中', tone: 'status-busy' },
  REPLANNING: { text: '执行中', tone: 'status-busy' },
  RECEIVED: { text: '规划中', tone: 'status-busy' },
  RETRIEVING: { text: '规划中', tone: 'status-busy' },
  PLANNING: { text: '规划中', tone: 'status-busy' },
  RUNNING: { text: '执行中', tone: 'status-busy' },
  PENDING: { text: '待执行', tone: '' },
  SKIPPED: { text: '已跳过', tone: '' }
}

export const presentStatus = status => statuses[status] || { text: '未知状态', tone: '' }
