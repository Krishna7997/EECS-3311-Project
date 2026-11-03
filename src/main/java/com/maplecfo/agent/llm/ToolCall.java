package com.maplecfo.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/** The model asking to run one tool, e.g. contribution_room with {"account":"TFSA"}. */
public record ToolCall(String id, String name, JsonNode arguments) {

    public ToolCall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arguments, "arguments");
    }
}
