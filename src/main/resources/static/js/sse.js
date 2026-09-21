/**
 * Agent 任务的可靠 SSE 客户端。
 * 支持游标续传、事件去重、连接状态通知和异常重连。
 */

export class AgentStreamClient {
  constructor(projectId, taskId, options = {}) {
    this.projectId = projectId;
    this.taskId = taskId;
    this.options = options;
    this.cursor = options.startCursor || 0;
    this.eventSource = null;
    this.listeners = new Set();
    this.openListeners = new Set();
    this.errorListeners = new Set();
    this.seenSequences = new Set();
    this.isClosed = false;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
  }

  connect() {
    if (this.isClosed) return;
    this.closeConnection();

    const url = `/api/v1/projects/${this.projectId}/agent-tasks/${this.taskId}/stream?after=${this.cursor}`;
    this.eventSource = new EventSource(url);
    this.eventSource.onopen = () => {
      this.reconnectAttempts = 0;
      this.emitOpen();
    };

    this.eventSource.addEventListener('agent-event', (e) => {
      try {
        const eventData = JSON.parse(e.data);
        const sequenceNo = Number(eventData.sequenceNo || 0);
        if (sequenceNo > 0 && (sequenceNo <= this.cursor || this.seenSequences.has(sequenceNo))) {
          return;
        }
        if (sequenceNo > 0) {
          this.cursor = sequenceNo;
          this.seenSequences.add(sequenceNo);
        }
        this.emit(eventData);

        // 收到明确终态事件后立即关闭连接，避免 EventSource 再次自动重连。
        const terminalStates = ['SUCCEEDED', 'FAILED', 'NEEDS_REVIEW', 'CANCELLED'];
        const terminalEvents = ['TASK_COMPLETED', 'TASK_CANCELLED', 'MANUAL_REVIEW_REQUIRED'];
        if (terminalStates.includes(eventData.state) && terminalEvents.includes(eventData.eventType)) {
          this.disconnect();
        }
      } catch (err) {
        console.error('解析 SSE Agent 事件失败：', err, e.data);
      }
    });

    this.eventSource.onerror = (err) => {
      if (this.isClosed) return;
      this.emitError(err);

      this.reconnectAttempts++;
      if (this.reconnectAttempts > this.maxReconnectAttempts) {
        console.warn(`任务 ${this.taskId} 的 SSE 已达到最大重连次数 ${this.maxReconnectAttempts}`);
        this.disconnect();
      }
    };
  }

  onEvent(callback) {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }

  onOpen(callback) {
    this.openListeners.add(callback);
    return () => this.openListeners.delete(callback);
  }

  onError(callback) {
    this.errorListeners.add(callback);
    return () => this.errorListeners.delete(callback);
  }

  emit(data) {
    for (const listener of this.listeners) {
      try {
        listener(data);
      } catch (err) {
        console.error('SSE 事件监听器执行失败：', err);
      }
    }
  }

  emitOpen() {
    for (const listener of this.openListeners) {
      try {
        listener();
      } catch (err) {
        console.error('SSE 连接监听器执行失败：', err);
      }
    }
  }

  emitError(err) {
    for (const listener of this.errorListeners) {
      try {
        listener(err);
      } catch (e) {
        console.error('SSE 错误监听器执行失败：', e);
      }
    }
  }

  closeConnection() {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  disconnect() {
    this.isClosed = true;
    this.closeConnection();
    this.listeners.clear();
    this.openListeners.clear();
    this.errorListeners.clear();
  }
}
