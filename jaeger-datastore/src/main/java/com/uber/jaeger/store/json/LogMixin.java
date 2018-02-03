package com.uber.jaeger.store.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uber.jaeger.LogData;

import java.util.List;
import java.util.stream.Collectors;

public class LogMixin {

    private final LogData data;

    public LogMixin(LogData data) {
        this.data = data;
    }

    @JsonProperty("timestamp")
    public long getTimestamp() {
        return data.getTime();
    }

    @JsonProperty("fields")
    public List<TagMixin> getFields() {
        return data.getFields().entrySet().stream().map(entry -> new TagMixin(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}
