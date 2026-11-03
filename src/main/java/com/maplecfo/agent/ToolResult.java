package com.maplecfo.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * What a finance tool returns to the agent: JSON data with a plain-language
 * "summary", or an error message the model can react to.
 */
public record ToolResult(String callId, boolean success, ObjectNode data, String error) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static ToolResult ok(String callId, ObjectNode data) {
        return new ToolResult(callId, true, data, null);
    }

    public static ToolResult error(String callId, String message) {
        return new ToolResult(callId, false, null, message);
    }

    /** The text sent back to the model. */
    public String toJson() {
        if (success) {
            return data.toString();
        }
        ObjectNode e = JSON.createObjectNode();
        e.put("error", error);
        return e.toString();
    }
}
