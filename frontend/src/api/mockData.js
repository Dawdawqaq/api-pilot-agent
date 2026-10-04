/**
 * 代表性工作台预览演示数据 [MOCK_PREVIEW]
 * 静态示例仅用于外观预览；不执行任务，不作为接口或实际成功证据。
 * 不混淆真实数据，明确标记 [MOCK_PREVIEW]。
 */

export const MOCK_PROJECTS = [
  {
    id: "10001",
    code: "order-service",
    name: "电商交易与订单微服务",
    description: "核心交易履约链路 API，包含下单、支付回调、退款与订单查询",
    status: "ACTIVE",
    currentImportId: "20001",
    createdAt: "2026-10-01T10:00:00",
    updatedAt: "2026-10-02T15:30:00"
  },
  {
    id: "10002",
    code: "auth-gateway",
    name: "统一鉴权中心",
    description: "OAuth2 / JWT 令牌签发与校验系统",
    status: "ACTIVE",
    currentImportId: "20002",
    createdAt: "2026-09-28T09:12:00",
    updatedAt: "2026-10-01T11:20:00"
  }
]

export const MOCK_ENVIRONMENTS = [
  {
    id: "30001",
    projectId: "10001",
    name: "本地集成环境 (Local-Dev)",
    baseUrl: "http://127.0.0.1:8081",
    allowedMethods: "GET,POST,PUT,DELETE",
    allowPrivateNetwork: true,
    defaultEnvironment: true,
    createdAt: "2026-10-01T10:05:00",
    updatedAt: "2026-10-01T10:05:00"
  },
  {
    id: "30002",
    projectId: "10001",
    name: "预发布沙箱 (Staging-Sandbox)",
    baseUrl: "https://staging.order.internal.net",
    allowedMethods: "GET,POST",
    allowPrivateNetwork: false,
    defaultEnvironment: false,
    createdAt: "2026-10-01T10:06:00",
    updatedAt: "2026-10-02T16:00:00"
  }
]

export const MOCK_SYSTEM_OVERVIEW = {
  application: "DocHelper / ApiPilot",
  schemaVersion: "2026.10",
  qdrantCollection: "dochelper_knowledge",
  objectStorageBucket: "dochelper-files",
  knowledgeEnabled: true,
  chatModel: "qwen-max-2026",
  modelMode: "API",
  modelProvider: "DASHSCOPE"
}

export const MOCK_TASKS_LIST = [
  {
    id: "9001",
    projectId: "10001",
    environmentId: "30001",
    goal: "针对订单退款接口执行全链路负向验证，测试非法金额与重复退款拦截",
    status: "WAITING_CONFIRMATION", // 处于待人工确认阶段
    currentStep: 2,
    maxSteps: 4,
    toolCallCount: 3,
    replanCount: 0,
    modificationCount: 0,
    createdAt: "2026-10-03T11:15:20",
    updatedAt: "2026-10-03T11:16:05"
  },
  {
    id: "9002",
    projectId: "10001",
    environmentId: "30001",
    goal: "验证 GET /api/v1/orders 分页查询接口在 page=0 和 size=1000 时的容错与边界断言",
    status: "SUCCEEDED",
    currentStep: 3,
    maxSteps: 3,
    toolCallCount: 4,
    replanCount: 0,
    modificationCount: 0,
    createdAt: "2026-10-03T10:45:00",
    updatedAt: "2026-10-03T10:46:12"
  },
  {
    id: "9003",
    projectId: "10001",
    environmentId: "30001",
    goal: "测试支付超时关单触发写操作，核实远端持久化结果",
    status: "NEEDS_REVIEW",
    currentStep: 2,
    maxSteps: 3,
    toolCallCount: 2,
    replanCount: 1,
    modificationCount: 0,
    createdAt: "2026-10-03T09:30:00",
    updatedAt: "2026-10-03T09:32:45"
  }
]

