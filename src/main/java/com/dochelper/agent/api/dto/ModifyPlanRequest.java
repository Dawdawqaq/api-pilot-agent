package com.dochelper.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 人工多轮计划修改请求。
 *
 * @param instruction 修改指令
 */
public record ModifyPlanRequest(
        @NotBlank(message = "修改指令不能为空")
        @Size(max = 2000, message = "修改指令不能超过 2000 个字符")
        String instruction
) {
}
