package com.dochelper.agent.domain;

/**
 * MVP 固定的六个 Agent 工具。
 */
public enum AgentToolName {
    SEARCH_API_DOCUMENT("searchApiDocument"),
    GET_API_SCHEMA("getApiSchema"),
    EXECUTE_HTTP_REQUEST("executeHttpRequest"),
    EXTRACT_RESPONSE_VALUE("extractResponseValue"),
    VALIDATE_RESPONSE("validateResponse"),
    GENERATE_TEST_REPORT("generateTestReport");

    private final String value;

    AgentToolName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
