package liveness.android;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates events against ui-contract/events-schema.json. Supports the JSON-Schema subset that file uses:
 * type, enum, required, properties, minimum/maximum, local "$ref" into definitions. Unknown properties are
 * allowed (the schema does not set additionalProperties:false), which is how Android adds localized
 * "message", "rtl" and the attestation block.
 */
final class SchemaValidator {

    private final Map<String, Object> root;

    SchemaValidator() {
        Path p = Paths.get("ui-contract", "events-schema.json");
        if (!Files.exists(p)) p = Paths.get("..", "ui-contract", "events-schema.json");
        try {
            root = MiniJson.parseObject(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read events-schema.json from " + p.toAbsolutePath(), e);
        }
    }

    /** Returns a list of violations; empty means valid. */
    List<String> validate(Object instance) {
        List<String> errs = new ArrayList<>();
        check(root, instance, "$", errs);
        return errs;
    }

    @SuppressWarnings("unchecked")
    private void check(Map<String, Object> schema, Object v, String path, List<String> errs) {
        Object ref = schema.get("$ref");
        if (ref != null) {
            String r = (String) ref;
            if (!r.startsWith("#/definitions/")) { errs.add(path + ": unsupported $ref " + r); return; }
            Map<String, Object> defs = (Map<String, Object>) root.get("definitions");
            check((Map<String, Object>) defs.get(r.substring("#/definitions/".length())), v, path, errs);
            return;
        }
        Object type = schema.get("type");
        if (type != null && !typeOk((String) type, v)) {
            errs.add(path + ": expected " + type + " but was " + (v == null ? "null" : v.getClass().getSimpleName()));
            return;
        }
        Object en = schema.get("enum");
        if (en != null && !((List<Object>) en).contains(v)) errs.add(path + ": " + v + " not in enum " + en);
        if (v instanceof Number) {
            Object min = schema.get("minimum"), max = schema.get("maximum");
            double d = ((Number) v).doubleValue();
            if (min != null && d < ((Number) min).doubleValue()) errs.add(path + ": below minimum");
            if (max != null && d > ((Number) max).doubleValue()) errs.add(path + ": above maximum");
        }
        if (v instanceof Map) {
            Map<String, Object> obj = (Map<String, Object>) v;
            Object req = schema.get("required");
            if (req != null) for (Object k : (List<Object>) req) {
                if (!obj.containsKey(k)) errs.add(path + ": missing required '" + k + "'");
            }
            Object addl = schema.get("additionalProperties");
            Object props = schema.get("properties");
            Map<String, Object> pm = props instanceof Map ? (Map<String, Object>) props : java.util.Collections.emptyMap();
            if (Boolean.FALSE.equals(addl)) {
                for (String k : obj.keySet()) {
                    if (!pm.containsKey(k)) {
                        errs.add(path + ": additional property not allowed: '" + k + "'");
                    }
                }
            }
            for (Map.Entry<String, Object> e : obj.entrySet()) {
                Object sub = pm.get(e.getKey());
                if (sub != null) check((Map<String, Object>) sub, e.getValue(), path + "." + e.getKey(), errs);
            }
        }
    }

    private static boolean typeOk(String t, Object v) {
        switch (t) {
            case "object": return v instanceof Map;
            case "array": return v instanceof List;
            case "string": return v instanceof String;
            case "boolean": return v instanceof Boolean;
            case "integer": return v instanceof Long || v instanceof Integer
                    || (v instanceof Double && ((Double) v) == Math.rint((Double) v));
            case "number": return v instanceof Number;
            default: return true;
        }
    }
}
