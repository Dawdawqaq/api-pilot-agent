package com.dochelper.system.api.vo;

import java.util.List;

/** 就绪检查只证明本地配置满足条件，不代表已连接模型供应商或目标服务。 */
public record WorkspaceReadinessResponse(boolean ready, List<Check> checks) {
    public record Check(String key, String label, String status, String message, String action) { }
}
