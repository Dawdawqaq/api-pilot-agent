package com.dochelper.agent.application;

import java.util.List;
import java.util.Objects;

import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.VariableExtractorRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 限制自动修复的权限，防止模型通过更换接口或削弱断言掩盖失败。
 */
@Component
public class AgentPlanRevisionPolicy {
    private final ObjectMapper objectMapper;

    public AgentPlanRevisionPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(List<AgentPlanStep> original, List<AgentPlanStep> revised, int completedCount) {
        if (revised == null || revised.size() != original.size()) {
            reject("自动重规划不能增删步骤");
        }
        for (int index = 0; index < original.size(); index++) {
            AgentPlanStep before = original.get(index);
            AgentPlanStep after = revised.get(index);
            if (after == null || after.request() == null || after.index() != index) {
                reject("重规划步骤结构不合法：" + index);
            }
            if (index < completedCount && !objectMapper.valueToTree(before).equals(objectMapper.valueToTree(after))) {
                reject("重规划修改了已成功步骤：" + index);
            }
            if (!before.request().method().equalsIgnoreCase(after.request().method())
                    || !before.request().path().equals(after.request().path())) {
                reject("自动修复不能更换接口，请重新提交测试目标：" + index);
            }
            // 锁定全部断言，包括变量模板；用户显式修改计划是另一条确认流程。
            if (!Objects.equals(before.request().assertions(), after.request().assertions())) {
                reject("自动修复不能修改或删除原有断言：" + index);
            }
            // 提取路径可以修正，但不能删除失败提取器来掩盖错误或更改变量契约。
            if (!extractorNames(before).equals(extractorNames(after))) {
                reject("自动修复不能增删或重命名原有提取变量：" + index);
            }
        }
    }

    private List<String> extractorNames(AgentPlanStep step) {
        return step.request().extractors() == null ? List.of()
                : step.request().extractors().stream().map(VariableExtractorRequest::name).toList();
    }

    private void reject(String message) {
        throw new BusinessException(AgentErrorCode.INVALID_PLAN, message);
    }
}
