package com.uber.jaeger.store.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uber.jaeger.Reference;

public class ReferenceMixin {

    private final Reference reference;

    public ReferenceMixin(Reference reference) {
        this.reference = reference;
    }

    @JsonProperty("refType")
    public String getReferenceType() {
        return reference.getType();
    }

    @JsonProperty("traceID")
    public String getTraceId() {
        return String.valueOf(reference.getSpanContext().getTraceId());
    }

    @JsonProperty("spanID")
    public String getSpanId() {
        return String.valueOf(reference.getSpanContext().getSpanId());
    }

}
