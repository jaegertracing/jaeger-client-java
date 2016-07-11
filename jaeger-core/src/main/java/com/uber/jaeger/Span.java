/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.uber.jaeger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.twitter.zipkin.thriftjava.Endpoint;
import com.uber.jaeger.utils.Utils;
import io.opentracing.tag.Tags;

public class Span implements io.opentracing.Span {
    private final Tracer tracer;
    private final long start; // time in microseconds
    private final String operationName;
    private TraceContext context;
    private Map<String, String> baggage;
    private Endpoint peer;
    private long duration; // time in microseconds
    private Map<String,Object> tags;
    private List<LogData> logs;
    private String localComponent;
    private boolean isClient;
    private boolean isRPC;

    public String getLocalComponent() {
        return localComponent;
    }

    public long getStart() {
        return start;
    }

    public long getDuration() {
        synchronized (this) {
            return duration;
        }
    }

    public Tracer getTracer() {
        return tracer;
    }

    public Endpoint getPeer() {
        synchronized (this) {
            if (peer == null) {
                peer = new Endpoint();
            }
        }

        return peer;
    }

    public Map<String, Object> getTags() {
        synchronized (this) {
            return new HashMap<>(tags);
        }
    }

    public String getOperationName() {
        return operationName;
    }

    public List<LogData> getLogs() {
        synchronized (this) {
            if (logs == null) {
                return null;
            }

            return new ArrayList<>(logs);
        }
    }

    public Map<String, String> getBaggage() {
        synchronized(this) {
            if(baggage == null) {
                return null;
            }

            return new HashMap<>(baggage);
        }
    }

    // This is public since extractors, and injectors will need access to a span's context.
    public TraceContext getContext() {
        // doesn't need to be a copy since all fields are final
        return this.context;
    }

    Span(Tracer tracer, String operationName, TraceContext context, long start, Map<String, Object> tags, Map<String, String> baggage) {
        this.tracer = tracer;
        this.operationName = operationName;
        this.context = context;
        this.start = start;
        this.tags = tags;
        this.baggage = baggage;
    }

    @Override
    public io.opentracing.Span setBaggageItem(String key, String value) {
        String normalizedKey = Utils.normalizeBaggageKey(key);

        synchronized (this) {
            if (baggage == null) {
                baggage = new HashMap<>();
            }

            baggage.put(normalizedKey, value);
            return this;
        }
    }

    @Override
    public String getBaggageItem(String key) {
        if (baggage == null) {
            return null;
        }

        String normalizedKey = Utils.normalizeBaggageKey(key);
        synchronized (this) {
            return baggage.get(normalizedKey);
        }
    }

    public String toString() {
        // no synchronization needed because context fields are immutable.
        return context.contextAsString() + " - " + operationName;
    }

    @Override
    public void finish() {
        long finish = Utils.getMicroseconds();

        synchronized(this) {
            this.duration = finish - start;
        }

        if (context.isSampled()) {
            tracer.reportSpan(this);
        }
    }

    @Override
    public void close() {
        finish();
    }

    @Override
    public Span setTag(String key, String value) {
        return setTagAsObject(key, value);
    }

    @Override
    public Span setTag(String key, boolean value) {
        return setTagAsObject(key, value);
    }

    @Override
    public Span setTag(String key, Number value) {
        return setTagAsObject(key, value);
    }

    private boolean handleSpecialTag(String key, Object value) {
        if (key.equals(Tags.COMPONENT.getKey()) && value instanceof String) {
            localComponent = (String) value;
            return true;
        }

        if (key.equals(Tags.PEER_HOST_IPV4.getKey()) && value instanceof Integer) {
            getPeer().setIpv4((Integer) value);
            return true;
        }

        if (key.equals(Tags.PEER_PORT.getKey()) && value instanceof Number) {
            getPeer().setPort(((Number) value).shortValue());
            return true;
        }

        if (key.equals(Tags.PEER_SERVICE.getKey()) && value instanceof String) {
            getPeer().setService_name((String) value);
            return true;
        }

        if (key.equals(Tags.SPAN_KIND.getKey()) && value instanceof String) {
            isClient = Tags.SPAN_KIND_CLIENT.equals(value);
            isRPC = true;
            return true;
        }

        return false;
    }

    private Span setTagAsObject(String key, Object value) {
        synchronized (this) {
            if (key.equals(Tags.SAMPLING_PRIORITY.getKey()) && (value instanceof Number)) {
                int priority = ((Number) value).intValue();
                byte newFlags;
                if (priority > 0) {
                    newFlags = (byte) (context.getFlags() | TraceContext.flagSampled | TraceContext.flagDebug);
                } else {
                    newFlags = (byte) (context.getFlags() & (~TraceContext.flagSampled));
                }

                context = new TraceContext(
                        context.getTraceID(),
                        context.getSpanID(),
                        context.getParentID(),
                        newFlags
                );
            }

            if (context.isSampled()) {
                if (!handleSpecialTag(key, value)) {
                    if (this.tags == null) {
                        this.tags = new HashMap<>();
                    }

                    tags.put(key, value);
                }
            }
        }

        return this;
    }

    @Override
    public Span log(String message, /* @Nullable */ Object payload) {
        return log(Utils.getMicroseconds(), message, payload);
    }

    @Override
    public Span log(long instantMicroseconds, String message, /* @Nullable */ Object payload) {
        // TODO(oibe) maybe use a concurrent structure instead of synchronizing
        synchronized (this) {
            if (context.isSampled()) {
                if (logs == null) {
                    this.logs = new ArrayList<>();
                }

                logs.add(new LogData(instantMicroseconds, message, payload));
            }
            return this;
        }
    }

    public boolean isRPC() {
        return isRPC;
    }

    public boolean isRPCClient() {
        return isClient;
    }
}
