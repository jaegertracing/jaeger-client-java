/*
 * Copyright (c) 2017, Uber Technologies, Inc
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
package com.uber.jaeger.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class Java6CompatibleThreadLocalRandomTest {

    @After
    @Before
    public void clearState() throws Exception {
        // As test classes are targeted at 1.7 and gradle 3.x can't be run with Java 6,
        // its safe to say that this test won't ever be executed with JDK 6
        Java6CompatibleThreadLocalRandom.threadLocalRandomPresent = true;
    }

    @Test
    public void testThreadLocalRandomPresent() throws Exception {
        assertSame(Java6CompatibleThreadLocalRandom.current(), ThreadLocalRandom.current());
        assertNotNull(Java6CompatibleThreadLocalRandom.current().nextLong());
    }

    @Test
    public void testThreadLocalRandomNotPresent() throws Exception {
        Java6CompatibleThreadLocalRandom.threadLocalRandomPresent = false;
        assertNotSame(Java6CompatibleThreadLocalRandom.current(), ThreadLocalRandom.current());
        assertNotNull(Java6CompatibleThreadLocalRandom.current().nextLong());
    }

    @Test
    public void testRandomFromOtherThreadIsNotSame() throws Exception {
        Java6CompatibleThreadLocalRandom.threadLocalRandomPresent = false;
        final AtomicReference<Random> randomFromOtherThread = new AtomicReference<>();
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                randomFromOtherThread.set(Java6CompatibleThreadLocalRandom.current());
            }
        });
        thread.start();
        thread.join();
        assertNotSame(Java6CompatibleThreadLocalRandom.current(), randomFromOtherThread.get());
        assertNotNull(Java6CompatibleThreadLocalRandom.current().nextLong());
        assertNotNull(randomFromOtherThread.get());
        assertNotNull(randomFromOtherThread.get().nextLong());
    }

}