// 核心待审查任务代表性详情（涵盖完整的 WAITING_CONFIRMATION 安全闸门、步骤、工具调用与模型审计）
const MOCK_ACTIVE_TASK = {
  id: "9001",
  projectId: "10001",
  environmentId: "30001",
  conversationId: "888001",
  goal: "针对订单退款接口执行全链路负向验证，测试非法金额与重复退款拦截 [MOCK_PREVIEW]",
  status: "WAITING_CONFIRMATION",
  currentStep: 2,
  maxSteps: 4,
  toolCallCount: 3,
  replanCount: 0,
  modificationCount: 0,
  resultSummary: null,
  errorCode: null,
  errorMessage: null,
  cancelRequested: false,
  deadlineAt: "2026-10-03T11:45:20",
  createdAt: "2026-10-03T11:15:20",
  startedAt: "2026-10-03T11:15:21",
  completedAt: null,
  updatedAt: "2026-10-03T11:16:05",

  // 严格的 AgentConfirmationResponse (带有真实 SHA-256 64 位哈希)
  confirmation: {
    id: "7701",
    stepIndex: 1,
    status: "PENDING",
    planHash: "6c2084df0a9965d1d6a69ef2944b20a7b4582f3c0e5a88c227318ec89ef14c5a",
    decisionNote: null,
    decidedByUserId: null,
    expiresAt: "2026-10-03T11:30:00",
    createdAt: "2026-10-03T11:16:05",
    decidedAt: null
  },

  // 规划的计划步骤 (AgentPlanStep)
  plan: [
    {
      index: 0,
      objective: "获取前置订单标识：调用 GET /api/v1/orders 检索待退款测试订单并提取 orderId 与 paymentToken",
      request: {
        name: "查询可用测试订单",
        method: "GET",
        path: "/api/v1/orders",
        queryParams: { status: "PAID", limit: "1" },
        headers: { "X-Request-Source": "ApiPilot-E2E" },
        extractors: [
          { name: "orderId", jsonPath: "$.data.items[0].id" },
          { name: "paymentToken", jsonPath: "$.data.items[0].paymentToken" }
        ],
        assertions: [
          { type: "STATUS_CODE", jsonPath: null, expectedValue: 200, expectedType: "NUMBER" },
          { type: "FIELD_EXISTS", jsonPath: "$.data.items[0].id", expectedValue: null, expectedType: "STRING" }
        ],
        dangerousOperationConfirmed: false
      }
    },
    {
      index: 1,
      objective: "【危险写操作-需人工确认】发起退款请求 POST /api/v1/orders/{orderId}/refund，传入金额 refundAmount: -500.00 检验非法负数拦截",
      request: {
        name: "提交负数金额退款申请",
        method: "POST",
        path: "/api/v1/orders/{orderId}/refund",
        pathVariables: { orderId: "{{orderId}}" },
        headers: { "Content-Type": "application/json", "Authorization": "Bearer {{paymentToken}}" },
        body: { refundAmount: -500.00, reason: "自动化负向测试 [E2E_TEST]" },
        assertions: [
          { type: "STATUS_CODE", jsonPath: null, expectedValue: 400, expectedType: "NUMBER" },
          { type: "FIELD_EQUALS", jsonPath: "$.code", expectedValue: "ORDER_400_003", expectedType: "STRING" }
        ],
        dangerousOperationConfirmed: false
      }
    },
    {
      index: 2,
      objective: "校验订单状态持久化未被异常篡改：调用 GET /api/v1/orders/{orderId} 断言订单状态依然为 PAID",
      request: {
        name: "核验订单原状",
        method: "GET",
        path: "/api/v1/orders/{orderId}",
        pathVariables: { orderId: "{{orderId}}" },
        assertions: [
          { type: "STATUS_CODE", jsonPath: null, expectedValue: 200, expectedType: "NUMBER" },
          { type: "FIELD_EQUALS", jsonPath: "$.data.status", expectedValue: "PAID", expectedType: "STRING" }
        ],
        dangerousOperationConfirmed: false
      }
    }
  ],

  // 历史工具调用记录 (AgentToolCallResponse)
  toolCalls: [
    {
      id: "5501",
      stepIndex: 0,
      toolName: "executeHttpRequest",
      status: "SUCCEEDED",
      attempt: 1,
      request: {
        stepIndex: 0,
        name: "查询可用测试订单",
        method: "GET",
        path: "/api/v1/orders?status=PAID&limit=1",
        headers: { "X-Request-Source": "ApiPilot-E2E" }
      },
      response: {
        httpStatus: 200,
        durationMs: 38,
        responseBody: {
          code: "SUCCESS",
          message: "操作成功",
          data: {
            items: [
              {
                id: "ord_99812401",
                orderNo: "PO-20261003-8891",
                amount: 1299.00,
                status: "PAID",
                paymentToken: "tok_sec_live_998273"
              }
            ]
          }
        },
        extractedVariables: {
          orderId: "ord_99812401",
          paymentToken: "tok_sec_live_998273"
        },
        assertions: [
          { type: "STATUS_CODE", passed: true, expected: "200", actual: "200", message: "HTTP 状态码匹配" },
          { type: "FIELD_EXISTS", jsonPath: "$.data.items[0].id", passed: true, expected: "EXISTS", actual: "ord_99812401", message: "主键存在" }
        ]
      },
      durationMs: 42,
      errorCode: null,
      errorMessage: null,
      createdAt: "2026-10-03T11:15:25",
      completedAt: "2026-10-03T11:15:26"
    }
  ],

  // 模型调用审计 (AgentModelCallResponse)
  modelCalls: [
    {
      id: "6601",
      modelName: "qwen-max-2026",
      status: "SUCCEEDED",
      attempt: 1,
      promptTokens: 840,
      completionTokens: 312,
      totalTokens: 1152,
      durationMs: 1420,
      errorCode: null,
      errorMessage: null,
      createdAt: "2026-10-03T11:15:21",
      completedAt: "2026-10-03T11:15:23"
    }
  ]
}

