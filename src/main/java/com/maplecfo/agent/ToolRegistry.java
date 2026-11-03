package com.maplecfo.agent;

import com.maplecfo.agent.llm.ToolCall;
import com.maplecfo.agent.llm.ToolSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All tools the agent may call, looked up by name. */
public class ToolRegistry {

    private final Map<String, FinanceTool> tools = new LinkedHashMap<>();

    public void register(FinanceTool t) {
        if (tools.containsKey(t.name())) {
            throw new IllegalArgumentException("Tool already registered: " + t.name());
        }
        tools.put(t.name(), t);
    }

    public List<ToolSchema> schemas() {
        return tools.values().stream().map(FinanceTool::toSchema).toList();
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    public ToolResult execute(ToolCall call) {
        FinanceTool t = tools.get(call.name());
        if (t == null) {
            return ToolResult.error(call.id(), "Unknown tool '" + call.name() + "'. Available: " + tools.keySet());
        }
        return t.execute(call.id(), call.arguments());
    }
}
