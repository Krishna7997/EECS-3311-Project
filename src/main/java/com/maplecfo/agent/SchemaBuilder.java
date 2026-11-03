package com.maplecfo.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds small JSON schemas from compact specs such as
 * {@code "account:string!:TFSA|RRSP|FHSA:Which account"} (name : type, "!" = required : enum : description).
 */
final class SchemaBuilder {

    private static final ObjectMapper JSON = new ObjectMapper();

    private SchemaBuilder() {
    }

    static ObjectNode of(String... specs) {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        var required = schema.putArray("required");
        for (String spec : specs) {
            String[] p = spec.split(":", 4);
            String name = p[0];
            boolean req = p[1].endsWith("!");
            String type = req ? p[1].substring(0, p[1].length() - 1) : p[1];
            ObjectNode prop = props.putObject(name);
            prop.put("type", type);
            if (p.length > 2 && !p[2].isEmpty()) {
                var e = prop.putArray("enum");
                for (String v : p[2].split("\\|")) e.add(v);
            }
            if (p.length > 3) prop.put("description", p[3]);
            if (type.equals("number")) prop.put("minimum", 0);
            if (req) required.add(name);
        }
        schema.put("additionalProperties", false);
        return schema;
    }
}