export const MOCK_TASK_9002 = {
  id: "9002",
  projectId: "10001",
  environmentId: "30001",
  conversationId: "888002",
  goal: "验证 GET /api/v1/orders 分页查询接口在 page=0 和 size=1000 时的容错与边界断言",
  status: "SUCCEEDED",
  currentStep: 3,
  maxSteps: 3,
  toolCallCount: 4,
  replanCount: 0,
  modificationCount: 0,
  resultSummary: "分页边界测试全部完成：\n1. page=0 服务端自动校正为第 1 页并返回 HTTP 200。\n2. size=1000 触发服务端单页大小上限熔断（自动截断为 100 条）。\n全部 4 项断言均严格通过，符合接口契约规范。",
  errorCode: null,
  errorMessage: null,
  cancelRequested: false,
  deadlineAt: "2026-10-03T11:00:00",
  createdAt: "2026-10-03T10:45:00",
  startedAt: "2026-10-03T10:45:01",
  completedAt: "2026-10-03T10:46:12",
  updatedAt: "2026-10-03T10:46:12",
  confirmation: null,
  plan: [
    {
      index: 0,
      objective: "测试 page=0 非法入参校正：调用 GET /api/v1/orders?page=0&size=10 断言 200 且 items 存在",
      request: { method: "GET", path: "/api/v1/orders?page=0&size=10" }
    },
    {
      index: 1,
      objective: "测试 size=1000 超限截断：调用 GET /api/v1/orders?page=1&size=1000 断言返回条数 <= 100",
      request: { method: "GET", path: "/api/v1/orders?page=1&size=1000" }
    },
    {
      index: 2,
      objective: "断言响应结构符合 OpenAPI 分页契约包含 total、items 与 page 字段",
      request: { method: "GET", path: "/api/v1/orders?page=1&size=1" }
    }
  ],
  toolCalls: [
    {
      id: "5601",
      stepIndex: 0,
      toolName: "executeHttpRequest",
      status: "SUCCEEDED",
      attempt: 1,
      request: {
        stepIndex: 0,
        name: "测试 page=0 非法入参校正",
        method: "GET",
        path: "/api/v1/orders?page=0&size=10"
      },
      response: {
        httpStatus: 200,
        durationMs: 25,
        responseBody: { code: "SUCCESS", message: "操作成功", data: { items: [], page: 1, size: 10 } },
        assertions: [
          { type: "STATUS_CODE", passed: true, expected: "200", actual: "200", message: "HTTP 200 正常响应" },
          { type: "FIELD_EQUALS", jsonPath: "$.data.page", passed: true, expected: "1", actual: "1", message: "自动校正 page=1" }
        ]
      },
      durationMs: 30,
      createdAt: "2026-10-03T10:45:05",
      completedAt: "2026-10-03T10:45:06"
    }
  ],
  modelCalls: [
    {
      id: "6701",
      modelName: "qwen-max-2026",
      status: "SUCCEEDED",
      attempt: 1,
      promptTokens: 720,
      completionTokens: 260,
      totalTokens: 980,
      durationMs: 1100,
      createdAt: "2026-10-03T10:45:01",
      completedAt: "2026-10-03T10:45:03"
    }
  ]
}

