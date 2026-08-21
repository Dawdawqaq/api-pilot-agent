package com.dochelper.governance.domain;

/**
 * 项目级模型数据出站策略。
 */
public record ProjectModelPolicy(
        Long projectId,
        boolean externalModelAllowed,
        String allowedProvider,
        boolean allowDocumentContent,
        boolean allowSchemaContent,
        int promptCharacterBudget,
        int endpointTopK
) {

    public static ProjectModelPolicy defaults(Long projectId, int endpointTopK) {
        return new ProjectModelPolicy(
                projectId, true, "ANY", true, true, 40000, endpointTopK
        );
    }
}
