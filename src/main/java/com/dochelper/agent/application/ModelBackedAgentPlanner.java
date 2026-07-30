package com.dochelper.agent.application;

import java.util.List;
import java.util.Set;
import java.time.Duration;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.domain.AgentModelCall;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.model.ModelProviderProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 真实模型 Profile 使用的结构化规划器。
 */
@Component
@Profile("!stub")
public class ModelBackedAgentPlanner implements AgentPlanner {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AgentTaskRepository repository;
    private final ModelProviderProperties modelProperties;

    public ModelBackedAgentPlanner(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            Validator validator,
            AgentTaskRepository repository,
            ModelProviderProperties modelProperties
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.repository = repository;
        this.modelProperties = modelProperties;
    }

    @Override
    public List<AgentPlanStep> plan(AgentPlanningContext context) {
        try {
            String basePrompt = """
                    你是 API 测试计划生成器。只输出严格 JSON 数组，禁止解释、注释和
                    Markdown 代码块。不得编造接口目录中不存在的路径和方法。

                    用户目标：
                    %s

                    可用初始变量名：
                    %s

                    检索证据：
                    %s

                    接口目录：
                    %s

                    每个数组元素必须使用以下结构：
                    {
                      "index": 0,
                      "objective": "步骤目标",
                      "request": {
                        "name": "步骤名称",
                        "method": "GET或POST或PUT或PATCH或DELETE",
                        "path": "/真实接口路径",
                        "pathVariables": {},
                        "queryParams": {},
                        "headers": {},
                        "body": null,
                        "authentication": null,
                        "extractors": [
                          {
                            "name": "accessToken",
                            "jsonPath": "$.data.token"
                          }
                        ],
                        "assertions": [
                          {
                            "type": "STATUS_CODE",
                            "jsonPath": null,
                            "expectedValue": 200,
                            "expectedType": null
                          }
                        ],
                        "dangerousOperationConfirmed": false
                      }
                    }

                    规则：
                    1. index 必须从 0 连续递增。
                    2. 变量模板语法是 {{变量名}}，只能引用可用初始变量名或前序
                       extractors 提取的变量。
                    3. 登录响应令牌应使用 JSONPath 提取，再通过
                       authentication={"type":"BEARER","token":"{{accessToken}}"} 传给后续步骤。
                    4. 断言类型只允许 STATUS_CODE、FIELD_EXISTS、FIELD_NOT_EXISTS、
                       FIELD_TYPE、FIELD_EQUALS、FIELD_CONTAINS。验证字段不存在时
                       必须使用 FIELD_NOT_EXISTS，不要给 FIELD_EXISTS 传 false。
                    5. 不要把密码或令牌明文写入计划。
                    """.formatted(
                    context.goal(),
                    objectMapper.writeValueAsString(context.initialVariableNames()),
                    objectMapper.writeValueAsString(context.evidence()),
                    objectMapper.writeValueAsString(context.endpoints())
            );
            String correction = "";
            String lastError = "模型没有返回合法的结构化计划";
            for (int attempt = 1; attempt <= 2; attempt++) {
                LocalDateTime startedAt = LocalDateTime.now();
                long startNanos = System.nanoTime();
                ChatResponse response;
                try {
                    response = chatModel.call(new Prompt(basePrompt + correction));
                } catch (Exception exception) {
                    saveModelCall(
                            context.taskId(),
                            attempt,
                            null,
                            Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                            "FAILED",
                            "MODEL_CALL_FAILED",
                            safeMessage(exception.getMessage()),
                            startedAt
                    );
                    throw exception;
                }
                String content = response.getResult().getOutput().getText();
                try {
                    List<AgentPlanStep> plan = objectMapper.readValue(
                            stripCodeFence(content),
                            new TypeReference<List<AgentPlanStep>>() {
                            }
                    );
                    List<String> errors = validationErrors(plan);
                    if (errors.isEmpty()) {
                        saveModelCall(
                                context.taskId(),
                                attempt,
                                response,
                                Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                                "SUCCEEDED",
                                null,
                                null,
                                startedAt
                        );
                        return plan;
                    }
                    lastError = String.join("；", errors);
                } catch (JsonProcessingException exception) {
                    lastError = "返回内容不是合法 JSON 数组";
                }
                saveModelCall(
                        context.taskId(),
                        attempt,
                        response,
                        Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                        "FAILED",
                        "INVALID_PLAN",
                        safeMessage(lastError),
                        startedAt
                );
                correction = """

                        上次输出校验失败：%s
                        请重新输出完整 JSON 数组，并严格修正以上问题。
                        """.formatted(lastError);
            }
            throw new BusinessException(
                    AgentErrorCode.PLANNING_FAILED,
                    "模型计划连续两次校验失败：" + safeMessage(lastError)
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                    AgentErrorCode.PLANNING_FAILED,
                    "模型规划调用失败：" + safeMessage(exception.getMessage())
            );
        }
    }

    private void saveModelCall(
            Long taskId,
            int attempt,
            ChatResponse response,
            long durationMs,
            String status,
            String errorCode,
            String errorMessage,
            LocalDateTime startedAt
    ) {
        Usage usage = response == null ? null : response.getMetadata().getUsage();
        String modelName = response == null || response.getMetadata().getModel() == null
                ? modelProperties.chatModel()
                : response.getMetadata().getModel();
        repository.createModelCall(new AgentModelCall(
                IdWorker.getId(),
                taskId,
                modelName,
                status,
                attempt,
                token(usage == null ? null : usage.getPromptTokens()),
                token(usage == null ? null : usage.getCompletionTokens()),
                token(usage == null ? null : usage.getTotalTokens()),
                durationMs,
                errorCode,
                errorMessage,
                startedAt,
                LocalDateTime.now()
        ));
    }

    private int token(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private List<String> validationErrors(List<AgentPlanStep> plan) {
        if (plan == null || plan.isEmpty()) {
            return List.of("计划不能为空");
        }
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        for (int index = 0; index < plan.size(); index++) {
            AgentPlanStep step = plan.get(index);
            if (step == null) {
                errors.add("第 " + index + " 个步骤为空");
                continue;
            }
            if (step.index() != index) {
                errors.add("步骤 index 必须从 0 连续递增");
            }
            if (step.request() == null) {
                errors.add("第 " + index + " 个步骤缺少 request");
                continue;
            }
            Set<ConstraintViolation<ExecutionStepRequest>> violations =
                    validator.validate(step.request());
            int currentIndex = index;
            violations.forEach(violation -> errors.add(
                    "步骤 " + currentIndex + " 的 " + violation.getPropertyPath()
                            + " " + violation.getMessage()
            ));
        }
        return errors;
    }

    private String safeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "未知模型错误";
        }
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    private String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLine = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        return firstLine >= 0 && lastFence > firstLine
                ? trimmed.substring(firstLine + 1, lastFence).trim()
                : trimmed;
    }
}