export const MOCK_TASK_9003 = {
  id: "9003",
  projectId: "10001",
  environmentId: "30001",
  conversationId: "888003",
  goal: "测试支付超时关单触发写操作，核实远端持久化结果",
  status: "NEEDS_REVIEW",
  currentStep: 2,
  maxSteps: 3,
  toolCallCount: 2,
  replanCount: 1,
  modificationCount: 0,
  resultSummary: "人工复核建议：关单操作触发了服务端 HTTP 504 Gateway Timeout。由于是写操作，可能造成远端数据部分写入但调用方未收到确认，请人工比对支付网关日志并排查数据库死锁。",
  errorCode: "GATEWAY_TIMEOUT",
  errorMessage: "上游服务响应超时 (HTTP 504)，无法保证数据写入原子性",
  cancelRequested: false,
  deadlineAt: "2026-10-03T10:00:00",
  createdAt: "2026-10-03T09:30:00",
  startedAt: "2026-10-03T09:30:01",
  completedAt: "2026-10-03T09:32:45",
  updatedAt: "2026-10-03T09:32:45",
  confirmation: null,
  plan: [
    {
      index: 0,
      objective: "获取待关单的测试订单",
      request: { method: "GET", path: "/api/v1/orders/pending-timeout" }
    },
    {
      index: 1,
      objective: "【写操作】请求 POST /api/v1/orders/ord_test_01/close-timeout 触发关单",
      request: { method: "POST", path: "/api/v1/orders/ord_test_01/close-timeout" }
    }
  ],
  toolCalls: [
    {
      id: "5701",
      stepIndex: 1,
      toolName: "executeHttpRequest",
      status: "FAILED",
      attempt: 1,
      request: {
        stepIndex: 1,
        name: "请求关单",
        method: "POST",
        path: "/api/v1/orders/ord_test_01/close-timeout"
      },
      response: {
        httpStatus: 504,
        durationMs: 5020,
        responseBody: { code: "GATEWAY_TIMEOUT", message: "网关超时" },
        assertions: [
          { type: "STATUS_CODE", passed: false, expected: "200", actual: "504", message: "HTTP 状态码不匹配" }
        ]
      },
      durationMs: 5025,
      createdAt: "2026-10-03T09:31:00",
      completedAt: "2026-10-03T09:31:05"
    }
  ],
  modelCalls: []
}

export const MOCK_TASK_MAP = {
  "9001": MOCK_ACTIVE_TASK,
  "9002": MOCK_TASK_9002,
  "9003": MOCK_TASK_9003
}

