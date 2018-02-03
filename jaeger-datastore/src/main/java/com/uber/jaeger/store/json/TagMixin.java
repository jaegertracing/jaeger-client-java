package com.uber.jaeger.store.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TagMixin {

    private final String key;
    private final Object value;

    public TagMixin(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    @JsonProperty("key")
    public String getKey() {
        return key;
    }

    @JsonProperty("value")
    public Object getValue() {
        return value;
    }

    @JsonProperty("type")
    public String getType() {
        if (value instanceof Long || value instanceof Integer) {
            return "int64";
        } else if (value instanceof String) {
            return "string";
        } else if (value instanceof Boolean) {
            return "bool";
        } else if (value instanceof Float || value instanceof Double) {
            return "float64";
        } else {
            return "blob";
        }

    }
}
