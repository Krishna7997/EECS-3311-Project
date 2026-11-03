package com.maplecfo.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.maplecfo.agent.llm.ToolSchema;

/**
 * A capability the AI agent may use. Every number the agent reports comes
 * from a tool backed by tested Java code (Stage 1 decision D1).
 */
public interface FinanceTool {

    String name();

    String description();

    /** JSON schema of the arguments. */
    JsonNode schema();

    ToolResult execute(String callId, JsonNode args);

    default ToolSchema toSchema() {
        return new ToolSchema(name(), description(), schema());
    }
}