// 接口文档导入记录 (OpenApiImportResponse)
export const MOCK_OPENAPI_IMPORTS = [
  {
    id: "20001",
    projectId: "10001",
    revisionNumber: 2,
    retryOfId: null,
    fileName: "order-service-api-v2.yaml",
    contentHash: "8f4a13b5c6d7e8f90123456789abcdef0123456789abcdef0123456789abcdef",
    specificationVersion: "3.0.3",
    documentTitle: "Order Fulfillment Service API",
    documentVersion: "2.1.0",
    status: "SUCCEEDED",
    errorMessage: null,
    endpointCount: 14,
    schemaCount: 28,
    securitySchemeCount: 1,
    createdAt: "2026-10-02T15:20:00",
    completedAt: "2026-10-02T15:20:03"
  },
  {
    id: "20002",
    projectId: "10001",
    revisionNumber: 1,
    retryOfId: null,
    fileName: "order-service-api-v1-broken.json",
    contentHash: "123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0",
    specificationVersion: "3.0.0",
    documentTitle: "Order API Draft",
    documentVersion: "1.0.0-snapshot",
    status: "FAILED",
    errorMessage: "YAML/JSON 解析异常: 无法解析 schemas.PaymentCallback 中的循环引用定义",
    endpointCount: 0,
    schemaCount: 0,
    securitySchemeCount: 0,
    createdAt: "2026-10-01T10:10:00",
    completedAt: "2026-10-01T10:10:02"
  }
]

// 接口清单 (ApiEndpointResponse)
export const MOCK_ENDPOINTS = [
  {
    id: "40001",
    importId: "20001",
    path: "/api/v1/orders",
    httpMethod: "GET",
    operationId: "listOrders",
    summary: "分页检索订单列表",
    description: "支持按订单状态 status (PAID/UNPAID/CANCELLED) 和时间范围进行筛选，默认单页 20 条",
    tags: ["OrderQuery"],
    deprecated: false,
    requestBody: null,
    responses: {
      "200": { description: "成功获取订单列表", schema: "OrderListResponse" },
      "400": { description: "非法查询参数", schema: "ApiError" }
    },
    parameters: [
      { name: "status", location: "query", required: false, description: "订单状态过滤枚举" },
      { name: "limit", location: "query", required: false, description: "单页条数，最大 100" }
    ]
  },
  {
    id: "40002",
    importId: "20001",
    path: "/api/v1/orders/{orderId}/refund",
    httpMethod: "POST",
    operationId: "applyRefund",
    summary: "发起订单退款申请",
    description: "对已支付订单进行全额或部分退款。此接口属于危险写操作，需通过人工授权确认。",
    tags: ["OrderWrite"],
    deprecated: false,
    requestBody: {
      required: true,
      content: {
        "application/json": {
          schema: {
            type: "object",
            required: ["refundAmount"],
            properties: {
              refundAmount: { type: "number", description: "退款金额，必须大于 0" },
              reason: { type: "string", description: "退款原因说明" }
            }
          }
        }
      }
    },
    responses: {
      "200": { description: "退款申请成功受理" },
      "400": { description: "金额非法或订单状态不支持退款" },
      "409": { description: "检测到重复退款申请" }
    },
    parameters: [
      { name: "orderId", location: "path", required: true, description: "目标订单唯一标识" }
    ]
  },
  {
    id: "40003",
    importId: "20001",
    path: "/api/v1/orders/{orderId}",
    httpMethod: "GET",
    operationId: "getOrderById",
    summary: "根据主键获取订单详情",
    description: "返回包含支付令牌、履约物流状态和金额明细的订单全量聚合根数据",
    tags: ["OrderQuery"],
    deprecated: false,
    parameters: [
      { name: "orderId", location: "path", required: true, description: "订单全局标识" }
    ]
  }
]

