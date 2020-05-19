package io.jaegertracing.internal.clock;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;

import java.lang.reflect.Method;

/**
 * This interface contains is responsible for supplier the current time in microseconds
 * to the {@link SystemClock} implementation. It contains two implementations
 * <ul>
 *     <li>{@link MillisAccuracy} - For when the JDK version is below 9. Calls
 *     {@link System#currentTimeMillis} and multiplies the result
 *     </li>
 *     <li>{@link MicrosAccuracy} - For when the JDK version is equal to or above 9.
 *     It loads and uses java.time types Instant, ChronoUnit and Temporal (available since JDK 1.8)
 *     via reflection in order generate real microsecond accurate timestamp.
 *     </li>
 * </ul>
 * <p>
 *     NOTE: the {@link MicrosAccuracy} implementation pre-generates and stores all reflective accessors on class-loading,
 *     minimizing reflective operations in real-time to just 2 non-parameterized method calls. This are also further optimized by the
 *     JIT compiler and Hot-Spot JVM upon recurring use, so we should not suffer any notable performance penalties here
 * <p>
 * <p>
 *     While it would have been preferable to generate multi-version/release jars to differentiate between the two cases
 *     The project currently targets JDK 1.6, and reworking gradle and release cycle to accommodate for this change would
 *     be too much big of change for now.
 * <p>
 * <p>
 *     Thus, the current implementation remains our best option
 *
 * @author <a href="mailto:ishinberg0@gmail.com">Idan Sheinberg</a>
 * @see System#currentTimeMillis()
 */
interface CurrentTimeSupplier {

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    final class MillisAccuracy implements CurrentTimeSupplier {

        static final CurrentTimeSupplier INSTANCE = new MillisAccuracy();

        @Override
        public long micros() {
            return System.currentTimeMillis() * 1000;
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    final class MicrosAccuracy implements CurrentTimeSupplier {

        static final CurrentTimeSupplier INSTANCE = new MicrosAccuracy();

        private static final Method NOW;
        private static final Object EPOCH;
        private static final Object CHRONO_UNIT_MICROS;
        private static final Method CHRONO_UNIT_BETWEEN;

        static {
            try {
                val classLoader = ClassLoader.getSystemClassLoader();
                val instant = classLoader.loadClass("java.time.Instant");
                val temporal = classLoader.loadClass("java.time.temporal.Temporal");
                val chronoUnit = classLoader.loadClass("java.time.temporal.ChronoUnit");

                NOW = instant.getMethod("now");
                EPOCH = instant.getField("EPOCH").get(null);
                CHRONO_UNIT_MICROS = chronoUnit.getField("MICROS").get(null);
                CHRONO_UNIT_BETWEEN = chronoUnit.getMethod("between", temporal, temporal);
            } catch (Exception x) {
                throw new IllegalStateException("Could not setup microseconds accurate time supplier", x);
            }
        }

        @Override
        public long micros() {
            try {
                val now = NOW.invoke(null);
                return (Long) CHRONO_UNIT_BETWEEN.invoke(CHRONO_UNIT_MICROS, EPOCH, now);
            } catch (Exception x) {
                throw new IllegalStateException("Could not acquire current microseconds accurate timestamp", x);
            }
        }
    }

    long micros();
}
