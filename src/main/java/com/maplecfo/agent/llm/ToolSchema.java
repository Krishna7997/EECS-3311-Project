package com.maplecfo.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;

/** What the model is told about a tool: its name, what it does and its JSON input schema. */
public record ToolSchema(String name, String description, JsonNode inputSchema) {
}