// 接口依赖边 (EndpointDependencyEdge)
export const MOCK_DEPENDENCY_EDGES = [
  {
    producerEndpointId: "40001",
    producerOperationId: "listOrders",
    consumerEndpointId: "40002",
    consumerOperationId: "applyRefund",
    sharedField: "items[].id -> orderId",
    confidence: 0.96,
    reason: "GET /api/v1/orders 响应体 items 中的订单标识作为退款接口的路径变量输入"
  },
  {
    producerEndpointId: "40001",
    producerOperationId: "listOrders",
    consumerEndpointId: "40003",
    consumerOperationId: "getOrderById",
    sharedField: "items[].id -> orderId",
    confidence: 0.99,
    reason: "列表查询主键用于单条订单详情核实校验"
  }
]

// 测试报告列表 (TestReportResponse)
export const MOCK_TEST_REPORTS = [
  {
    id: "80001",
    projectId: "10001",
    taskId: "9002",
    executionId: "70002",
    title: "GET /api/v1/orders 分页查询边界及容错自动化测试报告",
    status: "SUCCEEDED",
    summary: "全链路 3 个步骤全部断言成功，通过率 100%，未触发任何异常",
    totalSteps: 3,
    passedSteps: 3,
    failedSteps: 0,
    totalToolCalls: 4,
    durationMs: 420,
    evidenceCitations: ["order-service-api-v2.yaml#/paths/~1api~1v1~1orders/get"],
    createdAt: "2026-10-03T10:46:12"
  },
  {
    id: "80002",
    projectId: "10001",
    taskId: "9003",
    executionId: "70003",
    title: "订单支付超时关单触发写操作测试",
    status: "NEEDS_REVIEW",
    summary: "第 2 步网关超时 (HTTP 504)，远端持久化结果不确定，需要人工进入数据库核验",
    totalSteps: 3,
    passedSteps: 1,
    failedSteps: 1,
    totalToolCalls: 2,
    durationMs: 5120,
    evidenceCitations: ["order-service-api-v2.yaml#/paths/~1api~1v1~1orders~1{id}~1close-timeout/post"],
    createdAt: "2026-10-03T09:32:45"
  }
]

// 详细测试报告单条数据
export const MOCK_REPORT_DETAIL = {
  id: "80001",
  projectId: "10001",
  taskId: "9002",
  executionId: "70002",
  title: "GET /api/v1/orders 分页查询边界及容错自动化测试报告 [MOCK_PREVIEW]",
  status: "SUCCEEDED",
  summary: "3 个执行步骤全部断言成功，通过率 100%，总耗时 420ms，生成的断言包括状态码校验、JSONPath 数组非空校验及分页上限保护。",
  totalSteps: 3,
  passedSteps: 3,
  failedSteps: 0,
  totalToolCalls: 4,
  durationMs: 420,
  evidenceCitations: [
    "OpenAPI Spec v2.1.0: GET /api/v1/orders",
    "业务退款规则指南 第 3.2 节"
  ],
  steps: [
    {
      id: "81001",
      stepIndex: 0,
      stepName: "基础分页检索 (page=1, limit=10)",
      httpMethod: "GET",
      requestUrl: "http://127.0.0.1:8081/api/v1/orders?page=1&limit=10",
      requestHeaders: { "X-Request-Source": "ApiPilot-Automated-Test" },
      requestBody: null,
      responseStatus: 200,
      responseHeaders: { "Content-Type": "application/json" },
      responseBody: {
        code: "SUCCESS",
        data: {
          items: [{ id: "ord_001", amount: 199.00, status: "PAID" }],
          total: 1
        }
      },
      assertions: [
        { type: "STATUS_CODE", passed: true, expected: "200", actual: "200", message: "HTTP 状态码符合预期" },
        { type: "FIELD_EXISTS", jsonPath: "$.data.items", passed: true, message: "数组节点存在" }
      ],
      success: true,
      durationMs: 35,
      errorMessage: null,
      createdAt: "2026-10-03T10:45:10"
    },
    {
      id: "81002",
      stepIndex: 1,
      stepName: "边界参数测试 (page=0)",
      httpMethod: "GET",
      requestUrl: "http://127.0.0.1:8081/api/v1/orders?page=0&limit=10",
      requestHeaders: { "X-Request-Source": "ApiPilot-Automated-Test" },
      requestBody: null,
      responseStatus: 200,
      responseHeaders: { "Content-Type": "application/json" },
      responseBody: { code: "SUCCESS", data: { items: [], total: 0 } },
      assertions: [
        { type: "STATUS_CODE", passed: true, expected: "200", actual: "200", message: "HTTP 状态码自动平滑修正为 200" }
      ],
      success: true,
      durationMs: 28,
      errorMessage: null,
      createdAt: "2026-10-03T10:45:30"
    },
    {
      id: "81003",
      stepIndex: 2,
      stepName: "超大单页条数边界保护 (limit=1000)",
      httpMethod: "GET",
      requestUrl: "http://127.0.0.1:8081/api/v1/orders?limit=1000",
      requestHeaders: { "X-Request-Source": "ApiPilot-Automated-Test" },
      requestBody: null,
      responseStatus: 200,
      responseHeaders: { "Content-Type": "application/json" },
      responseBody: { code: "SUCCESS", message: "服务端自动按最大 100 条截断保护", data: { items: [], total: 0 } },
      assertions: [
        { type: "STATUS_CODE", passed: true, expected: "200", actual: "200", message: "服务端截断机制生效，未抛出 500" }
      ],
      success: true,
      durationMs: 40,
      errorMessage: null,
      createdAt: "2026-10-03T10:46:00"
    }
  ]
}

