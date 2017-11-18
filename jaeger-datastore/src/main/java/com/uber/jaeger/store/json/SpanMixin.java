package com.uber.jaeger.store.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uber.jaeger.Span;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Jackson mixin for defining how a {@link com.uber.jaeger.Span} gets turned into JSON
 * This was built using the fixtures from Jaeger codebase --
 * The usefulness of this class would be if as part of your infrastructure there are already ways to get log data
 * injested into specific Jaeger datastore (i.e. ElasticSearch). You can use this mixin to right the final expected
 * data to go directly to it.
 */
public class SpanMixin {

    private final Span span;

    public SpanMixin(Span span) {
        this.span = span;
    }

    @JsonProperty("traceID")
    public String getTraceId() {
        return String.valueOf(span.context().getTraceId());
    }

    @JsonProperty("spanID")
    public String getSpanId() {
        return String.valueOf(span.context().getSpanId());
    }

    @JsonProperty("operationName")
    public String getOperationName() {
        return span.getOperationName();
    }

    @JsonProperty("flags")
    public int getFlags() {
        return (int) span.context().getFlags();
    }

    @JsonProperty("parentSpanID")
    public String getParentSpanId() {
        return String.valueOf(span.context().getParentId());
    }

    @JsonProperty("references")
    public List<ReferenceMixin> getReferences() {
        return span.getReferences().stream().map(ReferenceMixin::new).collect(Collectors.toList());
    }

    @JsonProperty("startTime")
    public long getStartTime() {
        return span.getStart();
    }

    @JsonProperty("duration")
    public long getDuration() {
        return span.getDuration();
    }

    @JsonProperty("tags")
    public List<TagMixin> getTags() {
        return span.getTags().entrySet().stream().map(entry -> new TagMixin(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    @JsonProperty("logs")
    public List<LogMixin> getLogs() {
        return Optional.ofNullable(span.getLogs())
                .map(logs -> logs.stream().map(LogMixin::new).collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @JsonProperty("process")
    public ProcessMixin getProcess() {
        return new ProcessMixin(span.getTracer().getServiceName(), span.getTracer().tags());
    }

}
