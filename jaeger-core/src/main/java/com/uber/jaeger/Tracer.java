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

import com.uber.jaeger.exceptions.UnsupportedFormatException;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.metrics.StatsReporter;
import com.uber.jaeger.propagation.Extractor;
import com.uber.jaeger.propagation.Injector;
import com.uber.jaeger.propagation.TextMapCodec;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.samplers.Sampler;
import com.uber.jaeger.utils.Utils;
import io.opentracing.References;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class Tracer implements io.opentracing.Tracer {
    private final Reporter reporter;
    private final Sampler sampler;
    private final PropagationRegistry registry;
    private final String serviceName;
    private final Metrics metrics;
    private int ip;

    private Tracer(String serviceName, Reporter reporter, Sampler sampler, PropagationRegistry registry, Metrics metrics) {
        this.serviceName = serviceName;
        try {
            this.ip = Utils.ipToInt(Inet4Address.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            this.ip = 0;
        }

        this.reporter = reporter;
        this.sampler = sampler;
        this.registry = registry;
        this.metrics = metrics;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getIP() {
        return ip;
    }

    Reporter getReporter() {
        return reporter;
    }

    void reportSpan(Span span) {
        reporter.report(span);
        metrics.spansFinished.inc(1);
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder buildSpan(String operationName) {
        return new SpanBuilder(operationName);
    }

    @Override
    public <T> void inject(io.opentracing.SpanContext spanContext, Format<T> format, T carrier) {
        Injector<T> injector = registry.getInjector(format);
        if (injector == null) {
            throw new UnsupportedFormatException(format);
        }
        injector.inject((SpanContext) spanContext, carrier);
    }

    @Override
    public <T> io.opentracing.SpanContext extract(Format<T> format, T carrier) {
        Extractor<T> extractor = registry.getExtractor(format);
        if (extractor == null) {
            throw new UnsupportedFormatException(format);
        }
        return extractor.extract(carrier);
    }

    private class SpanBuilder implements io.opentracing.Tracer.SpanBuilder {

        private String operationName = null;
        private long start;
        private SpanContext parent;
        private final Map<String, Object> tags = new HashMap<>();

        SpanBuilder(String operationName) {
            this.operationName = operationName;
        }

        @Override
        public io.opentracing.Tracer.SpanBuilder asChildOf(io.opentracing.SpanContext parent) {
            return addReference(References.CHILD_OF, parent);
        }

        @Override
        public io.opentracing.Tracer.SpanBuilder asChildOf(io.opentracing.Span parent) {
            return addReference(References.CHILD_OF, parent.context());
        }

        @Override
        public io.opentracing.Tracer.SpanBuilder addReference(String referenceType, io.opentracing.SpanContext referencedContext) {
            if (parent == null && (
                    referenceType == References.CHILD_OF || referenceType == References.FOLLOWS_FROM)) {
                this.parent = (SpanContext) referencedContext;
            }
            return this;
        }

        @Override
        public io.opentracing.Tracer.SpanBuilder withTag(String key, String value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public io.opentracing.Tracer.SpanBuilder withTag(String key, boolean value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public io.opentracing.Tracer.SpanBuilder withTag(String key, Number value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public io.opentracing.Tracer.SpanBuilder withStartTimestamp(long microseconds) {
            this.start = microseconds;
            return this;
        }

        private SpanContext createNewContext() {
            long id = Utils.uniqueID();

            byte flags = 0;
            if (sampler.isSampled(id)) {
                flags |= SpanContext.flagSampled;
                metrics.traceStartedSampled.inc(1);
            } else {
                metrics.traceStartedNotSampled.inc(1);
            }
            return new SpanContext(id, id, 0, flags);
        }

        private SpanContext createChildContext() {
            // For server-side RPC spans we reuse spanID per Zipkin convention
            if (tags.get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_SERVER) {
                if (parent.isSampled()) {
                    metrics.tracesJoinedSampled.inc(1);
                } else {
                    metrics.tracesJoinedNotSampled.inc(1);
                }
                return parent;
            }
            return new SpanContext(
                    parent.getTraceID(),
                    Utils.uniqueID(),
                    parent.getSpanID(),
                    parent.getFlags(),
                    parent.baggage());
        }

        @Override
        public io.opentracing.Span start() {
            SpanContext context = parent == null ? createNewContext() : createChildContext();

            if (start == 0) {
                start = Utils.getMicroseconds();
            }

            Span span = new Span(Tracer.this, operationName, context, start, tags);
            if (context.isSampled()) {
                metrics.spansSampled.inc(1);
            } else {
                metrics.spansNotSampled.inc(1);
            }
            metrics.spansStarted.inc(1);
            return span;
        }
    }

    public static final class Builder {
        private final Sampler sampler;
        private final Reporter reporter;
        private final PropagationRegistry registry = new PropagationRegistry();
        private Metrics metrics;
        private String serviceName;

        public Builder(String serviceName, Reporter reporter, Sampler sampler) {
            this.serviceName = serviceName;
            this.reporter = reporter;
            this.sampler = sampler;
            this.metrics = new Metrics(new StatsFactoryImpl(new NullStatsReporter()));

            TextMapCodec textMapCodec = new TextMapCodec(false);
            this.registerInjector(Format.Builtin.TEXT_MAP, textMapCodec);
            this.registerExtractor(Format.Builtin.TEXT_MAP, textMapCodec);
            // TODO for now we register the same codec for HTTP_HEADERS
            TextMapCodec httpCodec = new TextMapCodec(true);
            this.registerInjector(Format.Builtin.HTTP_HEADERS, httpCodec);
            this.registerExtractor(Format.Builtin.HTTP_HEADERS, httpCodec);
            // TODO binary codec not implemented
        }

        public <T> Builder registerInjector(Format<T> format, Injector<T> injector) {
            this.registry.register(format, injector);
            return this;
        }

        public <T> Builder registerExtractor(Format<T> format, Extractor<T> extractor) {
            this.registry.register(format, extractor);
            return this;
        }

        public Builder withStatsReporter(StatsReporter statsReporter) {
            this.metrics = new Metrics(new StatsFactoryImpl(statsReporter));
            return this;
        }

        Builder withMetrics(Metrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Tracer build() {
            return new Tracer(this.serviceName, reporter, sampler, registry, metrics);
        }
    }

    private static class PropagationRegistry {
        private final Map<Format<?>, Injector<?>> injectors = new HashMap<>();
        private final Map<Format<?>, Extractor<?>> extractors = new HashMap<>();

        @SuppressWarnings("unchecked")
        <T> Injector<T> getInjector(Format<T> format) {
            return (Injector<T>) injectors.get(format);
        }

        @SuppressWarnings("unchecked")
        <T> Extractor<T> getExtractor(Format<T> format) {
            return (Extractor<T>) extractors.get(format);
        }

        public <T> void register(Format<T> format, Injector<T> injector) {
            injectors.put(format, injector);
        }

        public <T> void register(Format<T> format, Extractor<T> extractor) {
            extractors.put(format, extractor);
        }
    }
}