// 业务知识库文档 (KnowledgeDocumentResponse)
export const MOCK_KNOWLEDGE_DOCUMENTS = [
  {
    id: "60001",
    projectId: "10001",
    fileName: "电商交易核心逆向退款与结算业务规范_v3.docx",
    contentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    fileSize: 1048576,
    contentHash: "a1b2c3d4e5f67890123456789abcdef0",
    title: "交易核心逆向退款与结算规范",
    status: "INDEXED",
    errorMessage: null,
    chunkCount: 18,
    createdAt: "2026-10-02T11:00:00",
    indexedAt: "2026-10-02T11:00:04",
    updatedAt: "2026-10-02T11:00:04"
  },
  {
    id: "60002",
    projectId: "10001",
    fileName: "网关认证与风控限流参数手册.pdf",
    contentType: "application/pdf",
    fileSize: 524288,
    contentHash: "b2c3d4e5f67890123456789abcdef012",
    title: "网关认证与风控限流参数手册",
    status: "INDEXED",
    errorMessage: null,
    chunkCount: 12,
    createdAt: "2026-10-01T14:30:00",
    indexedAt: "2026-10-01T14:30:05",
    updatedAt: "2026-10-01T14:30:05"
  }
]

// 混合检索模拟结果 (RetrievalResult)
export const MOCK_RETRIEVAL_RESULTS = [
  {
    chunkId: "61001",
    documentId: "60001",
    sourceName: "电商交易核心逆向退款与结算业务规范_v3.docx",
    section: "第三章 逆向风控与非法负数金额拦截",
    chunkIndex: 4,
    content: "3.2 资金安全性约束：所有通过退款网关（/api/v1/orders/{id}/refund）提交的退款金额 refundAmount 必须大于零，且不能超过订单实付金额。对于非法负数请求，服务端统一拦截并返回错误码 ORDER_400_003。",
    fusedScore: 0.942,
    keywordRank: 1,
    vectorRank: 1,
    citation: "电商交易核心逆向退款与结算业务规范_v3.docx (第3章)"
  },
  {
    chunkId: "61002",
    documentId: "60001",
    sourceName: "电商交易核心逆向退款与结算业务规范_v3.docx",
    section: "第四章 重复退款防抖机制",
    chunkIndex: 7,
    content: "4.1 幂等性要求：同一订单在 30 秒内仅允许提交一次相同业务流水号的退款请求，若检测到重复提交应返回 HTTP 409 Conflict。",
    fusedScore: 0.815,
    keywordRank: 2,
    vectorRank: 3,
    citation: "电商交易核心逆向退款与结算业务规范_v3.docx (第4章)"
  }
]
