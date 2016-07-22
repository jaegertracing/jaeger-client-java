package com.uber.jaeger.context;

import java.util.concurrent.ExecutorService;

public class TracingUtils {
    private static final TraceContext traceContext = new ThreadLocalTraceContext();

    public static TraceContext getTraceContext() {
        return traceContext;
    }

    public static ExecutorService tracedExecutor(ExecutorService wrappedExecutorService) {
        return new TracedExecutorService(wrappedExecutorService, traceContext);
    }
}
