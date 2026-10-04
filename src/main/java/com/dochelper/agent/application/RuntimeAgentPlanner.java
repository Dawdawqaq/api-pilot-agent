package com.dochelper.agent.application;

import java.util.List;

import com.dochelper.agent.config.AgentProperties;
import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.governance.application.ModelDataPolicyService;
import com.dochelper.model.RuntimeModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 依据本轮模型快照切换规划器，原有出站限制和模型审计继续由真实规划器执行。 */
@Component
@Primary
public class RuntimeAgentPlanner implements AgentPlanner {
    private final RuntimeModelProvider models;
    private final DeterministicAgentPlanner offlinePlanner;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final AgentTaskRepository repository;
    private final ModelDataPolicyService policy;
    private final AgentProperties properties;
    private final PlanningSchemaResolver schemas;

    public RuntimeAgentPlanner(RuntimeModelProvider models, DeterministicAgentPlanner offlinePlanner,
            ObjectMapper mapper, Validator validator, AgentTaskRepository repository,
            ModelDataPolicyService policy, AgentProperties properties, PlanningSchemaResolver schemas) {
        this.models = models;
        this.offlinePlanner = offlinePlanner;
        this.mapper = mapper;
        this.validator = validator;
        this.repository = repository;
        this.policy = policy;
        this.properties = properties;
        this.schemas = schemas;
    }

    private AgentPlanner currentPlanner() {
        var snapshot = models.snapshot();
        return snapshot.offline() ? offlinePlanner : new ModelBackedAgentPlanner(snapshot.chatModel(), mapper,
                validator, repository, snapshot.auditProperties(), policy, properties, schemas,
                snapshot.configuration().provider());
    }

    @Override public List<AgentPlanStep> plan(AgentPlanningContext context) { return currentPlanner().plan(context); }
    @Override public List<AgentPlanStep> replan(AgentReplanContext context) { return currentPlanner().replan(context); }
    @Override public List<AgentPlanStep> modify(AgentModifyPlanContext context) { return currentPlanner().modify(context); }
}
