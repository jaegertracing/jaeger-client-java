package com.uber.jaeger.store.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProcessMixin {

    private final String name;

    private final Map<String, ?> tags;

    public ProcessMixin(String name, Map<String, ?> tags) {
        this.name = name;
        this.tags = tags;
    }

    @JsonProperty("serviceName")
    public String getName() {
        return name;
    }

    @JsonProperty("tags")
    public List<TagMixin> getTags() {
        return tags.entrySet().stream().map(entry -> new TagMixin(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}
