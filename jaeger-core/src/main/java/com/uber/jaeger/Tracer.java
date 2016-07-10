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

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.metrics.StatsReporter;
import com.uber.jaeger.propagation.ExtractorFactory;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.samplers.Sampler;
import com.uber.jaeger.utils.Utils;

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

    public io.opentracing.Tracer.SpanBuilder getExtractedSpanBuilder(String operationName,
                                                        TraceContext context,
                                                        HashMap<String, String> baggage) {
        return new ExtractedSpanBuilder(operationName, context, baggage);
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
        }

        public <T> Builder register(Class<T> key, Injector<T> injector) {
            this.registry.register(key, injector);
            return this;
        }

        public <T> Builder register(Class<T> key, ExtractorFactory extractor) {
            this.registry.register(key, extractor);
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
    public <T> void inject(io.opentracing.Span span, T carrier) {
        registry.getInjector(carrier.getClass()).inject(span, carrier);
    }

    @Override
    public <T> io.opentracing.Tracer.SpanBuilder join(T carrier) {
        return registry.getExtractor(carrier.getClass()).provide(this).join(carrier);
    }

    private static class PropagationRegistry {
        private final Map<Class, Injector<?>> injectors = new HashMap<>();
        private final Map<Class, ExtractorFactory> extractors = new HashMap<>();

        Object lookup(Class<?> carrierType, Map<Class, ?> codecMap, String type) {
            Class<?> c = carrierType;
            // match first on concrete classes
            do {
                if (codecMap.containsKey(c)) {
                    return codecMap.get(c);
                }
                c = c.getSuperclass();
            } while (c != null);
            // match second on interfaces
            for (Class<?> iface : carrierType.getInterfaces()) {
                if (codecMap.containsKey(iface)) {
                    return codecMap.get(iface);
                }
            }
            throw new IllegalArgumentException("no registered " + type + " for " + carrierType.getName());
        }

        // Lookup returns an Object reference because it must return both Injectors, and Extractors.
        // In theory this cast is unsafe, but in reality the code won't abuse this lack of type safety.
        @SuppressWarnings("unchecked")
        <T> Injector<T> getInjector(Class<?> carrierType) {
            return (Injector<T>) lookup(carrierType, injectors, "injector");
        }

        // Lookup returns an Object reference because it must return both Injectors, and Extractors.
        // In theory this cast is unsafe, but in reality the code won't abuse this lack of type safety.
        @SuppressWarnings("unchecked")
        <T> ExtractorFactory<T> getExtractor(Class<?> carrierType) {
            return (ExtractorFactory<T>) lookup(carrierType, extractors, "extractor");
        }

        public <T> Injector<T> register(Class<T> carrierType, Injector<?> injector) {
            if (injectors.containsKey(carrierType)) {
                return null;
            }

            // We store injectors with multiple carrierTypes, but only return the injector for the carrierType
            // that we just registered.  Thus this is type unsafe.  However logically we know that the Injector
            // type return the type we just associated with its key.
            @SuppressWarnings("unchecked")
            Injector<T> receivedInjector =  (Injector<T>) injectors.put(carrierType, injector);
            return receivedInjector;
        }

        public <T> ExtractorFactory register(Class<T> carrierType, ExtractorFactory extractor) {
            if (extractors.containsKey(carrierType)) {
                return null;
            }

            return extractors.put(carrierType, extractor);
        }
    }

    private abstract class AbstractSpanBuilder implements io.opentracing.Tracer.SpanBuilder {

        private String operationName = null;
        private long start;
        private final Map<String, Object> tags = new HashMap<>();

        protected abstract Map<String, String> getBaggage();

        protected abstract TraceContext getContext();

        AbstractSpanBuilder(String operationName) {
            this.operationName = operationName;
        }

        @Override
        public io.opentracing.Tracer.SpanBuilder withOperationName(String operationName) {
            this.operationName = operationName;
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

        TraceContext createNewContext() {
            long id = Utils.uniqueID();

            byte flags = 0;
            if (sampler.isSampled(id)) {
                flags |= TraceContext.flagSampled;
                metrics.traceStartedSampled.inc(1);
            } else {
                metrics.traceStartedNotSampled.inc(1);
            }
            return new TraceContext(id, id, 0, flags);
        }

        @Override
        public io.opentracing.Span start() {
            TraceContext context = getContext();

            if (start == 0) {
                start = Utils.getMicroseconds();
            }

            Span span = new Span(Tracer.this, operationName, context, start, tags, getBaggage());
            if (context.isSampled()) {
                metrics.spansSampled.inc(1);
            } else {
                metrics.spansNotSampled.inc(1);
            }
            metrics.spansStarted.inc(1);
            return span;
        }
    }

    /*
    * SpanBuilder is a span builder meant for creating a span from a parent span.
    * This means the SpanBuilder will handle the logic of creating the child span's
    * TraceContext.
    */
    private class SpanBuilder extends AbstractSpanBuilder {
        private Span parent;

        SpanBuilder(String operationName) {
            super(operationName);
        }

        @Override
        protected Map<String, String> getBaggage() {
            if (parent == null) {
                return null;
            }

            return parent.getBaggage();
        }

        @Override
        protected TraceContext getContext() {
            if (parent == null) {
                return super.createNewContext();
            }

            // span must be created with a context, so no NPE possible.
            TraceContext parentContext = parent.getContext();
            return new TraceContext(parentContext.getTraceID(), Utils.uniqueID(), parentContext.getSpanID(), parentContext.getFlags());
        }

        @Override
        public io.opentracing.Tracer.SpanBuilder withParent(io.opentracing.Span parent) {
            this.parent = (Span) parent;
            return this;
        }
    }

    /*
    * ExtractedSpanBuilder is a span builder used for when you want to initialize a span from a TraceContext.
    * Such a situation arises when you deserialize a transport (i.e. an rpc call).
    */
    private class ExtractedSpanBuilder extends AbstractSpanBuilder {
        private final TraceContext context;
        private final Map<String, String> baggage;

        ExtractedSpanBuilder(String operationName, TraceContext context, HashMap<String, String> baggage) {
            super(operationName);
            this.context = context;
            if (context.isSampled()) {
                metrics.tracesJoinedSampled.inc(1);
            } else {
                metrics.tracesJoinedNotSampled.inc(1);
            }
            this.baggage = baggage;
        }

        @Override
        public io.opentracing.Tracer.SpanBuilder withParent(io.opentracing.Span span) {
            throw new IllegalStateException("Cannot call withParent on ExtractedSpanBuilder");
        }

        @Override
        protected Map<String, String> getBaggage() {
            return baggage;
        }

        @Override
        protected TraceContext getContext() {
            return context;
        }
    }
}
