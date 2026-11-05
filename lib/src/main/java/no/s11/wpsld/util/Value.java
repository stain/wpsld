package no.s11.wpsld.util;

import java.util.Objects;
import java.util.function.Supplier;

public interface Value<T> {
    T get();

    static <T> Value<T> of(T obj) {
        if (obj == null) { 
            throw new NullPointerException("Value must be non-null");
        }
        return new Value<T>() {
            @Override
            public T get() {
                return obj;
            }
        };
    }

    /**
     * A value computed on first {@link #get()} and then kept, e.g. for
     * traversals of an unchanging schema. The supplier must not return null.
     */
    static <T> Value<T> lazy(Supplier<T> supplier) {
        Objects.requireNonNull(supplier);
        return new Value<T>() {
            private volatile T value;

            @Override
            public T get() {
                T v = value;
                if (v == null) {
                    synchronized (this) {
                        v = value;
                        if (v == null) {
                            v = Objects.requireNonNull(supplier.get(), "Lazy value must be non-null");
                            value = v;
                        }
                    }
                }
                return v;
            }
        };
    }
}
