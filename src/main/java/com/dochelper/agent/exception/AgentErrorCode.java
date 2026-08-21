package com.dochelper.agent.exception;

import com.dochelper.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Agent 模块稳定错误码。
 */
public enum AgentErrorCode implements ErrorCode {
    TASK_NOT_FOUND("AGENT_404_001", "Agent 任务不存在", HttpStatus.NOT_FOUND),
    INVALID_TASK("AGENT_400_001", "Agent 任务参数不合法", HttpStatus.BAD_REQUEST),
    INVALID_PLAN("AGENT_400_002", "Agent 执行计划不合法", HttpStatus.BAD_REQUEST),
    INVALID_STATE("AGENT_409_001", "当前任务状态不允许该操作", HttpStatus.CONFLICT),
    CONFIRMATION_NOT_FOUND("AGENT_404_002", "待确认操作不存在", HttpStatus.NOT_FOUND),
    CONFIRMATION_EXPIRED("AGENT_409_002", "危险操作确认已过期", HttpStatus.CONFLICT),
    TOOL_CALL_LIMIT("AGENT_429_001", "Agent 工具调用次数达到上限", HttpStatus.TOO_MANY_REQUESTS),
    PLAN_MODIFICATION_LIMIT("AGENT_429_002", "任务计划修改轮次已达上限", HttpStatus.TOO_MANY_REQUESTS),
    TASK_TIMEOUT("AGENT_408_001", "Agent 任务执行超时", HttpStatus.REQUEST_TIMEOUT),
    DUPLICATE_TOOL_CALL("AGENT_409_003", "检测到重复工具调用", HttpStatus.CONFLICT),
    TASK_BUSY("AGENT_409_004", "任务正在处理或修改中，请稍后重试", HttpStatus.CONFLICT),
    PLANNING_FAILED("AGENT_422_001", "Agent 无法生成可执行计划", HttpStatus.UNPROCESSABLE_ENTITY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    AgentErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
