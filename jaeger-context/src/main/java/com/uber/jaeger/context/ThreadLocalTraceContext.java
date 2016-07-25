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
package com.uber.jaeger.context;

import io.opentracing.Span;

import java.util.EmptyStackException;
import java.util.Stack;

public class ThreadLocalTraceContext implements TraceContext {
    private final ThreadLocal<Stack<Span>> threadLocal = new ThreadLocal<Stack<Span>>() {
        @Override public Stack<Span> initialValue() {
            return new Stack<Span>();
        }
    };

    public void push(Span span) {
        if (span == null) {
            throw new NullPointerException();
        }

        Stack<Span> spanStack = threadLocal.get();
        spanStack.push(span);
    }

    public boolean isEmpty() {
        return threadLocal.get().isEmpty();
    }

    public Span pop() throws EmptyStackException {
        return threadLocal.get().pop();
    }

    public Span getCurrentSpan() throws EmptyStackException {
        return threadLocal.get().peek();
    }
}
