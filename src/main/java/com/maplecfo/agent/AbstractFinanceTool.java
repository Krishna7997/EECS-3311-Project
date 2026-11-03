package com.maplecfo.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.domain.Money;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Template Method pattern. {@link #execute} is fixed: validate the arguments
 * against the declared schema, run the tool, and turn any failure into an
 * error result the model can see. Subclasses only supply
 * {@link #doExecute} (and optionally extra checks), so no tool can skip
 * validation (Stage 1 decision D9).
 */
public abstract class AbstractFinanceTool implements FinanceTool {

    protected static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public final ToolResult execute(String callId, JsonNode args) {
        JsonNode a = args == null || args.isNull() ? JSON.createObjectNode() : args;
        List<String> problems = validateArgs(a);
        if (!problems.isEmpty()) {
            return ToolResult.error(callId, "Invalid arguments: " + String.join("; ", problems));
        }
        try {
            return ToolResult.ok(callId, doExecute(a));
        } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException e) {
            return ToolResult.error(callId, e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error(callId, "The tool failed: " + e.getClass().getSimpleName());
        }
    }

    /** Checks required fields and types from {@link #schema()}. Subclasses may add rules. */
    protected List<String> validateArgs(JsonNode args) {
        List<String> problems = new ArrayList<>();
        JsonNode props = schema().path("properties");
        for (JsonNode req : schema().path("required")) {
            if (!args.hasNonNull(req.asText())) {
                problems.add("missing '" + req.asText() + "'");
            }
        }
        args.fieldNames().forEachRemaining(f -> {
            JsonNode spec = props.get(f);
            if (spec == null) {
                problems.add("unknown argument '" + f + "'");
                return;
            }
            JsonNode v = args.get(f);
            String type = spec.path("type").asText();
            if (type.equals("number") && !v.isNumber()) problems.add("'" + f + "' must be a number");
            if (type.equals("string") && !v.isTextual()) problems.add("'" + f + "' must be text");
            if (spec.has("enum") && v.isTextual()) {
                boolean ok = false;
                for (JsonNode e : spec.get("enum")) ok |= e.asText().equalsIgnoreCase(v.asText());
                if (!ok) problems.add("'" + f + "' must be one of " + spec.get("enum"));
            }
            if (type.equals("number") && v.isNumber() && spec.has("minimum") && v.asDouble() < spec.get("minimum").asDouble()) {
                problems.add("'" + f + "' must be at least " + spec.get("minimum"));
            }
        });
        return problems;
    }

    /** Runs the tool. The returned object must contain a plain-language "summary". */
    protected abstract ObjectNode doExecute(JsonNode args);

    // ------------------------------------------------------------- helpers

    protected static ObjectNode schemaOf(String... propertySpecs) {
        return SchemaBuilder.of(propertySpecs);
    }

    protected static Money money(JsonNode args, String field, Money defaultValue) {
        return args.hasNonNull(field) ? Money.of(args.get(field).decimalValue()) : defaultValue;
    }

    protected static YearMonth month(JsonNode args, String field, YearMonth defaultValue) {
        if (!args.hasNonNull(field)) return defaultValue;
        try {
            return YearMonth.parse(args.get(field).asText().trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'" + field + "' must look like 2026-10");
        }
    }

    protected static String upper(JsonNode args, String field, String defaultValue) {
        return args.hasNonNull(field) ? args.get(field).asText().trim().toUpperCase(Locale.ROOT) : defaultValue;
    }

    protected static ObjectNode obj() {
        return JSON.createObjectNode();
    }

    protected static ArrayNode arr() {
        return JSON.createArrayNode();
    }
}
